package com.alexii.j2v8debugger

import com.alexii.j2v8debugger.utils.logger
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.inspector.V8Inspector
import com.eclipsesource.v8.inspector.V8InspectorDelegate
import com.facebook.stetho.inspector.network.NetworkPeerManager
import com.facebook.stetho.json.ObjectMapper
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.LinkedHashMap

class V8Messenger(v8: V8): V8InspectorDelegate {
    private val dtoMapper: ObjectMapper = ObjectMapper()
    private val chromeMessageQueue = Collections.synchronizedMap(LinkedHashMap<String, JSONObject>())
    private val v8ScriptMap = mutableMapOf<String, String>()
    private val v8MessageQueue = Collections.synchronizedMap(LinkedHashMap<String, JSONObject?>())
    private val pendingMessageQueue = Collections.synchronizedList(mutableListOf<PendingResponse>())
    private val nextDispatchId = AtomicInteger(0)
    private var debuggerState = DebuggerState.Disconnected

    private val v8Inspector by lazy {
        V8Inspector.createV8Inspector(v8, this, TAG)
    }

    /**
     * Pass a method and params through to J2V8 to get the response.
     */
    fun getV8Result(method: String, params: JSONObject?): String? {
        val pendingMessage = PendingResponse(method, nextDispatchId.incrementAndGet())
        pendingMessageQueue.add(pendingMessage)

        v8MessageQueue[method] = params ?: JSONObject()
        while (pendingMessage.response.isNullOrBlank()) {
            // wait for response from server
        }
        pendingMessageQueue.remove(pendingMessage)
        return pendingMessage.response
    }

    /**
     * This method is called continuously while J2V8 is paused.
     * Any communication must be done inside of this method while debugger is paused.
     */
    override fun waitFrontendMessageOnPause() {
        if (debuggerState != DebuggerState.Paused) {
            // If we haven't attached to chrome yet, resume code (or else we're stuck)
            logger.d(TAG, "Debugger paused without connection.  Resuming J2V8")
            dispatchMessage(Protocol.Debugger.Resume)
        } else {
            // Check for messages to send to J2V8
            if (v8MessageQueue.any()) {
                for ((k, v) in v8MessageQueue) {
                    logger.d(TAG, "Sending v8 $k with $v")
                    dispatchMessage(k, v.toString())
                }
                v8MessageQueue.clear()
            }

            // Check for messages to send to Chrome DevTools
            if (chromeMessageQueue.any()) {
                val networkPeerManager = NetworkPeerManager.getInstanceOrNull()
                for ((k, v) in chromeMessageQueue) {
                    logger.d(TAG, "Sending chrome $k with $v")
                    networkPeerManager?.sendNotificationToPeers(k, v)
                }
                chromeMessageQueue.clear()
            }
        }
    }

    /**
     * Responses from J2V8 come through here.
     */
    override fun onResponse(p0: String?) {
        logger.d(TAG, "onResponse $p0")
        val message = dtoMapper.convertValue(JSONObject(p0), V8Response::class.java)
        if (message.isResponse) {
            // This is a command response
            val pendingMessage = pendingMessageQueue.firstOrNull { msg -> msg.pending && msg.messageId == message.id }
            if (pendingMessage != null) {
                pendingMessage.response = message.result?.optString("result")
            }
        } else {
            val responseParams = message.params

            when (val responseMethod = message.method) {
                Protocol.Debugger.ScriptParsed -> handleScriptParsedEvent(responseParams)
                Protocol.Debugger.BreakpointResolved -> handleBreakpointResolvedEvent(responseParams, responseMethod)
                Protocol.Debugger.Paused -> handleDebuggerPausedEvent(responseParams, responseMethod)
                Protocol.Debugger.Resumed -> handleDebuggerResumedEvent()
            }
        }
    }

    private fun handleDebuggerResumedEvent() {
        debuggerState = DebuggerState.Connected
    }

    private fun handleDebuggerPausedEvent(responseParams: JSONObject?, responseMethod: String?) {
        if (responseParams != null) {
            debuggerState = DebuggerState.Paused
            val updatedScript = replaceScriptId(responseParams, v8ScriptMap)
            chromeMessageQueue[responseMethod] = updatedScript
        }
    }

    private fun handleScriptParsedEvent(responseParams: JSONObject?) {
        val scriptParsedEvent = dtoMapper.convertValue(responseParams, ScriptParsedEventRequest::class.java)
        if (scriptParsedEvent.url.isNotEmpty()) {
            // Get the V8 Script ID to map to the Chrome ScriptId (stored in url)
            v8ScriptMap[scriptParsedEvent.scriptId] = scriptParsedEvent.url
        }
    }

    /**
     * For BreakpointResolved events, we need to convert the scriptId from the J2V8 scriptId
     * to the Chrome DevTools scriptId before passing it through
     */
    private fun handleBreakpointResolvedEvent(responseParams: JSONObject?, responseMethod: String?) {
        val breakpointResolvedEvent = dtoMapper.convertValue(responseParams, BreakpointResolvedEvent::class.java)
        val location = breakpointResolvedEvent.location
        val response = BreakpointResolvedEvent().also {
            it.breakpointId = breakpointResolvedEvent.breakpointId
            it.location = LocationResponse().also {
                it.scriptId = v8ScriptMap[location?.scriptId]
                it.lineNumber = location?.lineNumber
                it.columnNumber = location?.columnNumber
            }
        }
        chromeMessageQueue[responseMethod] = dtoMapper.convertValue(response, JSONObject::class.java)
    }

    /**
     * Send messages to J2V8
     * If debugger is paused, they will be queued to send in [waitFrontendMessageOnPause]
     * otherwise we can send now.
     * Some messages are only relevant while paused so ignore them if it's not
     */
    fun sendMessage(message: String, params: JSONObject? = null, runOnlyWhenPaused: Boolean = false) {
        if (debuggerState == DebuggerState.Paused) {
            v8MessageQueue[message] = params
        } else if (!runOnlyWhenPaused) {
            dispatchMessage(message, params.toString())
        }
    }

    /**
     * Change debugger state when DevTools connects and disconnects
     */
    fun setDebuggerConnected(isConnected: Boolean) {
        debuggerState = if (isConnected) DebuggerState.Connected else DebuggerState.Disconnected
    }

    /**
     * Pass message to J2V8
     * If we're awaiting a response in the pendingMessageQueue, use the Id and set to pending
     */
    private fun dispatchMessage(method: String, params: String? = null) {
        val messageId: Int
        val pendingMessage = pendingMessageQueue.firstOrNull { msg -> msg.method == method && !msg.pending }
        if (pendingMessage != null) {
            pendingMessage.pending = true
            messageId = pendingMessage.messageId
        } else {
            messageId = nextDispatchId.incrementAndGet()
        }
        val message = "{\"id\":$messageId,\"method\":\"$method\", \"params\": ${params ?: "{}"}}"
        logger.d(TAG, "dispatching $message")
        v8Inspector?.dispatchProtocolMessage(message)
    }

    /**
     * Track messages waiting for responses.
     * These Ids are set when the message is created so the response can be tied back to the request
     */
    private data class PendingResponse(val method: String, var messageId: Int) {
        var response: String? = null
        var pending = false
    }

    internal enum class DebuggerState {
        Disconnected,
        Paused,
        Connected
    }


    companion object {
        const val TAG = "V8Messenger"
    }
}