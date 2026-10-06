package com.example.astroxplore.features.groups.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupJoinRequestModel(
    @SerialName("id") val id: String? = null,
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("status") val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
    val userName: String? = "Applicant Scholar"
)
