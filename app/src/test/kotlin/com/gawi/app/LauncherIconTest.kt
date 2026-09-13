package com.gawi.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.gawi.core.ui.component.MomoPalette
import com.gawi.core.ui.theme.WCAG_NON_TEXT_FLOOR
import com.gawi.core.ui.theme.contrastRatio
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

    /**
     * The XML copy and the Kotlin value are the same colour. Which role that is
     * moved when the face went (§7.1) and could move again; that they agree
     * cannot, because an adaptive icon is XML and cannot read Kotlin.
     */
    @Test
    fun `the icon's ground is the scheme's, not a third value`() {
        val context = RuntimeEnvironment.getApplication()
        val resolved = context.getColor(R.color.ic_launcher_background)
        assertEquals(
            "ic_launcher_background and gawiLauncherBackground disagree",
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

    /**
     * The mark has no face, and nothing about it is drawn with a line
     * (docs/ux/visual-identity.md §7.1). It is three frond dots and one paler
     * body circle: every path is a fill, and the stroked mouth that used to be
     * here went with the face rather than being restyled.
     */
    @Test
    fun `the mark is filled shapes, with nothing stroked`() {
        val stroked = paths(FOREGROUND).filter { it.getAttribute("android:strokeColor").isNotEmpty() }
        assertTrue("a faceless mark has nothing to draw with a line, found $stroked", stroked.isEmpty())
    }

    /**
     * The mark's colours are the character's, hand-copied from [MomoPalette]
     * because a drawable cannot read Kotlin. Compared as ints, not hex strings
     * — a string comparison is not a colour comparison.
     *
     * **Two, not four.** `Ink` and `Mouth` were the face's, and listing them
     * here would let the face back in one path at a time.
     */
    @Test
    fun `the mark is drawn in Momo's own colours`() {
        val palette = listOf(MomoPalette.Bead, MomoPalette.Body).map { it.toArgb() }
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

    /**
     * The mark reads on its ground, which is a claim this file did not use to
     * make.
     *
     * With a face, contrast was not the claim: momo.md §2 says an illustration
     * carries its silhouette with the ink of the eyes and the deeper coral of
     * the gills. Take the face away and the cluster has neither, so the ground
     * inverted and the ratio became the reason it works
     * (docs/ux/visual-identity.md §7.1). A non-text graphic takes 3:1.
     */
    @Test
    fun `the mark clears the non-text floor on its own ground`() {
        val ground = gawiLauncherBackground()
        paths(FOREGROUND).forEach { path ->
            // A path with no fill parses as the empty string, and measuring that
            // is a NumberFormatException rather than a failure naming the ratio.
            val fill = path.getAttribute("android:fillColor")
            assertTrue("a path with no fill has nothing to measure", fill.isNotEmpty())
            val argb = fill.removePrefix("#").toLong(16).toInt()
            val ratio = contrastRatio(Color(argb), ground)
            assertTrue("a fill measured $ratio on the launcher ground", ratio >= WCAG_NON_TEXT_FLOOR)
        }
    }

    /**
     * The reminder cuts nothing out any more.
     *
     * `evenOdd` was there to hold two eyes open through one colour on an
     * alpha-only icon. There is no face to hold open (docs/ux/reminder.md §4),
     * and leaving the attribute on a cluster whose circles overlap would punch
     * holes where the lobes cross.
     */
    @Test
    fun `the reminder cuts nothing out of itself`() {
        val cut = paths(REMINDER).filter { it.getAttribute("android:fillType") == "evenOdd" }
        assertTrue("a faceless silhouette has no holes to preserve, found $cut", cut.isEmpty())
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
