package com.gawi.core.domain.command

/**
 * Why a command was refused. Commands validate business rules
 * (architecture §1.6); none of these can ever be raised by replay.
 */
sealed interface CommandError {
    /** The habit does not exist in the projected state. */
    data object HabitNotFound : CommandError

    /** The habit exists but is archived. */
    data object HabitIsArchived : CommandError

    /** A habit name must not be blank. */
    data object BlankName : CommandError

    /** The logical date is more than three days before today (architecture §5). */
    data object RetroWindowExceeded : CommandError

    /** Completions cannot be logged for future logical dates. */
    data object FutureLogicalDate : CommandError

    /** No live completion to undo or annotate. */
    data object CompletionNotFound : CommandError
}

/**
 * Outcome of a command: validated payload(s) for the caller to wrap in
 * envelopes and append, or a typed rejection. The domain never stamps
 * envelopes — ids and clocks live in the data layer.
 */
sealed interface CommandResult<out T> {
    data class Accepted<T>(val payload: T) : CommandResult<T>

    data class Rejected(val error: CommandError) : CommandResult<Nothing>
}
