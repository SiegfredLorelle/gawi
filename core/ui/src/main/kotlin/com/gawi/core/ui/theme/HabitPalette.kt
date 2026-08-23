package com.gawi.core.ui.theme

/**
 * The colours and icons a habit can be given.
 *
 * A fixed set rather than a free-text hex field or a full colour wheel, for two
 * reasons. The editor is a phone form, and typing `#A94FF6` into one is not a
 * thing anyone wants to do. More usefully, picking from a list is what makes
 * every stored colour a valid one, which leaves a blank name as the only
 * validation error the form can actually produce — the domain rejects
 * `BlankName` and nothing else about metadata.
 *
 * [parseHabitColor] still exists and is still needed: these are what the editor
 * offers, not a guarantee about what is in the log. A habit created before this
 * set was retuned, or by a future import, carries whatever it carries.
 *
 * **Designed, as of docs/ux/visual-identity.md §6, and to one rule**: the same
 * eight hue families at a fixed OKLCH lightness of 0.62, each taking the most
 * chroma sRGB allows it there. Uniform *perceived* lightness so the eight read
 * as one set; per-hue chroma so each family stays recognisable. §6.1 records the
 * two tidier rules that were tried first and both fail — fixing chroma too
 * turns blue to slate and yellow to khaki, and fixing WCAG luminance turns
 * yellow to olive-bronze. Eight recognisable families cannot share one
 * luminance, so what is made uniform is *contrast*, which is [glyphColorOn]'s
 * job and not this list's.
 */
object HabitPalette {

    /**
     * Uppercase and six digits, because [HabitPalette] is not the only thing
     * that writes a habit colour and the editor matches by exact string.
     * `ColorPicker` compares `hex == form.color`, so `"#a94ff6"` would render
     * as an unselected form holding a colour it is already set to. Anything
     * else that writes one — an import, a seeder — has to spell it this way.
     *
     * Retuning this list migrates nothing, and must not: a colour lives as raw
     * hex in an append-only log, and rewriting history to restyle it is the
     * thing an event log exists not to do. §6.3 is what happens instead — the
     * editor grows a leading swatch for a colour it no longer offers.
     */
    val Colors: List<String> = listOf(
        "#F22935",
        "#E92786",
        "#A94FF6",
        "#427FF6",
        "#249899",
        "#24A047",
        "#9C851F",
        "#C26E1F",
    )

    /**
     * Emoji rather than vector assets, because `HabitMetadata.icon` is a String
     * and drawable ids are not portable through an event log that has to
     * survive an export and an import.
     *
     * Still emoji, and still open: §7.3 compares these against two drawn icon
     * sets and finds the choice *wire-neutral*, because a stable icon **name**
     * is a String too and would need no schema bump. So this is the one part of
     * the palette a later decision can replace without touching the log.
     */
    val Icons: List<String> = listOf(
        "📖",
        "🏃",
        "💧",
        "🧘",
        "🛌",
        "🥗",
        "🎸",
        "🧹",
        "✍️",
        "🗣️",
        "💊",
        "🌱",
    )

    /** What an untouched create form starts on. */
    val DefaultColor: String = Colors.first()

    /** What an untouched create form starts on. */
    val DefaultIcon: String = Icons.first()
}
