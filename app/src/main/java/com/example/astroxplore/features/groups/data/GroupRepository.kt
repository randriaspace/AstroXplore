package com.example.astroxplore.features.groups.data

import com.example.astroxplore.core.database.dao.*
import com.example.astroxplore.core.database.entity.*
import com.example.astroxplore.features.groups.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import com.example.astroxplore.core.network.NetworkConnectivityObserver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val groupDao: GroupDao,
    private val groupPaperDao: GroupPaperDao,
    private val groupPresentationDao: GroupPresentationDao,
    private val groupReviewDao: GroupReviewDao,
    private val groupMemberDao: GroupMemberDao,
    private val groupJoinRequestDao: GroupJoinRequestDao,
    private val networkConnectivityObserver: NetworkConnectivityObserver
) {
    fun isNetworkAvailable(): Boolean = networkConnectivityObserver.isNetworkAvailable()

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
     * Reactively observe joined clubs from the local Room database (Offline-First)
     */
    fun getMyGroups(): Flow<List<GroupModel>> = groupDao.getMyGroups().map { entities ->
        entities.map { it.toDomainModel() }
    }

    fun getMyGroupCount(): Flow<Int> = groupDao.getMyGroupCount()

    /**
     * Reactively observe discoverable public clubs from the local database
     */
    fun getExploreGroups(): Flow<List<GroupModel>> = groupDao.getExploreGroups().map { entities ->
        entities.map { it.toDomainModel() }
    }

    /**
     * Reactively observe all clubs
     */
    fun getLocalGroups(): Flow<List<GroupModel>> = groupDao.getAllGroups().map { entities ->
        entities.map { it.toDomainModel() }
    }

    suspend fun getGroupById(groupId: String): GroupModel? = withContext(Dispatchers.IO) {
        groupDao.getGroupById(groupId)?.toDomainModel()
    }

    /**
     * Reactively observe papers in a group from local cache
     */
    fun getLocalGroupPapers(groupId: String): Flow<List<GroupPaperModel>> = 
        groupPaperDao.getGroupPapers(groupId).map { entities ->
            entities.map { it.toDomainModel() }
        }

    fun getGroupPresentationsFlow(groupId: String): Flow<List<PresentationModel>> =
        groupPresentationDao.getPresentations(groupId).map { entities ->
            entities.map { it.toDomainModel() }
        }

    fun getSessionReviewsFlow(groupId: String): Flow<List<SessionReviewModel>> =
        groupReviewDao.getReviews(groupId).map { entities ->
            entities.map { it.toDomainModel() }
        }

    fun getGroupMembersFlow(groupId: String): Flow<List<GroupMemberModel>> =
        groupMemberDao.getMembers(groupId).map { entities ->
            entities.map { it.toDomainModel() }
        }

    suspend fun syncGroups() = withContext(Dispatchers.IO) {
        if (!networkConnectivityObserver.isNetworkAvailable()) {
            return@withContext // Offline: preserve local Room cache in read-only mode
        }

        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        try {
            val memberships = try {
                supabaseClient.postgrest["group_members"]
                    .select(columns = Columns.ALL) {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<GroupMemberModel>()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                emptyList()
            }

            val memberGroupIds = memberships.map { it.groupId }.toSet()

            var remoteFetchFailed = false
            val remoteGroups = try {
                supabaseClient.postgrest["groups"]
                    .select(columns = Columns.ALL) {
                        filter { neq("status", "archived") }
                    }
                    .decodeList<GroupModel>()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                remoteFetchFailed = true
                emptyList()
            }

            // If remote groups could not be loaded due to network/server error, do not prune local data!
            if (remoteFetchFailed) {
                return@withContext
            }

            val existingLocal = groupDao.getAllGroups().firstOrNull()?.associateBy { it.id } ?: emptyMap()
            val remoteGroupIds = remoteGroups.map { it.id }.toSet()

            // Prune local groups that no longer exist remotely (e.g. dissolved or deleted online)
            existingLocal.values.forEach { localGroup ->
                val isRemoteStillValid = remoteGroupIds.contains(localGroup.id)
                if (!isRemoteStillValid) {
                    groupDao.deleteGroup(localGroup.id)
                    groupPaperDao.deleteGroupPapers(localGroup.id)
                    groupPresentationDao.deleteGroupPresentations(localGroup.id)
                    groupReviewDao.deleteGroupReviews(localGroup.id)
                    groupMemberDao.deleteGroupMembers(localGroup.id)
                }
            }

            if (remoteGroups.isNotEmpty()) {
                val entities = remoteGroups.map { group ->
                    val isMember = memberGroupIds.contains(group.id) ||
                            group.ownerId.equals(userId, ignoreCase = true) ||
                            existingLocal[group.id]?.isMember == true ||
                            group.isMember
                    val schedule = group.meetingSchedule.ifBlank { existingLocal[group.id]?.meetingSchedule.orEmpty() }
                    val location = group.meetingLocation.ifBlank { existingLocal[group.id]?.meetingLocation.orEmpty() }
                    group.toEntity(isSynced = true).copy(
                        isMember = isMember,
                        meetingSchedule = schedule,
                        meetingLocation = location
                    )
                }
                groupDao.insertGroups(entities)

                remoteGroups.filter { memberGroupIds.contains(it.id) || it.ownerId.equals(userId, ignoreCase = true) }.forEach { group ->
                    try {
                        syncGroupPapers(group.id)
                        syncGroupMembers(group.id)
                        syncGroupPresentations(group.id)
                        syncGroupReviews(group.id)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
    }

    suspend fun syncGroupPapers(groupId: String) = withContext(Dispatchers.IO) {
        try {
            val remotePapers = supabaseClient.postgrest["group_papers"]
                .select(columns = Columns.ALL) {
                    filter { eq("group_id", groupId) }
                }
                .decodeList<GroupPaperModel>()
            
            if (remotePapers.isNotEmpty()) {
                groupPaperDao.insertGroupPapers(remotePapers.map { it.toEntity(isSynced = true) })
            }
        } catch (_: Exception) {
            // Keep local cached papers
        }
    }

    suspend fun syncGroupMembers(groupId: String) = withContext(Dispatchers.IO) {
        try {
            val remoteMembers = supabaseClient.postgrest["group_members"]
                .select(columns = Columns.ALL) {
                    filter { eq("group_id", groupId) }
                }
                .decodeList<GroupMemberModel>()
            
            if (remoteMembers.isNotEmpty()) {
                groupMemberDao.insertMembers(remoteMembers.map { it.toEntity(isSynced = true) })
            }
        } catch (_: Exception) {
            // Keep local cached members
        }
    }

    suspend fun syncGroupPresentations(groupId: String) = withContext(Dispatchers.IO) {
        try {
            val remotePresentations = supabaseClient.postgrest["group_presentations"]
                .select(columns = Columns.ALL) {
                    filter { eq("group_id", groupId) }
                }
                .decodeList<PresentationModel>()
            
            if (remotePresentations.isNotEmpty()) {
                groupPresentationDao.insertPresentations(remotePresentations.map { it.toEntity(isSynced = true) })
            }
        } catch (_: Exception) {
            // Keep local cached presentations
        }
    }

    suspend fun syncGroupReviews(groupId: String) = withContext(Dispatchers.IO) {
        try {
            val remoteReviews = supabaseClient.postgrest["group_session_reviews"]
                .select(columns = Columns.ALL) {
                    filter { eq("group_id", groupId) }
                }
                .decodeList<SessionReviewModel>()
            
            if (remoteReviews.isNotEmpty()) {
                groupReviewDao.insertReviews(remoteReviews.map { it.toEntity(isSynced = true) })
            }
        } catch (_: Exception) {
            // Keep local cached reviews
        }
    }

    suspend fun syncPendingMutations() = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        
        // 1. In online-only mode, clean up any unsynced legacy groups so they don't linger as ghosts
        try {
            val unsyncedGroups = groupDao.getUnsyncedGroups()
            if (unsyncedGroups.isNotEmpty()) {
                for (localGroup in unsyncedGroups) {
                    groupDao.deleteGroup(localGroup.id)
                }
            }
        } catch (_: Exception) {}

        // 2. Sync pending local papers
        try {
            val unsyncedPapers = groupPaperDao.getUnsyncedGroupPapers()
            for (paper in unsyncedPapers) {
                try {
                    val supabaseGroupPaper = mapOf(
                        "group_id" to paper.groupId,
                        "bibcode" to paper.bibcode,
                        "added_by" to (if (paper.addedBy.startsWith("local_")) userId else paper.addedBy)
                    )
                    val inserted = supabaseClient.postgrest["group_papers"]
                        .insert(supabaseGroupPaper) { select() }
                        .decodeSingleOrNull<GroupPaperModel>()
                    if (inserted != null) {
                        groupPaperDao.deleteGroupPaperById(paper.id)
                        groupPaperDao.insertGroupPaper(
                            inserted.copy(
                                title = paper.title,
                                authors = paper.authors,
                                year = paper.year,
                                isVotedByMe = paper.isVotedByMe
                            ).toEntity(isSynced = true)
                        )
                    } else {
                        groupPaperDao.updateSyncStatus(paper.id, true)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 3. Sync pending local presentations
        try {
            val unsyncedPresentations = groupPresentationDao.getUnsyncedPresentations()
            for (pres in unsyncedPresentations) {
                try {
                    val inserted = supabaseClient.postgrest["group_presentations"].insert(
                        mapOf(
                            "group_id" to pres.groupId,
                            "bibcode" to pres.bibcode,
                            "presenter_id" to (if (pres.presenterId.startsWith("local_")) userId else pres.presenterId),
                            "scheduled_at" to pres.scheduledAt,
                            "time_zone" to java.time.ZoneId.systemDefault().id
                        )
                    ) { select() }.decodeSingleOrNull<PresentationModel>()
                    if (inserted != null) {
                        groupPresentationDao.deletePresentation(pres.id)
                        groupPresentationDao.insertPresentation(
                            inserted.copy(
                                paperTitle = pres.paperTitle,
                                presenterName = pres.presenterName,
                                meetingLocation = pres.meetingLocation,
                                isCheckedIn = pres.isCheckedIn
                            ).toEntity(isSynced = true)
                        )
                    } else {
                        groupPresentationDao.updateSyncStatus(pres.id, true)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 4. Sync pending local reviews
        try {
            val unsyncedReviews = groupReviewDao.getUnsyncedReviews()
            for (review in unsyncedReviews) {
                try {
                    val inserted = supabaseClient.postgrest["group_session_reviews"].insert(
                        mapOf(
                            "group_id" to review.groupId,
                            "bibcode" to review.bibcode,
                            "reviewer_id" to (if (review.reviewerId.startsWith("local_")) userId else review.reviewerId),
                            "notes" to review.notes,
                            "rating" to review.rating
                        )
                    ) { select() }.decodeSingleOrNull<SessionReviewModel>()
                    if (inserted != null) {
                        groupReviewDao.deleteReview(review.id)
                        groupReviewDao.insertReview(
                            inserted.copy(
                                paperTitle = review.paperTitle,
                                reviewerName = review.reviewerName
                            ).toEntity(isSynced = true)
                        )
                    } else {
                        groupReviewDao.updateSyncStatus(review.id, true)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        
        // 5. Run full sync
        syncGroups()
    }

    suspend fun observeRealtimeGroupUpdates(groupId: String, onUpdate: suspend () -> Unit) = withContext(Dispatchers.IO) {
        try {
            val channel = supabaseClient.channel("group_realtime_$groupId")
            val papersFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "group_papers"
            }
            val votesFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "group_paper_votes"
            }
            channel.subscribe()
            
            kotlinx.coroutines.coroutineScope {
                launch {
                    papersFlow.collect {
                        syncGroupPapers(groupId)
                        onUpdate()
                    }
                }
                launch {
                    votesFlow.collect {
                        syncGroupPapers(groupId)
                        onUpdate()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Room cache acts as offline source of truth
        }
    }

    suspend fun createGroup(
        name: String,
        description: String?,
        focusArea: String?,
        schedule: String = "",
        location: String = ""
    ): GroupModel = withContext(Dispatchers.IO) {
        if (!networkConnectivityObserver.isNetworkAvailable()) {
            throw IllegalStateException("Internet connection required to create a Journal Club.")
        }
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Please sign in to create a Journal Club.")

        val displayId = "ASTRO" + UUID.randomUUID().toString().filter { it.isLetterOrDigit() }.take(5).uppercase()
        val createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

        val payload = CreateGroupPayload(
            name = name.trim(),
            displayId = displayId,
            description = description?.trim()?.ifBlank { null },
            focusArea = focusArea?.trim()?.ifBlank { null },
            ownerId = currentUserId,
            visibility = "private",
            status = "active",
            isPublic = false,
            memberCount = 1,
            meetingSchedule = schedule.trim().ifBlank { null },
            meetingLocation = location.trim().ifBlank { null }
        )

        // Remote Supabase creation
        val inserted: GroupModel = try {
            supabaseClient.postgrest["groups"]
                .insert(payload) { select() }
                .decodeSingle<GroupModel>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Fall back to create_group RPC if direct insert fails
            try {
                val rpcRows = supabaseClient.postgrest.rpc(
                    function = "create_group",
                    parameters = buildJsonObject {
                        put("p_name", name.trim())
                        put("p_description", description?.trim()?.let(::JsonPrimitive) ?: JsonNull)
                        put("p_focus_area", focusArea?.trim()?.let(::JsonPrimitive) ?: JsonNull)
                        put("p_visibility", "private")
                    }
                ).decodeList<GroupModel>()
                val remote = rpcRows.singleOrNull() ?: throw IllegalStateException("Server returned empty group response.")
                if (schedule.isNotBlank() || location.isNotBlank()) {
                    try {
                        supabaseClient.postgrest["groups"].update(
                            UpdateGroupPayload(
                                name = name.trim(),
                                description = description?.trim(),
                                focusArea = focusArea?.trim(),
                                meetingSchedule = schedule.trim().ifBlank { null },
                                meetingLocation = location.trim().ifBlank { null }
                            )
                        ) { filter { eq("id", remote.id) } }
                    } catch (_: Exception) {}
                }
                remote.copy(meetingSchedule = schedule, meetingLocation = location)
            } catch (rpcEx: Exception) {
                if (rpcEx is CancellationException) throw rpcEx
                throw IllegalStateException("Failed to create club on server: ${e.message ?: rpcEx.message ?: "Unknown error"}")
            }
        }

        // Add founder as admin member in Supabase
        try {
            supabaseClient.postgrest["group_members"].insert(
                GroupMemberInsertPayload(
                    groupId = inserted.id,
                    userId = currentUserId,
                    role = "admin"
                )
            )
        } catch (_: Exception) {}

        val finalized = inserted.copy(
            meetingSchedule = schedule.ifBlank { inserted.meetingSchedule },
            meetingLocation = location.ifBlank { inserted.meetingLocation },
            isMember = true
        )

        // Store confirmed server group in local Room cache with isSynced = true
        groupDao.insertGroup(finalized.toEntity(isSynced = true))

        val ownerMember = GroupMemberModel(
            id = UUID.randomUUID().toString(),
            groupId = finalized.id,
            userId = currentUserId,
            userName = "You (Founder)",
            role = "admin",
            joinedAt = createdAt
        )
        groupMemberDao.insertMember(ownerMember.toEntity(isSynced = true))

        finalized
    }

    suspend fun joinGroup(inviteOrDisplayId: String): Boolean = withContext(Dispatchers.IO) {
        if (!networkConnectivityObserver.isNetworkAvailable()) {
            throw IllegalStateException("Internet connection required to join a club.")
        }
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Please sign in to join a club.")

        val normalized = normalizeJoinInput(inviteOrDisplayId)

        // 1. Check local clubs first (matches displayId e.g. "ASTRO123", "JWST01", or ID)
        val allLocal = groupDao.getAllGroups().firstOrNull() ?: emptyList()
        val localMatch = allLocal.find { 
            normalizeJoinInput(it.displayId) == normalized || 
            normalizeJoinInput(it.id) == normalized ||
            it.name.contains(inviteOrDisplayId.trim(), ignoreCase = true)
        }

        if (localMatch != null) {
            groupDao.updateMembership(localMatch.id, true)
            val member = GroupMemberModel(
                id = UUID.randomUUID().toString(),
                groupId = localMatch.id,
                userId = currentUserId,
                userName = "You",
                role = "member",
                joinedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            )
            groupMemberDao.insertMember(member.toEntity(isSynced = true))

            try {
                supabaseClient.postgrest["group_members"].insert(
                    GroupMemberInsertPayload(
                        groupId = localMatch.id,
                        userId = currentUserId,
                        role = "member"
                    )
                )
            } catch (_: Exception) {}
            return@withContext true
        }

        // 2. Lookup remote group by display_id or id
        try {
            val remoteGroups = supabaseClient.postgrest["groups"]
                .select(columns = Columns.ALL)
                .decodeList<GroupModel>()

            val found = remoteGroups.find {
                normalizeJoinInput(it.displayId) == normalized ||
                normalizeJoinInput(it.id) == normalized ||
                it.name.contains(inviteOrDisplayId.trim(), ignoreCase = true)
            }

            if (found != null) {
                // Save membership to Supabase
                try {
                    supabaseClient.postgrest["group_members"].insert(
                        GroupMemberInsertPayload(
                            groupId = found.id,
                            userId = currentUserId,
                            role = "member"
                        )
                    )
                } catch (_: Exception) {}

                // Save to local Room DB
                groupDao.insertGroup(found.toEntity(isSynced = true).copy(isMember = true))
                val member = GroupMemberModel(
                    id = UUID.randomUUID().toString(),
                    groupId = found.id,
                    userId = currentUserId,
                    userName = "You",
                    role = "member",
                    joinedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                )
                groupMemberDao.insertMember(member.toEntity(isSynced = true))

                try {
                    syncGroupPapers(found.id)
                    syncGroupMembers(found.id)
                    syncGroupPresentations(found.id)
                } catch (_: Exception) {}

                return@withContext true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep local fallback
        }

        false
    }

    suspend fun joinPublicClub(groupId: String): Boolean = withContext(Dispatchers.IO) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: "local_user"
        groupDao.updateMembership(groupId, true)
        val member = GroupMemberModel(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            userId = currentUserId,
            userName = "You",
            role = "member",
            joinedAt = LocalDateTime.now().toString()
        )
        groupMemberDao.insertMember(member.toEntity(isSynced = true))
        true
    }

    suspend fun createGroupInvite(groupId: String): String = withContext(Dispatchers.IO) {
        try {
            val expiresAt = java.time.OffsetDateTime.now().plusDays(7).toString()
            supabaseClient.postgrest.rpc(
                function = "create_group_invite",
                parameters = buildJsonObject {
                    put("p_group_id", groupId)
                    put("p_expires_at", expiresAt)
                    put("p_max_uses", 10)
                }
            ).decodeSingle<String>()
        } catch (_: Exception) {
            // Generate clean offline invite token
            val group = groupDao.getGroupById(groupId)
            "ASTRO-${group?.displayId ?: groupId.take(6).uppercase()}-INVITE"
        }
    }

    fun getPendingJoinRequestsFlow(groupId: String): Flow<List<GroupJoinRequestModel>> =
        groupJoinRequestDao.getPendingRequestsForGroup(groupId).map { entities ->
            entities.map { it.toDomainModel() }
        }

    suspend fun syncPendingJoinRequests(groupId: String) = withContext(Dispatchers.IO) {
        try {
            val remoteRequests = supabaseClient.postgrest["group_join_requests"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("group_id", groupId)
                        eq("status", "pending")
                    }
                }
                .decodeList<GroupJoinRequestModel>()

            if (remoteRequests.isNotEmpty()) {
                groupJoinRequestDao.insertRequests(remoteRequests.map { it.toEntity(isSynced = true) })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun submitJoinRequest(groupId: String): Boolean = withContext(Dispatchers.IO) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: "local_user"
        val request = GroupJoinRequestModel(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            userId = currentUserId,
            status = "pending",
            createdAt = LocalDateTime.now().toString(),
            userName = "You"
        )
        groupJoinRequestDao.insertRequest(request.toEntity(isSynced = false))

        try {
            supabaseClient.postgrest.rpc(
                function = "submit_group_join_request",
                parameters = buildJsonObject { put("p_group_id", groupId) }
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Direct table fallback if RPC is missing
            try {
                supabaseClient.postgrest["group_join_requests"].insert(
                    mapOf("group_id" to groupId, "user_id" to currentUserId, "status" to "pending")
                )
            } catch (_: Exception) {}
            true
        }
    }

    suspend fun approveJoinRequest(requestId: String, groupId: String, applicantUserId: String): Boolean = withContext(Dispatchers.IO) {
        groupJoinRequestDao.updateRequestStatus(requestId, "accepted")
        val newMember = GroupMemberModel(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            userId = applicantUserId,
            userName = "Scholar",
            role = "member",
            joinedAt = LocalDateTime.now().toString()
        )
        groupMemberDao.insertMember(newMember.toEntity(isSynced = true))

        try {
            supabaseClient.postgrest.rpc(
                function = "approve_group_join_request",
                parameters = buildJsonObject { put("p_request_id", requestId) }
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Direct table fallback
            try {
                supabaseClient.postgrest["group_join_requests"].update(mapOf("status" to "accepted")) {
                    filter { eq("id", requestId) }
                }
                supabaseClient.postgrest["group_members"].insert(
                    mapOf("group_id" to groupId, "user_id" to applicantUserId, "role" to "member")
                )
            } catch (_: Exception) {}
            true
        }
    }

    suspend fun declineJoinRequest(requestId: String): Boolean = withContext(Dispatchers.IO) {
        groupJoinRequestDao.updateRequestStatus(requestId, "rejected")
        try {
            supabaseClient.postgrest.rpc(
                function = "reject_group_join_request",
                parameters = buildJsonObject { put("p_request_id", requestId) }
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Direct table fallback
            try {
                supabaseClient.postgrest["group_join_requests"].update(mapOf("status" to "rejected")) {
                    filter { eq("id", requestId) }
                }
            } catch (_: Exception) {}
            true
        }
    }

    private suspend fun fetchGroupById(groupId: String): GroupModel =
        supabaseClient.postgrest["groups"]
            .select(columns = Columns.ALL) { filter { eq("id", groupId) } }
            .decodeSingle<GroupModel>()

    suspend fun leaveGroup(groupId: String) = withContext(Dispatchers.IO) {
        if (!networkConnectivityObserver.isNetworkAvailable()) {
            throw IllegalStateException("Internet connection required to leave club.")
        }
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Please sign in to leave a club.")

        try {
            supabaseClient.postgrest.rpc<JsonElement>(
                function = "leave_group",
                parameters = buildJsonObject { put("p_group_id", groupId) }
            )
        } catch (_: Exception) {
            try {
                supabaseClient.postgrest["group_members"].delete {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", currentUserId)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw IllegalStateException("Failed to leave club on server: ${e.message ?: "Unknown error"}")
            }
        }

        groupDao.updateMembership(groupId, false)
        groupMemberDao.removeMember(groupId, currentUserId)
    }

    suspend fun dissolveGroup(groupId: String) = withContext(Dispatchers.IO) {
        if (!networkConnectivityObserver.isNetworkAvailable()) {
            throw IllegalStateException("Internet connection required to dissolve club.")
        }
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Please sign in to dissolve a club.")

        var remoteDeleted = false
        var lastError: Exception? = null

        // 1. Try dissolve_group RPC
        try {
            supabaseClient.postgrest.rpc<JsonElement>(
                function = "dissolve_group",
                parameters = buildJsonObject { put("p_group_id", groupId) }
            )
            remoteDeleted = true
        } catch (e: Exception) {
            lastError = e
        }

        // 2. Direct delete with RLS groups_delete_policy
        if (!remoteDeleted) {
            try {
                supabaseClient.postgrest["groups"].delete {
                    filter { eq("id", groupId) }
                }
                remoteDeleted = true
            } catch (e: Exception) {
                lastError = e
            }
        }

        // 3. Fallback to archive_group RPC
        if (!remoteDeleted) {
            try {
                supabaseClient.postgrest.rpc<JsonElement>(
                    function = "archive_group",
                    parameters = buildJsonObject { put("p_group_id", groupId) }
                )
                remoteDeleted = true
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (!remoteDeleted) {
            throw IllegalStateException("Failed to dissolve club on server: ${lastError?.message ?: "Check permissions or network"}")
        }

        // Clean up local cache once confirmed deleted remotely
        groupDao.deleteGroup(groupId)
        groupPaperDao.deleteGroupPapers(groupId)
        groupPresentationDao.deleteGroupPresentations(groupId)
        groupReviewDao.deleteGroupReviews(groupId)
        groupMemberDao.deleteGroupMembers(groupId)
    }

    suspend fun archiveGroup(groupId: String) = dissolveGroup(groupId)

    suspend fun updateGroup(
        groupId: String,
        name: String,
        description: String?,
        focusArea: String?,
        schedule: String? = null,
        location: String? = null
    ) = withContext(Dispatchers.IO) {
        if (!networkConnectivityObserver.isNetworkAvailable()) {
            throw IllegalStateException("Internet connection required to update club settings.")
        }
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Please sign in to update club settings.")

        val payload = UpdateGroupPayload(
            name = name.trim(),
            description = description?.trim()?.ifBlank { null },
            focusArea = focusArea?.trim()?.ifBlank { null },
            meetingSchedule = schedule?.trim()?.ifBlank { null },
            meetingLocation = location?.trim()?.ifBlank { null }
        )

        try {
            supabaseClient.postgrest["groups"].update(payload) {
                filter { eq("id", groupId) }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw IllegalStateException("Failed to update club on server: ${e.message ?: "Unknown error"}")
        }

        groupDao.updateGroupDetails(groupId, name.trim(), description?.trim(), focusArea?.trim(), schedule?.trim(), location?.trim())
    }

    suspend fun addPaperToGroup(
        groupId: String,
        bibcode: String,
        title: String? = null,
        authors: String? = null,
        year: String? = null
    ) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: "local_user"
        val localId = UUID.randomUUID().toString()
        val groupPaper = GroupPaperModel(
            id = localId,
            groupId = groupId,
            bibcode = bibcode,
            addedBy = userId,
            voteCount = 1,
            isVotedByMe = true,
            addedAt = LocalDateTime.now().toString(),
            title = title,
            authors = authors,
            year = year
        )
        
        groupPaperDao.insertGroupPaper(groupPaper.toEntity(isSynced = false))

        try {
            val supabaseGroupPaper = mapOf(
                "group_id" to groupId,
                "bibcode" to bibcode,
                "added_by" to userId
            )
            val inserted = supabaseClient.postgrest["group_papers"]
                .insert(supabaseGroupPaper) { select() }
                .decodeSingleOrNull<GroupPaperModel>()
            if (inserted != null) {
                groupPaperDao.deleteGroupPaperById(localId)
                groupPaperDao.insertGroupPaper(
                    inserted.copy(title = title, authors = authors, year = year, isVotedByMe = true)
                        .toEntity(isSynced = true)
                )
            } else {
                groupPaperDao.updateSyncStatus(localId, true)
            }
        } catch (_: Exception) {}
    }

    suspend fun voteForPaper(groupPaperId: String) = withContext(Dispatchers.IO) {
        groupPaperDao.votePaper(groupPaperId)
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        try {
            val vote = mapOf("group_paper_id" to groupPaperId, "user_id" to userId)
            supabaseClient.postgrest["group_paper_votes"].insert(vote)
        } catch (_: Exception) {}
    }

    suspend fun unvoteForPaper(groupPaperId: String) = withContext(Dispatchers.IO) {
        groupPaperDao.unvotePaper(groupPaperId)
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        try {
            supabaseClient.postgrest["group_paper_votes"].delete {
                filter {
                    eq("group_paper_id", groupPaperId)
                    eq("user_id", userId)
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun schedulePresentation(
        groupId: String,
        bibcode: String,
        paperTitle: String?,
        dateTime: LocalDateTime,
        location: String = "Virtual Seminar Room"
    ) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: "local_user"
        val localId = UUID.randomUUID().toString()
        val presentation = PresentationModel(
            id = localId,
            groupId = groupId,
            bibcode = bibcode,
            paperTitle = paperTitle,
            presenterId = userId,
            presenterName = "You (Presenter)",
            scheduledAt = dateTime.toString(),
            meetingLocation = location,
            attendeeCount = 1,
            isCheckedIn = true,
            createdAt = LocalDateTime.now().toString()
        )
        groupPresentationDao.insertPresentation(presentation.toEntity(isSynced = false))

        try {
            val inserted = supabaseClient.postgrest["group_presentations"].insert(
                mapOf(
                    "group_id" to groupId,
                    "bibcode" to bibcode,
                    "presenter_id" to userId,
                    "scheduled_at" to dateTime.toString(),
                    "time_zone" to java.time.ZoneId.systemDefault().id
                )
            ) { select() }.decodeSingleOrNull<PresentationModel>()
            if (inserted != null) {
                groupPresentationDao.deletePresentation(localId)
                groupPresentationDao.insertPresentation(
                    inserted.copy(
                        paperTitle = paperTitle,
                        presenterName = "You (Presenter)",
                        meetingLocation = location,
                        isCheckedIn = true
                    ).toEntity(isSynced = true)
                )
            } else {
                groupPresentationDao.updateSyncStatus(localId, true)
            }
        } catch (_: Exception) {}
    }

    suspend fun checkInSession(presentationId: String) = withContext(Dispatchers.IO) {
        groupPresentationDao.checkIn(presentationId, true)
        try {
            supabaseClient.postgrest.rpc<JsonElement>(
                function = "check_in_session",
                parameters = buildJsonObject { put("p_presentation_id", presentationId) }
            )
        } catch (_: Exception) {}
    }

    suspend fun addSessionReview(
        groupId: String,
        bibcode: String,
        paperTitle: String?,
        notes: String,
        rating: Int
    ) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: "local_user"
        val localId = UUID.randomUUID().toString()
        val review = SessionReviewModel(
            id = localId,
            groupId = groupId,
            bibcode = bibcode,
            paperTitle = paperTitle,
            reviewerId = userId,
            reviewerName = "You",
            notes = notes,
            rating = rating,
            createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        )
        groupReviewDao.insertReview(review.toEntity(isSynced = false))

        try {
            val inserted = supabaseClient.postgrest["group_session_reviews"].insert(
                mapOf(
                    "group_id" to groupId,
                    "bibcode" to bibcode,
                    "reviewer_id" to userId,
                    "notes" to notes,
                    "rating" to rating
                )
            ) { select() }.decodeSingleOrNull<SessionReviewModel>()
            if (inserted != null) {
                groupReviewDao.deleteReview(localId)
                groupReviewDao.insertReview(
                    inserted.copy(
                        paperTitle = paperTitle,
                        reviewerName = "You"
                    ).toEntity(isSynced = true)
                )
            } else {
                groupReviewDao.updateSyncStatus(localId, true)
            }
        } catch (_: Exception) {}
    }

    suspend fun kickMember(groupId: String, targetUserId: String) = withContext(Dispatchers.IO) {
        groupMemberDao.removeMember(groupId, targetUserId)
        try {
            supabaseClient.postgrest.rpc<JsonElement>(
                function = "remove_group_member",
                parameters = buildJsonObject {
                    put("p_group_id", groupId)
                    put("p_user_id", targetUserId)
                }
            )
        } catch (_: Exception) {}
    }

    suspend fun updateMemberRole(groupId: String, targetUserId: String, newRole: String) = withContext(Dispatchers.IO) {
        groupMemberDao.updateRole(groupId, targetUserId, newRole)
        try {
            supabaseClient.postgrest.rpc<JsonElement>(
                function = "set_group_member_role",
                parameters = buildJsonObject {
                    put("p_group_id", groupId)
                    put("p_user_id", targetUserId)
                    put("p_new_role", newRole)
                }
            )
        } catch (_: Exception) {}
    }

    suspend fun getGroupKpis(groupId: String): GroupKpiModel = withContext(Dispatchers.IO) {
        val paperCount = groupPaperDao.getPaperCount(groupId)
        val totalVotes = groupPaperDao.getTotalVotes(groupId) ?: 0
        val presentations = groupPresentationDao.getPresentationCount(groupId)
        val memberCount = groupMemberDao.getMemberCount(groupId).coerceAtLeast(1)
        val reviewCount = groupReviewDao.getReviewCount(groupId)

        GroupKpiModel(
            totalPapers = paperCount,
            totalVotes = totalVotes,
            totalPresentations = presentations,
            totalMembers = memberCount,
            totalReviews = reviewCount,
            participationScore = (75 + (reviewCount * 5) + (totalVotes * 2)).coerceIn(50, 99),
            meetingStreak = (presentations + reviewCount).coerceAtLeast(1)
        )
    }
}
