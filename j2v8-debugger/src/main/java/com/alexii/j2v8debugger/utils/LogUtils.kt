package com.alexii.j2v8debugger.utils

import com.alexii.j2v8debugger.Debugger.Companion.TAG
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsMethod

/**
 * Calling this method is "expensive" operation. Make sure, that it's done rare or only in [BuildConfig.DEBUG]
 *
 * See http://stackoverflow.com/a/11306854/3134602
 */
internal object LogUtils {
    @JvmStatic
    var enabled = false

    /**
     * Returns name of the method annotated with @ChromeDevtoolsMethod.
     * Note: This method is always called from the child of [ChromeDevtoolsDomain]
     */
    fun getChromeDevToolsMethodName(): String {
        val stackTraceElements = Throwable().stackTrace

        val chromeDevtoolsStackTraceElement = stackTraceElements
                .find {
                    val clazz = Class.forName(it.className)
                    isChromeDevToolsClass(clazz) && isChromeDevToolsMethod(clazz, it.methodName)
                }

        return chromeDevtoolsStackTraceElement?.methodName.orEmpty()
    }

    private fun isChromeDevToolsClass(clazz: Class<*>) = ChromeDevtoolsDomain::class.java.isAssignableFrom(clazz)

    private fun isChromeDevToolsMethod(clazz: Class<*>, methodName: String?): Boolean {
        val method = clazz.methods.find { method -> method.name == methodName }
        return method?.isAnnotationPresent(ChromeDevtoolsMethod::class.java) ?: false
    }

    fun logChromeDevToolsCalled() {
        if (!enabled) return

        try {
            logger.i(TAG, "Calling ${getChromeDevToolsMethodName()}")
        } catch (e: Exception) {
            logger.e(TAG, "Unable to log called method", e)
        }
    }

}
