package com.alexii.j2v8debugger

import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer
import com.facebook.stetho.inspector.jsonrpc.JsonRpcResult
import com.facebook.stetho.inspector.network.NetworkPeerManager
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsMethod
import com.facebook.stetho.json.ObjectMapper
import com.alexii.j2v8debugger.utils.LogUtils
import com.alexii.j2v8debugger.utils.logger
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import com.facebook.stetho.inspector.protocol.module.Debugger as FacebookDebuggerStub

/**
 * Debugger Domain. Name of the class and methods must match names defined in Chrome Dev Tools protocol.
 */
internal class Debugger(
    private val scriptSourceProvider: ScriptSourceProvider
) : FacebookDebuggerStub() {
    var dtoMapper: ObjectMapper = ObjectMapper()

    /**
     * Needed as @ChromeDevtoolsMethod methods are called on Stetho threads, but not v8 thread.
     *
     * XXX: consider using ThreadBound from Facebook with an implementation, which uses Executor.
     */
    private var v8Executor: ExecutorService? = null
    private var v8Messenger: V8Messenger? = null
    private var connectedPeer: JsonRpcPeer? = null
    private val breakpointsAdded = mutableListOf<String>()

    fun initialize(v8Executor: ExecutorService, v8Messenger: V8Messenger) {
        this.v8Executor = v8Executor
        this.v8Messenger = v8Messenger
    }

    private fun validateV8Initialized() {
        if (v8Executor == null) {
            throw IllegalStateException("Unable to call method before v8 has been initialized")
        }
    }

    internal fun onScriptsChanged() {
        scriptSourceProvider.allScriptIds
            .map { ScriptParsedEvent(it) }
            .forEach { connectedPeer?.invokeMethod(Protocol.Debugger.ScriptParsed, it, null) }
    }

    @ChromeDevtoolsMethod
    override fun enable(peer: JsonRpcPeer, params: JSONObject?) {
        runStethoSafely {
            connectedPeer = peer

            // Notify DevTools of scripts we want to display/debug
            onScriptsChanged()

            peer.registerDisconnectReceiver(::onDisconnect)
        }
        v8Messenger?.setDebuggerConnected(true)
    }

    private fun onDisconnect() {
        logger.d(TAG, "Disconnecting from Chrome")
        runStethoSafely {
            // Remove added breakpoints
            breakpointsAdded.forEach { breakpointId ->
                v8Executor?.execute {
                    v8Messenger?.sendMessage(
                        Protocol.Debugger.RemoveBreakpoint,
                        JSONObject().put("breakpointId", breakpointId))

                }
            }
            breakpointsAdded.clear()

            NetworkPeerManager.getInstanceOrNull()?.removePeer(connectedPeer)
            connectedPeer = null

            // avoid app being freezed when no debugging happening anymore
            v8Messenger?.setDebuggerConnected(false)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @ChromeDevtoolsMethod
    fun evaluateOnCallFrame(peer: JsonRpcPeer, params: JSONObject?): JsonRpcResult? {
        val method = Protocol.Debugger.EvaluateOnCallFrame
        val result = v8Messenger?.getV8Result(method, params)
        return EvaluateOnCallFrameResult(JSONObject(result))
    }

    @Suppress("UNUSED_PARAMETER")
    @ChromeDevtoolsMethod
    fun setSkipAllPauses(peer: JsonRpcPeer, params: JSONObject?) {
        // This was changed from skipped to skip
        // https://chromium.googlesource.com/chromium/src/third_party/WebKit/Source/platform/v8_inspector/+/e7a781c04b7822a46e7de465623152ff1b45bdac%5E%21/
        v8Messenger?.sendMessage(Protocol.Debugger.SetSkipAllPauses, JSONObject().put("skip", params?.getBoolean("skipped")), true)
    }

    @ChromeDevtoolsMethod
    override fun disable(peer: JsonRpcPeer, params: JSONObject?) {
        v8Messenger?.setDebuggerConnected(false)
    }

    @Suppress("UNUSED_PARAMETER")
    @ChromeDevtoolsMethod
    fun getScriptSource(peer: JsonRpcPeer, params: JSONObject): JsonRpcResult? {
        return runStethoAndV8Safely {
            try {
                val request = dtoMapper.convertValue(params, GetScriptSourceRequest::class.java)
                val scriptSource = scriptSourceProvider.getSource(request.scriptId!!)
                GetScriptSourceResponse(scriptSource)
            } catch (e: Exception) {
                // Send exception as source code for debugging.
                // Otherwise If error is thrown - Stetho reports broken I/O pipe and disconnects
                GetScriptSourceResponse(logger.getStackTraceString(e))
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @ChromeDevtoolsMethod
    fun setBreakpointByUrl(peer: JsonRpcPeer, params: JSONObject): SetBreakpointByUrlResponse? {
        val request = dtoMapper.convertValue(params, SetBreakpointByUrlRequest::class.java)
        request.url = request.scriptId
        runStethoAndV8Safely {
            v8Executor?.execute {
                v8Messenger?.sendMessage(
                    Protocol.Debugger.SetBreakpointByUrl,
                    dtoMapper.convertValue(request, JSONObject::class.java)
                )
            }
        }
        val response = SetBreakpointByUrlResponse(request)
        // Save breakpoint to remove on disconnect
        breakpointsAdded.add(response.breakpointId)
        return response
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @ChromeDevtoolsMethod
    fun removeBreakpoint(peer: JsonRpcPeer, params: JSONObject) {
        runStethoAndV8Safely {
            v8Executor?.execute {
                v8Messenger?.sendMessage(
                    Protocol.Debugger.RemoveBreakpoint,
                    params
                )
            }
        }
        breakpointsAdded.remove(params.getString("breakpointId"))
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @ChromeDevtoolsMethod
    fun setBreakpointsActive(peer: JsonRpcPeer, params: JSONObject) {
        runStethoAndV8Safely {
            v8Executor?.execute { v8Messenger?.sendMessage(Protocol.Debugger.SetBreakpointsActive, params) }
        }
    }


    /**
     * Pass through to J2V8 methods
     */
    @Suppress("unused", "UNUSED_PARAMETER")
    @ChromeDevtoolsMethod
    fun resume(peer: JsonRpcPeer, params: JSONObject?) {
        v8Messenger?.sendMessage(Protocol.Debugger.Resume, params, true)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @ChromeDevtoolsMethod
    fun pause(peer: JsonRpcPeer, params: JSONObject?) {
        v8Messenger?.sendMessage(Protocol.Debugger.Pause, params, true)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @ChromeDevtoolsMethod
    fun stepOver(peer: JsonRpcPeer, params: JSONObject?) {
        v8Messenger?.sendMessage(Protocol.Debugger.StepOver, params, true)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @ChromeDevtoolsMethod
    fun stepInto(peer: JsonRpcPeer, params: JSONObject?) {
        v8Messenger?.sendMessage(Protocol.Debugger.StepInto, params, true)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @ChromeDevtoolsMethod
    fun stepOut(peer: JsonRpcPeer, params: JSONObject?) {
        v8Messenger?.sendMessage(Protocol.Debugger.StepOut, params, true)
    }

    /**
     *  Safe for Stetho - makes sure that no exception is thrown.
     * If any exception then [JsonRpcError] is thrown from method annotated with @ChromeDevtoolsMethod-
     * Stetho reports broken I/O pipe and Chrome DevTools disconnects.
     */
    private fun <T> runStethoSafely(action: () -> T): T? {
        LogUtils.logChromeDevToolsCalled()

        return try {
            action()
        } catch (e: Throwable) { //not Exception as V8 throws Error
            // Otherwise If error is thrown - Stetho reports broken I/O pipe and disconnects
            logger.w(TAG, "Unable to perform " + LogUtils.getChromeDevToolsMethodName(), e)
            null
        }
    }

    /**
     * Safe for Stetho - makes sure that no exception is thrown.
     * Safe for V8 - makes sure, that v8 initialized and v8 thread is not not paused in debugger.
     */
    private fun <T> runStethoAndV8Safely(action: () -> T): T? {
        return runStethoSafely {
            validateV8Initialized()

            action()
        }
    }

    companion object {
        const val TAG = "Debugger"
    }
}
