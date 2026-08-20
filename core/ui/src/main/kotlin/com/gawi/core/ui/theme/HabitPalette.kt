package com.gawi.core.ui.theme

/**
 * The colours and icons a habit can be given.
 *
 * A fixed set rather than a free-text hex field or a full colour wheel, for two
 * reasons. The editor is a phone form, and typing `#7E57C2` into one is not a
 * thing anyone wants to do. More usefully, picking from a list is what makes
 * every stored colour a valid one, which leaves a blank name as the only
 * validation error the form can actually produce — the domain rejects
 * `BlankName` and nothing else about metadata.
 *
 * [parseHabitColor] still exists and is still needed: these are what the editor
 * offers, not a guarantee about what is in the log. Habits created by the debug
 * seeder, or by a future import, carry whatever they carry.
 *
 * Not a design system. Momo's palette is PRD OQ-4 and undesigned, so these are
 * mid-tone Material hues chosen only to stay legible against both of
 * [GawiTheme]'s backgrounds. Same standing as [GawiSpacing].
 */
object HabitPalette {

    /**
     * Uppercase and six digits, matching what the debug seeder wrote, so
     * reopening a seeded habit finds its colour already selected rather than
     * showing an unpicked form.
     */
    val Colors: List<String> = listOf(
        "#EF5350",
        "#EC407A",
        "#7E57C2",
        "#42A5F5",
        "#26A69A",
        "#66BB6A",
        "#FFD54F",
        "#FFA726",
    )

    /**
     * Emoji rather than vector assets, because `HabitMetadata.icon` is a String
     * and drawable ids are not portable through an event log that has to
     * survive an export and an import. The two the seeder used lead the list.
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
