package com.iptvplayerpro.di

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlin.reflect.KClass

/** Contenedor de dependencias disponible en el árbol de Compose. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer no proporcionado")
}

/**
 * Crea un ViewModel con acceso al contenedor de dependencias y a los extras
 * (SavedStateHandle, etc.) sin frameworks de inyección.
 *
 * Nota: no puede ser `inline`/`reified` porque el compilador de Compose
 * elimina `inline` de las funciones @Composable; por eso la clase del
 * ViewModel se pasa explícitamente como [KClass].
 */
@Composable
fun <VM : ViewModel> containerViewModel(
    modelClass: KClass<VM>,
    key: String? = null,
    builder: (AppContainer, CreationExtras) -> VM
): VM {
    val container = LocalAppContainer.current
    val owner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No hay ViewModelStoreOwner disponible (LocalViewModelStoreOwner)."
    }
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: CreationExtras
        ): T {
            @Suppress("UNCHECKED_CAST")
            return builder(container, extras) as T
        }
    }
    val provider = ViewModelProvider(owner, factory)
    return if (key != null) {
        provider.get(key, modelClass.java)
    } else {
        provider.get(modelClass.java)
    }
}
