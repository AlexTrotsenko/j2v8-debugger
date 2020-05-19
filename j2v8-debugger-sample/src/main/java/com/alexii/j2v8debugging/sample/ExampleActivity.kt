package com.alexii.j2v8debugging.sample

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.alexii.j2v8debugger.StethoHelper
import com.alexii.j2v8debugger.V8Debugger
import com.alexii.j2v8debugging.R
import com.eclipsesource.v8.V8
import com.google.android.material.snackbar.Snackbar
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_example.fab
import kotlinx.android.synthetic.main.activity_example.toolbar
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import javax.inject.Inject

class ExampleActivity : AppCompatActivity() {
    @Inject
    lateinit var simpleScriptProvider: SimpleScriptProvider

    /** V8 should be initialized and further called on the same thread.*/
    @Inject
    lateinit var v8Executor: ExecutorService

    lateinit var v8Future: Future<V8>

    /** Must be called only in v8's thread only. */
    private val v8: V8 by lazy { v8Future.get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        val scriptName = "hello-world"
        var scriptPostfix = 1
        AndroidInjection.inject(this)
        updateUserToRandom()
        v8Future = initDebuggableV8()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_example)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            scriptPostfix = 1 - scriptPostfix
            val scriptToDebug = "${scriptName}${scriptPostfix}"
            val jsScript = simpleScriptProvider.getSource(scriptToDebug)

            v8Executor.submit {
                val result = v8.executeScript(jsScript, scriptToDebug, 0)
                println("[v8 execution result: ] $result")

                Snackbar.make(view, "V8 answers: $result", Snackbar.LENGTH_SHORT)
                    .setAction("V8Action", null).show()
            }
        }
    }

    private fun initDebuggableV8(): Future<V8> {
        return V8Debugger.createDebuggableV8Runtime(v8Executor, "demo", true)
    }

    override fun onDestroy() {
        releaseDebuggableV8()

        super.onDestroy()
    }

    private fun releaseDebuggableV8() {
        v8Executor.execute {
            v8.close()
        }
    }

    private fun updateUserToRandom() {
        val newUser = "user" + Random().nextInt(10)
        //following assumes, that some JS sources are different per user
        StethoHelper.scriptsPathPrefix = newUser
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
            R.id.action_scripts_changed -> {
                simpleScriptProvider.updateTimeToNow()
                StethoHelper.notifyScriptsChanged()
                true
            }
            R.id.action_user_and_scripts_changed -> {
                //here user and related scripts should be changed
                updateUserToRandom()
                simpleScriptProvider.updateTimeToNow()
                StethoHelper.notifyScriptsChanged()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
