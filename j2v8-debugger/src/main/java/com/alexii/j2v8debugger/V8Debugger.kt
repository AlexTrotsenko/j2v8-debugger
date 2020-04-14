package com.alexii.j2v8debugger

import com.alexii.j2v8debugger.utils.LogUtils
import com.eclipsesource.v8.V8
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

object V8Debugger {
    /**
     * Utility, which simplifies configuring V8 for debugging support and creation of new instance.
     * Creates V8 runtime, v8 debugger and binds it to Stetho.
     *
     * @param v8Executor single-thread executor where v8 will be created
     *  and all debug calls will be performed by Stetho later.
     *
     * NOTE: Should be declared as V8 class extensions when will be allowed (https://youtrack.jetbrains.com/issue/KT-11968)
     */
    fun createDebuggableV8Runtime(v8Executor: ExecutorService, globalAlias: String = "global", enableLogging: Boolean = true): Future<V8> {
        LogUtils.enabled = enableLogging
        return v8Executor.submit(Callable {
            val runtime = V8.createV8Runtime(globalAlias)
            val messenger = V8Messenger(runtime)
            with(messenger) {
                // Default Chrome DevTool protocol messages
                sendMessage(Protocol.Runtime.Enable)

                sendMessage(Protocol.Debugger.Enable, JSONObject().put("maxScriptsCacheSize", 10000000))
                sendMessage(Protocol.Debugger.SetPauseOnExceptions, JSONObject().put("state", "none"))
                sendMessage(Protocol.Debugger.SetAsyncCallStackDepth, JSONObject().put("maxDepth",32))
                sendMessage(Protocol.Runtime.RunIfWaitingForDebugger)
            }

            StethoHelper.initializeWithV8Messenger(messenger, v8Executor)

            runtime
        })
    }
}