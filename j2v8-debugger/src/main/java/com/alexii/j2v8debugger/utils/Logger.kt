package com.alexii.j2v8debugger.utils

import android.util.Log

internal var logger = Logger()

class Logger {

    fun d(tag: String, msg: String) = if (LogUtils.enabled) {
        Log.d(tag, msg)
    } else null

    fun i(tag: String, msg: String) = if (LogUtils.enabled) {
        Log.i(tag, msg)
    } else null

    fun w(tag: String, msg: String) = if (LogUtils.enabled) {
        Log.w(tag, msg)
    } else null

    fun w(tag: String, msg: String, tr: Throwable) = if (LogUtils.enabled) {
        Log.w(tag, msg, tr)
    } else null

    fun e(tag: String, msg: String, tr: Throwable) = if (LogUtils.enabled) {
        Log.e(tag, msg, tr)
    } else null

    fun getStackTraceString(tr: Throwable): String = Log.getStackTraceString(tr)
}