package com.example.astroxplore.features.sync

import com.example.astroxplore.core.database.entity.DownloadState
import com.example.astroxplore.core.database.entity.PendingPaperDeletionEntity
import com.example.astroxplore.core.database.entity.SavedPaperEntity
import com.example.astroxplore.features.feed.model.PaperModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncAndDeletionTest {

    @Test
    fun `SavedPaperEntity stores offline bookmarked papers with un-synced status`() {
        val paper = PaperModel(
            bibcode = "2026A&A...777..888Z",
            rawTitles = listOf("Direct Imaging of Super-Earths"),
            authors = listOf("Zimmerman, E.", "Smith, J."),
            abstractText = "We present direct imaging observations with high contrast.",
            keywords = listOf("Exoplanets"),
            citationCount = 42
        )

        val entity = SavedPaperEntity(
            bibcode = paper.bibcode,
            title = paper.title,
            authors = paper.authors.joinToString(", "),
            abstractText = paper.abstractText,
            category = paper.category,
            dateDisplay = paper.dateDisplay,
            citationCount = paper.citationCount,
            savedAt = System.currentTimeMillis(),
            isSynced = false,
            downloadState = DownloadState.NOT_DOWNLOADED
        )

        assertFalse("Offline bookmarked paper must have isSynced=false until Supabase sync completes", entity.isSynced)
        assertEquals("2026A&A...777..888Z", entity.bibcode)
        assertEquals("Direct Imaging of Super-Earths", entity.title)
        assertEquals("Zimmerman, E., Smith, J.", entity.authors)
        assertEquals(DownloadState.NOT_DOWNLOADED, entity.downloadState)

        val syncedCopy = entity.copy(isSynced = true)
        assertTrue(syncedCopy.isSynced)
    }

    @Test
    fun `PendingPaperDeletionEntity tracks offline deletions for remote queue drain`() {
        val deletion = PendingPaperDeletionEntity(
            bibcode = "2026A&A...777..888Z",
            userId = "user-uuid-123",
            timestamp = 1728000000L
        )

        assertEquals("2026A&A...777..888Z", deletion.bibcode)
        assertEquals("user-uuid-123", deletion.userId)
        assertEquals(1728000000L, deletion.timestamp)
    }
}
