package com.telegrambackup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegrambackup.ui.theme.TelegramBackupTheme

class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashInfo = intent.getStringExtra(EXTRA_CRASH_INFO) ?: "Unknown crash"
        val crashThread = intent.getStringExtra(EXTRA_CRASH_THREAD) ?: "Unknown thread"
        val crashTime = intent.getStringExtra(EXTRA_CRASH_TIME) ?: "Unknown time"

        setContent {
            TelegramBackupTheme {
                CrashScreen(
                    crashInfo = crashInfo,
                    crashThread = crashThread,
                    crashTime = crashTime,
                    onShare = { shareCrash(crashInfo, crashThread, crashTime) },
                    onCopy = { copyToClipboard(crashInfo) },
                    onRestart = { restartApp() }
                )
            }
        }
    }

    private fun shareCrash(info: String, thread: String, time: String) {
        val text = buildString {
            appendLine("=== Telegram Backup Crash Report ===")
            appendLine("Time: $time")
            appendLine("Thread: $thread")
            appendLine()
            appendLine(info)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Telegram Backup Crash Report")
        }
        startActivity(Intent.createChooser(intent, "Share crash report"))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("crash_report", text))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_CRASH_INFO = "crash_info"
        const val EXTRA_CRASH_THREAD = "crash_thread"
        const val EXTRA_CRASH_TIME = "crash_time"

        fun launch(context: Context, thread: String, throwable: Throwable) {
            val sw = java.io.StringWriter()
            throwable.printStackTrace(java.io.PrintWriter(sw))

            val intent = Intent(context, CrashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_CRASH_INFO, sw.toString())
                putExtra(EXTRA_CRASH_THREAD, thread)
                putExtra(EXTRA_CRASH_TIME, java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.US
                ).format(java.util.Date()))
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun CrashScreen(
    crashInfo: String,
    crashThread: String,
    crashTime: String,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onRestart: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "App Crashed",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Time: $crashTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "Thread: $crashThread",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }

                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restart App")
            }

            Spacer(Modifier.height(16.dp))

            // Crash details
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Error Details:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = crashInfo,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Send this error to the developer so they can fix it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
