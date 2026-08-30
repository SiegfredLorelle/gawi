package com.gawi.app

import androidx.compose.ui.graphics.toArgb
import com.gawi.core.ui.component.MomoPalette
import com.gawi.core.ui.theme.gawiLauncherBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The launcher icon and the reminder icon are shaped like drawables that draw.
 *
 * Read off disk the way `GawiIconsTest` reads the Lucide set, and for the same
 * reason: a `<path>` with `pathData` and no colour inflates without complaint
 * and draws nothing, and nothing else in the build notices. That is a
 * transparent launcher icon shipped with lint green. What this cannot prove is
 * that the picture is Momo — `docs/running.md` §4 looks at it.
 *
 * The colour case is the `WindowBackgroundTest` argument again: an adaptive
 * icon is XML, so `@color/ic_launcher_background` is the second scheme colour
 * that has to exist twice, and a comment asking for both to change is not a
 * mechanism. Robolectric is needed only there, and for the merged manifest.
 */
@RunWith(RobolectricTestRunner::class)
class LauncherIconTest {

    private companion object {
        val RES = File("src/main/res")
        val MANIFEST = File("src/main/AndroidManifest.xml")
        val FOREGROUND = RES.resolve("drawable/ic_launcher_foreground.xml")
        val MONOCHROME = RES.resolve("drawable/ic_launcher_monochrome.xml")
        val REMINDER = RES.resolve("drawable/ic_reminder.xml")
        val ADAPTIVE = RES.resolve("mipmap-anydpi/ic_launcher.xml")

        /** The adaptive icon's canvas, and the size the monochrome layer must match. */
        const val ADAPTIVE_SIZE = "108"
        const val WHITE = "#FFFFFFFF"
    }

    @Test
    fun `the manifest points the icon and the round icon at the adaptive icon`() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        // The merged manifest, which is what ships; sym_def_app_icon was the placeholder.
        assertEquals("android:icon", R.mipmap.ic_launcher, info.icon)
        // ApplicationInfo.roundIconRes is not public API, so the round icon is read
        // off the source manifest instead — one attribute, one file, no merge involved.
        val application = document(MANIFEST).getElementsByTagName("application").item(0) as Element
        assertEquals("android:roundIcon", "@mipmap/ic_launcher", application.getAttribute("android:roundIcon"))
    }

    @Test
    fun `the adaptive icon declares all three layers in the one file`() {
        // One file, no -v33 twin: lint's MonochromeLauncherIcon fails an adaptive
        // icon without the layer, and does not count the element as unused.
        val icon = document(ADAPTIVE).documentElement
        assertEquals("adaptive-icon", icon.tagName)
        assertEquals("@color/ic_launcher_background", layer(icon, "background"))
        assertEquals("@drawable/ic_launcher_foreground", layer(icon, "foreground"))
        assertEquals("@drawable/ic_launcher_monochrome", layer(icon, "monochrome"))
        assertTrue("a -v33 variant would be a second copy to keep in step", !RES.resolve("mipmap-anydpi-v33").exists())
    }

    @Test
    fun `the icon's ground is the scheme's primaryContainer, not a third value`() {
        val context = RuntimeEnvironment.getApplication()
        val resolved = context.getColor(R.color.ic_launcher_background)
        assertEquals(
            "ic_launcher_background and GawiLightColors.primaryContainer disagree",
            Integer.toHexString(gawiLauncherBackground().toArgb()),
            Integer.toHexString(resolved),
        )
    }

    @Test
    fun `both launcher layers are drawn on the adaptive icon's 108 grid`() {
        listOf(FOREGROUND, MONOCHROME).forEach { file ->
            val vector = document(file).documentElement
            assertEquals("${file.name} root", "vector", vector.tagName)
            assertEquals("${file.name} width", "${ADAPTIVE_SIZE}dp", vector.getAttribute("android:width"))
            assertEquals("${file.name} height", "${ADAPTIVE_SIZE}dp", vector.getAttribute("android:height"))
            assertEquals("${file.name} viewportWidth", ADAPTIVE_SIZE, vector.getAttribute("android:viewportWidth"))
            assertEquals("${file.name} viewportHeight", ADAPTIVE_SIZE, vector.getAttribute("android:viewportHeight"))
        }
    }

    @Test
    fun `every path in every icon draws something`() {
        listOf(FOREGROUND, MONOCHROME, REMINDER).forEach { file ->
            val paths = paths(file)
            assertTrue("${file.name} has no paths", paths.isNotEmpty())
            paths.forEachIndexed { index, path ->
                val where = "${file.name} path $index"
                val data = path.getAttribute("android:pathData")
                assertTrue("$where pathData does not open with a moveto: '$data'", data.startsWith("M"))
                val fill = path.getAttribute("android:fillColor")
                val stroke = path.getAttribute("android:strokeColor")
                // The invisible-icon failure: neither attribute is a parse error.
                assertTrue("$where has neither fillColor nor strokeColor, so it draws nothing", fill.isNotEmpty() || stroke.isNotEmpty())
                // And its quieter cousins, the GawiIconsTest idiom: a colour with a
                // 00 alpha or a zero stroke width also draws nothing, green.
                listOf(fill, stroke).filter { it.isNotEmpty() }.forEach { colour ->
                    assertEquals("$where colour is not an 8-digit ARGB literal: '$colour'", 9, colour.length)
                    assertEquals("$where colour is not opaque: '$colour'", "FF", colour.substring(1, 3).uppercase())
                }
                if (stroke.isNotEmpty()) {
                    val width = path.getAttribute("android:strokeWidth").toFloatOrNull() ?: 0f
                    assertTrue("$where is stroked at width ${path.getAttribute("android:strokeWidth")}", width > 0f)
                }
            }
        }
    }

    @Test
    fun `the alpha-only icons are white and nothing else`() {
        // A notification small icon and a themed-icon layer are read through
        // their alpha channel; any other colour here is a claim the platform
        // will not honour, so the file should not make it.
        listOf(MONOCHROME, REMINDER).forEach { file ->
            paths(file).forEachIndexed { index, path ->
                val where = "${file.name} path $index"
                listOf("android:fillColor", "android:strokeColor")
                    .map { path.getAttribute(it) }
                    .filter { it.isNotEmpty() }
                    .forEach { assertEquals("$where colour", WHITE, it.uppercase()) }
            }
        }
    }

    @Test
    fun `the mark's mouth is a stroke, not a wedge`() {
        // The one path with no fill: a filled quadratic closes into a slice of
        // pie. Pinned because it is the easiest thing to "fix" by adding a fill.
        val mouth = paths(FOREGROUND).single { it.getAttribute("android:fillColor").isEmpty() }
        assertTrue(mouth.getAttribute("android:pathData").contains("Q"))
        assertEquals("round", mouth.getAttribute("android:strokeLineCap"))
    }

    /**
     * The mark's colours are the character's, hand-copied from [MomoPalette]
     * because a drawable cannot read Kotlin. Compared as ints, not hex strings
     * — a string comparison is not a colour comparison. Every colour the file
     * uses must be one of hers.
     */
    @Test
    fun `the mark is drawn in Momo's own colours`() {
        val palette = listOf(MomoPalette.Bead, MomoPalette.Body, MomoPalette.Ink, MomoPalette.Mouth).map { it.toArgb() }
        paths(FOREGROUND).forEachIndexed { index, path ->
            listOf("android:fillColor", "android:strokeColor")
                .map { path.getAttribute(it) }
                .filter { it.isNotEmpty() }
                .forEach { colour ->
                    val argb = colour.removePrefix("#").toLong(16).toInt()
                    assertTrue("path $index colour $colour is not in MomoPalette", argb in palette)
                }
        }
    }

    /** The eyes are holes, and only because of this attribute: under nonZero both wind to 2 and the face is a blob. */
    @Test
    fun `the reminder's eyes are cut out with evenOdd`() {
        val body = paths(REMINDER).single { it.getAttribute("android:fillType").isNotEmpty() }
        assertEquals("evenOdd", body.getAttribute("android:fillType"))
        // Three subpaths — the body and two eyes — in the one evenOdd path.
        assertEquals(3, body.getAttribute("android:pathData").count { it == 'M' })
    }

    /**
     * The thread is three vertical warps crossed by one horizontal weft, which
     * is the picture `docs/running.md` §4 confirms on a launcher and the comment
     * above each path claims. Asserted inside `pathData`, which nothing else
     * here reads: until 2026-08-30 the warp path carried a fourth subpath,
     * `M54,45 V63`, commented as a short weft but *vertical* at x = 54 and so
     * wholly inside the centre warp at the same stroke width — it painted
     * nothing, and every other case in this file passed over it. Deleted once
     * the artboard confirmed three warps. A subpath that draws inside another is
     * more than this can see; a weft that is not horizontal is not.
     */
    @Test
    fun `the thread is three warps crossed by one weft`() {
        val (warps, weft) = paths(MONOCHROME).also { assertEquals("the thread is two paths", 2, it.size) }
        val warpData = warps.getAttribute("android:pathData")
        assertEquals("three warps: '$warpData'", 3, warpData.count { it == 'M' })
        assertTrue("a warp runs horizontally: '$warpData'", !warpData.contains("H"))
        assertTrue("a warp does not run vertically: '$warpData'", warpData.contains("V"))
        val weftData = weft.getAttribute("android:pathData")
        assertEquals("one weft: '$weftData'", 1, weftData.count { it == 'M' })
        assertTrue("the weft does not run horizontally: '$weftData'", weftData.contains("H"))
        assertTrue("the weft runs vertically: '$weftData'", !weftData.contains("V"))
    }

    /** The paths stay the canvas's; the safe-zone correction is a group scale about the centre, so it can be read back. */
    @Test
    fun `both launcher layers are scaled into the 66dp safe zone`() {
        assertGroupScale(FOREGROUND, pivot = "54", scale = 0.85f)
        assertGroupScale(MONOCHROME, pivot = "54", scale = 0.9f)
    }

    /** The other way round: the reminder's faithful 24/108 scale is too small for a status bar, so it grows. */
    @Test
    fun `the reminder mark is scaled up to the small icon's 2dp inset`() {
        assertGroupScale(REMINDER, pivot = "12", scale = 1.2f)
    }

    private fun assertGroupScale(file: File, pivot: String, scale: Float) {
        val groups = document(file).getElementsByTagName("group")
        assertEquals("${file.name} has one group", 1, groups.length)
        val group = groups.item(0) as Element
        assertEquals("${file.name} pivotX", pivot, group.getAttribute("android:pivotX"))
        assertEquals("${file.name} pivotY", pivot, group.getAttribute("android:pivotY"))
        assertEquals("${file.name} scaleX", scale, group.getAttribute("android:scaleX").toFloat())
        assertEquals("${file.name} scaleY", scale, group.getAttribute("android:scaleY").toFloat())
    }

    private fun layer(icon: Element, name: String): String {
        val nodes = icon.getElementsByTagName(name)
        assertEquals("one <$name>", 1, nodes.length)
        return (nodes.item(0) as Element).getAttribute("android:drawable")
    }

    private fun document(file: File): Document {
        assertTrue("expected ${file.absolutePath} — if this path is wrong the test proves nothing", file.isFile)
        return DocumentBuilderFactory.newInstance()
            .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            .newDocumentBuilder()
            .parse(file)
    }

    private fun paths(file: File): List<Element> {
        val nodes = document(file).getElementsByTagName("path")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
}
