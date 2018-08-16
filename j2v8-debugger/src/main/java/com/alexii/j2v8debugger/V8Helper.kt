package com.alexii.j2v8debugger

import com.alexii.j2v8debugger.V8Helper.releaseV8Debugger
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.debug.DebugHandler
import com.eclipsesource.v8.debug.DebugHandler.DEBUG_OBJECT_NAME
import java.lang.reflect.Field
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

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
    fun getOrCreateV8Debugger(v8: V8): DebugHandler {
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

    /**
     * Utility, which simplifies configuring V8 for debugging support and creation of new instance.
     * Creates V8 runtime, v8 debugger and binds it to Stetho.
     * For releasing resources [releaseDebuggable] should be used.
     *
     * @param v8Executor sigle-thread executor where v8 will be created
     *  and all debug calls will be performed by Stetho later.
     *
     * NOTE: Should be declared as V8 class extensions when will be allowed (https://youtrack.jetbrains.com/issue/KT-11968)
     */
    @JvmStatic
    fun createDebuggableV8Runtime(v8Executor: ExecutorService): Future<V8> {
        enableDebugging()

        val v8Future: Future<V8> = v8Executor.submit(Callable {
            val runtime = V8.createV8Runtime()
            val v8Debugger = getOrCreateV8Debugger(runtime)

            StethoHelper.initializeWithV8Debugger(v8Debugger, v8Executor)

            runtime
        })

        return v8Future;
    }

}


/**
 * Releases V8 and V8 debugger if any was created.
 *
 * Must be called on V8's thread becuase of the J2V8 limitations.
 *
 * @see V8.release
 * @see releaseV8Debugger
 */
@JvmOverloads
fun V8.releaseDebuggable(reportMemoryLeaks: Boolean = true) {
    V8Helper.releaseV8Debugger()
    this.release(reportMemoryLeaks)
}
