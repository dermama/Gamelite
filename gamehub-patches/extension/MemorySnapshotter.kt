package com.gamehub.lite.extension

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream

class MemorySnapshotter(
    private val winePrefixDir: File,
    private val connection: HybridConnection
) {
    private val TAG = "GameHubNexus-Snapshotter"
    private val httpClient = OkHttpClient()

    // Space C Compute Daemon URL
    private val SPACE_C_URL = "https://your-username-space-c-compute.hf.space"

    fun freezeAndSave(gameId: String) {
        Log.i(TAG, "Initiating Wine process freeze and save sequence...")
        
        try {
            // 1. Send freeze command to Wine process (simulated by pausing emulation loop or via SIGSTOP)
            sendSignalToWineProcess("SIGSTOP")
            
            // 2. Package saves, registry (user.reg), and user settings into a compressed file
            val snapshotFile = packageSnapshot(gameId)
            
            if (snapshotFile != null && snapshotFile.exists()) {
                // 3. Upload snapshot to Space C
                uploadSnapshotToCloud(gameId, snapshotFile)
            }
            
            // 4. Resume Wine process
            sendSignalToWineProcess("SIGCONT")
            
        } catch (e: Exception) {
            Log.e(TAG, "Snapshot freeze failed: ${e.message}")
            sendSignalToWineProcess("SIGCONT") // Safety resume
        }
    }

    fun restoreAndResume(gameId: String) {
        Log.i(TAG, "Restoring state from cloud snapshot...")
        
        // Request Space C to prepare snapshot URL
        val payload = JSONObject().apply {
            put("game_id", gameId)
        }
        
        connection.sendTaskToSpaceC("load_snapshot", payload)
    }

    fun handleSnapshotDownload(gameId: String, downloadUrl: String) {
        // Download the snapshot and extract it back into the wine prefix directory
        val request = Request.Builder().url(SPACE_C_URL + downloadUrl).build()
        
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.error(TAG, "Failed to download snapshot: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val bodyStream = response.body?.byteStream()
                    if (bodyStream != null) {
                        val localZip = File(winePrefixDir, "temp_snapshot.tar.gz")
                        FileOutputStream(localZip).use { fos ->
                            bodyStream.copyTo(fos)
                        }
                        
                        // Extract and restore
                        extractSnapshot(localZip)
                        localZip.delete()
                        Log.i(TAG, "Snapshot restored successfully.")
                    }
                }
            }
        })
    }

    private fun sendSignalToWineProcess(signal: String) {
        // Run native shell command (kill -SIGSTOP/SIGCONT) on Wine PID
        // This stops the emulator execution thread in Unix level
        try {
            val winePid = getWinePid()
            if (winePid > 0) {
                val process = Runtime.getRuntime().exec(arrayOf("kill", "-$signal", winePid.toString()))
                process.waitFor()
                Log.d(TAG, "Signal $signal sent to Wine PID: $winePid")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Unix signal to Wine process: ${e.message}")
        }
    }

    private fun getWinePid(): Int {
        // Retrieve Wine/Box64 running process ID
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "pgrep -f 'wine|box64|wineserver'"))
            val reader = process.inputStream.bufferedReader()
            val pidText = reader.readLine()
            process.waitFor()
            pidText?.trim()?.toInt() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    private fun packageSnapshot(gameId: String): File? {
        // Package: winePrefix/drive_c/users/xdg-user/AppData/Local/Temp or Documents etc
        val saveDir = File(winePrefixDir, "drive_c/users/xdg-user/Documents") // Typical Wine document folder
        val targetZip = File(winePrefixDir, "${gameId}_snapshot.tar.gz")
        
        if (!saveDir.exists()) {
            Log.w(TAG, "No game save directory found inside WinePrefix.")
            return null
        }
        
        // Run tar shell command inside android container
        try {
            val cmd = arrayOf("sh", "-c", "tar -czf ${targetZip.absolutePath} -C ${saveDir.parentFile.absolutePath} ${saveDir.name}")
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor()
            return targetZip
        } catch (e: Exception) {
            Log.e(TAG, "Tar packing failed: ${e.message}")
            return null
        }
    }

    private fun extractSnapshot(tarFile: File) {
        try {
            val cmd = arrayOf("sh", "-c", "tar -xzf ${tarFile.absolutePath} -C ${winePrefixDir.absolutePath}/drive_c/users/xdg-user/")
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Extract failed: ${e.message}")
        }
    }

    private fun uploadSnapshotToCloud(gameId: String, file: File) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("game_id", gameId)
            .addFormDataPart("file", file.name, file.asRequestBody("application/gzip".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("$SPACE_C_URL/upload_snapshot")
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "Upload failed: ${e.message}")
                file.delete()
            }

            override fun onResponse(call: Call, response: Response) {
                Log.i(TAG, "Snapshot uploaded successfully. Status: ${response.code}")
                file.delete()
            }
        })
    }
}
private fun Log.Companion.error(tag: String, message: String) {
    Log.e(tag, message)
}
