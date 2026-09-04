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
 * transparent launcher icon shipped with lint green. Only the facts that guard
 * a failure of that kind are asserted — a layer missing, a path that draws
 * nothing, a fill that turns a stroke or a hole into a blob, a colour that is
 * not the character's. Sizes, pivots, scales and subpath counts are the
 * artboard's to say, and `docs/running.md` §4 looks at the picture.
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
        val FOREGROUND = RES.resolve("drawable/ic_launcher_foreground.xml")
        val MONOCHROME = RES.resolve("drawable/ic_launcher_monochrome.xml")
        val REMINDER = RES.resolve("drawable/ic_reminder.xml")
        val ADAPTIVE = RES.resolve("mipmap-anydpi/ic_launcher.xml")
    }

    @Test
    fun `the manifest points the icon at the adaptive icon`() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        // The merged manifest, which is what ships.
        assertEquals("android:icon", R.mipmap.ic_launcher, info.icon)
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
    fun `the mark's mouth is a stroke, not a wedge`() {
        // Exactly one path with no fill: a filled mouth closes into a slice of
        // pie, and adding a fill is the easiest "fix" for a stroke that looks thin.
        val strokes = paths(FOREGROUND).filter { it.getAttribute("android:fillColor").isEmpty() }
        assertEquals("one stroke-only path, the mouth", 1, strokes.size)
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
