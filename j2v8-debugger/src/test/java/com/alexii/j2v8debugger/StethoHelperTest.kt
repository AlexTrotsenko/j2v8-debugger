package com.alexii.j2v8debugger

import android.app.Application
import com.eclipsesource.v8.debug.DebugHandler
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ExecutorService
import com.facebook.stetho.inspector.protocol.module.Debugger as FacebookDebuggerStub

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
class StethoHelperTest {

    @Test
    fun `returns custom Debugger and no Stetho Debugger Stub`() {
        val scriptSourceProviderMock = mock<ScriptSourceProvider> {}
        val contextMock = mock<Application> {}
        whenever(contextMock.applicationContext).thenReturn(contextMock)

        val domains = StethoHelper.getDefaultInspectorModulesWithDebugger(contextMock, scriptSourceProviderMock)

        assertTrue("No Debugger present", domains.any { it.javaClass == Debugger::class.java })
        assertFalse("Stetho Debugger present", domains.any { it.javaClass ==  FacebookDebuggerStub::class.java} )
    }

    @Test
    fun `initialized when Stetho created before v8`() {
        val scriptSourceProviderMock = mock<ScriptSourceProvider> {}
        val contextMock = mock<Application> {}
        whenever(contextMock.applicationContext).thenReturn(contextMock)
        StethoHelper.getDefaultInspectorModulesWithDebugger(contextMock, scriptSourceProviderMock)

        val v8DebugHandlerMock = mock<DebugHandler>()
        val v8ExecutorServiceMock = mock<ExecutorService> {
            on { execute(any()) } doAnswer { it.getArgument<Runnable>(0).run() }
        }
        StethoHelper.initializeWithV8Debugger(v8DebugHandlerMock, v8ExecutorServiceMock)

        verify(v8ExecutorServiceMock, times(1)).execute(any())
        assertTrue(StethoHelper.isStethoAndV8DebuggerFullyInitialized)
    }

    @Test
    fun `initialized when v8 created before Stetho`() {
        val v8DebugHandlerMock = mock<DebugHandler>()
        val v8ExecutorServiceMock = mock<ExecutorService> {
            on { execute(any()) } doAnswer { it.getArgument<Runnable>(0).run() }
        }
        StethoHelper.initializeWithV8Debugger(v8DebugHandlerMock, v8ExecutorServiceMock)

        val scriptSourceProviderMock = mock<ScriptSourceProvider> {}
        val contextMock = mock<Application> {}
        whenever(contextMock.applicationContext).thenReturn(contextMock)
        StethoHelper.getDefaultInspectorModulesWithDebugger(contextMock, scriptSourceProviderMock)

        verify(v8ExecutorServiceMock, times(1)).execute(any())
        assertTrue(StethoHelper.isStethoAndV8DebuggerFullyInitialized)
    }


    val StethoHelper.isStethoAndV8DebuggerFullyInitialized
        get() = this.debugger?.v8Debugger != null
}