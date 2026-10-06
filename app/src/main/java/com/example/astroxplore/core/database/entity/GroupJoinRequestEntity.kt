package com.example.astroxplore.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.astroxplore.features.groups.model.GroupJoinRequestModel

@Entity(tableName = "group_join_requests")
data class GroupJoinRequestEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val userId: String,
    val userName: String = "Applicant Scholar",
    val status: String = "pending",
    val createdAt: String? = null,
    val isSynced: Boolean = true
) {
    fun toDomainModel() = GroupJoinRequestModel(
        id = id,
        groupId = groupId,
        userId = userId,
        userName = userName,
        status = status,
        createdAt = createdAt
    )
}

fun GroupJoinRequestModel.toEntity(isSynced: Boolean = true) = GroupJoinRequestEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    userId = userId,
    userName = userName ?: "Applicant Scholar",
    status = status,
    createdAt = createdAt,
    isSynced = isSynced
)
