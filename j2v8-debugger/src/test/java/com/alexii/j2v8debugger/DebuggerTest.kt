package com.alexii.j2v8debugger

import com.alexii.j2v8debugger.utils.logger
import com.eclipsesource.v8.debug.DebugHandler
import com.facebook.stetho.json.ObjectMapper
import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockito_kotlin.*
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class DebuggerTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            logger = mock()
        }
    }

    @Test
    fun `on enable all scripts retrieved`() {
        val scriptSourceProviderMock = mock<ScriptSourceProvider> {}
        val debugger = Debugger(scriptSourceProviderMock)

        debugger.enable(mock(), null)

        verify(scriptSourceProviderMock, times(1)).allScriptIds
        verifyNoMoreInteractions(scriptSourceProviderMock)
    }

    //todo test all @ChromeDevtoolsMethod

    @Test
    fun `works when V8 initialized`() {
        val v8DebugHandlerMock = mock<DebugHandler>()
        val scriptSourceProviderMock = mock<ScriptSourceProvider> {}
        val directExecutor = MoreExecutors.newDirectExecutorService()

        val debugger = Debugger(scriptSourceProviderMock)
        debugger.initialize(v8DebugHandlerMock, directExecutor)

        verify(v8DebugHandlerMock).addBreakHandler(any())


        val requestStub = Debugger.SetBreakpointByUrlRequest()
        requestStub.url = "testUrl"
        requestStub.lineNumber = 0;
        requestStub.columnNumber = 0

        val jsonParamsMock = mock<JSONObject>()
        val mapperMock = mock<ObjectMapper> {
            on { convertValue(eq(jsonParamsMock), eq(requestStub::class.java)) } doReturn requestStub
        }
        debugger.dtoMapper = mapperMock

        val response = debugger.setBreakpointByUrl(mock(), jsonParamsMock)

        verify(mapperMock, times(1)).convertValue(eq(jsonParamsMock), eq(requestStub::class.java))
        verifyNoMoreInteractions(mapperMock)

        verify(v8DebugHandlerMock).setScriptBreakpoint(eq(requestStub.scriptId), eq(requestStub.lineNumber!!))
        verifyNoMoreInteractions(v8DebugHandlerMock)

        assertTrue(response is Debugger.SetBreakpointByUrlResponse)
        val responseLocation: Debugger.Location = (response as Debugger.SetBreakpointByUrlResponse).locations[0]

        assertEquals(requestStub.scriptId, responseLocation.scriptId)
        assertEquals(requestStub.lineNumber, responseLocation.lineNumber)
        assertEquals(requestStub.columnNumber, responseLocation.columnNumber)
    }

    @Test
    fun `No exceptions thrown when V8 not initialized`() {
        val scriptSourceProviderMock = mock<ScriptSourceProvider> {}
        val debugger = Debugger(scriptSourceProviderMock)


        val requestMock = mock<Debugger.SetBreakpointByUrlRequest>()
        val jsonParamsMock = mock<JSONObject>()
        val mapperMock = mock<ObjectMapper> {
            on { convertValue(eq(jsonParamsMock), eq(requestMock::class.java)) } doReturn requestMock
        }
        debugger.dtoMapper = mapperMock

        val response = debugger.setBreakpointByUrl(mock(), jsonParamsMock)

        verifyZeroInteractions(mapperMock)
        verifyZeroInteractions(requestMock)
        verifyZeroInteractions(jsonParamsMock)

        assertTrue(response == null)
    }

}