# J2V8-Debugger

This project is an add-on for the excellent [J2V8 Project](https://github.com/eclipsesource/J2V8).

It allows users to debug JS running in V8 using [Chrome DevTools](https://developers.google.com/web/tools/chrome-devtools/).

Uses [Stetho](https://github.com/facebook/stetho) for communication with Chrome DevTools.

## Features
* Debugging embedded V8 in Android app using Chrome DevTools.
* Support setting/removing breakpoints, step into, step out and step over, variables inspection, etc.
* Debugging embedded V8 is similar to [Remote Debugging WebViews](https://developers.google.com/web/tools/chrome-devtools/remote-debugging/webviews).
* Access debuggable V8 in the app via **chrome://inspect**.

## SetUp
Add JitPack repository in your root build.gradle at the end of repositories:

```gradle
allprojects {
    repositories {
        maven { url "https://jitpack.io" }
    }
}
```

Add dependency in *gradle.build* file of your app module
```gradle
dependencies {
    implementation ('com.github.AlexTrotsenko:j2v8-debugger:0.2.2') // {
    //     optionally J2V8 can be excluded if specific version of j2v8 is needed or defined by other libs
    //     exclude group: 'com.eclipsesource.j2v8'
    // }
}
```

**Note:** current `j2v8-debugger` version is designed for `J2V8` version _6.1+_.

Use [0.1.2](https://github.com/AlexTrotsenko/j2v8-debugger/tree/0.1.2) when debugging of older J2V8 _(4.6.0+)_ is required.  

## Usage

`StethoHelper` and `V8Debugger` are used for set-up of Chrome DevTools and V8 for debugging.

1. Initialization Stetho in `Application` class.

```.Kotlin
    StethoHelper.initializeDebugger(context, scriptSourceProvider)
```

2. Creation of debuggable V8 instance.

Use `V8Debugger.createDebuggableV8Runtime()` instead of `V8.createV8Runtime()`

```.Kotlin
    val debuggableV8Runtime : Future<V8> = V8Debugger.createDebuggableV8Runtime(v8Executor, globalAlias, enableLogging)
```

See [sample project](https://github.com/AlexTrotsenko/j2v8-debugger/blob/master/j2v8-debugger-sample/src/main/java/com/alexii/j2v8debugging/sample/ExampleActivity.kt) for more info.

### Notes regarding J2V8 threads.
- Creation and clean-up of V8 should run on fixed V8 thread.
- Creation and clean-up of V8 Debugger should run on fixed V8 thread.
- Debugging operation like set/remove breakpoint should run on fixed V8 thread.
- Execution of any JS script/function should run on fixed V8 thread.

It's easier to implement such behaviour _(especially from lib point of view)_ if single-threaded V8 executor is used.

This way all above mentioned operations would run on such executor.

Therefore lib api like `V8Debugger.createDebuggableV8Runtime(v8Executor)` is build with this concept in mind.

Later v8 executor will be passed to Chrome DevTools and used for performing debug-related operations.

If Guava is already used in project - MoreExecutors and [ListenableFuture](https://github.com/google/guava/wiki/ListenableFutureExplained) could be handy.

### License

```
Copyright 2015 Alexii Trotsenko

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
