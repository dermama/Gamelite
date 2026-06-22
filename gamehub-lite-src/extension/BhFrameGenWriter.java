package com.xj.winemu.sidebar;

import android.content.Context;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 10-byte little-endian protocol at <filesDir>/usr/etc/gamescope.control:
 *   0-1: uint16 FPS limit            — owned by sidebar FPS control, not touched here
 *   2:   enabled flag (0/1)
 *   3:   NativeRenderingMode         — owned by host launcher, not touched here
 *   4-7: float flowScale (0.2..1.0)
 *   8:   AI model byte (0=standard, 1=clear)
 *   9:   AI multiplier byte (fixed 2x)
 */
public class BhFrameGenWriter {

    public static void applyFromPrefs(Context ctx) {
        BhFrameGenSettings s = BhFrameGenSettings.load(ctx);
        write(resolveControlPath(ctx), s);
        BhVulkanIcdWriter.ensureIcdJson(ctx);
    }

    /** Resolves the app Context via ActivityThread reflection so the launch-time smali
     *  hook can call this without passing one in. */
    public static void applyFromPrefsNoContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app instanceof Context) {
                applyFromPrefs((Context) app);
            }
        } catch (Exception ignored) {}
    }

    public static void write(String controlPath, BhFrameGenSettings s) {
        if (controlPath == null || controlPath.isEmpty() || s == null) return;
        try {
            File f = new File(controlPath);
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
                if (raf.length() < 10) raf.setLength(10);
                FileChannel ch = raf.getChannel();
                MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_WRITE, 0, 10);
                buf.order(ByteOrder.LITTLE_ENDIAN);

                buf.put(2, (byte) (s.enabled ? 1 : 0));
                buf.putFloat(4, clampFloat(s.flowScale, 0.2f, 1.0f));
                buf.put(8, (byte) (s.model & 0x01));
                buf.put(9, (byte) 2);
                buf.force();
            }
        } catch (Exception ignored) {}
    }

    public static void writeEnabled(String controlPath, boolean enabled) {
        writeByteAt(controlPath, 2, (byte) (enabled ? 1 : 0));
    }

    public static void writeModel(String controlPath, int model) {
        writeByteAt(controlPath, 8, (byte) (model & 0x01));
    }

    public static void writeFlowScale(String controlPath, float flowScale) {
        try (RandomAccessFile raf = new RandomAccessFile(controlPath, "rw")) {
            if (raf.length() < 10) raf.setLength(10);
            MappedByteBuffer buf = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 10);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putFloat(4, clampFloat(flowScale, 0.2f, 1.0f));
            buf.force();
        } catch (Exception ignored) {}
    }

    public static String resolveControlPath(Context ctx) {
        return new File(ctx.getFilesDir(), "usr/etc/gamescope.control").getAbsolutePath();
    }

    private static void writeByteAt(String controlPath, int offset, byte value) {
        try (RandomAccessFile raf = new RandomAccessFile(controlPath, "rw")) {
            if (raf.length() < 10) raf.setLength(10);
            MappedByteBuffer buf = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 10);
            buf.put(offset, value);
            buf.force();
        } catch (Exception ignored) {}
    }

    private static float clampFloat(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
