package com.alexii.j2v8debugger

import android.app.Application
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
}