package com.eclipsesource.v8

import com.eclipsesource.v8.utils.V8ObjectUtils
import com.eclipsesource.v8.utils.typedarrays.*



/**
 * Backport of [V8ObjectUtils.getValue], which not available before J2V8 version 4.8.
 *
 * It's needed since latest J2V8 version (4.8+) is not released only on Mac and Windows:
 * thus some project might still use older j2v8 version (4.6) in order to keep the same version across the platforms.
 */
object V8ObjectUtilsBackport {

    /**
     * Create a Java Object from a result from V8. V8 can return
     * basic Java types, or V8Values (V8Object, V8Array, etc...). This method
     * will attempt to convert the result into a pure Java object using a
     * deep copy.
     *
     * If the input is basic Java type (Integer, Double, Boolean, String)
     * it will be returned. If the input is a V8Value, it will be converted.
     *
     * All elements in the V8Object are released after they are accessed.
     * However, the root object itself is not released.
     *
     * @param v8Object The input to convert.
     * @return A Java object representing the input.
     */
    fun getValue(v8Object: Any?): Any? {
        if (v8Object is V8Value?) {
            val v8Type = v8Object?.v8Type
            return getValueInner(v8Object)
        } else {
            return v8Object
        }
    }

    /**
     * Backport of [V8ObjectUtils.getValue].
     *
     * Since the result is used as output in Chrome Debugger:
     *  modifies initial behaviour by returning "undefined" and actual js function content instead of custome using TypeAdapter.
     */
    private fun getValueInner(v8Value: V8Value?): Any? {
        return when {
            /* real implementation checks for V8Value.INTEGER, V8Value.DOUBLE, V8Value.BOOLEAN, V8Value.STRING which we don't need */
            /* real implementation return IGNORE */
            v8Value == null -> null
            /* real implementation return V8.getUndefined() */
            v8Value.isUndefined -> v8Value.toString()
            v8Value is V8Function -> v8Value.toString()
            v8Value is V8ArrayBuffer -> ArrayBuffer(v8Value.backingStore)
            v8Value is V8TypedArray -> toTypedArray(v8Value)
            v8Value is V8Array -> V8ObjectUtils.toList(v8Value)
            v8Value is V8Object -> V8ObjectUtils.toMap(v8Value as V8Object)
            /* real implementation return V8.getUndefined() */
            else -> "{unknown value}: " + v8Value
        }
    }

    /**
     * Copy implementation of [V8ObjectUtils.toTypedArray], which is private.
     */
    private fun toTypedArray(typedArray: V8TypedArray): Any {
        val arrayType = typedArray.type
        val buffer = typedArray.byteBuffer
        return when (arrayType) {
            V8Value.INT_8_ARRAY -> Int8Array(buffer)
            V8Value.UNSIGNED_INT_8_ARRAY -> UInt8Array(buffer)
            V8Value.UNSIGNED_INT_8_CLAMPED_ARRAY -> UInt8ClampedArray(buffer)
            V8Value.INT_16_ARRAY -> Int16Array(buffer)
            V8Value.UNSIGNED_INT_16_ARRAY -> UInt16Array(buffer)
            V8Value.INT_32_ARRAY -> Int32Array(buffer)
            V8Value.UNSIGNED_INT_32_ARRAY -> UInt32Array(buffer)
            V8Value.FLOAT_32_ARRAY -> Float32Array(buffer)
            V8Value.FLOAT_64_ARRAY -> Float64Array(buffer)
            else -> "{unknown array type}: " + arrayType
        }
    }
}