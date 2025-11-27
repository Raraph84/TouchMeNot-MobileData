package com.djay.touchmenot_mm;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class RecorderHook implements IXposedHookLoadPackage {
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String TAG = "RecorderHook";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEMUI.equals(lpparam.packageName)) return;

        log("=== RecorderHook loaded into SystemUI ===");

        // HOOK 1: QSTileImpl#click (Generic QS tile click)
        try {
            Class<?> qst = XposedHelpers.findClass("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader);
            for (Method m : qst.getDeclaredMethods()) {
                if (!m.getName().equals("click")) continue;

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        logEntry(param.thisObject, "click", "QSTileImpl clicked");
                        log("STACK: " + shortStack());
                    }
                });
                log("Hooked QSTileImpl#click");
                break;
            }
        } catch (Throwable t) {
            log("Failed to hook QSTileImpl#click: " + t.getMessage());
        }

        // HOOK 2: InternetTile (all declared methods)
        hookAllMethods("com.android.systemui.qs.tiles.InternetTile", lpparam);

        // HOOK 3: InternetDialogControllerImpl (all declared methods)
        hookAllMethods("com.android.systemui.qs.tiles.dialog.InternetDialogControllerImpl", lpparam);

        // HOOK 4: Footer power button classes
        hookAllMethods("com.android.systemui.statusbar.phone.StatusBarFooterView", lpparam);
        hookAllMethods("com.android.systemui.qs.QSFooterImpl", lpparam);
        hookAllMethods("com.android.systemui.qs.footer.domain.interactor.QSFooterInteractorImpl", lpparam);

        // HOOK 5: Global View.performClick (captures footer button!)
        try {
            Class<?> v = View.class;
            for (Method m : v.getDeclaredMethods()) {
                if (!m.getName().equals("performClick")) continue;
                if (m.getParameterTypes().length != 0) continue;

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        logEntry(param.thisObject, "performClick", "View.performClick()");
                        try {
                            Object parent = XposedHelpers.callMethod(param.thisObject, "getParent");
                            if (parent != null)
                                log("PARENT=" + parent.getClass().getName());
                        } catch (Throwable ignored) {}

                        log("STACK: " + shortStack());
                    }
                });
                log("Hooked View.performClick");
                break;
            }
        } catch (Throwable t) {
            log("Failed to hook View.performClick: " + t.getMessage());
        }
    }

    // ————————————————————————————————————————————————————————————————
    // Helper: hook all declared methods of a class
    // ————————————————————————————————————————————————————————————————
    private void hookAllMethods(String className, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            Method[] methods = clazz.getDeclaredMethods();

            for (Method m : methods) {
                Method target = m;

                XposedBridge.hookMethod(target, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        logEntry(param.thisObject, target.getName(), "Method in " + className);
                        log("STACK: " + shortStack());
                    }
                });
            }

            log("Hooked ALL methods of " + className);

        } catch (Throwable t) {
            log("Class NOT found or failed: " + className + " → " + t.getMessage());
        }
    }

    // ————————————————————————————————————————————————————————————————
    // Logging utilities
    // ————————————————————————————————————————————————————————————————
    private void log(String msg) {
        Log.i(TAG, msg);
        XposedBridge.log("[" + TAG + "] " + msg);
    }

    private void logEntry(Object instance, String method, String note) {
        String cls = (instance != null) ? instance.getClass().getName() : "null";
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String out = "[" + ts + "] " + cls + "#" + method + " — " + note;
        log(out);
    }

    private String shortStack() {
        try {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder();

            for (int i = 4; i < Math.min(st.length, 12); i++) {
                sb.append(st[i].getClassName())
                        .append(".")
                        .append(st[i].getMethodName())
                        .append("():")
                        .append(st[i].getLineNumber());
                if (i < 11) sb.append(" <- ");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "stack unavailable";
        }
    }
}
