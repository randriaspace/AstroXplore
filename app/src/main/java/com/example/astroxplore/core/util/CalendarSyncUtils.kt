package com.example.astroxplore.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import com.example.astroxplore.features.groups.model.GroupModel
import com.example.astroxplore.features.groups.model.PresentationModel
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

enum class MeetingFrequency(val label: String, val rrule: String) {
    ONE_TIME("One-time Meeting", ""),
    WEEKLY("Weekly", "FREQ=WEEKLY;INTERVAL=1"),
    BIWEEKLY("Bi-weekly", "FREQ=WEEKLY;INTERVAL=2"),
    MONTHLY("Monthly", "FREQ=MONTHLY;INTERVAL=1")
}

data class ParsedSchedule(
    val frequency: MeetingFrequency,
    val dayOfWeek: DayOfWeek,
    val time: LocalTime,
    val nextOccurrence: ZonedDateTime
)

object CalendarSyncUtils {

    private val UTC_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    /**
     * Parse human-readable meeting schedule string into structured frequency, day, and next occurrence.
     */
    fun parseSchedule(scheduleText: String, defaultHour: Int = 17, defaultMinute: Int = 0): ParsedSchedule {
        val lower = scheduleText.lowercase()

        val frequency = when {
            lower.contains("bi-weekly") || lower.contains("biweekly") || lower.contains("every 2 weeks") -> MeetingFrequency.BIWEEKLY
            lower.contains("monthly") -> MeetingFrequency.MONTHLY
            lower.contains("once") || lower.contains("one-time") -> MeetingFrequency.ONE_TIME
            else -> MeetingFrequency.WEEKLY
        }

        val dayOfWeek = when {
            lower.contains("monday") -> DayOfWeek.MONDAY
            lower.contains("tuesday") -> DayOfWeek.TUESDAY
            lower.contains("wednesday") -> DayOfWeek.WEDNESDAY
            lower.contains("thursday") -> DayOfWeek.THURSDAY
            lower.contains("friday") -> DayOfWeek.FRIDAY
            lower.contains("saturday") -> DayOfWeek.SATURDAY
            lower.contains("sunday") -> DayOfWeek.SUNDAY
            else -> DayOfWeek.THURSDAY
        }

        // Try extracting time if present (e.g., "17:00", "5:00 PM")
        var time = LocalTime.of(defaultHour, defaultMinute)
        val timeRegex = Regex("""(\d{1,2}):(\d{2})""")
        val match = timeRegex.find(scheduleText)
        if (match != null) {
            val (h, m) = match.destructured
            val hour = h.toIntOrNull()?.coerceIn(0, 23) ?: defaultHour
            val minute = m.toIntOrNull()?.coerceIn(0, 59) ?: defaultMinute
            time = LocalTime.of(hour, minute)
        }

        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zoneId)
        var nextDate = now.toLocalDate()

        if (nextDate.dayOfWeek != dayOfWeek || now.toLocalTime().isAfter(time)) {
            nextDate = nextDate.with(TemporalAdjusters.next(dayOfWeek))
        }

        val nextOccurrence = ZonedDateTime.of(nextDate, time, zoneId)
        return ParsedSchedule(frequency, dayOfWeek, time, nextOccurrence)
    }

    /**
     * Build Android Calendar Intent (Universal across Google Calendar, Samsung Calendar, Outlook).
     */
    fun buildCalendarIntent(
        group: GroupModel,
        presentation: PresentationModel? = null,
        startTime: ZonedDateTime? = null,
        frequency: MeetingFrequency? = null
    ): Intent {
        val schedule = parseSchedule(group.meetingSchedule)
        val eventStart = startTime ?: presentation?.scheduledAt?.let { scheduledStr ->
            try {
                LocalDateTime.parse(scheduledStr).atZone(ZoneId.systemDefault())
            } catch (_: Exception) {
                null
            }
        } ?: schedule.nextOccurrence
        val startMillis = eventStart.toInstant().toEpochMilli()
        val endMillis = startMillis + (90 * 60 * 1000L) // 1 hour 30 min duration
        val effFreq = frequency ?: if (presentation != null) MeetingFrequency.ONE_TIME else schedule.frequency

        val title = if (presentation != null && !presentation.paperTitle.isNullOrBlank()) {
            "[Journal Club] ${group.name}: ${presentation.paperTitle}"
        } else {
            "[Journal Club] ${group.name}"
        }

        val description = buildEventDescription(group, presentation)

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            if (group.meetingLocation.isNotBlank()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, group.meetingLocation)
            }
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)

            if (effFreq != MeetingFrequency.ONE_TIME && effFreq.rrule.isNotBlank()) {
                putExtra(CalendarContract.Events.RRULE, effFreq.rrule)
            }
        }
    }

    /**
     * Generate standard RFC-5545 .ics iCalendar file content compatible with iOS Apple Calendar,
     * Google Calendar, Outlook, and macOS.
     */
    fun generateIcsContent(
        group: GroupModel,
        presentation: PresentationModel? = null,
        startTime: ZonedDateTime? = null,
        frequency: MeetingFrequency? = null
    ): String {
        val schedule = parseSchedule(group.meetingSchedule)
        val eventStart = startTime ?: presentation?.scheduledAt?.let { scheduledStr ->
            try {
                LocalDateTime.parse(scheduledStr).atZone(ZoneId.systemDefault())
            } catch (_: Exception) {
                null
            }
        } ?: schedule.nextOccurrence
        val eventEnd = eventStart.plusMinutes(90)
        val effFreq = frequency ?: if (presentation != null) MeetingFrequency.ONE_TIME else schedule.frequency

        val startUtc = eventStart.withZoneSameInstant(ZoneOffset.UTC).format(UTC_FORMATTER)
        val endUtc = eventEnd.withZoneSameInstant(ZoneOffset.UTC).format(UTC_FORMATTER)
        val nowUtc = ZonedDateTime.now(ZoneOffset.UTC).format(UTC_FORMATTER)
        val uid = "${UUID.randomUUID()}@astroxplore.app"

        val summary = if (presentation != null && !presentation.paperTitle.isNullOrBlank()) {
            "[Journal Club] ${group.name}: ${cleanIcsText(presentation.paperTitle)}"
        } else {
            "[Journal Club] ${cleanIcsText(group.name)}"
        }

        val description = cleanIcsText(buildEventDescription(group, presentation))
        val location = cleanIcsText(group.meetingLocation)

        val rruleLine = if (effFreq != MeetingFrequency.ONE_TIME && effFreq.rrule.isNotBlank()) {
            "RRULE:${effFreq.rrule}\r\n"
        } else {
            ""
        }

        return buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("PRODID:-//AstroXplore//Journal Club Calendar Sync//EN\r\n")
            append("CALSCALE:GREGORIAN\r\n")
            append("METHOD:PUBLISH\r\n")
            append("BEGIN:VEVENT\r\n")
            append("UID:$uid\r\n")
            append("DTSTAMP:$nowUtc\r\n")
            append("DTSTART:$startUtc\r\n")
            append("DTEND:$endUtc\r\n")
            append(rruleLine)
            append("SUMMARY:$summary\r\n")
            append("DESCRIPTION:$description\r\n")
            if (location.isNotBlank()) {
                append("LOCATION:$location\r\n")
            }
            if (presentation != null && presentation.bibcode.isNotBlank()) {
                append("URL:https://ui.adsabs.harvard.edu/abs/${presentation.bibcode}\r\n")
            }
            append("STATUS:CONFIRMED\r\n")
            append("END:VEVENT\r\n")
            append("END:VCALENDAR\r\n")
        }
    }

    /**
     * Write .ics file to cache and launch Android Share Sheet for sharing to iOS/Desktop/email.
     */
    fun shareIcsInvite(
        context: Context,
        group: GroupModel,
        presentation: PresentationModel? = null,
        startTime: ZonedDateTime? = null,
        frequency: MeetingFrequency? = null
    ) {
        try {
            val icsContent = generateIcsContent(group, presentation, startTime, frequency)
            val calendarDir = File(context.cacheDir, "calendar").apply { mkdirs() }
            val safeClubName = group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
            val file = File(calendarDir, "${safeClubName}_meeting.ics")
            file.writeText(icsContent)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Journal Club Calendar Invite: ${group.name}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Join our Journal Club meeting for '${group.name}'. Download or open this calendar file to sync to Apple Calendar, Google Calendar, or Outlook."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Journal Club Calendar Invite"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildEventDescription(group: GroupModel, presentation: PresentationModel?): String {
        return buildString {
            append("AstroXplore Journal Club: ${group.name}\n\n")

            if (presentation != null) {
                append("PRESENTATION & DISCUSSION TOPIC:\n")
                if (!presentation.paperTitle.isNullOrBlank()) {
                    append("Title: ${presentation.paperTitle}\n")
                }
                append("Bibcode: ${presentation.bibcode}\n")
                append("NASA ADS Link: https://ui.adsabs.harvard.edu/abs/${presentation.bibcode}\n\n")
            }

            if (!group.focusArea.isNullOrBlank()) {
                append("Research Focus: ${group.focusArea}\n")
            }

            if (group.meetingSchedule.isNotBlank()) {
                append("Recurring Cadence: ${group.meetingSchedule}\n")
            }

            if (group.meetingLocation.isNotBlank()) {
                append("Venue / Link: ${group.meetingLocation}\n")
            }

            append("\nManaged with AstroXplore Astrophysics Research Platform.")
        }
    }

    private fun cleanIcsText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
    }
}
