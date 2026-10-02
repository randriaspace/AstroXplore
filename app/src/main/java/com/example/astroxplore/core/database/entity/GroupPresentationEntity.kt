package com.example.astroxplore.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.astroxplore.features.groups.model.PresentationModel

@Entity(tableName = "group_presentations")
data class GroupPresentationEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val bibcode: String,
    val paperTitle: String? = null,
    val presenterId: String,
    val presenterName: String? = "Lead Presenter",
    val scheduledAt: String,
    val meetingLocation: String? = "Virtual Seminar Room",
    val attendeeCount: Int = 1,
    val isCheckedIn: Boolean = false,
    val createdAt: String? = null,
    val isSynced: Boolean = true
) {
    fun toDomainModel() = PresentationModel(
        id = id,
        groupId = groupId,
        bibcode = bibcode,
        presenterId = presenterId,
        scheduledAt = scheduledAt,
        createdAt = createdAt,
        paperTitle = paperTitle,
        presenterName = presenterName,
        meetingLocation = meetingLocation,
        attendeeCount = attendeeCount,
        isCheckedIn = isCheckedIn
    )
}

fun PresentationModel.toEntity(isSynced: Boolean = true) = GroupPresentationEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    bibcode = bibcode,
    paperTitle = paperTitle,
    presenterId = presenterId,
    presenterName = presenterName,
    scheduledAt = scheduledAt,
    meetingLocation = meetingLocation,
    attendeeCount = attendeeCount,
    isCheckedIn = isCheckedIn,
    createdAt = createdAt,
    isSynced = isSynced
)
