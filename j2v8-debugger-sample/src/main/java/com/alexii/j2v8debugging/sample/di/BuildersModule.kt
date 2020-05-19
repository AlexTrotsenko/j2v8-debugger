package com.alexii.j2v8debugging.sample.di

import com.alexii.j2v8debugging.sample.ExampleActivity

import dagger.Module
import dagger.android.ContributesAndroidInjector

/**
 * Binds sub-components within the app.
 */
@Module
abstract class BuildersModule {

    @ContributesAndroidInjector
    internal abstract fun bindExampleActivity(): ExampleActivity
}
