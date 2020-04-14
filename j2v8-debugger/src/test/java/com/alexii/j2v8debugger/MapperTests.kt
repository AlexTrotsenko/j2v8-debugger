package com.alexii.j2v8debugger

import com.facebook.stetho.json.ObjectMapper
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.Random

class MapperTests {
    @Test
    fun `ScriptIds are replaced`(){
        val v8ScriptMap = mutableMapOf<String, String>()
        val v8ScriptId = Random().nextInt(100).toString()
        val chromeScriptId = UUID.randomUUID().toString().substring(0,5)
        v8ScriptMap[v8ScriptId] = chromeScriptId
        val regex = "\"scriptId\":\"(\\d+)\"".toRegex()
        val incomingParams = """{"method":"Debugger.paused","params":{"callFrames":[{"callFrameId":"{\"ordinal\":0,\"injectedScriptId\":1}","functionName":"","functionLocation":{"scriptId":"$v8ScriptId","lineNumber":0,"columnNumber":0},"location":{"scriptId":"$v8ScriptId","lineNumber":0,"columnNumber":15},"url":"$chromeScriptId","scopeChain":[{"type":"global","object":{"type":"object","className":"global","description":"global","objectId":"{\"injectedScriptId\":1,\"id\":1}"}}],"this":{"type":"object","className":"global","description":"global","objectId":"{\"injectedScriptId\":1,\"id\":2}"}}],"reason":"","hitBreakpoints":[]}}"""
        val updatedScript = incomingParams.toString().replace(regex) {
            "\"scriptId\":\"${v8ScriptMap[it.groups[1]?.value]}\""
        }
        val result = """{"method":"Debugger.paused","params":{"callFrames":[{"callFrameId":"{\"ordinal\":0,\"injectedScriptId\":1}","functionName":"","functionLocation":{"scriptId":"$chromeScriptId","lineNumber":0,"columnNumber":0},"location":{"scriptId":"$chromeScriptId","lineNumber":0,"columnNumber":15},"url":"$chromeScriptId","scopeChain":[{"type":"global","object":{"type":"object","className":"global","description":"global","objectId":"{\"injectedScriptId\":1,\"id\":1}"}}],"this":{"type":"object","className":"global","description":"global","objectId":"{\"injectedScriptId\":1,\"id\":2}"}}],"reason":"","hitBreakpoints":[]}}"""

        assertEquals(updatedScript, result)
    }
    @Test

    fun `setBreakpointByUrl gets scriptId from url`(){
        val dtoMapper= ObjectMapper()
        val prefix = Random().nextInt(100).toString()
        StethoHelper.scriptsPathPrefix = prefix
        val url = UUID.randomUUID().toString()

        val params = JSONObject("""{"lineNumber":6,"url":"http:\/\/app\/\/$prefix\/$url","columnNumber":0,"condition":""}""")
        val request = dtoMapper.convertValue(params, SetBreakpointByUrlRequest::class.java)
        assertEquals(request.scriptId, url)
    }
}