package com.gamehub.lite.extension;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import okhttp3.*;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;

public class HybridConnection {
    private static final String TAG = "GameHubNexus-Connection";
    private final String deviceId;
    private final OkHttpClient client;
    private WebSocket webSocket;
    private boolean isConnected = false;
    private int reconnectAttempts = 0;
    private final int maxReconnectAttempts = 5;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Update with user's actual Hugging Face Space B URL
    private final String SPACE_B_WS_URL = "wss://biabobab-space-b-gateway.hf.space/ws/";

    public interface ConnectionListener {
        void onMessageReceived(String type, JSONObject data);
        void onConnectionStatusChanged(boolean connected);
    }

    private ConnectionListener listener;

    public HybridConnection(String deviceId) {
        this.deviceId = deviceId;
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // Keep connection alive
                .build();
    }

    public void setListener(ConnectionListener listener) {
        this.listener = listener;
    }

    public void connect() {
        if (isConnected) return;

        Request request = new Request.Builder()
                .url(SPACE_B_WS_URL + deviceId)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.i(TAG, "Connected to Space B Gateway");
                isConnected = true;
                reconnectAttempts = 0;
                handler.post(() -> {
                    if (listener != null) listener.onConnectionStatusChanged(true);
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Message received: " + text);
                try {
                    final JSONObject json = new JSONObject(text);
                    final String type = json.optString("type", "");
                    handler.post(() -> {
                        if (listener != null) listener.onMessageReceived(type, json);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse websocket message: " + e.getMessage());
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.w(TAG, "Connection closed: " + code);
                isConnected = false;
                handler.post(() -> {
                    if (listener != null) listener.onConnectionStatusChanged(false);
                });
                attemptReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                isConnected = false;
                handler.post(() -> {
                    if (listener != null) listener.onConnectionStatusChanged(false);
                });
                attemptReconnect();
            }
        });
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Disconnect requested by client");
        }
        isConnected = false;
    }

    public void sendPlayerPosition(String gameId, double x, double y, String mapZone) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", "player_position");
            payload.put("game_id", gameId);
            payload.put("x", x);
            payload.put("y", y);
            payload.put("map_zone", mapZone);
            payload.put("timestamp", System.currentTimeMillis() / 1000);
            send(payload.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error packaging player position: " + e.getMessage());
        }
    }

    public void requestTranslation(String base64Image) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", "ocr_translate_request");
            payload.put("image_data", base64Image);
            payload.put("target_lang", "ar");
            send(payload.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error packaging translation request: " + e.getMessage());
        }
    }

    public void sendTaskToSpaceC(String taskType, JSONObject taskPayload) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", "task_forward");
            payload.put("task", taskType);
            payload.put("payload", taskPayload);
            send(payload.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error packaging task forward: " + e.getMessage());
        }
    }

    private void send(String text) {
        if (isConnected && webSocket != null) {
            webSocket.send(text);
        } else {
            Log.w(TAG, "Cannot send, WebSocket is not connected");
        }
    }

    private void attemptReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(TAG, "Max reconnect attempts reached. Idle.");
            return;
        }

        reconnectAttempts++;
        long backoffDelay = reconnectAttempts * 2000L;
        Log.i(TAG, "Attempting reconnect in " + backoffDelay + " ms (Attempt " + reconnectAttempts + ")");

        handler.postDelayed(this::connect, backoffDelay);
    }
}
