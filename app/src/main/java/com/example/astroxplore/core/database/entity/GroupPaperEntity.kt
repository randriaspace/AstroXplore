package com.example.astroxplore.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.astroxplore.features.groups.model.GroupPaperModel

@Entity(tableName = "group_papers")
data class GroupPaperEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val bibcode: String,
    val addedBy: String,
    val voteCount: Int = 0,
    val isVotedByMe: Boolean = false,
    val addedAt: String?,
    val title: String? = null,
    val authors: String? = null,
    val year: String? = null,
    val isSynced: Boolean = true
) {
    fun toDomainModel() = GroupPaperModel(
        id = id,
        groupId = groupId,
        bibcode = bibcode,
        addedBy = addedBy,
        voteCount = voteCount,
        isVotedByMe = isVotedByMe,
        addedAt = addedAt,
        title = title,
        authors = authors,
        year = year
    )
}

fun GroupPaperModel.toEntity(isSynced: Boolean = true) = GroupPaperEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    bibcode = bibcode,
    addedBy = addedBy,
    voteCount = voteCount,
    isVotedByMe = isVotedByMe,
    addedAt = addedAt,
    title = title,
    authors = authors,
    year = year,
    isSynced = isSynced
)
