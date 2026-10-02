package com.example.astroxplore.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.astroxplore.features.groups.model.GroupMemberModel

@Entity(tableName = "group_members")
data class GroupMemberEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val userId: String,
    val userName: String? = "Fellow Researcher",
    val role: String = "member",
    val joinedAt: String? = null,
    val isSynced: Boolean = true
) {
    fun toDomainModel() = GroupMemberModel(
        id = id,
        groupId = groupId,
        userId = userId,
        userName = userName ?: "Fellow Researcher",
        role = role,
        joinedAt = joinedAt
    )
}

fun GroupMemberModel.toEntity(isSynced: Boolean = true) = GroupMemberEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    userId = userId,
    userName = userName,
    role = role,
    joinedAt = joinedAt,
    isSynced = isSynced
)
