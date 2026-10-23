package com.example.astroxplore.core.util

import com.example.astroxplore.features.groups.model.GroupModel
import com.example.astroxplore.features.groups.model.PresentationModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CalendarSyncUtilsTest {

    private val testGroup = GroupModel(
        id = "grp_test_1",
        displayId = "ASTRO-TEST",
        name = "Cosmology Journal Club",
        description = "Weekly discussion of cosmological simulations and surveys.",
        focusArea = "Cosmology",
        meetingSchedule = "Weekly on Thu at 17:00 UTC",
        meetingLocation = "Google Meet: meet.google.com/test-astro"
    )

    private val testPresentation = PresentationModel(
        id = "pres_1",
        groupId = "grp_test_1",
        presenterId = "usr_1",
        bibcode = "2024ApJ...961..102R",
        paperTitle = "NIRSpec observation of interstellar dust attenuation at cosmic dawn",
        presenterName = "Dr. Jane Scholar",
        scheduledAt = "2026-11-05T17:00:00",
        meetingLocation = "Google Meet: meet.google.com/test-astro"
    )

    @Test
    fun parseSchedule_extractsWeeklyFrequencyAndDay() {
        val parsed = CalendarSyncUtils.parseSchedule("Weekly on Thursdays at 17:00 UTC")
        assertEquals(MeetingFrequency.WEEKLY, parsed.frequency)
        assertEquals(DayOfWeek.THURSDAY, parsed.dayOfWeek)
        assertEquals(LocalTime.of(17, 0), parsed.time)
        assertNotNull(parsed.nextOccurrence)
    }

    @Test
    fun parseSchedule_extractsBiweeklyFrequency() {
        val parsed = CalendarSyncUtils.parseSchedule("Bi-weekly on Wednesday at 15:30")
        assertEquals(MeetingFrequency.BIWEEKLY, parsed.frequency)
        assertEquals(DayOfWeek.WEDNESDAY, parsed.dayOfWeek)
        assertEquals(LocalTime.of(15, 30), parsed.time)
    }

    @Test
    fun parseSchedule_extractsMonthlyFrequency() {
        val parsed = CalendarSyncUtils.parseSchedule("Monthly on 1st Friday at 18:00")
        assertEquals(MeetingFrequency.MONTHLY, parsed.frequency)
        assertEquals(DayOfWeek.FRIDAY, parsed.dayOfWeek)
        assertEquals(LocalTime.of(18, 0), parsed.time)
    }

    @Test
    fun generateIcsContent_createsValidRfc5545() {
        val ics = CalendarSyncUtils.generateIcsContent(
            group = testGroup,
            presentation = null,
            frequency = MeetingFrequency.WEEKLY
        )

        assertTrue(ics.contains("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.contains("VERSION:2.0\r\n"))
        assertTrue(ics.contains("BEGIN:VEVENT\r\n"))
        assertTrue(ics.contains("SUMMARY:[Journal Club] Cosmology Journal Club\r\n"))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;INTERVAL=1\r\n"))
        assertTrue(ics.contains("LOCATION:Google Meet: meet.google.com/test-astro\r\n"))
        assertTrue(ics.contains("END:VEVENT\r\n"))
        assertTrue(ics.contains("END:VCALENDAR\r\n"))
    }

    @Test
    fun generateIcsContent_includesPresentationDetailsAndNasaAdsUrl() {
        val ics = CalendarSyncUtils.generateIcsContent(
            group = testGroup,
            presentation = testPresentation,
            frequency = MeetingFrequency.ONE_TIME
        )

        assertTrue(ics.contains("SUMMARY:[Journal Club] Cosmology Journal Club: NIRSpec observation of interstellar dust attenuation at cosmic dawn\r\n"))
        assertTrue(ics.contains("URL:https://ui.adsabs.harvard.edu/abs/2024ApJ...961..102R\r\n"))
        assertTrue(ics.contains("Bibcode: 2024ApJ...961..102R"))
        // ONE_TIME should not have RRULE
        assertTrue(!ics.contains("RRULE:"))
    }

    @Test
    fun meetingFrequency_rruleDefinitionsAreRfc5545Compliant() {
        assertEquals("", MeetingFrequency.ONE_TIME.rrule)
        assertEquals("FREQ=WEEKLY;INTERVAL=1", MeetingFrequency.WEEKLY.rrule)
        assertEquals("FREQ=WEEKLY;INTERVAL=2", MeetingFrequency.BIWEEKLY.rrule)
        assertEquals("FREQ=MONTHLY;INTERVAL=1", MeetingFrequency.MONTHLY.rrule)
    }

    @Test
    fun generateIcsContent_formatsBiweeklyAndMonthlyRrule() {
        val biweeklyIcs = CalendarSyncUtils.generateIcsContent(
            group = testGroup,
            frequency = MeetingFrequency.BIWEEKLY
        )
        assertTrue(biweeklyIcs.contains("RRULE:FREQ=WEEKLY;INTERVAL=2\r\n"))

        val monthlyIcs = CalendarSyncUtils.generateIcsContent(
            group = testGroup,
            frequency = MeetingFrequency.MONTHLY
        )
        assertTrue(monthlyIcs.contains("RRULE:FREQ=MONTHLY;INTERVAL=1\r\n"))
    }
}
