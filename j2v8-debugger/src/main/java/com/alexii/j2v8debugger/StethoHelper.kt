package com.alexii.j2v8debugger

import android.content.Context
import com.eclipsesource.v8.V8
import com.facebook.stetho.InspectorModulesProvider
import com.facebook.stetho.Stetho
import com.facebook.stetho.inspector.console.RuntimeReplFactory
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain
import com.alexii.j2v8debugger.utils.logger
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import com.facebook.stetho.inspector.protocol.module.Debugger as FacebookDebuggerStub
import com.facebook.stetho.inspector.protocol.module.Runtime as FacebookRuntimeBase


object StethoHelper {
    private var debugger: Debugger? = null
    private var runtime: Runtime? = null

    private var v8MessengerRef: WeakReference<V8Messenger>? = null
    private var v8ExecutorRef: WeakReference<ExecutorService>? = null

    /**
     * Changing this prefix lead to changing the path of exposed to Chrome DevTools scripts.
     * It results in "collapsing" all further parsed scripts under path specified.
     * For multiple collapsed elements '/' could be used inside the string path.
     *
     * E.g. If set to "user1" or "user2" - scripts will be collapsed under "user1" or "user2" segment
     *  in Chrome DevTools UI.
     */
    var scriptsPathPrefix = ""
        set(value) {
            field = "/$value/"
        }

    /**
     * Initialize Stetho to enable Chrome DevTools to be intercepted
     */
    @JvmStatic
    fun initializeDebugger(context: Context, scriptSourceProvider: ScriptSourceProvider) {
        val initializer = Stetho.newInitializerBuilder(context)
            .enableDumpapp(Stetho.defaultDumperPluginsProvider(context))
            .enableWebKitInspector(defaultInspectorModulesProvider(context, scriptSourceProvider))

            .build()
        Stetho.initialize(initializer)
    }

    /**
     * @return Similar to [Stetho.defaultInspectorModulesProvider] but contains [Debugger] for [V8]
     */
    @JvmStatic
    fun defaultInspectorModulesProvider(
        context: Context,
        scriptSourceProvider: ScriptSourceProvider
    ): InspectorModulesProvider {
        return InspectorModulesProvider { getInspectorModules(context, scriptSourceProvider) }
    }

    @JvmOverloads
    fun getInspectorModules(
        context: Context,
        scriptSourceProvider: ScriptSourceProvider,
        factory: RuntimeReplFactory? = null
    ): Iterable<ChromeDevtoolsDomain> {
        return try {
            getDefaultInspectorModulesWithDebugger(context, scriptSourceProvider, factory)
        } catch (e: Throwable) { //v8 throws Error instead of Exception on wrong thread access, etc.
            logger.e(
                Debugger.TAG,
                "Unable to init Stetho with V8 Debugger. Default set-up will be used",
                e
            )

            getDefaultInspectorModules(context, factory)
        }
    }

    fun getDefaultInspectorModulesWithDebugger(
        context: Context,
        scriptSourceProvider: ScriptSourceProvider,
        factory: RuntimeReplFactory? = null
    ): Iterable<ChromeDevtoolsDomain> {
        val defaultInspectorModules = getDefaultInspectorModules(context, factory)

        //remove work-around when https://github.com/facebook/stetho/pull/600 is merged
        val inspectorModules = ArrayList<ChromeDevtoolsDomain>()
        for (defaultModule in defaultInspectorModules) {
            if (FacebookDebuggerStub::class != defaultModule::class
                && FacebookRuntimeBase::class != defaultModule::class
            ) {
                inspectorModules.add(defaultModule)
            }
        }

        debugger = Debugger(scriptSourceProvider)
        runtime = Runtime(factory)
        inspectorModules.add(debugger!!)
        inspectorModules.add(runtime!!)

        bindV8ToChromeDebuggerIfReady()

        return inspectorModules
    }

    /**
     * @param v8Executor executor, where V8 should be previously initialized and further will be called on.
     */
    fun initializeWithV8Messenger(v8Messenger: V8Messenger, v8Executor: ExecutorService) {
        v8MessengerRef = WeakReference(v8Messenger)
        v8ExecutorRef = WeakReference(v8Executor)

        bindV8ToChromeDebuggerIfReady()
    }

    /**
     * Inform Chrome DevTools, that scripts are changed. Currently closes Chrome DevTools.
     * New content will be displayed when it will be opened again.
     */
    fun notifyScriptsChanged() {
        debugger?.onScriptsChanged()
    }

    private fun bindV8ToChromeDebuggerIfReady() {
        val chromeDebuggerAttached = debugger != null && runtime != null

        val v8Messenger = v8MessengerRef?.get()
        val v8Executor = v8ExecutorRef?.get()

        if (v8Messenger != null && v8Executor != null && chromeDebuggerAttached) {
            v8Executor.execute {
                bindV8DebuggerToChromeDebugger(
                    debugger!!,
                    runtime!!,
                    v8Executor,
                    v8Messenger
                )
            }
        }
    }

    /**
     * Should be called when both Chrome debugger and V8 debugger is ready
     *  (When Chrome DevTools UI is open and V8 is created in debug mode with debugger object).
     */
    private fun bindV8DebuggerToChromeDebugger(
        chromeDebugger: Debugger,
        chromeRuntime: Runtime,
        v8Executor: ExecutorService,
        v8Messenger: V8Messenger
    ) {
        chromeDebugger.initialize(v8Executor, v8Messenger)
        chromeRuntime.initialize(v8Messenger)
    }

    /**
     * @return default Stetho.DefaultInspectorModulesBuilder
     *
     * @param context Android context, which is required to access android resources by Stetho.
     * @param factory copies behaviour of [Stetho.DefaultInspectorModulesBuilder.runtimeRepl] using [Runtime]
     */
    private fun getDefaultInspectorModules(
        context: Context,
        factory: RuntimeReplFactory?
    ): Iterable<ChromeDevtoolsDomain> {
        return Stetho.DefaultInspectorModulesBuilder(context)
            .runtimeRepl(factory)
            .finish()
    }
}
