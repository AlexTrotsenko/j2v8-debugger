package com.alexii.j2v8debugger

import android.content.Context
import android.support.annotation.VisibleForTesting
import com.facebook.stetho.InspectorModulesProvider
import com.facebook.stetho.Stetho
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain
import java.util.*

import com.facebook.stetho.inspector.protocol.module.Debugger as FacebookDebuggerStub

object StethoHelper {

    /**
     * @return Similar to [Stetho.defaultInspectorModulesProvider] but contains [Debugger]
     */
    @JvmStatic
    fun defaultInspectorModulesProvider(context: Context, scriptSourceProvider: ScriptSourceProvider): InspectorModulesProvider {
        return InspectorModulesProvider { getDefaultInspectorModulesWithDebugger(context, scriptSourceProvider) }
    }

    @VisibleForTesting
    fun getDefaultInspectorModulesWithDebugger(context: Context, scriptSourceProvider: ScriptSourceProvider): Iterable<ChromeDevtoolsDomain> {
        val defaultInspectorModules = getDefaultInspectorModules(context)

        //remove work-around when https://github.com/facebook/stetho/pull/600 is merged
        val inspectorModules = ArrayList<ChromeDevtoolsDomain>()
        for (defaultModule in defaultInspectorModules) {
            if (FacebookDebuggerStub::class.java != defaultModule.javaClass) inspectorModules.add(defaultModule)
        }
        inspectorModules.add(Debugger(scriptSourceProvider))

        return inspectorModules
    }

    private fun getDefaultInspectorModules(context: Context): Iterable<ChromeDevtoolsDomain> {
        return Stetho.DefaultInspectorModulesBuilder(context)
                .finish()
    }
}
