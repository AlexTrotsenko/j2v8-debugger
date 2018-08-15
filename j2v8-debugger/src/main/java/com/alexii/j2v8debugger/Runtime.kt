package com.alexii.j2v8debugger

import com.facebook.stetho.inspector.console.RuntimeReplFactory
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer
import com.facebook.stetho.inspector.jsonrpc.JsonRpcResult
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsMethod
import org.json.JSONObject
import com.facebook.stetho.inspector.protocol.module.Runtime as FacebookRuntimeBase

/**
 * V8 JS Debugger. Name of the class and methods must match names defined in Chrome Dev Tools protocol.
 *
 * [initialize] must be called before actual debugging (adding breakpoints in Chrome DevTools).
 *  Otherwise setting breakpoint, etc. makes no effect.
 */
class Runtime(
        replFactory: RuntimeReplFactory?
) : FacebookRuntimeBase(replFactory) {

    @ChromeDevtoolsMethod
    override fun getProperties(peer: JsonRpcPeer?, params: JSONObject?): JsonRpcResult {
        /**
         * hack needed to return local variables: Runtime.getProperties called after Debugger.paused.
         * https://github.com/facebook/stetho/issues/611
         * xxx: check if it should be conditional for requested related to Debugger only
         */

        params?.put("ownProperties", true)

        val result = super.getProperties(peer, params)

        return result
    }

    @ChromeDevtoolsMethod
    override fun releaseObject(peer: JsonRpcPeer?, params: JSONObject?) = super.releaseObject(peer, params)

    @ChromeDevtoolsMethod
    override fun releaseObjectGroup(peer: JsonRpcPeer?, params: JSONObject?) = super.releaseObjectGroup(peer, params)

    /**
     * Replaces [FacebookRuntimeBase.callFunctionOn] as override can't be used: CallFunctionOnResponse is private (return type)
     */
//FIXME check why when we add following - Stetho is not working completely
//    @ChromeDevtoolsMethod
//    fun callFunctionOn(peer: JsonRpcPeer?, params: Any?): JsonRpcResult? = super.callFunctionOn(peer, params as JSONObject?)

    @ChromeDevtoolsMethod
    override fun evaluate(peer: JsonRpcPeer?, params: JSONObject?): JsonRpcResult = super.evaluate(peer, params)
}
