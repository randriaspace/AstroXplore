package com.example.astroxplore.features.feed.data

import com.example.astroxplore.core.database.dao.FeedPaperDao
import com.example.astroxplore.core.database.entity.FeedPaperEntity
import com.example.astroxplore.core.network.NasaAdsApiService
import com.example.astroxplore.features.feed.model.PaperModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

import com.example.astroxplore.core.database.dao.SavedPaperDao
import com.example.astroxplore.core.database.entity.SavedPaperEntity
import java.util.concurrent.ConcurrentHashMap

data class SearchResultWithTotal(
    val papers: List<PaperModel>,
    val totalFound: Int
)

@Singleton
class PaperRepository @Inject constructor(
    private val apiService: NasaAdsApiService,
    private val feedPaperDao: FeedPaperDao,
    private val savedPaperDao: SavedPaperDao
) {
    private val memoryPaperCache = ConcurrentHashMap<String, PaperModel>()

    fun getCachedFeed(): Flow<List<PaperModel>> = feedPaperDao.getCachedFeed().map { entities ->
        entities.map { it.toDomainModel() }
    }

    fun getCachedFeedByCategory(category: String): Flow<List<PaperModel>> =
        feedPaperDao.getCachedFeedByCategory(category).map { entities ->
            entities.map { it.toDomainModel() }
        }

    suspend fun refreshFeed(query: String, append: Boolean = false) {
        val newPapers = getPapersByQuery(query, page = 0, pageSize = 20)
        if (!append) {
            feedPaperDao.refreshFeed(newPapers.map { it.toFeedEntity() })
        } else {
            feedPaperDao.insertPapers(newPapers.map { it.toFeedEntity() })
        }
    }

    suspend fun cacheCategoryPapers(category: String, papers: List<PaperModel>) {
        papers.forEach { memoryPaperCache[it.bibcode] = it }
        val entities = papers.map { it.toFeedEntity(category) }
        feedPaperDao.insertPapers(entities)
    }

    suspend fun getPaperByBibcode(bibcode: String): PaperModel? {
        memoryPaperCache[bibcode]?.let { return it }

        savedPaperDao.getPaperByBibcode(bibcode)?.let { entity ->
            val model = entity.toDomainModel()
            memoryPaperCache[bibcode] = model
            return model
        }

        feedPaperDao.getPaperByBibcode(bibcode)?.let { entity ->
            val model = entity.toDomainModel()
            memoryPaperCache[bibcode] = model
            return model
        }

        return try {
            val networkResults = getPapersByQuery(query = "bibcode:$bibcode", pageSize = 1)
            val found = networkResults.firstOrNull()
            if (found != null) {
                memoryPaperCache[bibcode] = found
                feedPaperDao.insertPapers(listOf(found.toFeedEntity()))
            }
            found
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPapersByQuery(
        query: String,
        sort: String = "date desc",
        page: Int = 0,
        pageSize: Int = 20
    ): List<PaperModel> {
        val response = apiService.executeQuery(
            query = query,
            sort = sort,
            rows = pageSize,
            start = page * pageSize
        )
        val papers = response.response.docs
        papers.forEach { memoryPaperCache[it.bibcode] = it }
        return papers
    }

    suspend fun getPapersByQueryWithTotal(
        query: String,
        sort: String = "date desc",
        page: Int = 0,
        pageSize: Int = 20
    ): SearchResultWithTotal {
        val response = apiService.executeQuery(
            query = query,
            sort = sort,
            rows = pageSize,
            start = page * pageSize
        )
        return SearchResultWithTotal(
            papers = response.response.docs,
            totalFound = response.response.numFound
        )
    }

    // Mappers
    private fun FeedPaperEntity.toDomainModel() = PaperModel(
        bibcode = bibcode,
        rawTitles = listOf(title),
        authors = authors.split(", "),
        abstractText = abstractText,
        keywords = listOf(category),
        rawPubDate = dateDisplay,
        citationCount = citationCount
    )

    private fun SavedPaperEntity.toDomainModel() = PaperModel(
        bibcode = bibcode,
        rawTitles = listOf(title),
        authors = authors.split(", "),
        abstractText = abstractText,
        keywords = listOf(category),
        rawPubDate = dateDisplay,
        citationCount = citationCount
    )

    private fun PaperModel.toFeedEntity(targetCategory: String = category) = FeedPaperEntity(
        bibcode = bibcode,
        title = title,
        authors = authors.joinToString(", "),
        abstractText = abstractText,
        category = targetCategory,
        dateDisplay = dateDisplay,
        citationCount = citationCount
    )
}
