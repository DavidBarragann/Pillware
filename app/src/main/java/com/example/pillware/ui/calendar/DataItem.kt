package com.example.pillware.ui.calendar

import java.util.Date

data class DateItem(
    val date: Date,
    val isToday: Boolean // Not strictly used for selection, but good for visual distinction
)