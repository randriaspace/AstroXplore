package com.example.astroxplore.features.search.model

import kotlinx.serialization.Serializable

@Serializable
data class AiResearchBriefing(
    val id: String,
    val title: String,
    val hypothesisSummary: String,
    val categoryBadge: String,
    val recommendedAdsQuery: String,
    val contextSourceReason: String,
    val suggestedKeywords: List<String> = emptyList(),
    val targetBibstem: String? = null
)
