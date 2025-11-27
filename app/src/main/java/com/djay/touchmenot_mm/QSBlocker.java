package com.djay.touchmenot_mm;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;
import android.view.View;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class QSBlocker implements IXposedHookLoadPackage {

    private static final String SYSTEMUI = "com.android.systemui";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEMUI.equals(lpparam.packageName)) return;

        XposedBridge.log("QSBlocker: Loaded into SystemUI with feedback");

        hookQSTileImplClick(lpparam);
        hookInternetDialog(lpparam);
        hookFooterPowerButton(lpparam);

        // Keep existing working tile hooks
        hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.AirplaneModeTile", "handleClick");
        hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.BluetoothTile", "handleClickWithSatelliteCheck");
    }

    // ----------------------------------------------------------------------
    // 1. BLOCK QSTileImpl#click for InternetTile
    // ----------------------------------------------------------------------
    private void hookQSTileImplClick(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> qstileImpl = XposedHelpers.findClass(
                    "com.android.systemui.qs.tileimpl.QSTileImpl",
                    lpparam.classLoader
            );

            for (Method m : qstileImpl.getDeclaredMethods()) {
                if (!m.getName().equals("click"))
                    continue;

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object thisObj = param.thisObject;
                        if (thisObj == null) return;

                        Context ctx = getContextFromAny(thisObj);
                        if (ctx == null) return;

                        if (!isKeyguardLocked(ctx)) return;

                        String instName = thisObj.getClass().getName();
                        if (instName.contains("InternetTile")) {
                            XposedBridge.log("QSBlocker: Blocked InternetTile via QSTileImpl#click");
                            rejectFeedback(ctx);
                            param.setResult(null);
                        }
                    }
                });

                XposedBridge.log("QSBlocker: Hooked QSTileImpl#click");
                return;
            }

        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: Failed hook on QSTileImpl#click: " + t.getMessage());
        }
    }

    // ----------------------------------------------------------------------
    // 2. BLOCK InternetDialogControllerImpl (wifi/data dialog)
    // ----------------------------------------------------------------------
    private void hookInternetDialog(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClass(
                    "com.android.systemui.qs.tiles.dialog.InternetDialogControllerImpl",
                    lpparam.classLoader
            );

            for (Method m : clazz.getDeclaredMethods()) {
                if (!m.getName().equals("onUserClickedInternetDialog"))
                    continue;

                Method target = m;
                XposedBridge.hookMethod(target, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                        Context ctx = getContextFromAny(param.thisObject);
                        if (ctx != null && isKeyguardLocked(ctx)) {
                            XposedBridge.log("QSBlocker: Blocked InternetDialogControllerImpl#onUserClickedInternetDialog");
                            rejectFeedback(ctx);
                            param.setResult(null);
                        }
                    }
                });

                XposedBridge.log("QSBlocker: Hooked InternetDialogControllerImpl#onUserClickedInternetDialog");
                return;
            }

        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: Failed hook on InternetDialogControllerImpl: " + t.getMessage());
        }
    }

    // ----------------------------------------------------------------------
    // 3. BLOCK Footer power menu (GlobalActionsDialogLite#showOrHideDialog)
    // ----------------------------------------------------------------------
    private void hookFooterPowerButton(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClass(
                    "com.android.systemui.globalactions.GlobalActionsDialogLite",
                    lpparam.classLoader
            );

            for (Method m : clazz.getDeclaredMethods()) {
                if (!m.getName().equals("showOrHideDialog"))
                    continue;

                Method target = m;

                XposedBridge.hookMethod(target, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                        Context ctx = getContextFromAny(param.thisObject);
                        if (ctx != null && isKeyguardLocked(ctx)) {
                            XposedBridge.log("QSBlocker: Blocked footer GlobalActionsDialogLite#showOrHideDialog");
                            rejectFeedback(ctx);
                            param.setResult(null);
                        }
                    }
                });

                XposedBridge.log("QSBlocker: Hooked GlobalActionsDialogLite#showOrHideDialog");
                return;
            }

        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: Failed to hook GlobalActionsDialogLite: " + t.getMessage());
        }
    }

    // ----------------------------------------------------------------------
    // Existing tile blocker (Airplane / Bluetooth)
    // ----------------------------------------------------------------------
    private void hookSimpleTile(XC_LoadPackage.LoadPackageParam lpparam, String className, String methodName) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            Method target = null;

            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    target = m;
                    break;
                }
            }

            if (target == null) {
                XposedBridge.log("QSBlocker: Not found " + className + "#" + methodName);
                return;
            }

            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Context ctx = getContextFromAny(param.thisObject);
                    if (ctx != null && isKeyguardLocked(ctx)) {
                        XposedBridge.log("QSBlocker: Blocked " + className + "#" + methodName);
                        rejectFeedback(ctx);
                        param.setResult(null);
                    }
                }
            });

            XposedBridge.log("QSBlocker: Hooked " + className + "#" + methodName);

        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: Failed to hookSimpleTile " + className + ": " + t.getMessage());
        }
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private void rejectFeedback(Context ctx) {
        try {
            Toast.makeText(ctx, "Unlock to use", Toast.LENGTH_SHORT).show();

            Vibrator vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null) {
                long[] pattern = new long[]{0, 40, 50, 40};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vib.vibrate(pattern, -1);
                }
            }

        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: feedback failed: " + t.getMessage());
        }
    }

    private boolean isKeyguardLocked(Context ctx) {
        try {
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    private Context getContextFromAny(Object obj) {
        try {
            if (obj == null) return null;

            Object c1 = XposedHelpers.getObjectField(obj, "mContext");
            if (c1 instanceof Context) return (Context) c1;

            try {
                Object c2 = XposedHelpers.callMethod(obj, "getContext");
                if (c2 instanceof Context) return (Context) c2;
            } catch (Throwable ignored) {}

            return null;

        } catch (Throwable ignored) {}
        return null;
    }
}
