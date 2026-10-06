package com.example.astroxplore.features.profile.data.remote.dto

import com.example.astroxplore.features.profile.model.ProfileModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    @SerialName("id")
    val id: String,
    @SerialName("full_name")
    val fullName: String = "",
    @SerialName("institution")
    val institution: String? = null,
    @SerialName("orcid_id")
    val orcidId: String? = null,
    @SerialName("selected_topics")
    val selectedTopics: List<String> = emptyList()
)

fun UserProfileDto.toProfileModel(email: String = ""): ProfileModel {
    val parts = fullName.trim().split(" ", limit = 2)
    val first = parts.getOrNull(0) ?: ""
    val last = parts.getOrNull(1) ?: ""
    return ProfileModel(
        id = id,
        email = email,
        fullName = fullName,
        firstName = first,
        lastName = last,
        institution = institution,
        orcidId = orcidId
    )
}

fun ProfileModel.toDto(selectedTopics: List<String> = emptyList()): UserProfileDto {
    return UserProfileDto(
        id = id,
        fullName = fullName ?: listOfNotNull(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
        institution = institution ?: affiliationName,
        orcidId = orcidId,
        selectedTopics = selectedTopics
    )
}
