package com.iptvplayerpro

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.iptvplayerpro.core.prefs.Settings
import com.iptvplayerpro.di.LocalAppContainer
import com.iptvplayerpro.ui.nav.AppNavHost
import com.iptvplayerpro.ui.theme.IptvPlayerProTheme

/** Estado global de modo PiP (lo actualiza MainActivity). */
val LocalPipMode = staticCompositionLocalOf { false }

class MainActivity : ComponentActivity() {

    private val isInPipMode = mutableStateOf(false)

    /** Caché del ajuste auto-PiP para consultarlo en onUserLeaveHint. */
    private var autoPipEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addOnPictureInPictureModeChangedListener { info ->
                isInPipMode.value = info.isInPictureInPictureMode
            }
        }

        val container = (application as IptvApp).container

        setContent {
            val settings by container.settings.settings.collectAsState(initial = Settings())
            autoPipEnabled = settings.autoPip

            IptvPlayerProTheme(themeMode = settings.themeMode, dynamicColors = settings.dynamicColors) {
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalPipMode provides isInPipMode.value,
                    LocalActivity provides this@MainActivity
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavHost()
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val controller = (application as IptvApp).container.playerController
        val active = controller.playerScreenActive.value
        val playing = controller.uiState.value.isPlaying
        if (active && playing && autoPipEnabled) {
            enterPipIfPossible()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }

    private fun enterPipIfPossible() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            val controller = (application as IptvApp).container.playerController
            val size = controller.uiState.value.videoSize
            val params = PictureInPictureParams.Builder().apply {
                if (size != null && size.width > 0 && size.height > 0) {
                    setAspectRatio(Rational(size.width, size.height))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(true)
                }
            }
            runCatching { enterPictureInPictureMode(params.build()) }
        }
    }
}

/** Activity actual (para orientación/barras del reproductor). */
val LocalActivity = staticCompositionLocalOf<androidx.activity.ComponentActivity?> {
    null
}

@Composable
fun rememberActivityOrNull(): androidx.activity.ComponentActivity? = LocalContext.current as? androidx.activity.ComponentActivity
