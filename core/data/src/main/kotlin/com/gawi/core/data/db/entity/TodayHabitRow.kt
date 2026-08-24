package com.gawi.core.data.db.entity

import androidx.room.ColumnInfo

/**
 * One row of the Today query: a habit joined to today's completion, its
 * progress this week and its cached streak. Flat because it is a query result
 * — the shaping into domain types happens in the mappers.
 */
internal data class TodayHabitRow(
    @ColumnInfo(name = "habit_id")
    val habitId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "icon")
    val icon: String,
    @ColumnInfo(name = "color")
    val color: String,
    @ColumnInfo(name = "schedule_kind")
    val scheduleKind: String,
    @ColumnInfo(name = "times_per_week")
    val timesPerWeek: Int?,
    @ColumnInfo(name = "tag")
    val tag: String?,
    @ColumnInfo(name = "archived")
    val archived: Boolean,
    @ColumnInfo(name = "created_on")
    val createdOn: String?,
    @ColumnInfo(name = "completed_today")
    val completedToday: Boolean,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "week_count")
    val weekCount: Int,
    @ColumnInfo(name = "current_streak")
    val currentStreak: Int,
    @ColumnInfo(name = "previous_streak")
    val previousStreak: Int,
    @ColumnInfo(name = "broken_on")
    val brokenOn: String?,
)
