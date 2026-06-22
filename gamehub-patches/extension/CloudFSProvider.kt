package com.gamehub.lite.extension

import android.util.Log
import okhttp3.*
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Executors

class CloudFSProvider(private val cacheDir: File) {
    private val TAG = "GameHubNexus-CloudFS"
    private val httpClient = OkHttpClient()
    private val executor = Executors.newFixedThreadPool(4)
    
    // Space A Content Delivery Network URL
    private val SPACE_A_CDN_URL = "https://your-username-space-a-storage.hf.space"

    fun readChunk(gameId: String, fileName: String, offset: Long, length: Int): ByteArray {
        val cacheFile = File(cacheDir, "$gameId/$fileName")
        if (!cacheFile.parentFile.exists()) {
            cacheFile.parentFile.mkdirs()
        }

        // 1. Check if the block is already cached locally
        if (cacheFile.exists() && cacheFile.length() >= (offset + length)) {
            try {
                RandomAccessFile(cacheFile, "r").use { raf ->
                    raf.seek(offset)
                    val buffer = ByteArray(length)
                    val read = raf.read(buffer)
                    if (read == length) {
                        return buffer
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading chunk from local cache: ${e.message}")
            }
        }

        // 2. Fetch chunk from Space A CDN on demand
        val chunkUrl = "$SPACE_A_CDN_URL/games/$gameId/$fileName"
        Log.i(TAG, "Cache Miss. Streaming remote chunk: $fileName at offset $offset (size $length)")

        val request = Request.Builder()
            .url(chunkUrl)
            .addHeader("Range", "bytes=$offset-${offset + length - 1}")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("CDN returned status code ${response.code}")
                }
                
                val bodyBytes = response.body?.bytes() ?: throw Exception("Empty response body")
                
                // Save fetched chunk to local cache asynchronously
                executor.execute {
                    writeToCache(cacheFile, offset, bodyBytes)
                }
                
                return bodyBytes
            }
        } catch (e: Exception) {
            Log.error(TAG, "Failed to stream chunk: ${e.message}. Returning empty bytes.")
            return ByteArray(length)
        }
    }

    fun prefetchRange(gameId: String, fileUrl: String, byteRange: String) {
        executor.execute {
            val fileName = fileUrl.substringAfterLast("/")
            val cacheFile = File(cacheDir, "$gameId/$fileName")
            
            Log.i(TAG, "Prefetch instruction received for $fileName (Range: $byteRange)")
            
            // Parse range: e.g., "104857600-115343360"
            val parts = byteRange.split("-")
            if (parts.size != 2) return@execute
            val start = parts[0].toLong()
            val end = parts[1].toLong()
            
            // Check if already cached
            if (cacheFile.exists() && cacheFile.length() >= end) {
                Log.d(TAG, "Chunk $byteRange already in cache.")
                return@execute
            }
            
            val absoluteUrl = if (fileUrl.startsWith("http")) fileUrl else "$SPACE_A_CDN_URL/$fileUrl"
            val request = Request.Builder()
                .url(absoluteUrl)
                .addHeader("Range", "bytes=$byteRange")
                .build()
                
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyBytes = response.body?.bytes()
                        if (bodyBytes != null) {
                            writeToCache(cacheFile, start, bodyBytes)
                            Log.i(TAG, "Prefetched and cached range: $byteRange for $fileName")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Prefetch failed: ${e.message}")
            }
        }
    }

    @Synchronized
    private fun writeToCache(file: File, offset: Long, data: ByteArray) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(offset)
                raf.write(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to cache file: ${e.message}")
        }
    }
}
private fun Log.Companion.error(tag: String, message: String) {
    Log.e(tag, message)
}
