package com.alexii.j2v8debugging.sample.di

import android.content.Context
import com.alexii.j2v8debugger.ScriptSourceProvider
import com.alexii.j2v8debugging.sample.App
import com.alexii.j2v8debugging.sample.SimpleScriptProvider
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * This is where you will inject application-wide dependencies.
 */
@Module
class AppModule {

    @Provides
    internal fun provideContext(application: App): Context {
        return application.applicationContext
    }

    @Singleton
    @Provides
    fun provideScriptSourceProvider(): ScriptSourceProvider {
        return SimpleScriptProvider()
    }
}
