package com.alexii.j2v8debugger

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutorService
import com.facebook.stetho.inspector.protocol.module.Debugger as FacebookDebuggerStub
import com.facebook.stetho.inspector.protocol.module.Runtime as FacebookRuntimeBase

class StethoHelperTest {

    @Test
    fun `returns custom Debugger and no Stetho Debugger Stub`() {
        val scriptSourceProviderMock = mockk<ScriptSourceProvider> {}
        val contextMock = mockk<Application> {}
        every {
            contextMock.applicationContext
        } returns contextMock

        val domains = StethoHelper.getDefaultInspectorModulesWithDebugger(contextMock, scriptSourceProviderMock, mockk())

        assertTrue(domains.any { it.javaClass == Debugger::class.java }, "No Debugger present")
        assertFalse(domains.any { it.javaClass == FacebookDebuggerStub::class.java }, "Stetho Debugger present")
    }

    @Test
    fun `returns custom Runtime and no Stetho base Runtime`() {
        val scriptSourceProviderMock = mockk<ScriptSourceProvider> {}
        val contextMock = mockk<Application> {}
        every { contextMock.applicationContext } returns contextMock

        val domains = StethoHelper.getDefaultInspectorModulesWithDebugger(contextMock, scriptSourceProviderMock, mockk())

        assertTrue(domains.any { it.javaClass == Runtime::class.java }, "No Debugger present")
        assertFalse(domains.any { it.javaClass == FacebookRuntimeBase::class.java }, "Stetho Debugger present")
    }

    @Test
    fun `initialized when Stetho created before v8`() {
        val scriptSourceProviderMock = mockk<ScriptSourceProvider> {}
        val contextMock = mockk<Application> {}
        every { contextMock.applicationContext } returns contextMock
        StethoHelper.getDefaultInspectorModulesWithDebugger(contextMock, scriptSourceProviderMock, mockk())

        val v8Messenger = mockk<V8Messenger>()
        val v8ExecutorServiceMock = mockk<ExecutorService> {
            every {
                execute(any())
            } answers { arg<Runnable>(0).run() }
        }
        StethoHelper.initializeWithV8Messenger(v8Messenger, v8ExecutorServiceMock)

        verify(exactly = 1) { v8ExecutorServiceMock.execute(any()) }
    }

    @Test
    @Disabled("This test won't work due to Debugger being a static property on StethoHelper (and set in the previous test)")
    fun `initialized when v8 created before Stetho`() {
        val v8Messenger = mockk<V8Messenger>()
        val v8ExecutorServiceMock = mockk<ExecutorService> {
            every { execute(any()) } answers { arg<Runnable>(0).run() }
        }
        StethoHelper.initializeWithV8Messenger(v8Messenger, v8ExecutorServiceMock)

        val scriptSourceProviderMock = mockk<ScriptSourceProvider> {}
        val contextMock = mockk<Application> {}
        every { contextMock.applicationContext } returns contextMock

        StethoHelper.getDefaultInspectorModulesWithDebugger(contextMock, scriptSourceProviderMock, mockk())

        verify(exactly = 1) { v8ExecutorServiceMock.execute(any()) }
    }
}