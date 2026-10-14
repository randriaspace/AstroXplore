package com.example.astroxplore.features.sync

import com.example.astroxplore.features.groups.model.GroupRole
import com.example.astroxplore.features.groups.model.GroupRolePermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRolePermissionsTest {

    @Test
    fun `fromString maps role strings correctly`() {
        assertEquals(GroupRole.OWNER, GroupRole.fromString("member", isOwner = true))
        assertEquals(GroupRole.OWNER, GroupRole.fromString("owner"))
        assertEquals(GroupRole.ADMIN, GroupRole.fromString("admin"))
        assertEquals(GroupRole.MODERATOR, GroupRole.fromString("moderator"))
        assertEquals(GroupRole.MEMBER, GroupRole.fromString("member"))
        assertEquals(GroupRole.APPLICANT, GroupRole.fromString("pending"))
        assertEquals(GroupRole.APPLICANT, GroupRole.fromString("applicant"))
        assertEquals(GroupRole.NONE, GroupRole.fromString(null))
        assertEquals(GroupRole.NONE, GroupRole.fromString("unknown_role"))
    }

    @Test
    fun `Owner has full administrative and archival rights`() {
        val role = GroupRole.OWNER
        assertTrue(GroupRolePermissions.canBrowsePreprints(role))
        assertTrue(GroupRolePermissions.canCastVotes(role))
        assertTrue(GroupRolePermissions.canNominatePapers(role))
        assertTrue(GroupRolePermissions.canSchedulePresentations(role))
        assertTrue(GroupRolePermissions.canSubmitReviews(role))
        assertTrue(GroupRolePermissions.canEditClubMetadata(role))
        assertTrue(GroupRolePermissions.canManageJoinRequests(role))
        assertTrue(GroupRolePermissions.canChangeMemberRoles(role))
        assertTrue(GroupRolePermissions.canKickMembers(role))
        assertTrue(GroupRolePermissions.canArchiveOrDissolveClub(role))
    }

    @Test
    fun `Admin has management rights but cannot archive club`() {
        val role = GroupRole.ADMIN
        assertTrue(GroupRolePermissions.canBrowsePreprints(role))
        assertTrue(GroupRolePermissions.canCastVotes(role))
        assertTrue(GroupRolePermissions.canNominatePapers(role))
        assertTrue(GroupRolePermissions.canSchedulePresentations(role))
        assertTrue(GroupRolePermissions.canSubmitReviews(role))
        assertTrue(GroupRolePermissions.canEditClubMetadata(role))
        assertTrue(GroupRolePermissions.canManageJoinRequests(role))
        assertTrue(GroupRolePermissions.canChangeMemberRoles(role))
        assertTrue(GroupRolePermissions.canKickMembers(role))
        assertFalse(GroupRolePermissions.canArchiveOrDissolveClub(role))
    }

    @Test
    fun `Moderator and Member cannot perform admin actions`() {
        val moderator = GroupRole.MODERATOR
        assertTrue(GroupRolePermissions.canCastVotes(moderator))
        assertTrue(GroupRolePermissions.canNominatePapers(moderator))
        assertTrue(GroupRolePermissions.canSchedulePresentations(moderator))
        assertTrue(GroupRolePermissions.canSubmitReviews(moderator))
        assertFalse(GroupRolePermissions.canEditClubMetadata(moderator))
        assertFalse(GroupRolePermissions.canManageJoinRequests(moderator))
        assertFalse(GroupRolePermissions.canChangeMemberRoles(moderator))
        assertFalse(GroupRolePermissions.canKickMembers(moderator))
        assertFalse(GroupRolePermissions.canArchiveOrDissolveClub(moderator))

        val member = GroupRole.MEMBER
        assertTrue(GroupRolePermissions.canCastVotes(member))
        assertTrue(GroupRolePermissions.canNominatePapers(member))
        assertTrue(GroupRolePermissions.canSchedulePresentations(member))
        assertTrue(GroupRolePermissions.canSubmitReviews(member))
        assertFalse(GroupRolePermissions.canEditClubMetadata(member))
        assertFalse(GroupRolePermissions.canManageJoinRequests(member))
    }

    @Test
    fun `Applicant can only browse and request to join`() {
        val applicant = GroupRole.APPLICANT
        assertTrue(GroupRolePermissions.canBrowsePreprints(applicant))
        assertTrue(GroupRolePermissions.canRequestToJoin(applicant))
        assertFalse(GroupRolePermissions.canCastVotes(applicant))
        assertFalse(GroupRolePermissions.canNominatePapers(applicant))
        assertFalse(GroupRolePermissions.canSchedulePresentations(applicant))
        assertFalse(GroupRolePermissions.canSubmitReviews(applicant))
        assertFalse(GroupRolePermissions.canEditClubMetadata(applicant))
    }

    @Test
    fun `assertCanPerform throws SecurityException when unauthorized`() {
        // Member trying to edit club
        assertThrows(SecurityException::class.java) {
            GroupRolePermissions.assertCanPerform(GroupRole.MEMBER, "edit_club")
        }

        // Admin trying to archive club
        assertThrows(SecurityException::class.java) {
            GroupRolePermissions.assertCanPerform(GroupRole.ADMIN, "archive_club")
        }

        // Applicant trying to vote
        assertThrows(SecurityException::class.java) {
            GroupRolePermissions.assertCanPerform(GroupRole.APPLICANT, "cast_vote")
        }

        // Owner can perform all
        GroupRolePermissions.assertCanPerform(GroupRole.OWNER, "archive_club")
        GroupRolePermissions.assertCanPerform(GroupRole.OWNER, "edit_club")
        GroupRolePermissions.assertCanPerform(GroupRole.OWNER, "cast_vote")
    }
}
