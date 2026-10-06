package com.example.astroxplore.features.library.data

import com.example.astroxplore.core.database.dao.PendingPaperDeletionDao
import com.example.astroxplore.core.database.dao.SavedPaperDao
import com.example.astroxplore.core.database.entity.PendingPaperDeletionEntity
import com.example.astroxplore.core.database.entity.SavedPaperEntity
import com.example.astroxplore.core.network.NetworkConnectivityObserver
import com.example.astroxplore.features.feed.model.PaperModel
import com.example.astroxplore.features.library.model.SavedPaperModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val savedPaperDao: SavedPaperDao,
    private val pendingPaperDeletionDao: PendingPaperDeletionDao,
    private val networkConnectivityObserver: NetworkConnectivityObserver
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        // Auto-sync when internet connectivity is restored
        repositoryScope.launch {
            networkConnectivityObserver.isConnected.collect { isConnected ->
                if (isConnected) {
                    syncLibrary()
                }
            }
        }
    }

    fun getSavedPapers(): Flow<List<PaperModel>> = savedPaperDao.getAllSavedPapers().map { entities ->
        entities.map { it.toDomainModel() }
    }

    fun getSavedPaperCount(): Flow<Int> = savedPaperDao.getSavedPaperCount()

    fun isPaperSaved(bibcode: String): Flow<Boolean> = savedPaperDao.isPaperSaved(bibcode)

    suspend fun toggleSave(paper: PaperModel) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        val isCurrentlySaved = savedPaperDao.isPaperSaved(paper.bibcode).first()
        
        if (isCurrentlySaved) {
            unsavePaper(userId, paper.bibcode)
        } else {
            savePaper(userId, paper)
        }
    }

    private suspend fun savePaper(userId: String, paper: PaperModel) {
        // Clear any pending deletion for this paper
        pendingPaperDeletionDao.removePendingDeletion(paper.bibcode)

        // Save locally first
        savedPaperDao.savePaper(paper.toEntity(isSynced = false))
        
        // Save to Supabase
        try {
            val model = paper.toSupabaseModel(userId)
            supabaseClient.postgrest["saved_papers"].insert(model)
            savedPaperDao.savePaper(paper.toEntity(isSynced = true))
        } catch (e: Exception) {
            e.printStackTrace() // Log for debugging
            // Remains unsynced locally, will sync when reconnected
        }
    }

    private suspend fun unsavePaper(userId: String, bibcode: String) {
        // Remove locally immediately
        savedPaperDao.deletePaper(bibcode)
        
        // Record pending deletion in case device is offline
        pendingPaperDeletionDao.insertPendingDeletion(
            PendingPaperDeletionEntity(bibcode = bibcode, userId = userId)
        )

        // Remove from Supabase
        try {
            supabaseClient.postgrest["saved_papers"].delete {
                filter {
                    eq("user_id", userId)
                    eq("bibcode", bibcode)
                }
            }
            pendingPaperDeletionDao.removePendingDeletion(bibcode)
        } catch (e: Exception) {
            e.printStackTrace()
            // Remains in pendingPaperDeletionDao until reconnected
        }
    }

    suspend fun syncLibrary() = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        try {
            // 1. Process pending remote deletions
            val pendingDeletions = pendingPaperDeletionDao.getPendingDeletions(userId)
            pendingDeletions.forEach { pending ->
                try {
                    supabaseClient.postgrest["saved_papers"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("bibcode", pending.bibcode)
                        }
                    }
                    pendingPaperDeletionDao.removePendingDeletion(pending.bibcode)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Sync local unsynced to remote
            val unsynced = savedPaperDao.getUnsyncedPapers()
            unsynced.forEach { paper ->
                try {
                    val model = paper.toSupabaseModel(userId)
                    supabaseClient.postgrest["saved_papers"].insert(model)
                    savedPaperDao.savePaper(paper.copy(isSynced = true))
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fail silently, retry next sync
                }
            }

            // 3. Fetch remote papers
            val remotePapers = supabaseClient.postgrest["saved_papers"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<SavedPaperModel>()
            
            // Filter out any papers still pending deletion
            val remainingPendingDeletions = pendingPaperDeletionDao.getPendingDeletions(userId)
                .map { it.bibcode }
                .toSet()

            // 4. Sync remote to local
            remotePapers.forEach { remote ->
                if (!remainingPendingDeletions.contains(remote.bibcode)) {
                    savedPaperDao.savePaper(remote.toEntity(isSynced = true))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun SavedPaperEntity.toSupabaseModel(userId: String) = SavedPaperModel(
        userId = userId,
        bibcode = bibcode,
        title = title,
        authors = authors,
        abstract = abstractText,
        category = category,
        dateDisplay = dateDisplay,
        citationCount = citationCount
    )

    // Mappers
    private fun SavedPaperEntity.toDomainModel() = PaperModel(
        bibcode = bibcode,
        rawTitles = listOf(title),
        authors = authors.split(", "),
        abstractText = abstractText,
        keywords = listOf(category),
        rawPubDate = dateDisplay,
        citationCount = citationCount
    )

    private fun PaperModel.toEntity(isSynced: Boolean = true) = SavedPaperEntity(
        bibcode = bibcode,
        title = title,
        authors = authors.joinToString(", "),
        abstractText = abstractText,
        category = category,
        dateDisplay = dateDisplay,
        citationCount = citationCount,
        isSynced = isSynced
    )

    private fun SavedPaperModel.toEntity(isSynced: Boolean = true) = SavedPaperEntity(
        bibcode = bibcode,
        title = title ?: "",
        authors = authors ?: "",
        abstractText = abstract ?: "",
        category = category ?: "",
        dateDisplay = dateDisplay ?: "",
        citationCount = citationCount,
        isSynced = isSynced
    )

    private fun PaperModel.toSupabaseModel(userId: String) = SavedPaperModel(
        userId = userId,
        bibcode = bibcode,
        title = title,
        authors = authors.joinToString(", "),
        abstract = abstractText,
        category = category,
        dateDisplay = dateDisplay,
        citationCount = citationCount
    )
}
