package com.example.astroxplore.features.groups.data

import com.example.astroxplore.core.database.dao.GroupDao
import com.example.astroxplore.core.database.dao.GroupPaperDao
import com.example.astroxplore.core.database.entity.GroupEntity
import com.example.astroxplore.core.database.entity.toEntity
import com.example.astroxplore.features.groups.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val groupDao: GroupDao,
    private val groupPaperDao: GroupPaperDao
) {
    companion object {
        fun normalizeJoinInput(raw: String): String {
            return raw
                .replace("-", "")
                .replace("_", "")
                .filterNot { it.isWhitespace() }
                .uppercase()
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
        if (userId == null) return@withContext
        try {
            // Membership rows are the authoritative source for this user's club list.
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
                            eq("status", "active")
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

    suspend fun createGroup(
        name: String,
        description: String?,
        focusArea: String?
    ): GroupModel = withContext(Dispatchers.IO) {
        val rows = supabaseClient.postgrest.rpc(
            function = "create_group",
            parameters = buildJsonObject {
                put("p_name", name.trim())
                put("p_description", description?.let(::JsonPrimitive) ?: JsonNull)
                put("p_focus_area", focusArea?.let(::JsonPrimitive) ?: JsonNull)
                put("p_visibility", "private")
            }
        ).decodeList<GroupModel>()
        val group = rows.singleOrNull() ?: error("The server did not return the created club")
        groupDao.insertGroup(group.toEntity(isSynced = true))
        group
    }

    suspend fun joinGroup(inviteOrDisplayId: String): Boolean = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext false
        val input = normalizeJoinInput(inviteOrDisplayId)
        try {
            val group = if (input.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                val groupId = supabaseClient.postgrest.rpc(
                    function = "accept_group_invite",
                    parameters = buildJsonObject { put("p_token", input) }
                ).decodeSingle<String>()
                fetchGroupById(groupId)
            } else {
                val group = supabaseClient.postgrest["groups"]
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("display_id", input.uppercase())
                            eq("visibility", "public")
                            eq("status", "active")
                        }
                    }
                    .decodeSingle<GroupModel>()
                supabaseClient.postgrest["group_members"].insert(
                    mapOf("group_id" to group.id, "user_id" to userId, "role" to "member")
                )
                group
            }
            groupDao.insertGroup(group.toEntity(isSynced = true))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun createGroupInvite(groupId: String): String = withContext(Dispatchers.IO) {
        val expiresAt = java.time.OffsetDateTime.now().plusDays(7).toString()
        supabaseClient.postgrest.rpc(
            function = "create_group_invite",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                put("p_expires_at", expiresAt)
                put("p_max_uses", 1)
            }
        ).decodeSingle<String>()
    }

    private suspend fun fetchGroupById(groupId: String): GroupModel =
        supabaseClient.postgrest["groups"]
            .select(columns = Columns.ALL) { filter { eq("id", groupId) } }
            .decodeSingle()

    suspend fun leaveGroup(groupId: String) = withContext(Dispatchers.IO) {
        supabaseClient.postgrest.rpc<JsonElement>(
            function = "leave_group",
            parameters = buildJsonObject { put("p_group_id", groupId) }
        )
        groupDao.deleteGroup(groupId)
        groupPaperDao.deleteGroupPapers(groupId)
    }

    suspend fun archiveGroup(groupId: String) = withContext(Dispatchers.IO) {
        supabaseClient.postgrest.rpc<JsonElement>(
            function = "archive_group",
            parameters = buildJsonObject { put("p_group_id", groupId) }
        )
        groupDao.deleteGroup(groupId)
        groupPaperDao.deleteGroupPapers(groupId)
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
        supabaseClient.postgrest["group_presentations"].insert(
            mapOf(
                "group_id" to groupId,
                "bibcode" to bibcode,
                "presenter_id" to userId,
                "scheduled_at" to scheduledAt.toString(),
                "time_zone" to java.time.ZoneId.systemDefault().id
            )
        )
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
        supabaseClient.postgrest.rpc<JsonElement>(
            function = "remove_group_member",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                put("p_user_id", targetUserId)
            }
        )
    }

    suspend fun updateMemberRole(groupId: String, targetUserId: String, newRole: String) = withContext(Dispatchers.IO) {
        supabaseClient.postgrest.rpc<JsonElement>(
            function = "set_group_member_role",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                put("p_user_id", targetUserId)
                put("p_new_role", newRole)
            }
        )
    }

    suspend fun addSessionReview(groupId: String, bibcode: String, notes: String, rating: Int) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        try {
            supabaseClient.postgrest["group_session_reviews"].insert(
                mapOf(
                    "group_id" to groupId,
                    "bibcode" to bibcode,
                    "reviewer_id" to userId,
                    "notes" to notes,
                    "rating" to rating
                )
            )
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
        supabaseClient.postgrest.rpc<JsonElement>(
            function = "check_in_session",
            parameters = buildJsonObject { put("p_presentation_id", presentationId) }
        )
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
