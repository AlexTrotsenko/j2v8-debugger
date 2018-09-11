package com.alexii.j2v8debugger

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.facebook.stetho.inspector.protocol.module.Runtime as FacebookRuntimeBase

@RunWith(RobolectricTestRunner::class)
class RuntimeTest {
    @Test
    fun `Runtime return stored properties despite ownProperties value`() {
        val runtime = Runtime(mockk())

        val adapteeMock = mockk<FacebookRuntimeBase>()
        val receivedJsonParams = slot<JSONObject>()
        every { adapteeMock.getProperties(any(), capture(receivedJsonParams)) } returns mockk()
        runtime.adaptee = adapteeMock

        val ownKey = "ownProperties"
        val idKey = "objectId"
        val ownKeyIncomingFalseValue = false
        val ownKeyExpectedResultTrueValue = true
        val requestParamsData = mapOf(ownKey to ownKeyIncomingFalseValue, idKey to 1)

        val ignoredResult = runtime.getProperties(mockk(), JSONObject(requestParamsData))

        val capturedJsonObject = receivedJsonParams.captured

        assertEquals(ownKeyExpectedResultTrueValue, capturedJsonObject[ownKey])
        assertEquals(requestParamsData[idKey], capturedJsonObject[idKey])
        verify(exactly = 1) { adapteeMock.getProperties(any(), any()) }
    }
}