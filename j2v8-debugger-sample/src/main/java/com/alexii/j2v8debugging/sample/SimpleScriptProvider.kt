package com.alexii.j2v8debugging.sample

import com.alexii.j2v8debugger.ScriptSourceProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimpleScriptProvider @Inject constructor() : ScriptSourceProvider {

    private val scriptName = "hello-world"

    override val allScriptIds = listOf(scriptName)

    override fun getSource(scriptId: String): String {
        val jsScript = ("""
            |var hello = 'hello, ';
            |var world = 'world!';
            |hello.concat(world);
        """).trimMargin()

        if (scriptId == scriptName) return jsScript

        return "JS source not found :("
    }
}
