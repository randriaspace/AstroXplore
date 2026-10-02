package com.example.astroxplore.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.astroxplore.features.groups.model.GroupModel

@Entity(tableName = "journal_clubs")
data class GroupEntity(
    @PrimaryKey
    val id: String,
    val displayId: String,
    val name: String,
    val description: String?,
    val ownerId: String,
    val focusArea: String?,
    val memberCount: Int = 1,
    val createdAt: String?,
    val meetingSchedule: String? = "Weekly on Thursdays",
    val meetingLocation: String? = "Google Meet / Seminar Room",
    val isMember: Boolean = true,
    val isSynced: Boolean = false
) {
    fun toDomainModel() = GroupModel(
        id = id,
        displayId = displayId,
        name = name,
        description = description,
        ownerId = ownerId,
        focusArea = focusArea,
        memberCount = memberCount,
        createdAt = createdAt,
        meetingSchedule = meetingSchedule ?: "Weekly on Thursdays",
        meetingLocation = meetingLocation ?: "Google Meet / Seminar Room",
        isMember = isMember
    )
}

fun GroupModel.toEntity(isSynced: Boolean = false) = GroupEntity(
    id = id,
    displayId = displayId,
    name = name,
    description = description,
    ownerId = ownerId,
    focusArea = focusArea,
    memberCount = memberCount,
    createdAt = createdAt,
    meetingSchedule = meetingSchedule,
    meetingLocation = meetingLocation,
    isMember = isMember,
    isSynced = isSynced
)
