package com.alexii.j2v8debugger.utils

import com.alexii.j2v8debugger.BuildConfig
import com.alexii.j2v8debugger.Debugger.Companion.TAG

/**
 *
 * Calling this method is "expensive" operation. Make sure, that it's done rare or only in [BuildConfig.DEBUG]
 *
 * See http://stackoverflow.com/a/11306854/3134602
 *
 * TODO: Use Throwable.getStackTrace() for better performance. See http://stackoverflow.com/a/2347878/3134602
 */
object LogUtils {
    private fun getCallerMethodName(): String {
        val stackTraceElements = Thread.currentThread().stackTrace

        val indexBeforeCaller = stackTraceElements.indexOfLast { it.className == this.javaClass.name }

        val callerStackTraceElement = stackTraceElements[indexBeforeCaller + 1]

        return callerStackTraceElement.methodName
    }

    fun logMethodCalled() {
        if (!BuildConfig.DEBUG) return;

        try {
            logger.w(TAG, "Calling " + getCallerMethodName())
        } catch (e: Exception) {
            logger.e(TAG, "Unable to log called method", e)
        }

    }
}
