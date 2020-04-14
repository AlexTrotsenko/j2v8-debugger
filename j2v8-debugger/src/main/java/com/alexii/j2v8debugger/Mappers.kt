package com.alexii.j2v8debugger

import com.facebook.stetho.inspector.jsonrpc.JsonRpcResult
import com.facebook.stetho.json.annotation.JsonProperty
import org.json.JSONObject

internal class EvaluateOnCallFrameResult(
        @field:JsonProperty
        @JvmField
        val result: JSONObject? = null
) : JsonRpcResult

/**
 * Fired as the result of [Debugger.enable]
 */
internal class ScriptParsedEvent(
        @field:JsonProperty @JvmField
        val scriptId: String,

        @field:JsonProperty @JvmField
        val url: String = scriptIdToUrl(scriptId)
)

internal class ScriptParsedEventRequest : JsonRpcResult {
    @field:JsonProperty
    @JvmField
    var scriptId: String = ""

    @field:JsonProperty
    @JvmField
    var url: String = ""
}

internal class GetScriptSourceRequest : JsonRpcResult {
    @field:JsonProperty
    @JvmField
    var scriptId: String? = null
}

internal class GetScriptSourceResponse(
        @field:JsonProperty @JvmField
        val scriptSource: String
) : JsonRpcResult

internal class SetBreakpointByUrlRequest : JsonRpcResult {
    @field:JsonProperty
    @JvmField
    var url: String? = null

    @field:JsonProperty
    @JvmField
    var lineNumber: Int? = null

    //unused for now
    @field:JsonProperty
    @JvmField
    var columnNumber: Int? = null

    //unused for now
    @field:JsonProperty
    @JvmField
    var condition: String? = null

    val scriptId get() = urlToScriptId(url)
}

internal class SetBreakpointByUrlResponse(
        request: SetBreakpointByUrlRequest) : JsonRpcResult {
    @field:JsonProperty
    @JvmField
    val breakpointId = "1:${request.lineNumber}:${request.columnNumber}:${request.scriptId}"

    @field:JsonProperty
    @JvmField
    val locations: List<Location> = listOf(Location(request.scriptId!!, request.lineNumber!!, request.columnNumber!!))
}

internal data class Location(
        @field:JsonProperty @JvmField
        val scriptId: String,

        @field:JsonProperty @JvmField
        val lineNumber: Int,

        @field:JsonProperty @JvmField
        val columnNumber: Int
)

internal class LocationResponse {
    @field:JsonProperty
    @JvmField
    var scriptId: String? = null

    @field:JsonProperty
    @JvmField
    var lineNumber: Int? = null

    @field:JsonProperty
    @JvmField
    var columnNumber: Int? = null
}

/**
 * Any message from J2V8
 * If it contains an id, it's a response from a request sent
 * Otherwise it's an event
 */
internal class V8Response : JsonRpcResult {
    val isResponse by lazy { (id != null) }

    @field:JsonProperty
    @JvmField
    var id: Int? = null

    @field:JsonProperty
    @JvmField
    var method: String? = null

    @field:JsonProperty
    @JvmField
    var result: JSONObject? = null

    @field:JsonProperty
    @JvmField
    var params: JSONObject? = null
}

internal class BreakpointResolvedEvent : JsonRpcResult {
    @field:JsonProperty
    @JvmField
    var breakpointId: String? = null

    @field:JsonProperty
    @JvmField
    var location: LocationResponse? = null
}

internal class GetPropertiesResult : JSONObject(), JsonRpcResult

/**
 * Replace any incoming scriptIds with scriptIds in map
 * This converts scriptId used in J2V8 with ones used in ScriptSourceProvider
 */
internal fun replaceScriptId(from: JSONObject, scriptMap: Map<String, String>): JSONObject{
    val regex = "\"scriptId\":\"(\\d+)\"".toRegex()
    val to = from.toString().replace(regex) {
        "\"scriptId\":\"${scriptMap[it.groups[1]?.value]}\""
    }
    return JSONObject(to)
}

//users of the lib can change this value
private val scriptsDomain = "http://app/"
private val scriptsUrlBase get() = scriptsDomain + StethoHelper.scriptsPathPrefix

private fun scriptIdToUrl(scriptId: String?) = scriptsUrlBase + scriptId
private fun urlToScriptId(url: String?) = url?.removePrefix(scriptsUrlBase)
