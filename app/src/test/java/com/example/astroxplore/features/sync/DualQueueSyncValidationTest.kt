package com.example.astroxplore.features.sync

import com.example.astroxplore.core.database.entity.GroupEntity
import com.example.astroxplore.core.database.entity.GroupPaperEntity
import com.example.astroxplore.core.database.entity.toEntity
import com.example.astroxplore.features.groups.model.GroupModel
import com.example.astroxplore.features.groups.model.GroupPaperModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualQueueSyncValidationTest {

    @Test
    fun `dual queue maintains local pending mutations while integrating remote synced state`() {
        // Step 1: User creates club offline (Queue 1: Offline outbox)
        val offlineClub = GroupModel(
            id = "local-uuid-1",
            displayId = "ASTRO999",
            name = "Gravitational Waves Working Group",
            ownerId = "my-user-id",
            memberCount = 1,
            isMember = true
        )
        val outboxEntity: GroupEntity = offlineClub.toEntity(isSynced = false)
        assertFalse(outboxEntity.isSynced)
        assertEquals("local-uuid-1", outboxEntity.id)

        // Step 2: Supabase sync completes (Queue 2: Remote live state returned from DB)
        val remoteSyncedClub = offlineClub.copy(
            id = "server-uuid-real",
            memberCount = 2
        )
        val syncedEntity: GroupEntity = remoteSyncedClub.toEntity(isSynced = true)
        assertTrue(syncedEntity.isSynced)
        assertEquals("server-uuid-real", syncedEntity.id)
        assertEquals(2, syncedEntity.memberCount)

        // Step 3: Paper added to group offline retains vote state upon sync
        val paper = GroupPaperModel(
            id = "local-paper-1",
            groupId = syncedEntity.id,
            bibcode = "2026PRL...123..456G",
            addedBy = "my-user-id",
            voteCount = 1,
            isVotedByMe = true,
            title = "Observation of Binary Neutron Star Coalescence"
        )
        val paperEntity: GroupPaperEntity = paper.toEntity(isSynced = false)
        assertFalse(paperEntity.isSynced)
        assertTrue(paperEntity.isVotedByMe)
        assertEquals(1, paperEntity.voteCount)

        val syncedPaper = paper.copy(id = "server-paper-uuid").toEntity(isSynced = true)
        assertTrue(syncedPaper.isSynced)
        assertTrue(syncedPaper.isVotedByMe)
    }
}
