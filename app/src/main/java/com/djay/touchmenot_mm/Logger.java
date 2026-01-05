package com.djay.touchmenot_mm;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class Logger implements IXposedHookLoadPackage {
    private static final String TAG = "TouchMeNot_Log";
    private static final String LOG_PATH = "/sdcard/Download/touchmenot_recorder.log";
    private static final AtomicBoolean inited = new AtomicBoolean(false);
    private static volatile Writer writer = null;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // initialize once (we only need to create/truncate the file once; SystemUI or any package is fine)
        initOnce();
    }

    private static void initOnce() {
        if (inited.compareAndSet(false, true)) {
            try {
                File f = new File(LOG_PATH);
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                // truncate and write header
                try (FileOutputStream fos = new FileOutputStream(f, false)) {
                    String header = "=== touchmenot_recorder log start: " + nowTs() + " ===\n";
                    fos.write(header.getBytes());
                    fos.flush();
                }
                writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true)));
                XposedBridge.log(TAG + ": logger initialized -> " + LOG_PATH);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": logger init failed: " + t.getMessage());
                writer = null;
            }
        }
    }

    private static String nowTs() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        } catch (Throwable t) {
            return Long.toString(System.currentTimeMillis());
        }
    }

    // Generic logger used by other classes. Category examples: HOOK, HOOK_FAIL, BLOCK, INFO, ERROR
    public static void log(String category, String message) {
        try {
            initOnce();
            Writer w = writer;
            if (w == null) return;
            String line = String.format(Locale.US, "%s | %s | %s\n", nowTs(), category, message);
            synchronized (w) {
                w.write(line);
                w.flush();
            }
        } catch (Throwable t) {
            try { XposedBridge.log(TAG + ": write failed: " + t.getMessage()); } catch (Throwable ignored) {}
            writer = null;
        }
    }

    public static void hookSuccess(String what) {
        log("HOOK", what);
    }

    public static void hookFail(String what, String reason) {
        log("HOOK_FAIL", what + " -> " + reason);
    }

    public static void blocked(String what, String reason) {
        log("BLOCK", what + " -> " + reason);
    }

    public static void info(String what) {
        log("INFO", what);
    }

    public static void error(String what, String reason) {
        log("ERROR", what + " -> " + reason);
    }
}
