package com.djay.touchmenot_mm;

import android.app.KeyguardManager;
import android.content.Context;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class QSBlocker implements IXposedHookLoadPackage {
    private static final String SYSTEMUI = "com.android.systemui";

    private static final ClassMethod[] QS_METHODS = new ClassMethod[]{
            new ClassMethod("com.android.systemui.qs.tiles.AirplaneModeTile", "handleClick"),
            new ClassMethod("com.android.systemui.qs.tiles.BluetoothTile", "handleClickWithSatelliteCheck"),
            new ClassMethod("com.android.systemui.qs.tiles.HotspotTile", "handleClick"),
            new ClassMethod("com.android.systemui.qs.tiles.FlashlightTile", "handleClick"),
    };

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(SYSTEMUI)) return;

        for (ClassMethod cm : QS_METHODS) {
            try {
                Class<?> clazz = XposedHelpers.findClass(cm.className, lpparam.classLoader);
                Method method = findMethodByName(clazz, cm.methodName);
                if (method == null) {
                    XposedBridge.log("QSBlocker: Method not found - " + cm.className + "#" + cm.methodName);
                    continue;
                }

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Context ctx = getContextFromAny(param.thisObject);
                        if (ctx != null && isKeyguardLocked(ctx)) {
                            XposedBridge.log("QSBlocker: Blocked " + cm.className + "#" + cm.methodName + " [LOCKED]");
                            param.setResult(null); // cancel method
                        }
                    }
                });

                XposedBridge.log("QSBlocker: Hooked " + cm.className + "#" + cm.methodName);
            } catch (Throwable t) {
                XposedBridge.log("QSBlocker: Failed to hook " + cm.className + " : " + t.getMessage());
            }
        }
    }

    private Method findMethodByName(Class<?> clazz, String methodName) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) return m;
        }
        return null;
    }

    private boolean isKeyguardLocked(Context ctx) {
        try {
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: isKeyguardLocked failed: " + t.getMessage());
            return false;
        }
    }

    private Context getContextFromAny(Object obj) {
        try {
            if (obj == null) return null;
            Object context = XposedHelpers.getObjectField(obj, "mContext");
            return (context instanceof Context) ? (Context) context : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // Helper class to pair class+method
    static class ClassMethod {
        String className;
        String methodName;

        ClassMethod(String cls, String method) {
            this.className = cls;
            this.methodName = method;
        }
    }
}
