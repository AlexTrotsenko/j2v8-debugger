package com.alexii.j2v8debugger

import android.util.Log
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.debug.*
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer
import com.facebook.stetho.inspector.jsonrpc.JsonRpcResult
import com.facebook.stetho.inspector.jsonrpc.protocol.EmptyResult
import com.facebook.stetho.inspector.network.NetworkPeerManager
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsMethod
import com.facebook.stetho.inspector.protocol.module.Runtime
import com.facebook.stetho.inspector.protocol.module.Runtime.RemoteObject
import com.facebook.stetho.json.ObjectMapper
import com.facebook.stetho.json.annotation.JsonProperty
import org.json.JSONObject
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import com.facebook.stetho.inspector.protocol.module.Debugger as FacebookDebuggerStub

/**
 * V8 JS Debugger. Name of the class and methods must match names defined in Chrome Dev Tools protocol.
 *
 * [initialize] must be called before actual debugging (adding breakpoints in Chrome DevTools).
 */
class Debugger(
    private val scriptSourceProvider: ScriptSourceProvider
) : FacebookDebuggerStub() {
    private val dtoMapper: ObjectMapper = ObjectMapper()
    //xxx: consider using WeakReference
    /** Must be called on [v8Executor]]. */
    private lateinit var v8Debugger: DebugHandler
    /**
     * Executor where V8 scripts are being executed on. Used by [v8Debugger].
     * Needed as @ChromeDevtoolsMethod methods are called on Stetho threads, but not v8 thread.
     */
    private lateinit var v8Executor: ExecutorService
    /** Whether Chrome DevTools is connected to the Stetho (in general) and this debugger (particularly). */
    private var isDebuggingOn = false

    companion object {
        const val TAG = "j2v0-debugger"
    }

    @ChromeDevtoolsMethod
    override fun enable(peer: JsonRpcPeer, params: JSONObject?) {
        scriptSourceProvider.allScriptIds
                .map { ScriptParsedEvent(it, it) }
                .forEach { peer.invokeMethod("Debugger.scriptParsed", it, null) }
    }

    @ChromeDevtoolsMethod
    override fun disable(peer: JsonRpcPeer, params: JSONObject?) {
        //check what's needed to be done here
    }

    @ChromeDevtoolsMethod
    fun getScriptSource(peer: JsonRpcPeer, params: JSONObject): JsonRpcResult {
        try {
            val request = dtoMapper.convertValue(params, GetScriptSourceRequest::class.java)

            val scriptSource = scriptSourceProvider.getSource(request.scriptId!!)

            return GetScriptSourceResponse(scriptSource)
        } catch (e: Exception) {
            // Send exception as source code for debugging.
            // Otherwise If error is thrown - Stetho reports broken I/O pipe and disconnects
            return GetScriptSourceResponse(Log.getStackTraceString(e))
        }
    }

    fun initialize(v8Debugger: DebugHandler, v8Executor: ExecutorService) {
        this.v8Debugger = v8Debugger
        this.v8Executor = v8Executor

        v8Debugger.addBreakHandler(V8ToChromeDevToolsBreakHandler());
    }

    @ChromeDevtoolsMethod
    fun resume(peer: JsonRpcPeer, params: JSONObject) {
        isDebuggingOn = false
    }

    @ChromeDevtoolsMethod
    fun pause(peer: JsonRpcPeer, params: JSONObject) {
        isDebuggingOn = true
    }

    @ChromeDevtoolsMethod
    fun stepOver(peer: JsonRpcPeer, params: JSONObject) {
        //TBD
    }

    @ChromeDevtoolsMethod
    fun stepInto(peer: JsonRpcPeer, params: JSONObject) {
        //TBD
    }

    @ChromeDevtoolsMethod
    fun stepOut(peer: JsonRpcPeer, params: JSONObject) {
        //TBD
    }

    @ChromeDevtoolsMethod
    fun setBreakpoint(peer: JsonRpcPeer, params: JSONObject): JsonRpcResult {
        //Looks like it's not called at all.
        Log.w(TAG, "Unexpected Debugger.setBreakpoint() is called by Chrome DevTools: " + params)

        return EmptyResult()
    }

    @ChromeDevtoolsMethod
    fun setBreakpointByUrl(peer: JsonRpcPeer, params: JSONObject): JsonRpcResult? {
        val responseFuture = v8Executor.submit(Callable {
            try {
                val request = dtoMapper.convertValue(params, SetBreakpointByUrlRequest::class.java)

                val breakpointId = v8Debugger.setScriptBreakpoint(request.url!!, request.lineNumber!!)

                SetBreakpointByUrlResponse(breakpointId.toString(), Location(request.url!!, request.lineNumber!!, request.columnNumber!!))
            } catch (e: Exception) {
                // Otherwise If error is thrown - Stetho reports broken I/O pipe and disconnects
                Log.w(TAG, "Unable to setBreakpointByUrl: " + params, e)
                null
            }
        })

        val response = responseFuture.get()
        return response;
    }

    @ChromeDevtoolsMethod
    fun removeBreakpoint(peer: JsonRpcPeer, params: JSONObject) {
        //TBD
    }

    /**
     * Fired as the result of [Debugger.enable]
     */
    class ScriptParsedEvent(
        @field:JsonProperty @JvmField
        val scriptId: String?,

        @field:JsonProperty @JvmField
        val url: String?
    )

    class GetScriptSourceRequest : JsonRpcResult {
        @field:JsonProperty @JvmField
        var scriptId: String? = null
    }

    class GetScriptSourceResponse(
        @field:JsonProperty @JvmField
        val scriptSource: String
    ) : JsonRpcResult

    class SetBreakpointByUrlRequest : JsonRpcResult {
        //script id
        @field:JsonProperty @JvmField
        var url: String? = null

        @field:JsonProperty @JvmField
        var lineNumber: Int? = null

        //unused for now
        @field:JsonProperty @JvmField
        var columnNumber: Int? = null

        //unused for now
        @field:JsonProperty @JvmField
        var condition: String? = null
    }


    class SetBreakpointByUrlResponse(
        @field:JsonProperty @JvmField
        val breakpointId: String,

        location: Location
    ) : JsonRpcResult {
        @field:JsonProperty @JvmField
        val locations: List<Location> = listOf(location)
    }

    /**
     * Fired as the result of matching breakpoint in V8, which is was previously set by [Debugger.setBreakpointByUrl]
     */
    class PausedEvent @JvmOverloads constructor(
            @field:JsonProperty @JvmField
            val callFrames: List<CallFrame>,

            //xxx: check what's exactly needed here
            @field:JsonProperty @JvmField
            val reason: String = "ambiguous"
    )

    //Not yet implemented method (check if it's required) :
    //Debugger:
    // .continueToLocation
    // .evaluateOnCallFrame
    // .getPossibleBreakpoints
    // .restartFrame
    // .searchInContent
    // .setAsyncCallStackDepth
    // .setBreakpointsActive
    // .setPauseOnExceptions
    // .setScriptSource
    // .setSkipAllPauses
    // .setVariableValue

    class Location(
        @field:JsonProperty @JvmField
        val scriptId: String,

        @field:JsonProperty @JvmField
        val lineNumbe: Int,

        @field:JsonProperty @JvmField
        val columnNumber: Int
    )

    class CallFrame @JvmOverloads constructor(
        @field:JsonProperty @JvmField
        val callFrameId: String,

        @field:JsonProperty @JvmField
        val functionName: String,

        @field:JsonProperty @JvmField
        val location: Location,

        /** JavaScript script name or url. */
        @field:JsonProperty @JvmField
        val url: String,

        @field:JsonProperty @JvmField
        val scopeChain: List<Scope>,

        //xxx: check how and whether it's wotking with this
        @field:JsonProperty @JvmField
        val `this`: RemoteObject? = null
    )

    class Scope(
        /** one of: global, local, with, closure, catch, block, script, eval, module. */
        @field:JsonProperty @JvmField
        val type: String,
        /**
         * Object representing the scope.
         * For global and with scopes it represents the actual object;
         * for the rest of the scopes, it is artificial transient object enumerating scope variables as its properties.
         */
        @field:JsonProperty @JvmField
        val `object`: RemoteObject
    )
}

private class V8ToChromeDevToolsBreakHandler : BreakHandler {
    override fun onBreak(type: DebugHandler.DebugEvent?, state: ExecutionState?, eventData: EventData?, data: V8Object?) {
        //XXX: optionally consider adding logging or throwing exceptions
        if (type != DebugHandler.DebugEvent.Break) return
        if (eventData == null) return
        if (eventData !is BreakEvent) return


        val networkPeerManager = NetworkPeerManager.getInstanceOrNull()

        //should be intialized in Network at Stetho initialization
        if (networkPeerManager == null) return

        if (state == null) return

        try {
            val frames = (0 until state.frameCount)
                    .map {
                        val frame = state.getFrame(it)
                        val scriptId = frame.sourceLocation.scriptName

                        val location = Debugger.Location(scriptId, eventData.sourceLine, eventData.sourceColumn)

                        val scopes = (0 until frame.scopeCount).map {
                            val scope = frame.getScope(it)
                            val scopeTypeString = scope.type.name.toLowerCase(Locale.ENGLISH)

                            //consider using like Runtime.Session.objectForRemote()
                            val remoteObject = RemoteObject()
                            //xxx: check what's exactly needed here, pick UNDEFINED or OBJECT for now
                            remoteObject.type = Runtime.ObjectType.UNDEFINED

                            Debugger.Scope(scopeTypeString, remoteObject)
                        }

                        val callFrame = Debugger.CallFrame(it.toString(), frame.function.name, location, scriptId, scopes)
                        callFrame
                    }

            networkPeerManager.sendNotificationToPeers("Debugger.paused", Debugger.PausedEvent(frames))

        } catch (e: Throwable) { //v8 throws Error instead of Exception on wrong thread access, etc.
            Log.w(Debugger.TAG, "Unable to forward break event to Chrome DevTools at ${eventData.sourceLine}, source: ${eventData.sourceLineText}")
        }
    }
}
