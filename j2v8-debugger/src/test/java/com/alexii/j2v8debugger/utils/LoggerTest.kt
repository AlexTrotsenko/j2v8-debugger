package com.alexii.j2v8debugger.utils

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class LoggerTest {
    val tag = "MyTag"
    val msg = "My message"
    val exception = RuntimeException()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
    }

    @After
    fun cleanUp() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `Logger i() redirects to Android Log`() {
        every { Log.i(any(), any()) } returns 0

        logger.i(tag, msg)

        verify { Log.i(tag, msg) }
    }

    @Test
    fun `Logger w() redirects to Android Log`() {
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0

        logger.w(tag, msg)
        logger.w(tag, msg, exception)

        verify { Log.w(tag, msg) }
        verify { Log.w(tag, msg, exception) }
    }


    @Test
    fun `Logger e() redirects to Android Log`() {
        every { Log.e(any(), any(), any()) } returns 0

        logger.e(tag, msg, exception)

        verify { Log.e(tag, msg, exception) }
    }

    @Test
    fun `Logger getStackTraceString() redirects to Android Log`() {
        every { Log.getStackTraceString(any()) } returns ""

        logger.getStackTraceString(exception)

        verify { Log.getStackTraceString(exception) }
    }

}