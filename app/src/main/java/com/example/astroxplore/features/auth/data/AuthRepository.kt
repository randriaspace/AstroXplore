package com.example.astroxplore.features.auth.data

import com.example.astroxplore.features.profile.model.ProfileModel
import com.example.astroxplore.features.profile.model.UserPreference
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    suspend fun login(email: String, password: String) {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun requestPasswordReset(email: String) {
        supabaseClient.auth.resetPasswordForEmail(email)
    }

    /**
     * @return Boolean - true if email confirmation is required
     */
    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        institution: String? = null,
        orcidId: String? = null
    ): Boolean {
        val trimmedFirstName = firstName.trim()
        val trimmedLastName = lastName.trim()
        val fullName = listOf(trimmedFirstName, trimmedLastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val user = supabaseClient.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            this.data = buildJsonObject {
                put("full_name", fullName)
                if (trimmedFirstName.isNotBlank()) put("first_name", trimmedFirstName)
                if (trimmedLastName.isNotBlank()) put("last_name", trimmedLastName)
            }
        } ?: throw Exception("Signup failed: User is null")

        val session = supabaseClient.auth.currentSessionOrNull()

        if (session != null) {
            val initialProfile = buildProfileFromUserData(
                userId = user.id,
                email = email,
                metadata = mapOf(
                    "full_name" to fullName,
                    "first_name" to trimmedFirstName,
                    "last_name" to trimmedLastName
                ),
                institution = institution,
                orcidId = orcidId
            )
            supabaseClient.postgrest["profiles"].upsert(initialProfile)
            return false
        }

        return true
    }

    suspend fun reauthenticateCurrentUser(password: String): Boolean {
        val user = currentUser ?: return false
        val email = user.email ?: return false
        return try {
            supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun buildProfileFromUserData(
            userId: String,
            email: String,
            firstName: String? = null,
            lastName: String? = null,
            metadata: Map<String, Any?>? = null,
            institution: String? = null,
            orcidId: String? = null,
            isOnboarded: Boolean = false
        ): ProfileModel {
            val metadataMap = metadata ?: emptyMap()
            val firstFromMeta = (metadataMap["first_name"] as? String)
                ?: firstName?.trim()
            val lastFromMeta = (metadataMap["last_name"] as? String)
                ?: lastName?.trim()
            val fullFromMeta = (metadataMap["full_name"] as? String)
                ?.trim()
                ?: listOf(firstFromMeta, lastFromMeta)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" ")

            return ProfileModel(
                id = userId,
                email = email,
                firstName = firstFromMeta?.ifBlank { null },
                lastName = lastFromMeta?.ifBlank { null },
                fullName = fullFromMeta.ifBlank { null },
                institution = institution,
                orcidId = orcidId,
                isOnboarded = isOnboarded
            )
        }
    }

    suspend fun logout() {
        supabaseClient.auth.signOut()
    }

    val sessionStatus: Flow<SessionStatus> = supabaseClient.auth.sessionStatus

    val currentUser get() = supabaseClient.auth.currentUserOrNull()
    
    fun isLoggedIn(): Boolean = supabaseClient.auth.currentSessionOrNull() != null
}
