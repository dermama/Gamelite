package com.gamehub.lite.extension;

import android.util.Log;
import okhttp3.*;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CloudFSProvider {
    private static final String TAG = "GameHubNexus-CloudFS";
    private final File cacheDir;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    // Space A Content Delivery Network URL
    private final String SPACE_A_CDN_URL = "https://biabobab-space-a-storage.hf.space";

    public CloudFSProvider(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    public byte[] readChunk(String gameId, String fileName, long offset, int length) {
        File cacheFile = new File(cacheDir, gameId + "/" + fileName);
        if (!cacheFile.getParentFile().exists()) {
            cacheFile.getParentFile().mkdirs();
        }

        // 1. Check if the block is already cached locally
        if (cacheFile.exists() && cacheFile.length() >= (offset + length)) {
            try (RandomAccessFile raf = new RandomAccessFile(cacheFile, "r")) {
                raf.seek(offset);
                byte[] buffer = new byte[length];
                int read = raf.read(buffer);
                if (read == length) {
                    return buffer;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed reading chunk from local cache: " + e.getMessage());
            }
        }

        // 2. Fetch chunk from Space A CDN on demand
        String chunkUrl = SPACE_A_CDN_URL + "/games/" + gameId + "/" + fileName;
        Log.i(TAG, "Cache Miss. Streaming remote chunk: " + fileName + " at offset " + offset + " (size " + length + ")");

        Request request = new Request.Builder()
                .url(chunkUrl)
                .addHeader("Range", "bytes=" + offset + "-" + (offset + length - 1))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("CDN returned status code " + response.code());
            }

            byte[] bodyBytes = response.body() != null ? response.body().bytes() : null;
            if (bodyBytes == null) {
                throw new IOException("Empty response body");
            }

            // Save fetched chunk to local cache asynchronously
            final byte[] finalBytes = bodyBytes;
            executor.execute(() -> writeToCache(cacheFile, offset, finalBytes));

            return bodyBytes;
        } catch (Exception e) {
            Log.e(TAG, "Failed to stream chunk: " + e.getMessage() + ". Returning empty bytes.");
            return new byte[length];
        }
    }

    public void prefetchRange(final String gameId, final String fileUrl, final String byteRange) {
        executor.execute(() -> {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            File cacheFile = new File(cacheDir, gameId + "/" + fileName);

            Log.i(TAG, "Prefetch instruction received for " + fileName + " (Range: " + byteRange + ")");

            String[] parts = byteRange.split("-");
            if (parts.length != 2) return;
            long start = Long.parseLong(parts[0]);
            long end = Long.parseLong(parts[1]);

            // Check if already cached
            if (cacheFile.exists() && cacheFile.length() >= end) {
                Log.d(TAG, "Chunk " + byteRange + " already in cache.");
                return;
            }

            String absoluteUrl = fileUrl.startsWith("http") ? fileUrl : SPACE_A_CDN_URL + "/" + fileUrl;
            Request request = new Request.Builder()
                    .url(absoluteUrl)
                    .addHeader("Range", "bytes=" + byteRange)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    byte[] bodyBytes = response.body().bytes();
                    writeToCache(cacheFile, start, bodyBytes);
                    Log.i(TAG, "Prefetched and cached range: " + byteRange + " for " + fileName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Prefetch failed: " + e.getMessage());
            }
        });
    }

    private synchronized void writeToCache(File file, long offset, byte[] data) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(offset);
            raf.write(data);
        } catch (Exception e) {
            Log.e(TAG, "Error writing to cache file: " + e.getMessage());
        }
    }
}
