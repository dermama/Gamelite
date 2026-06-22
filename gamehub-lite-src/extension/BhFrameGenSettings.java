package com.xj.winemu.sidebar;

import android.content.Context;
import android.content.SharedPreferences;

public class BhFrameGenSettings {

    public static final String PREFS = "bh_framegen";

    public enum Preset {
        ECO  (0, 0.2f, "bh_framegen_preset_eco_label",   "bh_framegen_preset_eco_desc",   "Eco",   "Lowest overhead, suitable for lower-end devices or battery-sensitive play."),
        FLOW (0, 0.4f, "bh_framegen_preset_flow_label",  "bh_framegen_preset_flow_desc",  "Flow",  "Low overhead smoothness boost for most lightweight games."),
        BAL  (0, 0.6f, "bh_framegen_preset_bal_label",   "bh_framegen_preset_bal_desc",   "Bal",   "Recommended. Balances smoothness, power usage, and stability."),
        BOOST(0, 0.8f, "bh_framegen_preset_boost_label", "bh_framegen_preset_boost_desc", "Boost", "Stronger motion smoothing for users who prefer extra fluidity."),
        CLEAR(1, 0.6f, "bh_framegen_preset_clear_label", "bh_framegen_preset_clear_desc", "Clear", "Prioritizes a steadier, cleaner image with fewer motion artifacts."),
        MAX  (1, 0.8f, "bh_framegen_preset_max_label",   "bh_framegen_preset_max_desc",   "Max",   "Highest quality preset with the highest power and performance cost.");

        public final int model;        // byte 8: 0=standard, 1=clear/extreme
        public final float flowScale;  // bytes 4-7: float (0.2..1.0)
        public final String labelResName;
        public final String descResName;
        public final String labelFallback;
        public final String descFallback;

        Preset(int model, float flowScale,
               String labelResName, String descResName,
               String labelFallback, String descFallback) {
            this.model = model;
            this.flowScale = flowScale;
            this.labelResName = labelResName;
            this.descResName = descResName;
            this.labelFallback = labelFallback;
            this.descFallback = descFallback;
        }

        public String getLabel(Context ctx) {
            return resolve(ctx, labelResName, labelFallback);
        }

        public String getDescription(Context ctx) {
            return resolve(ctx, descResName, descFallback);
        }

        private static String resolve(Context ctx, String name, String fallback) {
            if (ctx == null) return fallback;
            int id = ctx.getResources().getIdentifier(name, "string", ctx.getPackageName());
            return id != 0 ? ctx.getString(id) : fallback;
        }
    }

    public boolean enabled = false;
    public Preset preset = Preset.BAL;
    public float flowScale = 0.6f;      // bytes 4-7, clamped 0.2..1.0
    public int model = 0;               // byte 8, 0..1

    public static BhFrameGenSettings load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        BhFrameGenSettings s = new BhFrameGenSettings();
        s.enabled = sp.getBoolean("enabled", false);
        try { s.preset = Preset.valueOf(sp.getString("preset", "BAL")); }
        catch (Exception e) { s.preset = Preset.BAL; }
        s.flowScale = sp.getFloat("flowScale", s.preset.flowScale);
        s.model = sp.getInt("model", s.preset.model);
        return s;
    }

    public void save(Context ctx) {
        SharedPreferences.Editor ed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        ed.putBoolean("enabled", enabled);
        ed.putString("preset", preset.name());
        ed.putFloat("flowScale", flowScale);
        ed.putInt("model", model);
        ed.apply();
    }

    public void applyPreset(Preset p) {
        this.preset = p;
        this.flowScale = p.flowScale;
        this.model = p.model;
    }
}
