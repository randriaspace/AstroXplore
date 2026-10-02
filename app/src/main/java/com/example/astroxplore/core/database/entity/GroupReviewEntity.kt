package com.example.astroxplore.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.astroxplore.features.groups.model.SessionReviewModel

@Entity(tableName = "group_reviews")
data class GroupReviewEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val bibcode: String,
    val paperTitle: String? = null,
    val reviewerId: String,
    val reviewerName: String? = "Club Member",
    val notes: String,
    val rating: Int = 5,
    val createdAt: String? = null,
    val isSynced: Boolean = true
) {
    fun toDomainModel() = SessionReviewModel(
        id = id,
        groupId = groupId,
        bibcode = bibcode,
        reviewerId = reviewerId,
        notes = notes,
        rating = rating,
        createdAt = createdAt,
        paperTitle = paperTitle,
        reviewerName = reviewerName
    )
}

fun SessionReviewModel.toEntity(isSynced: Boolean = true) = GroupReviewEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    bibcode = bibcode,
    paperTitle = paperTitle,
    reviewerId = reviewerId,
    reviewerName = reviewerName,
    notes = notes,
    rating = rating,
    createdAt = createdAt,
    isSynced = isSynced
)
