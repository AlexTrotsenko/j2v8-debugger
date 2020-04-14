Change Log
==========

Version 0.2.0
----------------------------
* Updated V8 to 6.1.0
* Replaced DebugHandler with V8Inspector
* Removed backport (for v8 version below 4.8)

Version 0.1.2
----------------------------
* Fixed not working debugger on older then 4.8 version of j2v8: separate j2v8backport module is created with j2v8 v4.6.0 as dependency.
* j2v8backport is being published together with j2v8-debugger

Version 0.1.1
----------------------------
* Do not skip debugging "pause" event if some V8 local variables can't be converted to Java and sent to Chrome Debugger
* Added info about handled exception if debug event (break-point hit) can't be send to Chrome DevTools (Debugger UI)
* Added ability to use j2v8-debugger lib with older then 4.8 version of j2v8: E.g. version 4.6 (latest published for mac and windows)
* Added info about set-up, usage and known issues to Readme

Version 0.1
----------------------------
Initial version with basic JS debugging functionality is implemented:
* Debugging embedded V8 in Android app using Chrome DevTools.
* Support setting/removing breakpoints, step into, step out and step over, variables inspection, etc.
* Debugging embedded V8 is similar to [Remote Debugging WebViews](https://developers.google.com/web/tools/chrome-devtools/remote-debugging/webviews).
* Access debuggable V8 in the app via **chrome://inspect**.
