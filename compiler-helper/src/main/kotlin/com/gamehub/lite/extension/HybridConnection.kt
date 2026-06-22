package com.gamehub.lite.extension

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HybridConnection(private val deviceId: String) {
    private val TAG = "GameHubNexus-Connection"
    private var client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep connection alive
        .build()
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val handler = Handler(Looper.getMainLooper())

    // Update with user's actual Hugging Face Space B URL
    private val SPACE_B_WS_URL = "wss://your-username-space-b-gateway.hf.space/ws/"

    interface ConnectionListener {
        fun onMessageReceived(type: String, data: JSONObject)
        fun onConnectionStatusChanged(connected: Boolean)
    }

    private var listener: ConnectionListener? = null

    fun setListener(listener: ConnectionListener) {
        this.listener = listener
    }

    fun connect() {
        if (isConnected) return

        val request = Request.Builder()
            .url("$SPACE_B_WS_URL$deviceId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Connected to Space B Gateway")
                isConnected = true
                reconnectAttempts = 0
                handler.post { listener?.onConnectionStatusChanged(true) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message received: $text")
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type", "")
                    handler.post { listener?.onMessageReceived(type, json) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse websocket message: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Closing connection: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Connection closed: $code")
                isConnected = false
                handler.post { listener?.onConnectionStatusChanged(false) }
                attemptReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                isConnected = false
                handler.post { listener?.onConnectionStatusChanged(false) }
                attemptReconnect()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnect requested by client")
        isConnected = false
    }

    fun sendPlayerPosition(gameId: String, x: Double, y: Double, mapZone: String) {
        val payload = JSONObject().apply {
            put("type", "player_position")
            put("game_id", gameId)
            put("x", x)
            put("y", y)
            put("map_zone", mapZone)
            put("timestamp", System.currentTimeMillis() / 1000)
        }
        send(payload.toString())
    }

    fun requestTranslation(base64Image: String) {
        val payload = JSONObject().apply {
            put("type", "ocr_translate_request")
            put("image_data", base64Image)
            put("target_lang", "ar")
        }
        send(payload.toString())
    }

    fun sendTaskToSpaceC(taskType: String, taskPayload: JSONObject) {
        val payload = JSONObject().apply {
            put("type", "task_forward")
            put("task", taskType)
            put("payload", taskPayload)
        }
        send(payload.toString())
    }

    private fun send(text: String) {
        if (isConnected && webSocket != null) {
            webSocket?.send(text)
        } else {
            Log.w(TAG, "Cannot send, WebSocket is not connected")
        }
    }

    private fun attemptReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(TAG, "Max reconnect attempts reached. Idle.")
            return
        }

        reconnectAttempts++
        val backoffDelay = (reconnectAttempts * 2000).toLong() // 2s, 4s, 6s, 8s, 10s
        Log.i(TAG, "Attempting reconnect in $backoffDelay ms (Attempt $reconnectAttempts)")

        handler.postDelayed({
            connect()
        }, backoffDelay)
    }
}
