package com.alexii.j2v8backport

import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Object
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class V8ObjectUtilsBackportTest {
    lateinit var v8: V8

    @Before
    fun setup() {
        v8 = V8.createV8Runtime()
    }

    @After
    fun cleanUp() {
        v8.release()
    }

    @Test
    fun `string to string conversion`() {
        val initial = "TestValue"
        val expected = "TestValue"

        val jsToJavaResult = V8ObjectUtilsBackport.getValue(initial)
        assertEquals(expected, jsToJavaResult)
    }

    @Test
    fun `v8 object to map conversion`() {
        val initialKey = "TestValue"
        val initialValue = "TestValue"
        val expected = mapOf(initialKey to initialValue)

        val v8Object = V8Object(v8)
        v8Object.add(initialKey, initialValue)

        val jsToJavaResult = V8ObjectUtilsBackport.getValue(v8Object)
        v8Object.release()

        assertEquals(expected, jsToJavaResult)
    }

    @Test
    fun `v8 array to list conversion`() {
        val firstElement = "TestValue"
        val secondElement = "TestValue"
        val expected = listOf(firstElement, secondElement)

        val v8Array = V8Array(v8)
        v8Array.push(firstElement)
        v8Array.push(secondElement)

        val jsToJavaResult = V8ObjectUtilsBackport.getValue(v8Array)
        v8Array.release()

        assertEquals(expected, jsToJavaResult)
    }

    @Test
    fun `null to null conversion`() {
        val initial = null
        val expected = null

        val jsToJavaResult = V8ObjectUtilsBackport.getValue(expected)

        assertEquals(expected, jsToJavaResult)
    }

    @Test
    fun `v8 undefined to string undefined conversion`() {
        val initial = V8.getUndefined()
        val expected = initial.toString()

        val jsToJavaResult = V8ObjectUtilsBackport.getValue(expected)

        assertEquals(expected, jsToJavaResult)
    }

    @Test
    fun `v8 function to function source string conversion`() {
        val jsFunctionSource = "function () {return 'a'}"
        val initial = v8.executeObjectScript("x = $jsFunctionSource; x")
        val expected = jsFunctionSource


        val jsToJavaResult = V8ObjectUtilsBackport.getValue(initial)
        initial.release()

        if (jsToJavaResult !is String) throw AssertionError("String result expected, got: $jsToJavaResult");

        assertEquals(expected, jsToJavaResult)
    }
}