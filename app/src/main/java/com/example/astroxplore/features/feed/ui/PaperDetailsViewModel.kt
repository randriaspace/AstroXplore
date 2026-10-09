package com.example.astroxplore.features.feed.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.astroxplore.features.feed.data.PaperRepository
import com.example.astroxplore.features.feed.model.PaperModel
import com.example.astroxplore.features.groups.data.GroupRepository
import com.example.astroxplore.features.groups.model.GroupModel
import com.example.astroxplore.features.library.data.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PaperDetailsViewModel @Inject constructor(
    private val paperRepository: PaperRepository,
    private val libraryRepository: LibraryRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaperDetailsUiState>(PaperDetailsUiState.Loading)
    val uiState: StateFlow<PaperDetailsUiState> = _uiState.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _downloadState = MutableStateFlow(com.example.astroxplore.core.database.entity.DownloadState.NOT_DOWNLOADED)
    val downloadState: StateFlow<com.example.astroxplore.core.database.entity.DownloadState> = _downloadState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _localFilePath = MutableStateFlow<String?>(null)
    val localFilePath: StateFlow<String?> = _localFilePath.asStateFlow()

    // Offline-First: Reactively observe user groups
    val userGroups: StateFlow<List<GroupModel>> = groupRepository.getLocalGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            groupRepository.syncGroups()
        }
    }

    fun loadPaper(bibcode: String) {
        viewModelScope.launch {
            _uiState.value = PaperDetailsUiState.Loading
            try {
                val papers = paperRepository.getPapersByQuery(
                    query = "bibcode:$bibcode",
                    pageSize = 1
                )
                val paper = papers.firstOrNull()
                if (paper != null) {
                    _uiState.value = PaperDetailsUiState.Success(paper)
                    observeSavedState(bibcode)
                } else {
                    _uiState.value = PaperDetailsUiState.Error("Paper not found")
                }
            } catch (e: Exception) {
                _uiState.value = PaperDetailsUiState.Error("Failed to load paper details")
            }
        }
    }

    private fun observeSavedState(bibcode: String) {
        libraryRepository.isPaperSaved(bibcode)
            .onEach { _isSaved.value = it }
            .launchIn(viewModelScope)

        libraryRepository.observeSavedPaper(bibcode)
            .onEach { entity ->
                _downloadState.value = entity?.downloadState ?: com.example.astroxplore.core.database.entity.DownloadState.NOT_DOWNLOADED
                _downloadProgress.value = entity?.downloadProgress ?: 0
                _localFilePath.value = entity?.localFilePath
            }
            .launchIn(viewModelScope)
    }

    fun toggleSave(paper: PaperModel) {
        viewModelScope.launch {
            libraryRepository.toggleSave(paper)
        }
    }

    fun downloadAndOpenPdf(
        context: Context,
        paper: PaperModel,
        onOpenPdf: (bibcode: String, pdfFilePath: String, paperTitle: String) -> Unit
    ) {
        libraryRepository.downloadPaperPdf(context, paper)
        val expectedFile = File(context.filesDir, "pdfs/${paper.bibcode}.pdf")
        onOpenPdf(paper.bibcode, expectedFile.absolutePath, paper.title)
    }

    fun addPaperToGroup(groupId: String, bibcode: String) {
        viewModelScope.launch {
            groupRepository.addPaperToGroup(groupId, bibcode)
        }
    }
}

sealed interface PaperDetailsUiState {
    data object Loading : PaperDetailsUiState
    data class Success(val paper: PaperModel) : PaperDetailsUiState
    data class Error(val message: String) : PaperDetailsUiState
}
