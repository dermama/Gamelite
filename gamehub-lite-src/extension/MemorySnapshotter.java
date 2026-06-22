package com.gamehub.lite.extension;

import android.util.Log;
import okhttp3.*;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MemorySnapshotter {
    private static final String TAG = "GameHubNexus-Snapshotter";
    private final File winePrefixDir;
    private final HybridConnection connection;
    private final OkHttpClient httpClient = new OkHttpClient();

    // Unified Space B (Gateway & Compute) URL
    private final String SPACE_C_URL = "https://biabobab-space-b-gateway.hf.space";

    public MemorySnapshotter(File winePrefixDir, HybridConnection connection) {
        this.winePrefixDir = winePrefixDir;
        this.connection = connection;
    }

    public void freezeAndSave(String gameId) {
        Log.i(TAG, "Initiating Wine process freeze and save sequence...");

        try {
            // 1. Send freeze command to Wine process (SIGSTOP)
            sendSignalToWineProcess("SIGSTOP");

            // 2. Package saves
            File snapshotFile = packageSnapshot(gameId);

            if (snapshotFile != null && snapshotFile.exists()) {
                // 3. Upload snapshot to Space B
                uploadSnapshotToCloud(gameId, snapshotFile);
            }

            // 4. Resume Wine process (SIGCONT)
            sendSignalToWineProcess("SIGCONT");

        } catch (Exception e) {
            Log.e(TAG, "Snapshot freeze failed: " + e.getMessage());
            sendSignalToWineProcess("SIGCONT"); // Safety resume
        }
    }

    public void restoreAndResume(String gameId) {
        Log.i(TAG, "Restoring state from cloud snapshot...");

        try {
            JSONObject payload = new JSONObject();
            payload.put("game_id", gameId);
            connection.sendTaskToSpaceC("load_snapshot", payload);
        } catch (Exception e) {
            Log.e(TAG, "Restore request failed: " + e.getMessage());
        }
    }

    public void handleSnapshotDownload(final String gameId, String downloadUrl) {
        Request request = new Request.Builder().url(SPACE_C_URL + downloadUrl).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to download snapshot: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    File localZip = new File(winePrefixDir, "temp_snapshot.tar.gz");
                    try (InputStream is = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(localZip)) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }

                    // Extract and restore
                    extractSnapshot(localZip);
                    localZip.delete();
                    Log.i(TAG, "Snapshot restored successfully.");
                }
            }
        });
    }

    private void sendSignalToWineProcess(String signal) {
        try {
            int winePid = getWinePid();
            if (winePid > 0) {
                Process process = Runtime.getRuntime().exec(new String[]{"kill", "-" + signal, String.valueOf(winePid)});
                process.waitFor();
                Log.d(TAG, "Signal " + signal + " sent to Wine PID: " + winePid);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send Unix signal to Wine process: " + e.getMessage());
        }
    }

    private int getWinePid() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pgrep -f 'wine|box64|wineserver'"});
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String pidText = reader.readLine();
            process.waitFor();
            return pidText != null ? Integer.parseInt(pidText.trim()) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private File packageSnapshot(String gameId) {
        File saveDir = new File(winePrefixDir, "drive_c/users/xdg-user/Documents");
        File targetZip = new File(winePrefixDir, gameId + "_snapshot.tar.gz");

        if (!saveDir.exists()) {
            Log.w(TAG, "No game save directory found inside WinePrefix.");
            return null;
        }

        try {
            String cmd = "tar -czf " + targetZip.getAbsolutePath() + " -C " + saveDir.getParentFile().getAbsolutePath() + " " + saveDir.getName();
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            process.waitFor();
            return targetZip;
        } catch (Exception e) {
            Log.e(TAG, "Tar packing failed: " + e.getMessage());
            return null;
        }
    }

    private void extractSnapshot(File tarFile) {
        try {
            String cmd = "tar -xzf " + tarFile.getAbsolutePath() + " -C " + winePrefixDir.getAbsolutePath() + "/drive_c/users/xdg-user/";
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            process.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "Extract failed: " + e.getMessage());
        }
    }

    private void uploadSnapshotToCloud(String gameId, File file) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("game_id", gameId)
                .addFormDataPart("device_id", "default_device")
                .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("application/gzip"), file))
                .build();

        Request request = new Request.Builder()
                .url(SPACE_C_URL + "/upload_snapshot")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Upload failed: " + e.getMessage());
                file.delete();
            }

            @Override
            public void onResponse(Call call, Response response) {
                Log.i(TAG, "Snapshot uploaded successfully. Status: " + response.code());
                file.delete();
            }
        });
    }
}
