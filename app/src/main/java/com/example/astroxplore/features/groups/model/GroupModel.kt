package com.example.astroxplore.features.groups.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupModel(
    @SerialName("id") val id: String,
    @SerialName("display_id") val displayId: String, // Short unique ID for searching
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("focus_area") val focusArea: String? = null,
    @SerialName("member_count") val memberCount: Int = 1,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class GroupPaperModel(
    @SerialName("id") val id: String? = null,
    @SerialName("group_id") val groupId: String,
    @SerialName("bibcode") val bibcode: String,
    @SerialName("added_by") val addedBy: String,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("is_voted_by_me") val isVotedByMe: Boolean = false, // Client side field or calculated
    @SerialName("added_at") val addedAt: String? = null
)

@Serializable
data class PresentationModel(
    @SerialName("id") val id: String? = null,
    @SerialName("group_id") val groupId: String,
    @SerialName("bibcode") val bibcode: String,
    @SerialName("presenter_id") val presenterId: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class GroupMemberModel(
    @SerialName("id") val id: String? = null,
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("role") val role: String = "member", // admin, moderator, member
    @SerialName("joined_at") val joinedAt: String? = null
)

@Serializable
data class SessionReviewModel(
    @SerialName("id") val id: String? = null,
    @SerialName("group_id") val groupId: String,
    @SerialName("bibcode") val bibcode: String,
    @SerialName("reviewer_id") val reviewerId: String,
    @SerialName("notes") val notes: String,
    @SerialName("rating") val rating: Int = 5,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SessionAttendanceModel(
    @SerialName("id") val id: String? = null,
    @SerialName("presentation_id") val presentationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("checked_in_at") val checkedInAt: String? = null
)

@Serializable
data class GroupKpiModel(
    val totalPapers: Int = 0,
    val totalVotes: Int = 0,
    val totalPresentations: Int = 0,
    val totalMembers: Int = 1,
    val totalReviews: Int = 0
)
