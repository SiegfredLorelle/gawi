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
 * It is not free, and the cost lands on the readers the `sep=` argument below
 * declines to break. A **strict** UTF-8 reader — one not asked for `utf-8-sig`,
 * `pandas` among them — sees the first column named `\uFEFFhabit` rather than
 * `habit`. The mark is kept anyway, because that reader can be told the encoding
 * in one argument whereas Excel's mojibake cannot be fixed after the fact.
 *
 * **CRLF separates records**, per RFC 4180 and for the same reader. It governs
 * *row terminators only*: a line break **inside** a field is written through
 * exactly as it arrived, whether that is LF, CR or CRLF, and the field is quoted
 * around it — `MUST_QUOTE` covers both characters, so the file stays parseable
 * either way (checked: a field holding a bare LF reads back as one row).
 *
 * Passing it through is the same rule that decided the formula guard below: an
 * export whose justification is the user owning their data cannot quietly edit
 * it, and rewriting someone's note to suit RFC 4180 §2.6 is editing it. Excel
 * writes in-cell breaks as bare LF and reads them back as breaks, so
 * normalising would also diverge from the reader this file is tuned for. Note
 * how it can arrive at all: the note sheet's field is single-line, so a note
 * carrying a newline comes **from an import** rather than from the app.
 *
 * **Fields are quoted only when the format requires it**, so the common file
 * stays readable in a text editor.
 *
 * **Known limit, and it is the same population the byte order mark is for.**
 * Excel takes its CSV delimiter from the operating system's list separator
 * rather than from the file, so on an install where that separator is `;` —
 * German, French, Spanish, Dutch and others — double-clicking this file puts
 * every record in one column. An `sep=,` first line would fix that for Excel
 * and LibreOffice, and is deliberately **not** written: it is not part of RFC
 * 4180, so every other reader sees a junk first row, and `pandas` in particular
 * would take it as the header. The comma stays, and the workaround is the
 * import dialog rather than a change to the bytes. Recorded in
 * docs/ux/settings.md §6 and in docs/running.md §4 rather than left to be
 * discovered.
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
     * configurations, a DDE command. It is reachable by more than the user's own
     * typing, which is what makes it worth guarding rather than documenting:
     * import deliberately accepts a foreign file (docs/ux/settings.md §6), so a
     * habit name can arrive from whoever wrote that file.
     *
     * **The check is on the trimmed value, and be precise about why.** The
     * honest reason is consistency, not a demonstrated hole: testing `first()`
     * against a set that included TAB and CR but *not* space was incoherent
     * either way round — if leading whitespace is skipped before a cell is
     * typed then space had to be looked through too, and if it is not then the
     * TAB and CR entries were pointless. Trimming first covers space, TAB, CR,
     * LF and every Unicode space at once and lets the sigil set be the four
     * characters that are actually dangerous.
     *
     * **What was measured, 2026-08-21, because the first version of this
     * paragraph claimed more than it could show.** In LibreOffice a *bare*
     * quoted `"=1+1"` really does import as a formula and evaluate to 2 — so
     * the guard earns its place. But `" =1+1"` stayed text, and it stayed text
     * **even with the filter's leading-space removal on**, verified by a control
     * field that came back trimmed in the same run. So LibreOffice decides what
     * a cell is independently of that trimming, and the space variant is not
     * exploitable there. Excel and Google Sheets were not tested. The trim
     * guard is therefore defence in depth against readers nobody here has
     * measured, plus the consistency argument above — it is **not** a fix for a
     * reproduced bypass, and a claim that `pandas` matters was simply wrong,
     * since `pandas` strips spaces and evaluates nothing at all.
     *
     * **Quoting is not a substitute for this, and that part *is* measured.**
     * Quotes are a transport rule: a parser strips them and *then* the cell is
     * type-inferred. A file holding `"=1+1"`, quotes and all, converts in
     * LibreOffice to a cell whose stored formula is `of:=1+1` and whose value is
     * 2 — checked by reading the converted document rather than by looking at a
     * screen. So the apostrophe is the defence and the quotes are legibility; if
     * quoting were the defence, the apostrophe below would be redundant.
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
        val neutralised = value.trimStart().firstOrNull() in FORMULA_LEAD
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

    /**
     * Characters a spreadsheet reads as the start of a formula.
     *
     * Four, not six. TAB and CR used to be here as separate entries because they
     * can precede a sigil; [field] trims before it looks, so every kind of
     * leading whitespace is covered without enumerating it — and a lone leading
     * TAB, with no sigil behind it, was never dangerous in the first place.
     */
    private val FORMULA_LEAD = setOf('=', '+', '-', '@')

    /** Characters that make a field ambiguous unless it is quoted. */
    private val MUST_QUOTE = setOf(',', '"', '\r', '\n')
}
