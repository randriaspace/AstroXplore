package com.example.astroxplore.features.sync

import com.example.astroxplore.core.database.entity.toEntity
import com.example.astroxplore.features.groups.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaFallbackAndOutboxTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `GroupModel deserialization tolerates missing optional columns`() {
        // Simulates remote Supabase response where new columns (focus_area, visibility) are omitted
        val rawJson = """
            {
                "id": "123e4567-e89b-12d3-a456-426614174000",
                "display_id": "ASTRO99",
                "name": "Cosmology Journal Club",
                "owner_id": "user-uuid-1",
                "member_count": 8
            }
        """.trimIndent()

        val decoded = json.decodeFromString<GroupModel>(rawJson)
        assertEquals("Cosmology Journal Club", decoded.name)
        assertEquals("ASTRO99", decoded.displayId)
        assertEquals("private", decoded.visibility) // Default fallback
        assertEquals(null, decoded.description)
        assertEquals(null, decoded.focusArea)
    }

    @Test
    fun `GroupModel entity mapping correctly preserves outbox isSynced flag`() {
        val model = GroupModel(
            id = "grp-local-1",
            displayId = "ASTRO12345",
            name = "Stellar Astrophysics",
            description = "Stellar interiors and evolution",
            ownerId = "user-1",
            focusArea = "Stars",
            memberCount = 1,
            createdAt = "2026-10-13T20:00:00",
            visibility = "public",
            meetingSchedule = "Thursdays 4pm",
            meetingLocation = "Seminar Room B",
            isMember = true
        )

        // When saved locally offline:
        val unsyncedEntity = model.toEntity(isSynced = false)
        assertFalse("Unsynced entity must have isSynced=false for queue replay", unsyncedEntity.isSynced)
        assertEquals("grp-local-1", unsyncedEntity.id)

        // When synced online later:
        val syncedEntity = model.toEntity(isSynced = true)
        assertTrue("Synced entity must have isSynced=true", syncedEntity.isSynced)
    }

    @Test
    fun `PresentationModel entity mapping preserves check-in and outbox status`() {
        val presentation = PresentationModel(
            id = "pres-1",
            groupId = "grp-1",
            bibcode = "2026ApJ...999..111S",
            presenterId = "user-1",
            scheduledAt = "2026-10-20T15:00:00",
            paperTitle = "Protoplanetary Disks with JWST",
            presenterName = "Dr. Sagan",
            meetingLocation = "Virtual Room 1",
            attendeeCount = 12,
            isCheckedIn = true
        )

        val entity = presentation.toEntity(isSynced = false)
        assertFalse(entity.isSynced)
        assertTrue(entity.isCheckedIn)
        assertEquals("Dr. Sagan", entity.presenterName)

        val domain = entity.toDomainModel()
        assertEquals(presentation.paperTitle, domain.paperTitle)
        assertTrue(domain.isCheckedIn)
    }

    @Test
    fun `SessionReviewModel entity mapping preserves ratings and notes`() {
        val review = SessionReviewModel(
            id = "rev-1",
            groupId = "grp-1",
            bibcode = "2026MNRAS.555..222K",
            reviewerId = "user-2",
            notes = "Compelling Bayesian methodology for transit timing analysis.",
            rating = 5,
            paperTitle = "Exoplanet Transits",
            reviewerName = "Peer Reviewer"
        )

        val entity = review.toEntity(isSynced = false)
        assertFalse(entity.isSynced)
        assertEquals(5, entity.rating)
        assertEquals("Compelling Bayesian methodology for transit timing analysis.", entity.notes)

        val domain = entity.toDomainModel()
        assertEquals(review.id, domain.id)
        assertEquals(review.notes, domain.notes)
    }
}
