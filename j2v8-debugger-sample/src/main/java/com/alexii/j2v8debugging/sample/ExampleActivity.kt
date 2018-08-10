package com.alexii.j2v8debugging.sample

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import com.alexii.j2v8debugger.StethoHelper
import com.alexii.j2v8debugger.V8Helper
import com.alexii.j2v8debugging.R
import com.eclipsesource.v8.V8
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_example.*
import java.util.concurrent.ExecutorService
import javax.inject.Inject

class ExampleActivity : AppCompatActivity() {
    @Inject
    lateinit var simpleScriptProvider: SimpleScriptProvider

    /** V8 should be initialized and further called on the same thread.*/
    @Inject
    lateinit var v8Executor: ExecutorService

    /** Must be called only in v8's thread only. */
    private val v8: V8 by lazy(::initDebuggableV8)

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_example)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            val scriptName = "hello-world"
            val jsScript = simpleScriptProvider.getSource(scriptName)

            v8Executor.submit {
                val result = v8.executeScript(jsScript, scriptName, 0)
                println("[v8 execution result: ] $result")

                Snackbar.make(view, "V8 answers: $result", Snackbar.LENGTH_LONG)
                        .setAction("V8Action", null).show()
            }
        }
    }

    /**
     * Should be called on V8 specific thread only as well as further calls to v8 itself.
     */
    private fun initDebuggableV8(): V8 {
        V8Helper.enableDebugging()

        val runtime = V8.createV8Runtime()

        val v8Debugger = V8Helper.getV8Debugger(runtime)
        StethoHelper.initializeWithV8Debugger(v8Debugger, v8Executor)

        return runtime
    }

    override fun onDestroy() {
        releaseDebuggableV8()

        super.onDestroy()
    }

    private fun releaseDebuggableV8() {
        v8Executor.run {
            V8Helper.releaseV8Debugger()
            v8.release()
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_example, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
