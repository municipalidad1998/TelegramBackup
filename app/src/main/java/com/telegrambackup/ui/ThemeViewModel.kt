package com.telegrambackup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegrambackup.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    preferences: AppPreferences
) : ViewModel() {
    val darkMode = preferences.darkMode.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        true
    )
}
