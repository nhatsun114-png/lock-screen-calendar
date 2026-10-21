package edu.hcmute.duan.data.model

data class EventModel(
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val calendarId: Long
)
