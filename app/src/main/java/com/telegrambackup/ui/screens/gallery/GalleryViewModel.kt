package com.telegrambackup.ui.screens.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class GalleryUiState(
    val isLoading: Boolean = false,
    val allFiles: List<BackupFile> = emptyList(),
    val groupedFiles: Map<String, List<BackupFile>> = emptyMap(),
    val selectedFilter: FileType? = null,
    val error: String? = null
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())

    init {
        loadFiles()
    }

    private fun loadFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getAllFiles().collect { files ->
                val filtered = _uiState.value.selectedFilter?.let { filter ->
                    files.filter { it.fileType == filter }
                } ?: files

                val grouped = filtered.groupBy { file ->
                    val cal = Calendar.getInstance().apply { timeInMillis = file.dateAdded }
                    dateFormat.format(cal.time)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    allFiles = files,
                    groupedFiles = grouped
                )
            }
        }
    }

    fun setFilter(type: FileType?) {
        _uiState.value = _uiState.value.copy(selectedFilter = type)
        loadFiles() // reload with filter
    }

    fun refresh() {
        viewModelScope.launch {
            repository.scanAndRegisterNewFiles()
            loadFiles()
        }
    }

    fun getFileById(id: Long): Flow<BackupFile?> {
        return flow { emit(repository.getFileById(id)) }
    }
}
