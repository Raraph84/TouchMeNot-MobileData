package com.djay.touchmenot_mm;

import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class RecorderHook implements IXposedHookLoadPackage {

    private static final String OUTPUT_PATH = "/sdcard/Download/final_Qslog.txt";
    private static final int MAX_CALLS_PER_METHOD = 200;

    private final HashMap<String, Integer> methodCallCounts = new HashMap<>();

    // These are the known QS tile classes that have handleClick() and represent the core toggles
    private static final String[] QS_TILE_CLASSES = new String[] {
            "com.android.systemui.qs.tiles.AirplaneModeTile",
            "com.android.systemui.qs.tiles.BluetoothTile",
            "com.android.systemui.qs.tiles.FlashlightTile",
            "com.android.systemui.qs.tiles.MobileDataTile",
            "com.android.systemui.qs.tiles.WifiTile",
            "com.android.systemui.qs.tiles.HotspotTile",
            "com.android.systemui.qs.tiles.PowerMenuTile"
    };

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) return;

        try {
            PrintWriter writer = new PrintWriter(new FileWriter(OUTPUT_PATH, false));
            writer.println("========== QS Tile Log Started (" + getTimestamp() + ") ==========");
            writer.close();
        } catch (Throwable e) {
            XposedBridge.log("RecorderHook: Failed to create log file: " + e.getMessage());
        }

        for (String className : QS_TILE_CLASSES) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
                hookAllDeclaredMethods(clazz);
                XposedBridge.log("RecorderHook: Hooked methods from " + className);
            } catch (Throwable t) {
                XposedBridge.log("RecorderHook: Failed to hook " + className + ": " + t.getMessage());
            }
        }
    }

    private void hookAllDeclaredMethods(Class<?> clazz) {
        for (final java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String key = clazz.getName() + "#" + method.getName();
                        int count = methodCallCounts.getOrDefault(key, 0);
                        if (count >= MAX_CALLS_PER_METHOD) return;

                        methodCallCounts.put(key, count + 1);

                        StringBuilder logEntry = new StringBuilder();
                        logEntry.append("[").append(getTimestamp()).append("] ")
                                .append(clazz.getSimpleName()).append(" -> ")
                                .append(method.getName()).append("()");

                        try (FileWriter fw = new FileWriter(OUTPUT_PATH, true);
                             PrintWriter pw = new PrintWriter(fw)) {
                            pw.println(logEntry);
                        } catch (Throwable e) {
                            XposedBridge.log("RecorderHook: Failed to write to log: " + e.getMessage());
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private String getTimestamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }
}
