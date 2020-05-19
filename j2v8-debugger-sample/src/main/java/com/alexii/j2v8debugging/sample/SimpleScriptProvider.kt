package com.alexii.j2v8debugging.sample

import com.alexii.j2v8debugger.ScriptSourceProvider
import java.text.DateFormat
import java.util.Date
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

    override val allScriptIds = listOf("${scriptName}0", "${scriptName}1")

    override fun getSource(scriptId: String): String {
        val jsScript = ("""
            |var globalHi = "hi from $scriptId"
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

        return jsScript
    }
}
