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
    private val groupJoinRequestDao: GroupJoinRequestDao
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
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        try {
            val memberships = supabaseClient.postgrest["group_members"]
                .select(columns = Columns.ALL) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<GroupMemberModel>()

            val memberGroupIds = memberships.map { it.groupId }.toSet()

            val remoteGroups = supabaseClient.postgrest["groups"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("status", "active")
                    }
                }
                .decodeList<GroupModel>()

            groupDao.deleteAllSyncedGroups()
            if (remoteGroups.isNotEmpty()) {
                val entities = remoteGroups.map { group ->
                    val isMember = memberGroupIds.contains(group.id) || group.ownerId == userId
                    group.toEntity(isSynced = true).copy(isMember = isMember)
                }
                groupDao.insertGroups(entities)

                remoteGroups.filter { memberGroupIds.contains(it.id) || it.ownerId == userId }.forEach { group ->
                    syncGroupPapers(group.id)
                    syncGroupMembers(group.id)
                    syncGroupPresentations(group.id)
                    syncGroupReviews(group.id)
                }
            }
        } catch (_: Exception) {
            // Offline fallback - local room database holds active state
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
        
        // 1. Sync pending local groups
        try {
            val unsyncedGroups = groupDao.getUnsyncedGroups()
            for (localGroup in unsyncedGroups) {
                try {
                    val rows = supabaseClient.postgrest.rpc(
                        function = "create_group",
                        parameters = buildJsonObject {
                            put("p_name", localGroup.name.trim())
                            put("p_description", localGroup.description?.let(::JsonPrimitive) ?: JsonNull)
                            put("p_focus_area", localGroup.focusArea?.let(::JsonPrimitive) ?: JsonNull)
                            put("p_visibility", "public")
                        }
                    ).decodeList<GroupModel>()
                    val remote = rows.singleOrNull()
                    if (remote != null) {
                        groupDao.deleteGroup(localGroup.id)
                        groupDao.insertGroup(
                            remote.toEntity(isSynced = true).copy(
                                isMember = true,
                                meetingSchedule = localGroup.meetingSchedule,
                                meetingLocation = localGroup.meetingLocation
                            )
                        )
                    }
                } catch (_: Exception) {}
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
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Sign in to create a journal club.")
        val localId = UUID.randomUUID().toString()
        val displayId = "ASTRO" + UUID.randomUUID().toString().filter { it.isLetterOrDigit() }.take(5).uppercase()
        val createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

        val newGroup = GroupModel(
            id = localId,
            displayId = displayId,
            name = name.trim(),
            description = description?.trim(),
            ownerId = currentUserId,
            focusArea = focusArea?.trim(),
            memberCount = 1,
            createdAt = createdAt,
            meetingSchedule = schedule,
            meetingLocation = location,
            isMember = true
        )

        // 1. Immediately store in Room database
        groupDao.insertGroup(newGroup.toEntity(isSynced = false))
        
        // Add creator as Admin member
        val ownerMember = GroupMemberModel(
            id = UUID.randomUUID().toString(),
            groupId = localId,
            userId = currentUserId,
            userName = "You (Founder)",
            role = "admin",
            joinedAt = createdAt
        )
        groupMemberDao.insertMember(ownerMember.toEntity(isSynced = false))

        // 2. Try remote Supabase in background
        try {
            val rows = supabaseClient.postgrest.rpc(
                function = "create_group",
                parameters = buildJsonObject {
                    put("p_name", name.trim())
                    put("p_description", description?.let(::JsonPrimitive) ?: JsonNull)
                    put("p_focus_area", focusArea?.let(::JsonPrimitive) ?: JsonNull)
                    put("p_visibility", "public")
                }
            ).decodeList<GroupModel>()
            val remote = rows.singleOrNull()
            if (remote != null) {
                groupDao.deleteGroup(localId)
                groupDao.insertGroup(remote.toEntity(isSynced = true).copy(isMember = true, meetingSchedule = schedule, meetingLocation = location))
                return@withContext remote.copy(meetingSchedule = schedule, meetingLocation = location, isMember = true)
            }
        } catch (_: Exception) {
            // Successfully created locally
        }

        newGroup
    }

    suspend fun joinGroup(inviteOrDisplayId: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = normalizeJoinInput(inviteOrDisplayId)

        // 1. Check local clubs first (matches displayId e.g. "JWST01", "EXOATM02", or ID)
        val allLocal = groupDao.getAllGroups().firstOrNull() ?: emptyList()
        val localMatch = allLocal.find { 
            normalizeJoinInput(it.displayId) == normalized || 
            normalizeJoinInput(it.id) == normalized ||
            it.name.contains(inviteOrDisplayId.trim(), ignoreCase = true)
        }

        if (localMatch != null) {
                // Check if club is public - auto join, otherwise submit request
                if (localMatch.visibility == "public") {
                    return@withContext joinPublicClub(localMatch.id)
                } else {
                    return@withContext submitJoinRequest(localMatch.id)
                }
            }

            // 2. Lookup remote group by display_id
            try {
                val found = supabaseClient.postgrest["groups"]
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("display_id", normalized)
                            eq("status", "active")
                        }
                    }
                    .decodeSingle<GroupModel>()

                // Check visibility and act accordingly
                if (found.visibility == "public") {
                    return@withContext joinPublicClub(found.id)
                } else {
                    return@withContext submitJoinRequest(found.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
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
            true
        }
    }

    private suspend fun fetchGroupById(groupId: String): GroupModel =
        supabaseClient.postgrest["groups"]
            .select(columns = Columns.ALL) { filter { eq("id", groupId) } }
            .decodeSingle()

    suspend fun leaveGroup(groupId: String) = withContext(Dispatchers.IO) {
        groupDao.updateMembership(groupId, false)
        try {
            supabaseClient.postgrest.rpc<JsonElement>(
                function = "leave_group",
                parameters = buildJsonObject { put("p_group_id", groupId) }
            )
        } catch (_: Exception) {}
    }

    suspend fun archiveGroup(groupId: String) = withContext(Dispatchers.IO) {
        groupDao.deleteGroup(groupId)
        groupPaperDao.deleteGroupPapers(groupId)
        groupPresentationDao.deleteGroupPresentations(groupId)
        groupReviewDao.deleteGroupReviews(groupId)
        groupMemberDao.deleteGroupMembers(groupId)

        try {
            supabaseClient.postgrest.rpc<JsonElement>(
                function = "archive_group",
                parameters = buildJsonObject { put("p_group_id", groupId) }
            )
        } catch (_: Exception) {}
    }

    suspend fun updateGroup(
        groupId: String,
        name: String,
        description: String?,
        focusArea: String?,
        schedule: String? = null,
        location: String? = null
    ) = withContext(Dispatchers.IO) {
        groupDao.updateGroupDetails(groupId, name, description, focusArea, schedule, location)
        try {
            val update = mapOf(
                "name" to name,
                "description" to description,
                "focus_area" to focusArea
            )
            supabaseClient.postgrest["groups"].update(update) {
                filter { eq("id", groupId) }
            }
        } catch (_: Exception) {}
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
