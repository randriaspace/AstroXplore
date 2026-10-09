package com.example.astroxplore.features.library.data

import android.content.Context
import com.example.astroxplore.core.database.dao.PendingPaperDeletionDao
import com.example.astroxplore.core.database.dao.SavedPaperDao
import com.example.astroxplore.core.database.entity.PendingPaperDeletionEntity
import com.example.astroxplore.core.database.entity.SavedPaperEntity
import com.example.astroxplore.core.download.DownloadPaperWorker
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
import java.io.File
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

    fun observeSavedPaper(bibcode: String): Flow<SavedPaperEntity?> = savedPaperDao.observeSavedPaper(bibcode)

    suspend fun getPaperEntity(bibcode: String): SavedPaperEntity? = savedPaperDao.getPaperByBibcode(bibcode)

    suspend fun toggleSave(paper: PaperModel) = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
        val isCurrentlySaved = savedPaperDao.isPaperSaved(paper.bibcode).first()
        
        if (isCurrentlySaved) {
            unsavePaper(userId, paper.bibcode)
        } else {
            savePaper(userId, paper)
        }
    }

    fun downloadPaperPdf(context: Context, paper: PaperModel) {
        val pdfUrl = paper.pdfUrl ?: return
        
        repositoryScope.launch {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: "local_user"
            savePaper(userId, paper)

            val workData = androidx.work.workDataOf(
                DownloadPaperWorker.KEY_BIBCODE to paper.bibcode,
                DownloadPaperWorker.KEY_PDF_URL to pdfUrl
            )

            val request = androidx.work.OneTimeWorkRequestBuilder<DownloadPaperWorker>()
                .setInputData(workData)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()

            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "download_pdf_${paper.bibcode}",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }

    suspend fun getLocalPdfCacheSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        val pdfsDir = File(context.filesDir, "pdfs")
        if (!pdfsDir.exists()) return@withContext 0L
        pdfsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun clearLocalPdfCache(context: Context) = withContext(Dispatchers.IO) {
        val pdfsDir = File(context.filesDir, "pdfs")
        if (pdfsDir.exists()) {
            pdfsDir.listFiles()?.forEach { it.delete() }
        }
        savedPaperDao.clearAllLocalPdfs()
    }

    private suspend fun savePaper(userId: String, paper: PaperModel) {
        savedPaperDao.savePaper(paper.toEntity(isSynced = false))
        
        try {
            supabaseClient.postgrest["saved_papers"].insert(paper.toSupabaseModel(userId))
            savedPaperDao.savePaper(paper.toEntity(isSynced = true))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun unsavePaper(userId: String, bibcode: String) {
        savedPaperDao.deletePaper(bibcode)
        pendingPaperDeletionDao.insertPendingDeletion(PendingPaperDeletionEntity(bibcode, userId))

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
        }
    }

    suspend fun syncLibrary() = withContext(Dispatchers.IO) {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext

        try {
            // 1. Process pending deletions
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

            // 2. Process unsynced additions
            val unsyncedLocal = savedPaperDao.getUnsyncedPapers()
            unsyncedLocal.forEach { local ->
                try {
                    supabaseClient.postgrest["saved_papers"].insert(local.toSupabaseModel(userId))
                    savedPaperDao.savePaper(local.copy(isSynced = true))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. Fetch remote saved papers
            val remotePapers = supabaseClient.postgrest["saved_papers"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<SavedPaperModel>()

            val remainingPendingDeletions = pendingPaperDeletionDao.getPendingDeletions(userId).map { it.bibcode }.toSet()

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
        isSynced = isSynced,
        pdfUrl = pdfUrl
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
