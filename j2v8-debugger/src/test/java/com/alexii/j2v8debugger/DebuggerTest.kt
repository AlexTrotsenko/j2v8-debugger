package com.alexii.j2v8debugger

import com.alexii.j2v8debugger.utils.logger
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import org.junit.BeforeClass
import org.junit.Test

class DebuggerTest {
    companion object {
        @BeforeClass
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

}