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
import com.facebook.stetho.inspector.network.NetworkPeerManager
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsMethod
import com.facebook.stetho.inspector.protocol.module.Runtime
import com.facebook.stetho.inspector.protocol.module.Runtime.RemoteObject
import com.facebook.stetho.json.ObjectMapper
import com.facebook.stetho.json.annotation.JsonProperty
import org.json.JSONObject
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
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
 *  Above is also true for the case when debugger is paused due to pause/resume implementation as thread suspension.
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

    //xxx: consider encapsulate BreakHandler and DebugHandler into the separate class
    private val v8ToChromeBreakHandler = V8ToChromeDevToolsBreakHandler(::connectedPeer)

    /**
     * Executor where V8 scripts are being executed on. Used by [v8Debugger].
     * Needed as @ChromeDevtoolsMethod methods are called on Stetho threads, but not v8 thread.
     *
     * XXX: consider using ThreadBound from Facebook with an implementation, which uses Executor.
     */
    private var v8Executor: ExecutorService? = null

    private var connectedPeer: JsonRpcPeer? = null

    companion object {
        const val TAG = "j2v0-debugger"
    }

    fun initialize(v8Debugger: DebugHandler, v8Executor: ExecutorService) {
        this.v8Debugger = v8Debugger
        this.v8Executor = v8Executor

        v8Debugger.addBreakHandler(v8ToChromeBreakHandler)
    }

    private fun validateV8Initialized() {
        if (v8Executor == null || v8Debugger == null) {
            throw IllegalStateException("Unable to set breakpoint when v8 was not initialized yet")
        }
    }

    @ChromeDevtoolsMethod
    override fun enable(peer: JsonRpcPeer, params: JSONObject?) {
        runStethoSafely {
            connectedPeer = peer

            scriptSourceProvider.allScriptIds
                    .map { ScriptParsedEvent(it) }
                    .forEach { peer.invokeMethod("Debugger.scriptParsed", it, null) }
        }
    }

    //todo: figure-out why it's not called when Chrome DevTools is closed
    @ChromeDevtoolsMethod
    override fun disable(peer: JsonRpcPeer, params: JSONObject?) {
        runStethoSafely {
            connectedPeer = null
            //avoid app being freezed when no debugging happening anymore
            v8ToChromeBreakHandler.resume()
            //xxx:  remove breakpoints instead of disabling them
            v8Executor!!.execute { v8Debugger?.disableAllBreakPoints() }


            //xxx: check if something else is needed to be done here
        }
    }

    @ChromeDevtoolsMethod
    fun getScriptSource(peer: JsonRpcPeer, params: JSONObject): JsonRpcResult? {
        return runStethoSafely {
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

    @ChromeDevtoolsMethod
    fun resume(peer: JsonRpcPeer, params: JSONObject?) {
        runStethoSafely { v8ToChromeBreakHandler.resume() }
    }

    @ChromeDevtoolsMethod
    fun pause(peer: JsonRpcPeer, params: JSONObject?) {
        LogUtils.logChromeDevToolsCalled()

        //check what's needed here
    }

    @ChromeDevtoolsMethod
    fun stepOver(peer: JsonRpcPeer, params: JSONObject?) {
        runStethoSafely { v8ToChromeBreakHandler.stepOver() }
    }

    @ChromeDevtoolsMethod
    fun stepInto(peer: JsonRpcPeer, params: JSONObject?) {
        runStethoSafely { v8ToChromeBreakHandler.stepInto() }
    }

    @ChromeDevtoolsMethod
    fun stepOut(peer: JsonRpcPeer, params: JSONObject?) {
        runStethoSafely { v8ToChromeBreakHandler.stepOut() }
    }

    @ChromeDevtoolsMethod
    fun setBreakpoint(peer: JsonRpcPeer?, params: JSONObject?): JsonRpcResult?? {
        //Looks like this method should not be called at all.
        val action: () -> JsonRpcResult? = {
            throw IllegalArgumentException("Unexpected Debugger.setBreakpoint() is called by Chrome DevTools: " + params)
        }
        return runStethoSafely(action)
    }

    @ChromeDevtoolsMethod
    fun setBreakpointByUrl(peer: JsonRpcPeer, params: JSONObject): JsonRpcResult?? {
        return runStethoAndV8Safely {
            /**
             * xxx: since ScriptBreakPoint does not store script id - keep track of breakpoints manually
             *  in order to avoid setting breakpoint to the same location 2nd time
             *  (if .setBreakpointByUrl() without .removeBreakpoint() is called)
             */
            val responseFuture = v8Executor!!.submit(Callable {
                val request = dtoMapper.convertValue(params, SetBreakpointByUrlRequest::class.java)

                val breakpointId = v8Debugger!!.setScriptBreakpoint(request.scriptId!!, request.lineNumber!!)

                SetBreakpointByUrlResponse(breakpointId.toString(), Location(request.scriptId!!, request.lineNumber!!, request.columnNumber!!))
            })

            responseFuture.get()
        }
    }

    @ChromeDevtoolsMethod
    fun removeBreakpoint(peer: JsonRpcPeer, params: JSONObject) {
        //Chrome DevTools are removing breakpoint from UI regardless of the response (unlike setting breakpoint):
        // -> do best effort to remove breakpoint when executor is free
        runStethoSafely {
            val request = dtoMapper.convertValue(params, RemoveBreakpointRequest::class.java)
            v8Executor!!.execute { v8Debugger!!.clearBreakPoint(request.breakpointId!!.toInt()) }
        }
    }

    /**
     *  Safe for Stetho - makes sure that no exception is thrown.
     *  Safe for V8 - makes sure, that v8 initialized and v8 thread is not not paused in debugger.
     */
    private fun <T> runStethoSafely(action: () -> T): T? {
        LogUtils.logChromeDevToolsCalled()

        try {
            return action()
        } catch (e: Throwable) { //not Exception as V8 throws Error
            // Otherwise If error is thrown - Stetho reports broken I/O pipe and disconnects
            logger.w(TAG, "Unable to perform " + LogUtils.getChromeDevToolsMethodName(), e)

            return null
        }
    }

    /**
     * Safe for Stetho - makes sure that no exception is thrown.
     * If any exception then [JsonRpcError] is thrown from method annotated with @ChromeDevtoolsMethod-
     * Stetho reports broken I/O pipe and Chrome DevTools disconnects.
     */
    private fun <T> runStethoAndV8Safely(action: () -> T): T? {
        return runStethoSafely {
            validateV8Initialized()
            valideV8NotSuspended()

            action()
        }
    }

    private fun valideV8NotSuspended() {
        if (v8ToChromeBreakHandler.suspended) {
            throw IllegalStateException("Can't peform ${LogUtils.getChromeDevToolsMethodName()} while paused in debugger.")
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
    //todo: replace with proper v8's debugger .pause() api if any exists: https://github.com/eclipsesource/J2V8/issues/411
    //xxx: replace with java.util.concurrent.Phaser when min supported api will be 21
    private var debuggingLatch = CountDownLatch(1)
    var suspended = false
        private set

    private var nextDebugAction: StepAction? = null

    /**
     * Called on V8 thread and suspends it if breakpoint is hit. [resume] chould be called to restore V8 executioin.
     */
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

            pause()

            nextDebugAction?.let { state.prepareStep(it) }

        } catch (e: Throwable) { //v8 throws Error instead of Exception on wrong thread access, etc.
            logger.w(Debugger.TAG, "Unable to forward break event to Chrome DevTools at ${eventData.sourceLine}, source: ${eventData.sourceLineText}")
        }
    }

    /**
     * Pauses V8 execution. Called from V8 thread.
     */
    private fun pause() {
        suspended = true
        debuggingLatch.await()
        suspended = false
    }

    /**
     * Resumes V8 execution. Called from Stetho thread.
     */
    fun resume() {
        resumeWith(null)
    }

    fun stepOver() {
        resumeWith(StepAction.STEP_NEXT)
    }

    fun stepInto() {
        resumeWith(StepAction.STEP_IN)
    }

    fun stepOut() {
        resumeWith(StepAction.STEP_OUT)
    }

    private fun resumeWith(nextDebugAction: StepAction?) {
        val currentLatch = debuggingLatch

        //initialize new latch, which will be used by break handler on next break event.
        debuggingLatch = CountDownLatch(1)

        //release suspended break handler if any
        currentLatch.countDown()

        this.nextDebugAction = nextDebugAction
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
