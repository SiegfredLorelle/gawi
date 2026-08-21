package com.gawi.core.data.backup

import com.gawi.core.data.db.dao.CompletionExportRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The CSV's bytes, field by field.
 *
 * A plain JVM test with no runner: [CompletionCsv] touches nothing Android and
 * that is the point of it being split from the class that opens a document.
 *
 * The formula-injection cases are the reason this file is long. They are the one
 * part of the CSV that is a security property rather than a formatting choice,
 * and every one of them is a habit name a user can really type.
 */
class CompletionCsvTest {

    private fun row(habit: String = "read", date: String = "2026-08-16", note: String? = null) =
        CompletionExportRow(habit = habit, logicalDate = date, note = note)

    /** Header, then one line per row, and never a bare `\n`. */
    private fun lines(vararg rows: CompletionExportRow): List<String> =
        CompletionCsv.encode(rows.toList()).decodeToString().removePrefix("\uFEFF").split("\r\n").dropLast(1)

    @Test
    fun `the file opens with a utf-8 byte order mark`() {
        val bytes = CompletionCsv.encode(emptyList())

        // The literal three bytes, not the string that produces them: what
        // Excel needs is a byte prefix, so asserting on the decoded character
        // would pass against a file written in any encoding.
        assertEquals(listOf<Byte>(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.take(3))
    }

    @Test
    fun `nothing logged still writes the column headings`() {
        assertEquals(listOf("habit,logical_date,note"), lines())
    }

    @Test
    fun `every line ends crlf, the last one included`() {
        val text = CompletionCsv.encode(listOf(row())).decodeToString()

        assertTrue("expected a trailing CRLF, got ${text.takeLast(4).map { it.code }}", text.endsWith("\r\n"))
        // No stray bare newline anywhere: a lone \n inside a CRLF file is what a
        // hand-rolled writer gets wrong on the last row.
        assertEquals(0, text.count { it == '\n' } - Regex("\r\n").findAll(text).count())
    }

    @Test
    fun `a plain row is written unquoted`() {
        assertEquals("read,2026-08-16,20 pages", lines(row(note = "20 pages"))[1])
    }

    @Test
    fun `an absent note is an empty field rather than an empty string`() {
        assertEquals("read,2026-08-16,", lines(row(note = null))[1])
    }

    @Test
    fun `a comma is quoted rather than ending the field`() {
        assertEquals("\"read, daily\",2026-08-16,", lines(row(habit = "read, daily"))[1])
    }

    @Test
    fun `a double quote is doubled inside a quoted field`() {
        assertEquals("read,2026-08-16,\"said \"\"yes\"\"\"", lines(row(note = "said \"yes\""))[1])
    }

    @Test
    fun `a newline inside a note stays inside one quoted field`() {
        val text = CompletionCsv.encode(listOf(row(note = "two\nlines"))).decodeToString()

        assertTrue(text, text.contains("\"two\nlines\""))
    }

    @Test
    fun `surrounding space is quoted so a reader cannot strip it`() {
        assertEquals("read,2026-08-16,\" padded \"", lines(row(note = " padded "))[1])
    }

    // --- formula injection ---------------------------------------------------

    @Test
    fun `an equals sign leading a habit name is neutralised`() {
        assertEquals("\"'=1+1\",2026-08-16,", lines(row(habit = "=1+1"))[1])
    }

    @Test
    fun `a plus sign leading a habit name is neutralised`() {
        assertEquals("\"'+1\",2026-08-16,", lines(row(habit = "+1"))[1])
    }

    /** The case that makes stripping the character unacceptable. */
    @Test
    fun `a minus sign leading a habit name is neutralised and not removed`() {
        val field = lines(row(habit = "-5kg"))[1].substringBefore(",2026")

        assertEquals("\"'-5kg\"", field)
        assertTrue("the user's own text must survive", field.contains("-5kg"))
    }

    @Test
    fun `an at sign leading a habit name is neutralised`() {
        assertEquals("\"'@SUM\",2026-08-16,", lines(row(habit = "@SUM"))[1])
    }

    /**
     * A spreadsheet skips leading whitespace before deciding what a cell is, so
     * a tab is a way past a guard that only looks for the four sigils.
     */
    @Test
    fun `a leading tab is neutralised`() {
        assertEquals("\"'\t=1+1\",2026-08-16,", lines(row(habit = "\t=1+1"))[1])
    }

    @Test
    fun `a leading carriage return is neutralised`() {
        val text = CompletionCsv.encode(listOf(row(habit = "\r=1+1"))).decodeToString()

        assertTrue(text, text.contains("\"'\r=1+1\""))
    }

    /**
     * A sigil behind a leading space is the same attack wearing a disguise.
     *
     * Found by review of this change. The guard used to test the *first*
     * character against a set that included TAB and CR but not space, which was
     * incoherent either way round — if leading whitespace is skipped before a
     * cell is typed then space had to be looked through too, and if it is not
     * then TAB and CR did not belong in the set.
     *
     * Do not read this test as pinning a reproduced exploit. Measured on
     * LibreOffice 2026-08-21, `" =1+1"` stays text even with leading-space
     * removal enabled; a bare `"=1+1"` does evaluate. This pins the consistent
     * rule, which is worth having for readers nobody has measured.
     *
     * Note the space itself survives, because neutralising must not edit the
     * user's text — only the apostrophe is added.
     */
    @Test
    fun `a sigil behind a leading space is neutralised`() {
        assertEquals("\"' =1+1\",2026-08-16,", lines(row(habit = " =1+1"))[1])
    }

    @Test
    fun `a sigil behind several kinds of leading whitespace is neutralised`() {
        assertEquals("\"'  \t@SUM\",2026-08-16,", lines(row(habit = "  \t@SUM"))[1])
        assertEquals("read,2026-08-16,\"' -1\"", lines(row(note = " -1"))[1])
    }

    /**
     * Quoting is not the guard, and this is the assertion that says so. A CSV
     * parser strips quotes and *then* the cell is typed, so a quoted `=1+1`
     * evaluates in Excel exactly as a bare one does — which is why every
     * neutralised field carries the apostrophe as well as the quotes.
     */
    @Test
    fun `a neutralised field carries the apostrophe and not merely quotes`() {
        val field = lines(row(habit = " =1+1"))[1].substringBefore(",2026")

        assertTrue("expected quotes: $field", field.startsWith("\"") && field.endsWith("\""))
        assertTrue("expected the apostrophe guard inside them: $field", field.startsWith("\"'"))
    }

    /** Leading whitespace with nothing dangerous behind it is left as it is. */
    @Test
    fun `leading whitespace alone is quoted but not neutralised`() {
        assertEquals("\" read\",2026-08-16,", lines(row(habit = " read"))[1])
    }

    /** A note is free text too, so the same guard has to cover it. */
    @Test
    fun `a note is neutralised the same way a name is`() {
        assertEquals("read,2026-08-16,\"'=cmd\"", lines(row(note = "=cmd"))[1])
    }

    @Test
    fun `a sigil that is not the first character is left alone`() {
        assertEquals("2 read-alouds,2026-08-16,1+1 pages", lines(row(habit = "2 read-alouds", note = "1+1 pages"))[1])
    }

    @Test
    fun `rows are written in the order they arrive`() {
        val written = lines(row(habit = "b"), row(habit = "a")).drop(1).map { it.substringBefore(",") }

        assertEquals(listOf("b", "a"), written)
    }
}
