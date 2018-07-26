package com.alexii.j2v8debugger

import com.eclipsesource.v8.V8
import com.eclipsesource.v8.debug.DebugHandler
import com.eclipsesource.v8.debug.DebugHandler.DEBUG_OBJECT_NAME
import java.lang.reflect.Field

/**
 * Debug-related utility functionality for [V8]
 */
object V8Helper {
    private var v8Debugger: DebugHandler? = null

    /**
     * Enables V8 debugging. All new runtimes will be created with debugging enabled.
     *
     * Must be enabled before the v8 runtime is created.
     *
     * @see com.eclipsesource.v8.debug.V8DebugServer.configureV8ForDebugging
     * @see com.eclipsesource.v8.debug.DebugHandler
     */
    fun enableDebugging() {
        V8.setFlags("-expose-debug-as=$DEBUG_OBJECT_NAME")
    }

    /**
     * @return wither debugging was enabled for V8.
     *
     * The condition is necessary, but not sufficient: v8 might be created before flags are set.
     */
    private val isDebuggingEnabled : Boolean
        get() {
            val v8FlagsField: Field = V8::class.java.getDeclaredField("v8Flags")
            v8FlagsField.isAccessible = true
            val v8FlagsValue = v8FlagsField.get(null) as String?

            return v8FlagsValue != null && v8FlagsValue.contains(DEBUG_OBJECT_NAME)
        }


    /**
     * @return new or existing v8 debugger object.
     * Must be released before [V8.release] is called.
     */
    fun getV8Debugger(v8: V8): DebugHandler {
        if (v8Debugger == null) {
            if (!isDebuggingEnabled) {
                throw IllegalStateException("V8 Debugging is not enabled. "
                        + "Call V8Helper.enableV8Debugging() before creation of V8 runtime!")
            }

            v8Debugger = DebugHandler(v8)
        }
        return v8Debugger as DebugHandler;
    }

    fun releaseV8Debugger() {
        v8Debugger?.release()
        v8Debugger = null
    }
}
/**
 * Utility method for simplification of configuring V8 for debugging support.
 */