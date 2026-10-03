package com.example.astroxplore.features.groups.data

import com.example.astroxplore.core.database.entity.toEntity
import com.example.astroxplore.features.groups.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRepositoryMappingTest {

    @Test
    fun `normalizeJoinInput handles hyphens, underscores, spaces, and case`() {
        val input = " astro_jwst-01 \t"
        val normalized = GroupRepository.normalizeJoinInput(input)
        assertEquals("ASTROJWST01", normalized)
    }

    @Test
    fun `GroupModel toEntity preserves sync status and fields accurately`() {
        val model = GroupModel(
            id = "grp-123",
            displayId = "EXO99",
            name = "Exoplanet Atmospheres",
            description = "Transmission spectroscopy",
            ownerId = "owner-456",
            focusArea = "Exoplanets",
            memberCount = 5,
            createdAt = "2026-10-03T12:00:00",
            meetingSchedule = "Fridays at 3pm",
            meetingLocation = "Room 404",
            isMember = true
        )

        val unsyncedEntity = model.toEntity(isSynced = false)
        assertFalse(unsyncedEntity.isSynced)
        assertEquals(model.id, unsyncedEntity.id)
        assertEquals(model.displayId, unsyncedEntity.displayId)
        assertEquals(model.name, unsyncedEntity.name)

        val restoredModel = unsyncedEntity.toDomainModel()
        assertEquals(model.id, restoredModel.id)
        assertEquals(model.displayId, restoredModel.displayId)
        assertEquals(model.name, restoredModel.name)
        assertEquals(model.meetingSchedule, restoredModel.meetingSchedule)
        assertEquals(model.meetingLocation, restoredModel.meetingLocation)
        assertTrue(restoredModel.isMember)
    }

    @Test
    fun `GroupPaperModel toEntity preserves voting and sync flag`() {
        val paper = GroupPaperModel(
            id = "paper-789",
            groupId = "grp-123",
            bibcode = "2026ApJ...123..456A",
            addedBy = "user-1",
            voteCount = 4,
            isVotedByMe = true,
            title = "Atmospheric Detection of CO2",
            authors = "Smith et al.",
            year = "2026"
        )

        val entity = paper.toEntity(isSynced = true)
        assertTrue(entity.isSynced)
        assertEquals(4, entity.voteCount)
        assertTrue(entity.isVotedByMe)

        val restored = entity.toDomainModel()
        assertEquals(paper.id, restored.id)
        assertEquals(paper.groupId, restored.groupId)
        assertEquals(paper.bibcode, restored.bibcode)
        assertEquals(4, restored.voteCount)
        assertTrue(restored.isVotedByMe)
    }

    @Test
    fun `PresentationModel toEntity preserves check-in and scheduling`() {
        val presentation = PresentationModel(
            id = "pres-1",
            groupId = "grp-123",
            bibcode = "2026ApJ...123..456A",
            presenterId = "user-1",
            scheduledAt = "2026-10-10T15:00:00",
            paperTitle = "Atmospheric Detection of CO2",
            presenterName = "Dr. Smith",
            meetingLocation = "Virtual Room A",
            attendeeCount = 12,
            isCheckedIn = true
        )

        val entity = presentation.toEntity(isSynced = false)
        assertFalse(entity.isSynced)
        assertTrue(entity.isCheckedIn)
        assertEquals(12, entity.attendeeCount)

        val restored = entity.toDomainModel()
        assertEquals(presentation.id, restored.id)
        assertEquals(presentation.scheduledAt, restored.scheduledAt)
        assertTrue(restored.isCheckedIn)
        assertEquals(12, restored.attendeeCount)
    }

    @Test
    fun `SessionReviewModel toEntity preserves ratings and notes`() {
        val review = SessionReviewModel(
            id = "rev-1",
            groupId = "grp-123",
            bibcode = "2026ApJ...123..456A",
            reviewerId = "user-2",
            notes = "Great discussion on data reductions.",
            rating = 5,
            paperTitle = "Atmospheric Detection of CO2",
            reviewerName = "Researcher Jane"
        )

        val entity = review.toEntity(isSynced = true)
        assertTrue(entity.isSynced)
        assertEquals(5, entity.rating)
        assertEquals("Great discussion on data reductions.", entity.notes)

        val restored = entity.toDomainModel()
        assertEquals(review.id, restored.id)
        assertEquals(5, restored.rating)
        assertEquals(review.notes, restored.notes)
    }
}
