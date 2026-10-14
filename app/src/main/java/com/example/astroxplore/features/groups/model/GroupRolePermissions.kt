package com.example.astroxplore.features.groups.model

enum class GroupRole {
    OWNER,
    ADMIN,
    MODERATOR,
    MEMBER,
    APPLICANT,
    NONE;

    companion object {
        fun fromString(role: String?, isOwner: Boolean = false): GroupRole {
            if (isOwner) return OWNER
            return when (role?.lowercase()?.trim()) {
                "owner" -> OWNER
                "admin" -> ADMIN
                "moderator" -> MODERATOR
                "member" -> MEMBER
                "applicant", "pending" -> APPLICANT
                else -> NONE
            }
        }
    }
}

object GroupRolePermissions {
    fun canBrowsePreprints(role: GroupRole): Boolean = true

    fun canRequestToJoin(role: GroupRole): Boolean = 
        role == GroupRole.APPLICANT || role == GroupRole.NONE

    fun canCastVotes(role: GroupRole): Boolean = 
        role in setOf(GroupRole.OWNER, GroupRole.ADMIN, GroupRole.MODERATOR, GroupRole.MEMBER)

    fun canNominatePapers(role: GroupRole): Boolean = 
        role in setOf(GroupRole.OWNER, GroupRole.ADMIN, GroupRole.MODERATOR, GroupRole.MEMBER)

    fun canSchedulePresentations(role: GroupRole): Boolean = 
        role in setOf(GroupRole.OWNER, GroupRole.ADMIN, GroupRole.MODERATOR, GroupRole.MEMBER)

    fun canSubmitReviews(role: GroupRole): Boolean = 
        role in setOf(GroupRole.OWNER, GroupRole.ADMIN, GroupRole.MODERATOR, GroupRole.MEMBER)

    fun canEditClubMetadata(role: GroupRole): Boolean = 
        role in setOf(GroupRole.OWNER, GroupRole.ADMIN)

    fun canManageJoinRequests(role: GroupRole): Boolean = 
        role in setOf(GroupRole.OWNER, GroupRole.ADMIN)

    fun canChangeMemberRoles(role: GroupRole): Boolean = 
        role in setOf(GroupRole.OWNER, GroupRole.ADMIN)

    fun canKickMembers(role: GroupRole): Boolean = 
        role in setOf(GroupRole.OWNER, GroupRole.ADMIN)

    fun canArchiveOrDissolveClub(role: GroupRole): Boolean = 
        role == GroupRole.OWNER

    fun assertCanPerform(role: GroupRole, action: String) {
        val allowed = when (action) {
            "cast_vote" -> canCastVotes(role)
            "nominate_paper" -> canNominatePapers(role)
            "schedule_presentation" -> canSchedulePresentations(role)
            "submit_review" -> canSubmitReviews(role)
            "edit_club" -> canEditClubMetadata(role)
            "manage_requests" -> canManageJoinRequests(role)
            "change_roles" -> canChangeMemberRoles(role)
            "kick_member" -> canKickMembers(role)
            "archive_club" -> canArchiveOrDissolveClub(role)
            else -> false
        }
        if (!allowed) {
            throw SecurityException("Role $role is unauthorized to perform action: $action")
        }
    }
}
