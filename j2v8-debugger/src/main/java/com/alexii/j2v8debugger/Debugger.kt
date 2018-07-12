package com.alexii.j2v8debugger

import android.util.Log
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer
import com.facebook.stetho.inspector.jsonrpc.JsonRpcResult
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsMethod
import com.facebook.stetho.json.ObjectMapper
import com.facebook.stetho.json.annotation.JsonProperty
import org.json.JSONObject
import com.facebook.stetho.inspector.protocol.module.Debugger as FacebookDebuggerStub

/**
 * V8 JS Debugger. Name of the class and methods must match names defined in Chrome Dev Tools protocol.
 */
class Debugger(private val scriptSourceProvider: ScriptSourceProvider) : FacebookDebuggerStub() {
    private val objectMapper: ObjectMapper = ObjectMapper()

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
            val request = objectMapper.convertValue(params, GetScriptSourceRequest::class.java)

            val scriptSource = scriptSourceProvider.getSource(request.scriptId!!)

            return GetScriptSourceResponse(scriptSource)
        } catch (e: Exception) {
            // Send exception as source code for debugging.
            // Otherwise If error is thrown - Stetho reports broken I/O pipe and disconnects
            return GetScriptSourceResponse(Log.getStackTraceString(e))
        }
    }

    @ChromeDevtoolsMethod
    fun resume(peer: JsonRpcPeer, params: JSONObject) {
        //TBD
    }

    @ChromeDevtoolsMethod
    fun pause(peer: JsonRpcPeer, params: JSONObject) {
        //TBD
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
    fun setBreakpoint(peer: JsonRpcPeer, params: JSONObject): JsonRpcResult? {
        //TBD
        return null
    }

    @ChromeDevtoolsMethod
    fun setBreakpointByUrl(peer: JsonRpcPeer, params: JSONObject): JsonRpcResult? {
        //TBD
        return null
    }

    @ChromeDevtoolsMethod
    fun removeBreakpoint(peer: JsonRpcPeer, params: JSONObject) {
        //TBD
    }

    class ScriptParsedEvent(
        @field:JsonProperty @JvmField
        var scriptId: String?,
        @field:JsonProperty @JvmField
        var url: String?
    )

    class GetScriptSourceRequest : JsonRpcResult {
        @field:JsonProperty @JvmField
        var scriptId: String? = null
    }

    class GetScriptSourceResponse(
        @field:JsonProperty @JvmField
        var scriptSource: String
    ) : JsonRpcResult

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
}
