package com.alexii.j2v8debugger

import com.facebook.stetho.json.ObjectMapper
import com.google.common.util.concurrent.MoreExecutors
import io.mockk.Runs
import io.mockk.called
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

class DebuggerTest {

    @Test
    fun `on enable all scripts retrieved`() {
        val scriptSourceProviderMock = mockk<ScriptSourceProvider> (relaxed = true)
        val debugger = Debugger(scriptSourceProviderMock)

        debugger.enable(mockk(relaxed = true), null)

        verify(exactly = 1) {
            scriptSourceProviderMock.allScriptIds
        }
    }

    //todo test all @ChromeDevtoolsMethod

    @Test
    fun `works when V8 initialized`() {
        val directExecutor = MoreExecutors.newDirectExecutorService()

        val v8Messenger = mockk<V8Messenger>(relaxed = true)
        val debugger = Debugger(mockk(relaxed = true))
        debugger.initialize(directExecutor, v8Messenger)

        val requestStub = SetBreakpointByUrlRequest()
        requestStub.url = "testUrl"
        requestStub.lineNumber = Random(100).nextInt()
        requestStub.columnNumber = Random(100).nextInt()

        val jsonParams = JSONObject()
        val jsonMappedResult = JSONObject()
        val mapperMock = mockk<ObjectMapper>(relaxed = true) {
            every { convertValue(jsonParams, eq(SetBreakpointByUrlRequest::class.java)) } returns  requestStub
            every { convertValue(requestStub, eq(JSONObject::class.java)) } returns jsonMappedResult
        }
        debugger.dtoMapper = mapperMock

        val response = debugger.setBreakpointByUrl(mockk(), jsonParams)

        verify (exactly = 1){mapperMock.convertValue(eq(jsonParams), eq(requestStub::class.java))}

        verify { v8Messenger.sendMessage(message = Protocol.Debugger.SetBreakpointByUrl, params = jsonMappedResult, runOnlyWhenPaused = any()) }

        assertTrue(response is SetBreakpointByUrlResponse)
        val responseLocation: Location = (response as SetBreakpointByUrlResponse).locations[0]

        assertEquals(requestStub.scriptId, responseLocation.scriptId)
        val lineNumber = requestStub.lineNumber
        val columnNumber = requestStub.columnNumber
        assertEquals(lineNumber, responseLocation.lineNumber)
        assertEquals(columnNumber, responseLocation.columnNumber)
    }

    @Test
    fun `No exceptions thrown when V8 not initialized`() {
        val debugger = Debugger(mockk())


        val requestMock = mockk<SetBreakpointByUrlRequest>()
        val jsonParamsMock = mockk<JSONObject>()
        val mapperMock = mockk<ObjectMapper> {
            every { convertValue(eq(jsonParamsMock), eq(requestMock::class.java)) } returns requestMock
        }
        debugger.dtoMapper = mapperMock

        val response = debugger.setBreakpointByUrl(mockk(), jsonParamsMock)

        verify {mapperMock wasNot called}
        verify {requestMock wasNot called}
        verify {jsonParamsMock wasNot called}

        assertTrue(response == null)
    }

    @Test
    fun `evaluateOnCallFrame gets V8Result`(){
        val v8Messenger = mockk<V8Messenger>(relaxed = true)
        val debugger = Debugger(mockk())
        debugger.initialize(mockk(), v8Messenger)
        val jsonParamsMock = mockk<JSONObject>()
        val jsonResult = JSONObject().put(UUID.randomUUID().toString(), UUID.randomUUID().toString())

        every {
            v8Messenger.getV8Result(Protocol.Debugger.EvaluateOnCallFrame, jsonParamsMock)
        }.returns(jsonResult.toString())


        val response = debugger.evaluateOnCallFrame(mockk(), jsonParamsMock)

        assertEquals((response as EvaluateOnCallFrameResult).result.toString(), jsonResult.toString())
    }

    @Test
    fun `setSkipAllPauses replaces skip with skipped`(){
        val v8Messenger = mockk<V8Messenger>(relaxed = true)
        val debugger = Debugger(mockk())
        debugger.initialize(mockk(), v8Messenger)
        val jsonResult = slot<JSONObject>()
        every {
            v8Messenger.sendMessage(message = Protocol.Debugger.SetSkipAllPauses, params = capture(jsonResult), runOnlyWhenPaused = any())
        } just Runs

        debugger.setSkipAllPauses(mockk(), JSONObject().put("skipped", true))

        assertEquals(jsonResult.captured.getBoolean("skip"), true)
    }

    @Test
    fun `getScriptSource returns result from scriptSourceProvider`(){
        val directExecutor = MoreExecutors.newDirectExecutorService()
        val scriptId = UUID.randomUUID().toString()
        val scriptSourceProvider = mockk<ScriptSourceProvider>()
        val v8Messenger = mockk<V8Messenger>(relaxed = true)
        val debugger = Debugger(scriptSourceProvider)
        debugger.initialize(directExecutor, v8Messenger)
        val requestJson = JSONObject().put("scriptId", scriptId)
        val scriptResponse = UUID.randomUUID().toString()

        every{
            scriptSourceProvider.getSource(scriptId)
        }.returns(scriptResponse)

        val result = debugger.getScriptSource(mockk(), requestJson)

        assertEquals((result as GetScriptSourceResponse).scriptSource, scriptResponse)
    }
}