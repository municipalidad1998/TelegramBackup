package com.telegrambackup.util

import android.util.Log
import java.io.PrintWriter
import java.io StringWriter

/**
 * Utility for safe error handling that returns Result instead of throwing.
 */
object CrashCatcher {

    /**
     * Run a block safely. If it throws, return Result.failure.
     */
    inline fun <T> safe(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Throwable) {
            Log.e("CrashCatcher", "Safe call failed", e)
            Result.failure(e)
        }
    }

    /**
     * Run a suspend block safely.
     */
    suspend inline fun <T> safeSuspend(crossinline block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Throwable) {
            Log.e("CrashCatcher", "Safe suspend call failed", e)
            Result.failure(e)
        }
    }

    fun getStacktrace(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
