package com.iptvplayerpro.ui.screens.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.domain.model.EpgProgramme
import com.iptvplayerpro.domain.model.GuideData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class EpgGuideUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val dayOffset: Int = 0,
    val guide: GuideData? = null,
    val selectedProgramme: EpgProgramme? = null,
    val error: String? = null
)

class EpgGuideViewModel(private val container: AppContainer) : ViewModel() {

    private val epgRepository = container.epgRepository

    private val _state = MutableStateFlow(EpgGuideUiState())
    val state: StateFlow<EpgGuideUiState> = _state.asStateFlow()

    val epgChannels = epgRepository.epgChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        load(0)
    }

    fun load(dayOffset: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, dayOffset = dayOffset, error = null)
            val day = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val from = day.timeInMillis
            val to = from + 24 * 60 * 60 * 1000L
            runCatching { epgRepository.guideWindow(from, to) }
                .onSuccess { guide ->
                    _state.value = _state.value.copy(loading = false, guide = guide)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "No se pudo cargar la guía."
                    )
                }
        }
    }

    fun refreshEpg() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            epgRepository.refreshAll()
            _state.value = _state.value.copy(refreshing = false)
            load(_state.value.dayOffset)
        }
    }

    fun selectProgramme(programme: EpgProgramme?) {
        _state.value = _state.value.copy(selectedProgramme = programme)
    }
}
