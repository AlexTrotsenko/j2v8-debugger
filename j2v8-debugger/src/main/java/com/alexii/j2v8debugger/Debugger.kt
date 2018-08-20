package com.alexii.j2v8debugger

import android.support.annotation.VisibleForTesting
import com.alexii.j2v8debugger.utils.LogUtils
import com.alexii.j2v8debugger.utils.logger
import com.eclipsesource.v8.Releasable
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.V8Value
import com.eclipsesource.v8.debug.*
import com.eclipsesource.v8.debug.mirror.Frame
import com.eclipsesource.v8.debug.mirror.Scope
import com.eclipsesource.v8.debug.mirror.ValueMirror
import com.eclipsesource.v8.utils.TypeAdapter
import com.eclipsesource.v8.utils.V8ObjectUtils
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

//users of the lib can change this value
var scriptsDomain = "http://app/"

//move to separate mapper class if conversion logic become complicated and used in many places
private fun scriptIdToUrl(scriptId: String?) = scriptsDomain + scriptId
private fun urlToScriptId(url: String?) = url?.removePrefix(scriptsDomain)

/**
 * V8 JS Debugger. Name of the class and methods must match names defined in Chrome Dev Tools protocol.
 *
 * [initialize] must be called before actual debugging (adding breakpoints in Chrome DevTools).
 *  Otherwise setting breakpoint, etc. makes no effect.
 */
class Debugger(
    private val scriptSourceProvider: ScriptSourceProvider
) : FacebookDebuggerStub() {
    var dtoMapper: ObjectMapper = ObjectMapper()
        @VisibleForTesting set
        @VisibleForTesting get

    //xxx: consider using WeakReference
    /** Must be called on [v8Executor]]. */
    var v8Debugger: DebugHandler? = null
        private set
        @VisibleForTesting get

    /**
     * Executor where V8 scripts are being executed on. Used by [v8Debugger].
     * Needed as @ChromeDevtoolsMethod methods are called on Stetho threads, but not v8 thread.
     *
     * XXX: consider using ThreadBound from Facebook with an implementation, which uses Executor.
     */
    private var v8Executor: ExecutorService? = null

    private var connectedPeer: JsonRpcPeer? = null

    /** Whether Chrome DevTools is connected to the Stetho (in general) and this debugger (particularly). */
    private var isDebuggingOn = false

    companion object {
        const val TAG = "j2v0-debugger"
    }

    fun initialize(v8Debugger: DebugHandler, v8Executor: ExecutorService) {
        this.v8Debugger = v8Debugger
        this.v8Executor = v8Executor

        v8Debugger.addBreakHandler(V8ToChromeDevToolsBreakHandler(::connectedPeer));
    }

    private fun validateV8Initialized() {
        if (v8Executor == null || v8Debugger == null) {
            throw IllegalStateException("Unable to set breakpoint when v8 was not initialized yet")
        };
    }

    @ChromeDevtoolsMethod
    override fun enable(peer: JsonRpcPeer, params: JSONObject?) {
        LogUtils.logMethodCalled()
        connectedPeer = peer

        scriptSourceProvider.allScriptIds
                .map { ScriptParsedEvent(it) }
                .forEach { peer.invokeMethod("Debugger.scriptParsed", it, null) }
    }

    @ChromeDevtoolsMethod
    override fun disable(peer: JsonRpcPeer, params: JSONObject?) {
        LogUtils.logMethodCalled()

        connectedPeer = null

        //check what else is needed to be done here
    }

    @ChromeDevtoolsMethod
    fun getScriptSource(peer: JsonRpcPeer, params: JSONObject): JsonRpcResult {
        LogUtils.logMethodCalled()

        try {
            val request = dtoMapper.convertValue(params, GetScriptSourceRequest::class.java)

            val scriptSource = scriptSourceProvider.getSource(request.scriptId!!)

            return GetScriptSourceResponse(scriptSource)
        } catch (e: Exception) {
            // Send exception as source code for debugging.
            // Otherwise If error is thrown - Stetho reports broken I/O pipe and disconnects
            return GetScriptSourceResponse(logger.getStackTraceString(e))
        }
    }

    @ChromeDevtoolsMethod
    fun resume(peer: JsonRpcPeer, params: JSONObject?) {
        LogUtils.logMethodCalled()

        isDebuggingOn = false
    }

    @ChromeDevtoolsMethod
    fun pause(peer: JsonRpcPeer, params: JSONObject?) {
        LogUtils.logMethodCalled()

        isDebuggingOn = true
    }

    @ChromeDevtoolsMethod
    fun stepOver(peer: JsonRpcPeer, params: JSONObject) {
        LogUtils.logMethodCalled()

        //TBD
    }

    @ChromeDevtoolsMethod
    fun stepInto(peer: JsonRpcPeer, params: JSONObject) {
        LogUtils.logMethodCalled()

        //TBD
    }

    @ChromeDevtoolsMethod
    fun stepOut(peer: JsonRpcPeer, params: JSONObject) {
        LogUtils.logMethodCalled()
        //TBD
    }

    @ChromeDevtoolsMethod
    fun setBreakpoint(peer: JsonRpcPeer, params: JSONObject): JsonRpcResult {
        LogUtils.logMethodCalled()

        //Looks like it's not called at all.
        logger.w(TAG, "Unexpected Debugger.setBreakpoint() is called by Chrome DevTools: " + params)

        return EmptyResult()
    }

    @ChromeDevtoolsMethod
    fun setBreakpointByUrl(peer: JsonRpcPeer, params: JSONObject): JsonRpcResult? {
        /**
         * xxx: since ScriptBreakPoint does not store script id - keep track of breakpoints manually
         *  in order to avoid setting breakpoint to the same location 2nd time
         *  (if .setBreakpointByUrl() without .removeBreakpoint() is called)
         */
        LogUtils.logMethodCalled()

        try {
            validateV8Initialized()

            val responseFuture = v8Executor!!.submit(Callable {
                val request = dtoMapper.convertValue(params, SetBreakpointByUrlRequest::class.java)

                val breakpointId = v8Debugger!!.setScriptBreakpoint(request.scriptId!!, request.lineNumber!!)

                SetBreakpointByUrlResponse(breakpointId.toString(), Location(request.scriptId!!, request.lineNumber!!, request.columnNumber!!))
            })

            val response = responseFuture.get()
            return response;
        } catch (e: Exception) {
            // Otherwise If error is thrown - Stetho reports broken I/O pipe and disconnects
            logger.w(TAG, "Unable to setBreakpointByUrl: " + params, e)
            return EmptyResult()
        }
    }

    @ChromeDevtoolsMethod
    fun removeBreakpoint(peer: JsonRpcPeer, params: JSONObject) {
        /**
         * xxx: since ScriptBreakPoint does not store script id - keep track of breakpoints manually
         *  in order to avoid exception caused by removing breakpoint, which is was set
         *  (if .setBreakpointByUrl() was not called .removeBreakpoint())
         */

        LogUtils.logMethodCalled()

        try {
            validateV8Initialized()

            val request = dtoMapper.convertValue(params, RemoveBreakpointRequest::class.java)
            val res = v8Executor!!.submit { v8Debugger!!.clearBreakPoint(request.breakpointId!!.toInt()) }
            //get exceptions if any
            res.get()

        } catch (e: Exception) {
            // Otherwise If error is thrown - Stetho reports broken I/O pipe and disconnects
            logger.w(TAG, "Unable to removeBreakpoint: " + params, e)
        }
    }

    /**
     * Fired as the result of [Debugger.enable]
     */
    class ScriptParsedEvent(
        @field:JsonProperty @JvmField
        val scriptId: String?,

        @field:JsonProperty @JvmField
        val url: String? = scriptIdToUrl(scriptId)
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

        val scriptId get() = urlToScriptId(url)
    }


    class SetBreakpointByUrlResponse(
        @field:JsonProperty @JvmField
        val breakpointId: String,

        location: Location
    ) : JsonRpcResult {
        @field:JsonProperty @JvmField
        val locations: List<Location> = listOf(location)
    }

    class RemoveBreakpointRequest : JsonRpcResult {
        //script id
        @field:JsonProperty @JvmField
        var breakpointId: String? = null
    }


    /**
     * Fired as the result of matching breakpoint in V8, which is was previously set by [Debugger.setBreakpointByUrl]
     */
    data class PausedEvent @JvmOverloads constructor(
        @field:JsonProperty @JvmField
        val callFrames: List<CallFrame>,

        @field:JsonProperty @JvmField
        val reason: String = "other"
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

    data class Location(
        @field:JsonProperty @JvmField
        val scriptId: String,

        @field:JsonProperty @JvmField
        val lineNumber: Int,

        @field:JsonProperty @JvmField
        val columnNumber: Int
    )

    data class CallFrame @JvmOverloads constructor(
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

    data class Scope(
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

private class V8ToChromeDevToolsBreakHandler(private val currentPeerProvider: () -> JsonRpcPeer?) : BreakHandler {
    override fun onBreak(event: DebugHandler.DebugEvent?, state: ExecutionState?, eventData: EventData?, data: V8Object?) {
        //XXX: optionally consider adding logging or throwing exceptions
        if (event != DebugHandler.DebugEvent.Break) return
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

                        //j2v8 has api to access only local variables. Scope class has no get-, but only .setVariableValue() method
                        val knowVariables = frame.getKnownVariables()

                        //todo: release objects by id on Resume when https://github.com/facebook/stetho/pull/614 is implemented.
                        //When debugger disconnects Runtime's session with stored object will be GC as well.
                        val storedVariablesId = Runtime.mapObject(currentPeerProvider(), knowVariables)

                        //consider using like Runtime.Session.objectForRemote()
                        val remoteObject = RemoteObject()
                                //check and use Runtime class here
                                .apply { objectId = storedVariablesId.toString() }
                                .apply { type = Runtime.ObjectType.OBJECT }
                                .apply { className = "Object" }
                                .apply { description = "Object" }

                        val scopeName = Scope.ScopeType.Local.name.toLowerCase(Locale.ENGLISH)
                        val syntheticScope = Debugger.Scope(scopeName, remoteObject)

                        val callFrame = Debugger.CallFrame(it.toString(), frame.function.name, location, scriptIdToUrl(scriptId), listOf(syntheticScope))

                        //clean-up v8 native resources
                        frame.release()
                        //xxx: check if Mirror-s need to released (e.g. frame.function)

                        callFrame
                    }

            val pausedEvent = Debugger.PausedEvent(frames)

            logger.w(Debugger.TAG, "Sending Debugger.paused: $pausedEvent")

            networkPeerManager.sendNotificationToPeers("Debugger.paused", pausedEvent)

        } catch (e: Throwable) { //v8 throws Error instead of Exception on wrong thread access, etc.
            logger.w(Debugger.TAG, "Unable to forward break event to Chrome DevTools at ${eventData.sourceLine}, source: ${eventData.sourceLineText}")
        }
    }

    /**
     * @return local variables and function arguments if any.
     */
    private fun Frame.getKnownVariables(): Map<String, Any?> {
        val args = (0 until argumentCount).associateBy({ getArgumentName(it) }, { getArgumentValue(it).toJavaObject() })
        val localJsVars = (0 until localCount).associateBy({ getLocalName(it) }, { getLocalValue(it).toJavaObject() })

        return args + localJsVars;
    }

    private fun ValueMirror.toJavaObject(): Any? {
        val v8Object = getValue()

        //xxx consider to provide the way to override adapter by user of the lib.
        val javaObject = V8ObjectUtils.getValue(v8Object) { type, value ->
            when (type) {
                V8Value.V8_FUNCTION -> value.toString() // override default skipping of functions
                V8Value.UNDEFINED -> value.toString() // return "undefined" instead of V8Object.Undefined()
                else -> TypeAdapter.DEFAULT
            }
        }

        if (v8Object is Releasable) v8Object.release()
        //check if mirror need to released
        this.release()

        return javaObject
    }
}
