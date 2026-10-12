package com.example.astroxplore.features.search.data

import com.example.astroxplore.features.search.model.AiResearchBriefing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GeminiContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null
)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@Serializable
data class GeminiContentResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiContentRequest
    ): GeminiContentResponse
}

@Singleton
class GeminiAiResearchService @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val service = retrofit.create(GeminiApiService::class.java)

    suspend fun synthesizeResearchBriefings(
        apiKey: String,
        userInterests: List<String>,
        groupTopics: List<String>,
        savedPaperTitles: List<String>
    ): List<AiResearchBriefing> {
        val prompt = buildString {
            appendLine("You are an expert theoretical astrophysicist and NASA ADS research advisor.")
            appendLine("Synthesize 3-4 highly tailored, academically rigorous trending research briefings for an astronomer based on their context:")
            if (userInterests.isNotEmpty()) {
                appendLine("- User Research Interests: ${userInterests.joinToString(", ")}")
            }
            if (groupTopics.isNotEmpty()) {
                appendLine("- Active Journal Club Discussion Topics: ${groupTopics.joinToString(", ")}")
            }
            if (savedPaperTitles.isNotEmpty()) {
                appendLine("- Saved Library Papers: ${savedPaperTitles.take(5).joinToString(" | ")}")
            }
            appendLine()
            appendLine("Respond ONLY with a valid JSON array of objects with these exact keys:")
            appendLine("- id: string (unique short slug)")
            appendLine("- title: string (professional academic research topic, e.g. 'JWST Transmission Spectroscopy of Sub-Neptunes')")
            appendLine("- hypothesisSummary: string (2 concise sentences explaining the current astrophysical problem, observations, or observational tension)")
            appendLine("- categoryBadge: string (e.g. 'Journal Club Synergy', 'High-Impact Citation', 'Exoplanet Atmospheres')")
            appendLine("- recommendedAdsQuery: string (valid Solr NASA ADS query syntax using title, abs, or keyword, e.g. '(title:\"transmission spectroscopy\" OR abs:\"atmospheric metallicity\") AND property:refereed')")
            appendLine("- contextSourceReason: string (e.g. 'Synthesized from your Journal Club presentations and recent saved papers')")
            appendLine("- suggestedKeywords: array of 2-3 short strings")
            appendLine("- targetBibstem: string or null (e.g. 'ApJ', 'MNRAS', 'A&A')")
        }

        val request = GeminiContentRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.4f
            )
        )

        val response = service.generateContent(apiKey = apiKey, request = request)
        val responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return emptyList()

        return try {
            json.decodeFromString<List<AiResearchBriefing>>(responseText)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
