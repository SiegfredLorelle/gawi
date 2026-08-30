package com.gawi.feature.settings

import androidx.annotation.StringRes

/**
 * The bundled third-party works, and the file each one's notice lives in.
 *
 * Listed by hand rather than found by scanning the assets directory: a third
 * bundled thing is a deliberate edit here, with a role sentence written for it,
 * not a file that appears on the screen with no account of what it is for
 * (docs/ux/settings.md §9). The order is the order a reader meets them in the
 * app — the type is on every screen, the icons on five app bars.
 *
 * [file] is the name under `licenses/` at the repository root, which the module
 * packages as an asset under that exact name; the drawable headers in
 * `:core:ui` cite the same path.
 */
internal enum class LicenceNotice(val file: String, @param:StringRes val title: Int, @param:StringRes val role: Int) {
    Outfit("Outfit-OFL.txt", R.string.settings_notice_outfit, R.string.settings_notice_outfit_role),
    Lucide("Lucide-ISC.txt", R.string.settings_notice_lucide, R.string.settings_notice_lucide_role),
}

/** One notice as the screen draws it: which work, and its licence text — the file's words, reflowed by [reflowNotice]. */
internal data class NoticeUi(val notice: LicenceNotice, val text: String)

internal sealed interface LicencesUiState {
    /** Blank, not a spinner: two small files off disk. */
    data object Loading : LicencesUiState

    /** One of the files did not read. Both or neither — see the strings. */
    data object Unavailable : LicencesUiState

    data class Ready(val notices: List<NoticeUi>) : LicencesUiState
}

/**
 * The text as the screen wraps it: paragraphs intact, the source's own line
 * breaks inside a paragraph joined into spaces.
 *
 * Both files are hard-wrapped at about 72 columns for a terminal. Drawn as-is
 * at `bodySmall` on a phone, every source line breaks once more a few words
 * before its end and leaves an orphan on the next — "worldwide", "creation" —
 * which reads as damage. Blank lines still separate paragraphs, and a line that
 * is a rule of dashes keeps the breaks on both sides so the OFL's boxed heading
 * stays a box, and a short all-capitals line — PREAMBLE, DEFINITIONS — keeps the
 * break after it rather than opening its own paragraph's first sentence. Short,
 * so that the hard-wrapped lines of an all-capitals disclaimer still join. Every word and every character that is not a line break is the
 * file's own; this is layout, not editing (docs/ux/settings.md §9).
 */
internal fun reflowNotice(text: String): String = text
    .trimEnd()
    .split("\n")
    .map { it.trimEnd() }
    .fold(StringBuilder()) { out, line ->
        val previous = out.lastOrNull()
        when {
            out.isEmpty() -> out.append(line)
            line.isEmpty() || previous == '\n' -> out.append('\n').append(line)
            line.isRule() || out.lastLine().isRule() || out.lastLine().isHeading() -> out.append('\n').append(line)
            else -> out.append(' ').append(line)
        }
    }
    .toString()

private fun String.isRule(): Boolean = length >= RULE_MIN_DASHES && all { it == '-' }

private fun StringBuilder.lastLine(): String = substring(lastIndexOf('\n') + 1)

/** All capitals, short, and not a wrapped fragment ending in a comma. */
private fun String.isHeading(): Boolean =
    length < HEADING_MAX_LENGTH && any { it.isLetter() } && none { it.isLowerCase() } && !endsWith(',')

private const val RULE_MIN_DASHES = 3
private const val HEADING_MAX_LENGTH = 40
