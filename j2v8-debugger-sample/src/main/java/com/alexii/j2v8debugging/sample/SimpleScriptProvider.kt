package com.alexii.j2v8debugging.sample

import com.alexii.j2v8debugger.ScriptSourceProvider
import java.text.DateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimpleScriptProvider @Inject constructor() : ScriptSourceProvider {

    private val scriptName = "hello-world"
    private lateinit var dateString: String

    init {
        updateTimeToNow()
    }

    fun updateTimeToNow() {
        dateString = DateFormat.getTimeInstance().format(Date())
    }

    override val allScriptIds = listOf(scriptName)

    override fun getSource(scriptId: String): String {
        val jsScript = ("""
            |var globalHi = "hi"
            |
            |function main(payloadObject) {
            |  var hello = 'hello, ';
            |  var world = 'world';
            |
            |  var testReload = '$dateString';
            |
            |  return globalHi + ' and ' + hello + world + ' at ' + testReload + ' with ' + payloadObject.load + ' !';
            |}
            |
            |main({
            |    load: 'object based payload',
            |    redundantLoad: 'this is ignored',
            |    callBack: function testCallBack() { print('Call back!') }
            |})
        """).trimMargin()

        if (scriptId == scriptName) return jsScript

        return "JS source not found :("
    }
}
