package com.iptvplayerpro.ui.screens.playlists

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvplayerpro.core.util.Urls
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.domain.model.Playlist
import com.iptvplayerpro.domain.model.PlaylistType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditUiState(
    val playlistId: Long = -1L,
    val existing: Playlist? = null,
    val name: String = "",
    val url: String = "",
    val epgUrl: String = "",
    val server: String = "",
    val user: String = "",
    val password: String = "",
    val fileName: String? = null,
    val fileContent: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
) {
    val isEdit: Boolean get() = playlistId > 0
    val canSaveM3uUrl: Boolean get() = name.isNotBlank() && Urls.isValidHttpUrl(Urls.normalize(url))
    val canSaveM3uFile: Boolean get() = name.isNotBlank() && fileContent != null
    val canSaveXtream: Boolean get() =
        name.isNotBlank() && server.isNotBlank() && user.isNotBlank() && password.isNotBlank()
}

/** Formulario para agregar/editar listas (Archivo M3U · URL M3U · Xtream). */
class PlaylistEditViewModel(private val container: AppContainer) : ViewModel() {

    private val playlistRepository = container.playlistRepository

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    fun load(playlistId: Long) {
        if (playlistId <= 0 || _state.value.playlistId == playlistId) return
        viewModelScope.launch {
            val playlist = playlistRepository.getPlaylist(playlistId) ?: return@launch
            _state.value = EditUiState(
                playlistId = playlistId,
                existing = playlist,
                name = playlist.name,
                url = playlist.sourceUrl.orEmpty(),
                epgUrl = playlist.epgUrl.orEmpty(),
                server = playlist.serverUrl.orEmpty(),
                user = playlist.username.orEmpty()
            )
        }
    }

    fun update(transform: (EditUiState) -> EditUiState) {
        _state.value = transform(_state.value)
    }

    /** Lee el archivo M3U/M3U8 seleccionado por el usuario (SAF). */
    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, content) = withContext(Dispatchers.IO) {
                    val resolver = container.appContext.contentResolver
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("No se pudo abrir el archivo.")
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "lista.m3u"
                    fileName to text
                }
                if (!Urls.looksLikeM3U(content)) {
                    _state.value = _state.value.copy(
                        error = "El archivo no parece una lista M3U/M3U8 válida."
                    )
                    return@launch
                }
                _state.value = _state.value.copy(
                    fileName = name,
                    fileContent = content,
                    error = null,
                    name = _state.value.name.ifBlank { name.removeSuffix(".m3u8").removeSuffix(".m3u") }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "No se pudo leer el archivo.")
            }
        }
    }

    fun saveXtream() {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(saving = true, error = null)
            val result = playlistRepository.addXtream(s.name, s.server, s.user, s.password)
            result.fold(
                onSuccess = { _state.value = _state.value.copy(saving = false, saved = true) },
                onFailure = { e ->
                    _state.value = _state.value.copy(saving = false, error = e.message)
                }
            )
        }
    }

    fun saveM3uFromUrl() {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(saving = true, error = null)
            val result = playlistRepository.addM3U(s.name, Urls.normalize(s.url), s.epgUrl, null)
            result.fold(
                onSuccess = { _state.value = _state.value.copy(saving = false, saved = true) },
                onFailure = { e ->
                    _state.value = _state.value.copy(saving = false, error = e.message)
                }
            )
        }
    }

    fun saveM3uFromFile() {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(saving = true, error = null)
            val result = playlistRepository.addM3U(
                name = s.name,
                url = null,
                epgUrl = s.epgUrl.ifBlank { null },
                content = s.fileContent
            )
            result.fold(
                onSuccess = { _state.value = _state.value.copy(saving = false, saved = true) },
                onFailure = { e ->
                    _state.value = _state.value.copy(saving = false, error = e.message)
                }
            )
        }
    }

    /** Edición: actualiza campos (contraseña vacía = conservar) y re-sincroniza. */
    fun saveEdit() {
        val s = _state.value
        val playlist = s.existing ?: return
        viewModelScope.launch {
            _state.value = s.copy(saving = true, error = null)
            val result = playlistRepository.update(
                id = playlist.id,
                name = s.name,
                url = s.url.takeIf { playlist.type == PlaylistType.M3U },
                epgUrl = s.epgUrl.takeIf { playlist.type == PlaylistType.M3U },
                server = s.server.takeIf { playlist.type == PlaylistType.XTREAM },
                user = s.user.takeIf { playlist.type == PlaylistType.XTREAM },
                password = s.password.takeIf { playlist.type == PlaylistType.XTREAM }
            )
            result.fold(
                onSuccess = { _state.value = _state.value.copy(saving = false, saved = true) },
                onFailure = { e ->
                    _state.value = _state.value.copy(saving = false, error = e.message)
                }
            )
        }
    }
}
