package com.iptvplayerpro.di

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory

/** Contenedor de dependencias disponible en el árbol de Compose. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer no proporcionado")
}

/**
 * Crea un ViewModel con acceso al contenedor de dependencias y a los extras
 * (SavedStateHandle, etc.) sin frameworks de inyección.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    noinline key: String? = null,
    crossinline create: (AppContainer, CreationExtras) -> VM
): VM {
    val container = LocalAppContainer.current
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(container, this) }
        }
    )
}
