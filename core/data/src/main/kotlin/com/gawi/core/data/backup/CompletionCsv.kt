package com.gawi.core.data.backup

import com.gawi.core.data.db.dao.CompletionExportRow

/**
 * The completions projection as CSV bytes (PRD §5).
 *
 * Pure, and in `:core:data` rather than `:core:domain` because
 * `docs/architecture.md` §2's module table puts the CSV here by name. It also
 * needs no kotlinx-serialization, which is what keeps the boundary guard that
 * table describes intact: that dependency appearing in this module is the
 * signal the export codec's reasoning has been crossed.
 *
 * **This writes a file for a spreadsheet and not a backup.** It carries no
 * events and no habit configuration, so nothing can be rebuilt from it —
 * `EventArchive` is the only path that can (architecture §6), and
 * docs/ux/settings.md §6 has the copy that has to keep saying so.
 *
 * Three format decisions, all recorded in docs/ux/settings.md §6:
 *
 * **A UTF-8 byte order mark is written.** Excel reads a BOM-less UTF-8 CSV as
 * the platform's legacy encoding, so a habit named in anything but ASCII comes
 * out as mojibake in the tool most likely to open this file. Note this is the
 * *opposite* of what [EventLogArchive] does with a BOM on import, where one is
 * stripped — the asymmetry is deliberate: that path is fed by editors that add
 * one uninvited, this one feeds a reader that needs one.
 *
 * **CRLF line endings**, per RFC 4180 and for the same reader.
 *
 * **Fields are quoted only when the format requires it**, so the common file
 * stays readable in a text editor.
 */
internal object CompletionCsv {

    /** [rows] as the bytes a CSV file holds, header included. */
    fun encode(rows: List<CompletionExportRow>): ByteArray {
        val text = buildString {
            append(BYTE_ORDER_MARK)
            append(HEADER)
            append(LINE_SEPARATOR)
            rows.forEach { row ->
                append(field(row.habit))
                append(FIELD_SEPARATOR)
                append(field(row.logicalDate))
                append(FIELD_SEPARATOR)
                // An absent note is an empty field and never a quoted empty
                // string: the two are the same value to every reader, and the
                // shorter one does not invite the question.
                append(field(row.note.orEmpty()))
                append(LINE_SEPARATOR)
            }
        }
        return text.encodeToByteArray()
    }

    /**
     * One field, neutralised against formula injection and then quoted if the
     * format needs it.
     *
     * **A habit name and a note are free text the user typed, and a spreadsheet
     * evaluates a cell that begins `=`, `+`, `-` or `@` as a formula.** That is
     * the whole risk here and it is a real one rather than a theoretical one:
     * the file is written to be opened in Excel or LibreOffice, an exported
     * habit called `=1+1` would be computed rather than shown, and the same
     * lead characters reach a formula that reads other cells or, in older
     * configurations, a DDE command. TAB and CR are included because a
     * spreadsheet skips leading whitespace before deciding what a cell is, so
     * they let a formula in behind one.
     *
     * **A leading apostrophe rather than removing the character.** Excel and
     * LibreOffice both read `'` as "this cell is text" and do not display it,
     * so the cell shows what the user typed. Be exact about the cost: this does
     * modify the bytes, and someone reading the raw file in a text editor sees
     * the apostrophe. What it does not do is *lose* anything — stripping the
     * lead character would turn a habit honestly named `-5kg` into `5kg` with
     * no way back, and an export whose whole justification is the user owning
     * their data cannot quietly edit it.
     *
     * A neutralised field is always quoted as well. That is legibility rather
     * than correctness — an unquoted `'=1+1` parses identically — but it marks
     * the apostrophe as belonging to the encoding rather than to the name.
     */
    private fun field(value: String): String {
        val neutralised = value.isNotEmpty() && value.first() in FORMULA_LEAD
        val text = if (neutralised) FORMULA_GUARD + value else value
        return if (neutralised || needsQuoting(text)) quoted(text) else text
    }

    /**
     * RFC 4180's rule, plus the surrounding-space case it leaves optional.
     *
     * A comma or a line break would otherwise end the field or the record, and
     * a double quote would be read as one. Leading or trailing space is quoted
     * because several readers strip it silently, which would edit a note.
     */
    private fun needsQuoting(text: String): Boolean = text.any { it in MUST_QUOTE } || text != text.trim()

    /** Doubling the inner quotes is how RFC 4180 escapes them. */
    private fun quoted(text: String): String = "\"" + text.replace("\"", "\"\"") + "\""

    /** U+FEFF, so that Excel reads the file as UTF-8. */
    private const val BYTE_ORDER_MARK = "\uFEFF"

    /**
     * The column names.
     *
     * `logical_date` rather than "date", matching the column the projection
     * stores and the vocabulary the rest of the app uses: which day a
     * completion belongs to is decided by the day cutoff (architecture §5), and
     * calling it a date would quietly claim otherwise.
     */
    private const val HEADER = "habit,logical_date,note"

    private const val FIELD_SEPARATOR = ","

    private const val LINE_SEPARATOR = "\r\n"

    private const val FORMULA_GUARD = "'"

    /** Lead characters a spreadsheet reads as the start of a formula. */
    private val FORMULA_LEAD = setOf('=', '+', '-', '@', '\t', '\r')

    /** Characters that make a field ambiguous unless it is quoted. */
    private val MUST_QUOTE = setOf(',', '"', '\r', '\n')
}
