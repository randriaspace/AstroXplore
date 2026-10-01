package com.example.astroxplore.features.groups.data

import android.util.Log
import com.example.astroxplore.core.database.dao.GroupDao
import com.example.astroxplore.core.database.dao.GroupPaperDao
import com.example.astroxplore.core.database.entity.GroupEntity
import com.example.astroxplore.core.database.entity.toEntity
import com.example.astroxplore.core.network.NetworkConnectivityObserver
import com.example.astroxplore.features.groups.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val groupDao: GroupDao,
    private val groupPaperDao: GroupPaperDao,
    private val networkConnectivityObserver: NetworkConnectivityObserver
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        repositoryScope.launch {
            networkConnectivityObserver.isConnected.collect { isConnected ->
                if (isConnected) {
                    syncGroups()
                }
            }
        }
    }
    /**
     * Reactively observe groups from the local database (Offline-First)
     */
    fun getLocalGroups(): Flow<List<GroupModel>> = groupDao.getAllGroups().map { entities ->
        entities.map { it.toDomainModel() }
    }

    /**
     * Reactively observe papers in a group from local cache
     */
    fun getLocalGroupPapers(groupId: String): Flow<List<GroupPaperModel>> = 
        groupPaperDao.getGroupPapers(groupId).map { entities ->
            entities.map { it.toDomainModel() }
        }

    suspend fun syncGroups() = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id 
        if (userId == null) {
            Log.e("GroupRepository", "syncGroups: currentUserOrNull() is null! User not authenticated.")
            return@withContext
        }
        try {
            // 0. Push any pending local unsynced groups to Supabase
            val unsyncedGroups = groupDao.getUnsyncedGroups()
            for (localGroup in unsyncedGroups) {
                try {
                    val supabaseGroup = mapOf(
                        "id" to localGroup.id,
                        "display_id" to localGroup.displayId,
                        "name" to localGroup.name,
                        "description" to localGroup.description,
                        "owner_id" to localGroup.ownerId,
                        "focus_area" to localGroup.focusArea,
                        "member_count" to localGroup.memberCount
                    )
                    val insertedGroup = supabaseClient.postgrest["groups"]
                        .insert(supabaseGroup) { select() }
                        .decodeSingle<GroupModel>()

                    val initialMember = GroupMemberModel(
                        groupId = insertedGroup.id,
                        userId = userId,
                        role = "admin"
                    )
                    try {
                        supabaseClient.postgrest["group_members"].insert(initialMember)
                    } catch (_: Exception) {}

                    // Remove local temporary row and insert synced row
                    groupDao.insertGroup(insertedGroup.toEntity(isSynced = true))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 1. Fetch group IDs where the user is a member
            val memberships = supabaseClient.postgrest["group_members"]
                .select(columns = Columns.ALL) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<GroupMemberModel>()
            
            val groupIds = memberships.map { it.groupId }
            
            if (groupIds.isNotEmpty()) {
                // 2. Fetch the actual group details for those IDs
                val remoteGroups = supabaseClient.postgrest["groups"]
                    .select(columns = Columns.ALL) {
                        filter { 
                            isIn("id", groupIds)
                        }
                    }
                    .decodeList<GroupModel>()
                
                // 3. Update local Room cache
                groupDao.insertGroups(remoteGroups.map { it.toEntity(isSynced = true) })

                // 4. Sync papers for each group
                remoteGroups.forEach { group ->
                    syncGroupPapers(group.id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace() // Simple logging to Logcat
        }
    }

    suspend fun syncGroupPapers(groupId: String) = withContext(Dispatchers.IO) {
        try {
            val remotePapers = supabaseClient.postgrest["group_papers"]
                .select(columns = Columns.ALL) {
                    filter { eq("group_id", groupId) }
                }
                .decodeList<GroupPaperModel>()
            
            groupPaperDao.insertGroupPapers(remotePapers.map { it.toEntity(isSynced = true) })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createGroup(name: String, description: String?, focusArea: String?) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id 
        if (userId == null) {
            Log.e("GroupRepository", "createGroup: currentUserOrNull() is null! User not authenticated.")
            return@withContext
        }
        
        // Short human-friendly ID
        val displayId = UUID.randomUUID().toString().take(8).uppercase()
        val localId = UUID.randomUUID().toString()
        
        val localGroup = GroupEntity(
            id = localId,
            displayId = displayId,
            name = name,
            description = description,
            ownerId = userId,
            focusArea = focusArea,
            memberCount = 1,
            createdAt = LocalDateTime.now().toString(),
            isSynced = false
        )
        groupDao.insertGroup(localGroup)

        try {
            val supabaseGroup = mapOf(
                "display_id" to displayId,
                "name" to name,
                "description" to description,
                "owner_id" to userId,
                "focus_area" to focusArea,
                "member_count" to 1
            )
            val insertedGroup = supabaseClient.postgrest["groups"]
                .insert(supabaseGroup) { select() }
                .decodeSingle<GroupModel>()
            
            // Also create initial membership for the owner
            val initialMember = GroupMemberModel(
                groupId = insertedGroup.id,
                userId = userId,
                role = "admin"
            )
            supabaseClient.postgrest["group_members"].insert(initialMember)
            
            // Update local
            groupDao.insertGroup(insertedGroup.toEntity(isSynced = true))
        } catch (e: Exception) {
            e.printStackTrace()
            // Remains unsynced
        }
    }

    suspend fun joinGroupByDisplayId(displayId: String): Boolean = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext false
        try {
            val group = supabaseClient.postgrest["groups"]
                .select(columns = Columns.ALL) {
                    filter { eq("display_id", displayId.uppercase()) }
                }
                .decodeSingle<GroupModel>()
            
            val membership = GroupMemberModel(
                groupId = group.id,
                userId = userId,
                role = "member"
            )
            supabaseClient.postgrest["group_members"].insert(membership)
            
            // Save locally
            groupDao.insertGroup(group.toEntity(isSynced = true))
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun leaveGroup(groupId: String) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        try {
            supabaseClient.postgrest["group_members"].delete {
                filter {
                    eq("group_id", groupId)
                    eq("user_id", userId)
                }
            }
            groupDao.clearAll()
            syncGroups()
        } catch (e: Exception) {
            // Log error
        }
    }

    suspend fun deleteGroup(groupId: String) = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["groups"].delete {
                filter { eq("id", groupId) }
            }
            groupDao.clearAll()
            syncGroups()
        } catch (e: Exception) {
            // Log error
        }
    }

    suspend fun updateGroup(groupId: String, name: String, description: String?, focusArea: String?) = withContext(Dispatchers.IO) {
        try {
            val update = mapOf(
                "name" to name,
                "description" to description,
                "focus_area" to focusArea
            )
            supabaseClient.postgrest["groups"].update(update) {
                filter { eq("id", groupId) }
            }
            syncGroups()
        } catch (e: Exception) {
            // Log error
        }
    }

    suspend fun addPaperToGroup(groupId: String, bibcode: String) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        val localId = UUID.randomUUID().toString()
        val groupPaper = GroupPaperModel(
            id = localId,
            groupId = groupId,
            bibcode = bibcode,
            addedBy = userId,
            addedAt = LocalDateTime.now().toString()
        )
        
        // Save locally first
        groupPaperDao.insertGroupPaper(groupPaper.toEntity(isSynced = false))

        try {
            val supabaseGroupPaper = mapOf(
                "group_id" to groupId,
                "bibcode" to bibcode,
                "added_by" to userId
            )
            val inserted = supabaseClient.postgrest["group_papers"]
                .insert(supabaseGroupPaper) { select() }
                .decodeSingle<GroupPaperModel>()
            
            // Mark as synced and update with real ID from Supabase
            groupPaperDao.deleteGroupPaper(groupId, bibcode)
            groupPaperDao.insertGroupPaper(inserted.toEntity(isSynced = true))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun voteForPaper(groupPaperId: String) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        try {
            val vote = mapOf(
                "group_paper_id" to groupPaperId,
                "user_id" to userId
            )
            supabaseClient.postgrest["group_paper_votes"].insert(vote)
        } catch (e: Exception) {
            // Log error
        }
    }

    suspend fun unvoteForPaper(groupPaperId: String) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        try {
            supabaseClient.postgrest["group_paper_votes"].delete {
                filter {
                    eq("group_paper_id", groupPaperId)
                    eq("user_id", userId)
                }
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    suspend fun getGroupPapers(groupId: String): List<GroupPaperModel> = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
        try {
            val papers = supabaseClient.postgrest["group_papers"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("group_id", groupId)
                    }
                }
                .decodeList<GroupPaperModel>()

            // If we have a user, check their votes
            if (userId.isNotEmpty() && papers.isNotEmpty()) {
                val userVotes = supabaseClient.postgrest["group_paper_votes"]
                    .select(columns = Columns.list("group_paper_id")) {
                        filter {
                            eq("user_id", userId)
                            // Ideally use 'in' filter for group paper ids
                        }
                    }
                    .decodeList<Map<String, String>>()
                    .map { it["group_paper_id"] }
                
                return@withContext papers.map { paper ->
                    paper.copy(isVotedByMe = userVotes.contains(paper.id))
                }
            }
            
            papers
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun schedulePresentation(groupId: String, bibcode: String, scheduledAt: LocalDateTime) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        val presentation = PresentationModel(
            groupId = groupId,
            bibcode = bibcode,
            presenterId = userId,
            scheduledAt = scheduledAt.toString()
        )
        try {
            supabaseClient.postgrest["group_presentations"].insert(presentation)
        } catch (e: Exception) {
            // Log error
        }
    }

    suspend fun getGroupPresentations(groupId: String): List<PresentationModel> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["group_presentations"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("group_id", groupId)
                    }
                }
                .decodeList<PresentationModel>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getGroupMembers(groupId: String): List<GroupMemberModel> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["group_members"]
                .select(columns = Columns.ALL) {
                    filter { eq("group_id", groupId) }
                }
                .decodeList<GroupMemberModel>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun kickMember(groupId: String, targetUserId: String) = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["group_members"].delete {
                filter {
                    eq("group_id", groupId)
                    eq("user_id", targetUserId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateMemberRole(groupId: String, targetUserId: String, newRole: String) = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["group_members"].update(mapOf("role" to newRole)) {
                filter {
                    eq("group_id", groupId)
                    eq("user_id", targetUserId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addSessionReview(groupId: String, bibcode: String, notes: String, rating: Int) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        val review = SessionReviewModel(
            groupId = groupId,
            bibcode = bibcode,
            reviewerId = userId,
            notes = notes,
            rating = rating,
            createdAt = LocalDateTime.now().toString()
        )
        try {
            supabaseClient.postgrest["group_session_reviews"].insert(review)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getSessionReviews(groupId: String): List<SessionReviewModel> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["group_session_reviews"]
                .select(columns = Columns.ALL) {
                    filter { eq("group_id", groupId) }
                }
                .decodeList<SessionReviewModel>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun checkInSession(presentationId: String) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        val attendance = SessionAttendanceModel(
            presentationId = presentationId,
            userId = userId,
            checkedInAt = LocalDateTime.now().toString()
        )
        try {
            supabaseClient.postgrest["group_session_attendance"].insert(attendance)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getSessionAttendance(presentationId: String): List<SessionAttendanceModel> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["group_session_attendance"]
                .select(columns = Columns.ALL) {
                    filter { eq("presentation_id", presentationId) }
                }
                .decodeList<SessionAttendanceModel>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getGroupKpis(groupId: String): GroupKpiModel = withContext(Dispatchers.IO) {
        try {
            val papers = getGroupPapers(groupId)
            val totalVotes = papers.sumOf { it.voteCount }
            val presentations = getGroupPresentations(groupId)
            val members = getGroupMembers(groupId)
            val reviews = getSessionReviews(groupId)
            
            GroupKpiModel(
                totalPapers = papers.size,
                totalVotes = totalVotes,
                totalPresentations = presentations.size,
                totalMembers = members.size.coerceAtLeast(1),
                totalReviews = reviews.size
            )
        } catch (e: Exception) {
            GroupKpiModel()
        }
    }

    // Mappers
    private fun GroupEntity.toDomainModel() = GroupModel(
        id = id,
        displayId = displayId,
        name = name,
        description = description,
        ownerId = ownerId,
        focusArea = focusArea,
        memberCount = memberCount,
        createdAt = createdAt
    )

    private fun GroupModel.toEntity(isSynced: Boolean) = GroupEntity(
        id = id,
        displayId = displayId,
        name = name,
        description = description,
        ownerId = ownerId,
        focusArea = focusArea,
        memberCount = memberCount,
        createdAt = createdAt,
        isSynced = isSynced
    )
}
