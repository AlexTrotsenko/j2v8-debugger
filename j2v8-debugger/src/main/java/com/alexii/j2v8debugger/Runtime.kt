package com.alexii.j2v8debugger

import com.facebook.stetho.inspector.console.RuntimeReplFactory
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer
import com.facebook.stetho.inspector.jsonrpc.JsonRpcResult
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsMethod
import org.json.JSONObject
import com.facebook.stetho.inspector.protocol.module.Runtime as FacebookRuntimeBase

/**
 * V8 JS Debugger. Name of the class and methods must match names defined in Chrome Dev Tools protocol.
 *
 * [initialize] must be called before actual debugging (adding breakpoints in Chrome DevTools).
 *  Otherwise setting breakpoint, etc. makes no effect.
 */
class Runtime(replFactory: RuntimeReplFactory?) : ChromeDevtoolsDomain {
    val adaptee = FacebookRuntimeBase(replFactory)

    @ChromeDevtoolsMethod
    fun getProperties(peer: JsonRpcPeer?, params: JSONObject?): JsonRpcResult {
        /**
         * hack needed to return local variables: Runtime.getProperties called after Debugger.paused.
         * https://github.com/facebook/stetho/issues/611
         * xxx: check if it should be conditional for requested related to Debugger only
         */

        params?.put("ownProperties", true)

        val result = adaptee.getProperties(peer, params)

        return result
    }

    @ChromeDevtoolsMethod
    fun releaseObject(peer: JsonRpcPeer?, params: JSONObject?) = adaptee.releaseObject(peer, params)

    @ChromeDevtoolsMethod
    fun releaseObjectGroup(peer: JsonRpcPeer?, params: JSONObject?) = adaptee.releaseObjectGroup(peer, params)

    /**
     * Replaces [FacebookRuntimeBase.callFunctionOn] as override can't be used: CallFunctionOnResponse is private (return type)
     */
    @ChromeDevtoolsMethod
    fun callFunctionOn(peer: JsonRpcPeer?, params: JSONObject?): JsonRpcResult? = adaptee.callFunctionOn(peer, params)

    @ChromeDevtoolsMethod
    fun evaluate(peer: JsonRpcPeer?, params: JSONObject?): JsonRpcResult = adaptee.evaluate(peer, params)
}
