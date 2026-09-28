package com.iptvplayerpro.ui.screens.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.iptvplayerpro.core.util.TimeFmt
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.domain.model.EpgProgramme
import com.iptvplayerpro.domain.model.GuideChannel
import com.iptvplayerpro.ui.components.AppTopBar
import com.iptvplayerpro.ui.components.EmptyState
import java.util.Calendar

private val PX_PER_MINUTE = 3.dp
private val CHANNEL_COLUMN_WIDTH = 120.dp

/** Guía de programación (EPG) con línea de tiempo de 24 horas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgGuideScreen(
    onBack: () -> Unit,
    onPlayChannel: (Long) -> Unit
) {
    val viewModel = containerViewModel(EpgGuideViewModel::class) { container, _ -> EpgGuideViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val dayNames = remember {
        (0..6).map { offset ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
            if (offset == 0) "Hoy" else TimeFmt.dayLabel(cal.timeInMillis)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Guía de programación",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.refreshEpg() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar EPG")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Selector de día
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dayNames.forEachIndexed { index, name ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (index == state.dayOffset) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (index == state.dayOffset) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.clickable { viewModel.load(index) }
                    ) {
                        Text(name, Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
            }

            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Cargando guía…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.guide == null || state.guide?.channels.isNullOrEmpty() -> {
                    EmptyState(
                        title = "EPG no disponible",
                        message = "Tu lista no entregó guía de programación. Verifica la URL " +
                            "XMLTV o la EPG de tu cuenta Xtream en Mis listas, y pulsa actualizar."
                    )
                }
                else -> {
                    EpgTimeline(
                        channels = state.guide!!.channels,
                        windowStart = state.guide!!.windowStart,
                        onPlayChannel = onPlayChannel,
                        onProgrammeClick = { viewModel.selectProgramme(it) }
                    )
                }
            }
        }
    }

    // Detalle del programa
    state.selectedProgramme?.let { programme ->
        AlertDialog(
            onDismissRequest = { viewModel.selectProgramme(null) },
            title = { Text(programme.title) },
            text = {
                Column {
                    Text(
                        "${TimeFmt.time(programme.start)} – ${TimeFmt.time(programme.end)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    programme.description?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.selectProgramme(null) }) { Text("Cerrar") }
            }
        )
    }
}

@Composable
private fun EpgTimeline(
    channels: List<GuideChannel>,
    windowStart: Long,
    onPlayChannel: (Long) -> Unit,
    onProgrammeClick: (EpgProgramme) -> Unit
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val totalWidth = (24 * 60 * PX_PER_MINUTE.value * density.density).toInt()
    val now = System.currentTimeMillis()
    val nowMinutes = ((now - windowStart) / 60_000f).toInt()
    val isToday = now in windowStart..(windowStart + 24 * 60 * 60 * 1000)

    // Posiciona la línea de tiempo en "ahora" al abrir.
    LaunchedEffect(Unit) {
        if (isToday) {
            scrollState.scrollTo(
                ((nowMinutes * PX_PER_MINUTE.value * density.density) - 100 * density.density).toInt()
                    .coerceAtLeast(0)
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Cabecera de horas
        Row {
            Spacer(Modifier.width(CHANNEL_COLUMN_WIDTH))
            Box(
                Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                Row(Modifier.width(with(density) { totalWidth.toDp() })) {
                    for (hour in 0 until 24) {
                        Box(
                            Modifier
                                .width(PX_PER_MINUTE * 60)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                String.format("%02d:00", hour),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(channels, key = { it.channel.id }) { guideChannel ->
                Row(Modifier.fillMaxWidth()) {
                    // Columna fija: canal
                    Column(
                        Modifier
                            .width(CHANNEL_COLUMN_WIDTH)
                            .clickable { onPlayChannel(guideChannel.channel.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            guideChannel.channel.logoUrl?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                guideChannel.channel.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Programas (desplazamiento sincronizado)
                    Box(
                        Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState)
                            .height(48.dp)
                    ) {
                        Row(
                            Modifier
                                .width(with(density) { totalWidth.toDp() })
                                .fillMaxHeight()
                        ) {
                            guideChannel.programmes.forEach { programme ->
                                val widthMinutes =
                                    ((programme.end - programme.start) / 60_000f).coerceAtLeast(5f)
                                val isNow = now in programme.start until programme.end
                                val isPast = programme.end < now
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when {
                                        isNow -> MaterialTheme.colorScheme.primaryContainer
                                        isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                    },
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .width(PX_PER_MINUTE * widthMinutes)
                                        .padding(1.dp)
                                        .clickable { onProgrammeClick(programme) }
                                ) {
                                    Column(
                                        Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            programme.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            TimeFmt.time(programme.start),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Línea "ahora"
                            if (isToday && nowMinutes in 0..(24 * 60)) {
                                Box(
                                    Modifier
                                        .offset(x = PX_PER_MINUTE * nowMinutes)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
