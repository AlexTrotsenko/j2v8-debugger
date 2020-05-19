package com.alexii.j2v8debugging.sample.di

import android.content.Context
import com.alexii.j2v8debugger.ScriptSourceProvider
import com.alexii.j2v8debugging.sample.App
import com.alexii.j2v8debugging.sample.SimpleScriptProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * This is where you will inject application-wide dependencies.
 */
@Module(includes = [AppModule.Declarations::class])
class AppModule {

    @Module
    interface Declarations {
        @Binds
        fun provideContext(application: App): Context

        @Binds
        fun provideSimpleScriptProvider(simpleScriptProvider: SimpleScriptProvider): ScriptSourceProvider
    }

    @Singleton
    @Provides
    fun provideScriptSourceProvider(): SimpleScriptProvider {
        return SimpleScriptProvider()
    }

    @Singleton
    @Provides
    fun provideV8ExecutorService(): ExecutorService {
        return Executors.newSingleThreadExecutor();
    }
}
