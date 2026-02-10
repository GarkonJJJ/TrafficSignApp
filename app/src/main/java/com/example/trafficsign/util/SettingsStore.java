package com.example.trafficsign.util;


import android.content.Context;
import android.content.SharedPreferences;

public class SettingsStore {
    private final SharedPreferences sp;

    public SettingsStore(Context c) {
        sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public float getConfThr() { return sp.getFloat("conf", 0.25f); }
    public float getNmsThr()  { return sp.getFloat("nms", 0.45f); }
    public int getInputSize() { return sp.getInt("input", 640); }
    public int getThreads()   { return sp.getInt("threads", 4); }
    public boolean isUseGpu() { return sp.getBoolean("gpu", false); }
    public int getSkipFrame() { return sp.getInt("skip", 0); }

    public void setConfThr(float v) { sp.edit().putFloat("conf", v).apply(); }
    public void setNmsThr(float v)  { sp.edit().putFloat("nms", v).apply(); }
    public void setInputSize(int v) { sp.edit().putInt("input", v).apply(); }
    public void setThreads(int v)   { sp.edit().putInt("threads", v).apply(); }
    public void setUseGpu(boolean v){ sp.edit().putBoolean("gpu", v).apply(); }
    public void setSkipFrame(int v) { sp.edit().putInt("skip", v).apply(); }
}
