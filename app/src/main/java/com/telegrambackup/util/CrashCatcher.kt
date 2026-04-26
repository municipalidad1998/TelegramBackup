package com.telegrambackup.util

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Global crash catcher that returns the full stacktrace as a string
 * instead of crashing the app.
 */
object CrashCatcher {

    private const val TAG = "CrashCatcher"

    /**
     * Run a block safely. If it throws, log the error and return the result of [onError].
     */
    inline fun <T> safe(
        operation: String = "unknown",
        onError: (String) -> T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: Throwable) {
            val stacktrace = getStacktrace(e)
            Log.e(TAG, "Crash in $operation: $stacktrace")
            onError("[$operation] ${e.javaClass.simpleName}: ${e.message}\n\n$stacktrace")
        }
    }

    /**
     * Run a suspend block safely.
     */
    suspend inline fun <T> safeSuspend(
        operation: String = "unknown",
        crossinline onError: (String) -> T,
        crossinline block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: Throwable) {
            val stacktrace = getStacktrace(e)
            Log.e(TAG, "Crash in $operation: $stacktrace")
            onError("[$operation] ${e.javaClass.simpleName}: ${e.message}\n\n$stacktrace")
        }
    }

    fun getStacktrace(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
