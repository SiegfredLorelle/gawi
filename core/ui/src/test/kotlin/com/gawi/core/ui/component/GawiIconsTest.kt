package com.gawi.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The vendored icons are shaped like drawables that will actually draw.
 *
 * Plain JVM and reading the files off disk, because this module's tests are
 * deliberately Robolectric-free (`build.gradle.kts`) and the precedent is
 * `GawiTypographyTest`, which checks the bundled font's `sfnt` signature the
 * same way. The alternative was adding Robolectric and Compose's test artifact
 * to `:core:ui` to inflate ten vectors, which is a large dependency for a
 * question about generated XML.
 *
 * **What this cannot prove, stated so nobody reads it as more than it is: that
 * the picture is right.** `pathData` that parses but draws a scribble passes
 * every assertion here. The drawing is a by-hand check —
 * `docs/running.md` §4 — and always will be.
 *
 * **What it does prove is the failure that is otherwise invisible.** A `<path>`
 * with `pathData` and no `strokeColor` inflates without complaint and draws
 * nothing at all, and because [GawiIconButton] names its control through
 * `contentDescription`, every existing semantics test still passes on a button
 * that renders empty. That is the exact shape of the "verification that passes
 * without proving anything" this project keeps finding, so it is pinned here
 * rather than left to the eye.
 *
 * The other half is `fillColor`. Lucide is `fill="none"` throughout, and three
 * of these ten — `chart-pie`, `pencil`, `settings` — are closed outlines that a
 * stray fill would turn into a solid blob rather than an obviously broken one.
 */
class GawiIconsTest {

    private companion object {
        /**
         * Read as a file rather than a resource, as `GawiTypographyTest` does:
         * a unit test's working directory is the module, and generated XML is
         * not on the test classpath in a form that keeps the attributes.
         */
        val DRAWABLES = File("src/main/res/drawable")

        /**
         * The marker every generated file carries in its header, and the reason
         * this suite sweeps a directory rather than a list of ten names.
         *
         * Without it the shape assertions below apply to *anything* ending
         * `.xml` in `res/drawable`, so `:core:ui` could never hold a drawable
         * that is not a Lucide icon — a background, a shape, or the sort of
         * alpha-only notification icon §7.5 describes — without three tests
         * going red about the wrong file. Filtering on the generator's own
         * header keeps the set self-maintaining instead of writing the ten names
         * down a fourth time, after `GawiIcons`, the generator's `ICONS` and
         * [MIRRORED] below.
         */
        const val GENERATED_BY = "scripts/convert-lucide.py"

        const val VIEWPORT = "24"
        const val SIZE = "24dp"
        const val STROKE_WIDTH = "2"
        const val ROUND = "round"

        /**
         * `VectorDrawable` has no element for any of these, so the converter
         * either translates one to path data or fails. Asserted on the output
         * too, because a conversion that silently dropped an element would
         * leave a valid file that is missing part of its drawing.
         */
        val UNSUPPORTED = listOf("circle", "rect", "line", "polyline", "polygon", "ellipse")

        /**
         * The icons that must flip under an RTL layout direction, named here a
         * second time rather than imported from the generator — two
         * independent statements that have to agree is the whole point.
         *
         * The three the app navigates with replaced `←` (U+2190), `‹` (U+2039)
         * and `›` (U+203A), which are all `Bidi_Mirrored`: the text shaper
         * flipped them, and a `VectorDrawable` without the attribute does not.
         * Missing it was a regression rather than a gap, and nothing here
         * caught it — this test is what review turned into a check.
         * `ic_list_checks` is consistency rather than regression: `☰` is
         * symmetric, but the icon leads with marks and follows with rules.
         */
        val MIRRORED = setOf(
            "ic_arrow_left.xml",
            "ic_chevron_left.xml",
            "ic_chevron_right.xml",
            "ic_list_checks.xml",
        )
    }

    private fun icons(): List<File> {
        // The guard first, and it is not decoration: every assertion below is
        // a loop over this list, so an empty or wrong directory would make the
        // whole class pass while checking nothing.
        assertTrue(
            "expected the drawables at ${DRAWABLES.absolutePath} — if this path is wrong the tests prove nothing",
            DRAWABLES.isDirectory,
        )
        val files = DRAWABLES.listFiles { file -> file.name.endsWith(".xml") }.orEmpty()
            .filter { GENERATED_BY in it.readText() }
            .sortedBy { it.name }
        // Still guarded, and now for two failures rather than one: a wrong
        // directory, and a generator header that stopped saying what this
        // filter looks for — which would otherwise narrow the sweep to nothing
        // and pass.
        assertTrue("no generated drawables found in ${DRAWABLES.absolutePath}", files.isNotEmpty())
        return files
    }

    /**
     * Namespace-unaware on purpose, so attributes read as the file spells
     * them: "android:strokeWidth" rather than a resolved URI.
     */
    private fun document(file: File): Document = DocumentBuilderFactory.newInstance()
        // Hardening rather than a fix: these ten files are generated from a
        // fixed template that copies only `d`, so nothing from a source
        // SVG's prolog can reach them and there is no live entity to
        // resolve. It is one line, it is the default static analysis flags,
        // and it leaves the namespace-unaware behaviour above intact.
        .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        .newDocumentBuilder()
        .parse(file)

    private fun paths(file: File): List<Element> {
        val nodes = document(file).getElementsByTagName("path")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun `every drawable is exposed through GawiIcons, and every exposure is distinct`() {
        // Compilation already guarantees the other direction: a GawiIcons entry
        // naming a drawable that does not exist is not a test failure, it is a
        // build failure. This is the orphan case — a file added to res/ that no
        // call site can reach.
        assertEquals(
            "GawiIcons.all and res/drawable disagree: ${icons().map { it.name }}",
            icons().size,
            GawiIcons.all.size,
        )
        assertEquals("GawiIcons.all repeats an id", GawiIcons.all.size, GawiIcons.all.toSet().size)
    }

    @Test
    fun `every icon is a 24dp square on a 24 unit viewport`() {
        icons().forEach { file ->
            val vector = document(file).documentElement
            assertEquals("${file.name} root", "vector", vector.tagName)
            assertEquals("${file.name} width", SIZE, vector.getAttribute("android:width"))
            assertEquals("${file.name} height", SIZE, vector.getAttribute("android:height"))
            assertEquals("${file.name} viewportWidth", VIEWPORT, vector.getAttribute("android:viewportWidth"))
            assertEquals("${file.name} viewportHeight", VIEWPORT, vector.getAttribute("android:viewportHeight"))
        }
    }

    @Test
    fun `every path is stroked, opaquely, and none is filled`() {
        icons().forEach { file ->
            val paths = paths(file)
            assertTrue("${file.name} draws nothing", paths.isNotEmpty())
            paths.forEachIndexed { index, path ->
                val where = "${file.name} path $index"
                // A moveto rather than merely non-blank. `isNotBlank` accepted
                // the literal "None", which is what an SVG `<path>` with no `d`
                // used to generate — non-blank, and not a path. Every SVG path
                // opens with a moveto, and all twenty here do.
                val data = path.getAttribute("android:pathData")
                assertTrue(
                    "$where pathData does not open with a moveto: '$data'",
                    data.startsWith("M") || data.startsWith("m"),
                )

                // The invisible-icon failure. A missing strokeColor is not a
                // parse error and Compose's tint cannot rescue it: ColorFilter
                // recolours what was drawn, and nothing was.
                val stroke = path.getAttribute("android:strokeColor")
                assertEquals("$where strokeColor is not an 8-digit ARGB literal: '$stroke'", 9, stroke.length)
                assertEquals("$where strokeColor is not opaque: '$stroke'", "FF", stroke.substring(1, 3).uppercase())

                assertEquals("$where strokeWidth", STROKE_WIDTH, path.getAttribute("android:strokeWidth"))
                assertEquals("$where strokeLineCap", ROUND, path.getAttribute("android:strokeLineCap"))
                assertEquals("$where strokeLineJoin", ROUND, path.getAttribute("android:strokeLineJoin"))

                // Absent, not transparent: VectorDrawable's default is no fill,
                // and saying so with a literal would invite someone to change it.
                assertEquals("$where is filled; Lucide is fill=none", "", path.getAttribute("android:fillColor"))
            }
        }
    }

    @Test
    fun `exactly the directional icons flip under RTL`() {
        // Asserted both ways round. A directional icon that lost the attribute
        // reads backwards in Arabic or Hebrew, and a symmetric one that gained
        // it is a gear that spins the wrong way for no reason — neither shows
        // up in a light-mode English screenshot, which is all this suite and
        // every device check so far has looked at.
        icons().forEach { file ->
            assertEquals(
                "${file.name} android:autoMirrored",
                file.name in MIRRORED,
                document(file).documentElement.getAttribute("android:autoMirrored") == "true",
            )
        }
    }

    @Test
    fun `no icon carries an element VectorDrawable cannot draw`() {
        icons().forEach { file ->
            val document = document(file)
            UNSUPPORTED.forEach { tag ->
                assertEquals(
                    "${file.name} carries <$tag>, which VectorDrawable cannot draw — convert it to path data",
                    0,
                    document.getElementsByTagName(tag).length,
                )
            }
        }
    }
}
