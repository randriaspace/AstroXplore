package com.example.astroxplore.core.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.astroxplore.core.database.AppDatabase
import com.example.astroxplore.core.database.entity.DownloadState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadWorkerEntryPoint {
    fun okHttpClient(): OkHttpClient
    fun appDatabase(): AppDatabase
}

class DownloadPaperWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DownloadWorkerEntryPoint::class.java
        )
    }

    override suspend fun doWork(): Result {
        val bibcode = inputData.getString(KEY_BIBCODE) ?: return Result.failure()
        val pdfUrl = inputData.getString(KEY_PDF_URL) ?: return Result.failure()

        val savedPaperDao = entryPoint.appDatabase().savedPaperDao()
        val okHttpClient = entryPoint.okHttpClient()

        val pdfsDir = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }
        val targetFile = File(pdfsDir, "$bibcode.pdf")
        val tempFile = File(pdfsDir, "$bibcode.tmp")

        try {
            savedPaperDao.updateDownloadProgress(bibcode, DownloadState.DOWNLOADING, 0)

            val request = Request.Builder().url(pdfUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                savedPaperDao.updateDownloadProgress(bibcode, DownloadState.FAILED, 0)
                return Result.failure()
            }

            val body = response.body
            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastProgress = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (contentLength > 0) {
                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                    if (progress - lastProgress >= 5) {
                        lastProgress = progress
                        setProgress(workDataOf(KEY_PROGRESS to progress))
                        savedPaperDao.updateDownloadProgress(bibcode, DownloadState.DOWNLOADING, progress)
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            val fileSize = targetFile.length()
            savedPaperDao.markDownloadComplete(
                bibcode = bibcode,
                state = DownloadState.DOWNLOADED,
                filePath = targetFile.absolutePath,
                fileSize = fileSize
            )

            return Result.success(workDataOf(KEY_FILE_PATH to targetFile.absolutePath))
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
            savedPaperDao.updateDownloadProgress(bibcode, DownloadState.FAILED, 0)
            return Result.failure()
        }
    }

    companion object {
        const val KEY_BIBCODE = "bibcode"
        const val KEY_PDF_URL = "pdf_url"
        const val KEY_PROGRESS = "progress"
        const val KEY_FILE_PATH = "file_path"
    }
}
