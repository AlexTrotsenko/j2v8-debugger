package com.alexii.j2v8debugging.sample

import android.app.Activity
import android.app.Application
import com.alexii.j2v8debugger.ScriptSourceProvider
import com.alexii.j2v8debugger.StethoHelper
import com.alexii.j2v8debugging.sample.di.DaggerAppComponent
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasActivityInjector
import timber.log.Timber
import javax.inject.Inject

class App : Application(), HasActivityInjector {
    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Activity>

    @Inject
    lateinit var scriptProvider: ScriptSourceProvider

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        DaggerAppComponent
                .builder()
                .application(this)
                .build()
                .inject(this)

        StethoHelper.initializeDebugger(this, scriptProvider)

        Timber.w("[Alex_Stetho] initialize")
    }

    override fun activityInjector(): AndroidInjector<Activity>? {
        return dispatchingAndroidInjector
    }
}
