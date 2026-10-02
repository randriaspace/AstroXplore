package com.example.astroxplore.features.auth.data

import com.example.astroxplore.features.groups.data.GroupRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthRepositoryProfileDataTest {
    @Test
    fun `buildProfileFromUserData keeps full name and split names from metadata`() {
        val profile = AuthRepository.buildProfileFromUserData(
            userId = "user-123",
            email = "ada@example.com",
            firstName = "Ada",
            lastName = "Lovelace",
            metadata = mapOf(
                "full_name" to "Ada Lovelace",
                "first_name" to "Ada",
                "last_name" to "Lovelace"
            )
        )

        assertEquals("user-123", profile.id)
        assertEquals("ada@example.com", profile.email)
        assertEquals("Ada Lovelace", profile.fullName)
        assertEquals("Ada", profile.firstName)
        assertEquals("Lovelace", profile.lastName)
        assertFalse(profile.isOnboarded)
    }

    @Test
    fun `normalizeJoinInput strips whitespace and separators from QR-scanned club codes`() {
        val normalized = GroupRepository.normalizeJoinInput(" ab-12 34\n")
        assertEquals("AB1234", normalized)
    }
}
