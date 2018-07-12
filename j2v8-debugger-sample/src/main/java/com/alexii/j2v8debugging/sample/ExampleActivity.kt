package com.alexii.j2v8debugging.sample

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import com.alexii.j2v8debugging.R
import com.eclipsesource.v8.V8
import dagger.android.AndroidInjection

import kotlinx.android.synthetic.main.activity_example.*
import javax.inject.Inject

class ExampleActivity : AppCompatActivity() {
    @Inject
    lateinit var simpleScriptProvider: SimpleScriptProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_example)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            val runtime = V8.createV8Runtime()
            val scriptName = "hello-world"
            val jsScript = simpleScriptProvider.getSource(scriptName)


            val result = runtime.executeScript(jsScript, scriptName, 0)
            println("[Alex_v8] $result")
            runtime.release()


            Snackbar.make(view, "V8 answer: $result", Snackbar.LENGTH_LONG)
                    .setAction("V8Action", null).show()
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
