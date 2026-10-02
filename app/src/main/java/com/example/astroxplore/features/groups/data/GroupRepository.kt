package com.example.astroxplore.features.groups.data

import com.example.astroxplore.core.database.dao.*
import com.example.astroxplore.core.database.entity.*
import com.example.astroxplore.features.groups.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
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
    private val groupMemberDao: GroupMemberDao
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

    suspend fun initSeedDataIfEmpty() = withContext(Dispatchers.IO) {
        val count = groupDao.getGroupCount()
        if (count == 0) {
            seedDefaultClubs()
        }
    }

    /**
     * Reactively observe joined clubs from the local Room database (Offline-First)
     */
    fun getMyGroups(): Flow<List<GroupModel>> = groupDao.getMyGroups().map { entities ->
        entities.map { it.toDomainModel() }
    }

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
        initSeedDataIfEmpty()
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        try {
            val memberships = supabaseClient.postgrest["group_members"]
                .select(columns = Columns.ALL) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<GroupMemberModel>()
            
            val groupIds = memberships.map { it.groupId }
            if (groupIds.isNotEmpty()) {
                val remoteGroups = supabaseClient.postgrest["groups"]
                    .select(columns = Columns.ALL) {
                        filter {
                            isIn("id", groupIds)
                            eq("status", "active")
                        }
                    }
                    .decodeList<GroupModel>()
                
                groupDao.insertGroups(remoteGroups.map { it.toEntity(isSynced = true).copy(isMember = true) })

                remoteGroups.forEach { group ->
                    syncGroupPapers(group.id)
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

    suspend fun createGroup(
        name: String,
        description: String?,
        focusArea: String?,
        schedule: String = "Weekly on Thursdays",
        location: String = "Google Meet / Seminar Room"
    ): GroupModel = withContext(Dispatchers.IO) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: "local_user_${UUID.randomUUID().toString().take(6)}"
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
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: "local_user"
        val normalized = normalizeJoinInput(inviteOrDisplayId)

        // 1. Check local clubs first (matches displayId e.g. "JWST01", "EXOATM02", or ID)
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
                joinedAt = LocalDateTime.now().toString()
            )
            groupMemberDao.insertMember(member.toEntity(isSynced = true))
            return@withContext true
        }

        // 2. Attempt remote join via Supabase
        try {
            val group = if (normalized.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                val groupId = supabaseClient.postgrest.rpc(
                    function = "accept_group_invite",
                    parameters = buildJsonObject { put("p_token", normalized) }
                ).decodeSingle<String>()
                fetchGroupById(groupId)
            } else {
                val found = supabaseClient.postgrest["groups"]
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("display_id", normalized)
                            eq("status", "active")
                        }
                    }
                    .decodeSingle<GroupModel>()
                supabaseClient.postgrest["group_members"].insert(
                    mapOf("group_id" to found.id, "user_id" to currentUserId, "role" to "member")
                )
                found
            }
            groupDao.insertGroup(group.toEntity(isSynced = true).copy(isMember = true))
            true
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
            supabaseClient.postgrest["group_papers"]
                .insert(supabaseGroupPaper) { select() }
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
        val presentation = PresentationModel(
            id = UUID.randomUUID().toString(),
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
            supabaseClient.postgrest["group_presentations"].insert(
                mapOf(
                    "group_id" to groupId,
                    "bibcode" to bibcode,
                    "presenter_id" to userId,
                    "scheduled_at" to dateTime.toString(),
                    "time_zone" to java.time.ZoneId.systemDefault().id
                )
            )
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
        val review = SessionReviewModel(
            id = UUID.randomUUID().toString(),
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
            supabaseClient.postgrest["group_session_reviews"].insert(
                mapOf(
                    "group_id" to groupId,
                    "bibcode" to bibcode,
                    "reviewer_id" to userId,
                    "notes" to notes,
                    "rating" to rating
                )
            )
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

    private suspend fun seedDefaultClubs() {
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
        val nextWeek = LocalDateTime.now().plusDays(4).withHour(17).withMinute(0).withSecond(0).toString()
        val nextTuesday = LocalDateTime.now().plusDays(2).withHour(15).withMinute(30).withSecond(0).toString()

        // 1. JWST Early Universe Club (Joined)
        val jwstGroup = GroupEntity(
            id = "jwst-early-univ",
            displayId = "JWST01",
            name = "JWST Early Universe & High-z Galaxies",
            description = "Analyzing the newest spectroscopic confirmations of z > 10 galaxies, Lyman-break candidates, and early supermassive black hole seeds from NIRCam and NIRSpec.",
            ownerId = "dr_rostova",
            focusArea = "High-z Galaxies & First Stars",
            memberCount = 18,
            createdAt = now,
            meetingSchedule = "Thursdays at 17:00 UTC",
            meetingLocation = "Google Meet: meet.google.com/ast-jwst-01",
            isMember = true,
            isSynced = true
        )

        // 2. Exoplanet Atmospheres Club (Joined)
        val exoGroup = GroupEntity(
            id = "exoplanet-atm",
            displayId = "EXOATM02",
            name = "Exoplanetary Atmospheres & Habitability",
            description = "Transmission spectroscopy, secondary eclipses, and photochemical modeling of temperate Earth-sized exoplanets orbiting nearby M-dwarf stars.",
            ownerId = "dr_tanaka",
            focusArea = "Transmission Spectroscopy & Biosignatures",
            memberCount = 24,
            createdAt = now,
            meetingSchedule = "Tuesdays at 15:30 UTC",
            meetingLocation = "Astrophysics Seminar Room 402 & Zoom",
            isMember = true,
            isSynced = true
        )

        // 3. Multi-Messenger Gravitational Astronomy (Explore)
        val gwGroup = GroupEntity(
            id = "multi-messenger-gw",
            displayId = "GWASTRO03",
            name = "Multi-Messenger Gravitational Astrophysics",
            description = "Investigating compact binary coalescences, prompt electromagnetic counterparts, r-process nucleosynthesis, and neutron star equation of state.",
            ownerId = "prof_thorne",
            focusArea = "Gravitational Waves & Kilonovae",
            memberCount = 31,
            createdAt = now,
            meetingSchedule = "Fridays at 14:00 UTC",
            meetingLocation = "Physics Hall Auditorium B",
            isMember = false,
            isSynced = true
        )

        // 4. Fast Radio Bursts & Magnetars (Explore)
        val frbGroup = GroupEntity(
            id = "frb-magnetars",
            displayId = "FRBMAG04",
            name = "Fast Radio Bursts & Magnetars",
            description = "Exploring periodic repeaters, galactic magnetar bursts, and extragalactic dispersion measure as cosmological probes.",
            ownerId = "dr_patel",
            focusArea = "Transient High-Energy Radio",
            memberCount = 14,
            createdAt = now,
            meetingSchedule = "Mondays at 16:00 UTC",
            meetingLocation = "Virtual: Zoom ID 849-204-118",
            isMember = false,
            isSynced = true
        )

        groupDao.insertGroups(listOf(jwstGroup, exoGroup, gwGroup, frbGroup))

        // Pre-seed papers for JWST
        val jwstPapers = listOf(
            GroupPaperEntity(
                id = "p-jwst-1",
                groupId = "jwst-early-univ",
                bibcode = "2023Natur.616...45C",
                addedBy = "dr_rostova",
                voteCount = 9,
                isVotedByMe = true,
                addedAt = now,
                title = "A population of pristine candidate galaxies at z ~ 11–13 discovered by JWST",
                authors = "Curtis-Lake, E., Robertson, B. et al.",
                year = "2023"
            ),
            GroupPaperEntity(
                id = "p-jwst-2",
                groupId = "jwst-early-univ",
                bibcode = "2024ApJ...960...12W",
                addedBy = "dr_patel",
                voteCount = 6,
                isVotedByMe = false,
                addedAt = now,
                title = "Spectroscopic verification of extreme emission line galaxies at cosmic dawn",
                authors = "Williams, H., Oesch, P. et al.",
                year = "2024"
            ),
            GroupPaperEntity(
                id = "p-jwst-3",
                groupId = "jwst-early-univ",
                bibcode = "2024MNRAS.527.1234F",
                addedBy = "prof_thorne",
                voteCount = 4,
                isVotedByMe = false,
                addedAt = now,
                title = "Supermassive black hole seeds forming in overdense halos at z > 15",
                authors = "Furtak, L., Zitrin, A. et al.",
                year = "2024"
            )
        )
        groupPaperDao.insertGroupPapers(jwstPapers)

        // Pre-seed presentations for JWST
        val jwstPres = GroupPresentationEntity(
            id = "pres-jwst-1",
            groupId = "jwst-early-univ",
            bibcode = "2023Natur.616...45C",
            paperTitle = "A population of pristine candidate galaxies at z ~ 11–13 discovered by JWST",
            presenterId = "dr_rostova",
            presenterName = "Dr. Elena Rostova",
            scheduledAt = nextWeek,
            meetingLocation = "Google Meet: meet.google.com/ast-jwst-01",
            attendeeCount = 7,
            isCheckedIn = false,
            createdAt = now
        )
        groupPresentationDao.insertPresentation(jwstPres)

        // Pre-seed reviews for JWST
        val jwstReview = GroupReviewEntity(
            id = "rev-jwst-1",
            groupId = "jwst-early-univ",
            bibcode = "2023Natur.616...45C",
            paperTitle = "A population of pristine candidate galaxies at z ~ 11–13 discovered by JWST",
            reviewerId = "dr_rostova",
            reviewerName = "Dr. Elena Rostova",
            notes = "Key discussion outcome: The observed ultraviolet luminosity density at z > 10 requires either an unexpectedly high star formation efficiency or a top-heavy stellar Initial Mass Function (IMF). Next step is cross-checking with ALMA [O III] 88μm line upper limits.",
            rating = 5,
            createdAt = "Yesterday"
        )
        groupReviewDao.insertReview(jwstReview)

        // Pre-seed members for JWST
        val jwstMembers = listOf(
            GroupMemberEntity(id = "m-1", groupId = "jwst-early-univ", userId = "dr_rostova", userName = "Dr. Elena Rostova (Host)", role = "admin", joinedAt = "2 months ago"),
            GroupMemberEntity(id = "m-2", groupId = "jwst-early-univ", userId = "prof_thorne", userName = "Prof. Marcus Thorne", role = "moderator", joinedAt = "1 month ago"),
            GroupMemberEntity(id = "m-3", groupId = "jwst-early-univ", userId = "dr_patel", userName = "Dr. Aisha Patel", role = "member", joinedAt = "3 weeks ago"),
            GroupMemberEntity(id = "m-4", groupId = "jwst-early-univ", userId = "local_user", userName = "You (Fellow)", role = "member", joinedAt = "Today")
        )
        groupMemberDao.insertMembers(jwstMembers)

        // Pre-seed papers for Exoplanet Atmospheres
        val exoPapers = listOf(
            GroupPaperEntity(
                id = "p-exo-1",
                groupId = "exoplanet-atm",
                bibcode = "2023Natur.622...48M",
                addedBy = "dr_tanaka",
                voteCount = 14,
                isVotedByMe = true,
                addedAt = now,
                title = "Carbon-bearing molecules in a possible ocean world exoplanet K2-18b",
                authors = "Madhusudhan, N., Sarkar, S. et al.",
                year = "2023"
            ),
            GroupPaperEntity(
                id = "p-exo-2",
                groupId = "exoplanet-atm",
                bibcode = "2024ApJ...965...89B",
                addedBy = "dr_tanaka",
                voteCount = 8,
                isVotedByMe = false,
                addedAt = now,
                title = "JWST thermal emission constraints on TRAPPIST-1b and TRAPPIST-1c",
                authors = "Benneke, B., Greene, T. et al.",
                year = "2024"
            )
        )
        groupPaperDao.insertGroupPapers(exoPapers)

        val exoPres = GroupPresentationEntity(
            id = "pres-exo-1",
            groupId = "exoplanet-atm",
            bibcode = "2023Natur.622...48M",
            paperTitle = "Carbon-bearing molecules in a possible ocean world exoplanet K2-18b",
            presenterId = "dr_tanaka",
            presenterName = "Dr. Kenji Tanaka",
            scheduledAt = nextTuesday,
            meetingLocation = "Astrophysics Seminar Room 402 & Zoom",
            attendeeCount = 12,
            isCheckedIn = false,
            createdAt = now
        )
        groupPresentationDao.insertPresentation(exoPres)

        val exoMembers = listOf(
            GroupMemberEntity(id = "m-exo-1", groupId = "exoplanet-atm", userId = "dr_tanaka", userName = "Dr. Kenji Tanaka", role = "admin", joinedAt = "3 months ago"),
            GroupMemberEntity(id = "m-exo-2", groupId = "exoplanet-atm", userId = "local_user", userName = "You", role = "member", joinedAt = "Today")
        )
        groupMemberDao.insertMembers(exoMembers)
    }
}
