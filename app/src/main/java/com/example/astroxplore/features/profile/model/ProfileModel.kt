package com.example.astroxplore.features.profile.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileModel(
    @SerialName("id")
    val id: String,
    @SerialName("email")
    val email: String,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    @SerialName("institution")
    val institution: String? = null,
    @SerialName("affiliation_type")
    val affiliationType: String? = null,
    @SerialName("affiliation_name")
    val affiliationName: String? = null,
    @SerialName("country")
    val country: String? = null,
    @SerialName("education_level")
    val educationLevel: String? = null,
    @SerialName("orcid_id")
    val orcidId: String? = null,
    @SerialName("is_onboarded")
    val isOnboarded: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class UserPreference(
    @SerialName("id")
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("keyword_tag")
    val keywordTag: String,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class KeywordModel(
    @SerialName("id")
    val id: String? = null,
    @SerialName("name")
    val name: String,
    @SerialName("category")
    val category: String? = null
)
