package com.djay.touchmenot_mm;

import android.app.KeyguardManager;
import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Hooks implements IXposedHookLoadPackage {

    private static final String TAG = "TouchMeNot_MM";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        // Hook SystemUI for QS Tile clicks
        if ("com.android.systemui".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + ": loaded SystemUI");
            hookQSTiles(lpparam);
        }

        // Hook system_server for Power Menu (PWR+VolUp)
        if ("android".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + ": loaded system_server (android)");
            hookPowerMenu(lpparam);
        }
    }

    private void hookQSTiles(final XC_LoadPackage.LoadPackageParam lpparam) {
        String[] tileCandidates = new String[]{
                "com.android.systemui.qs.tileimpl.QSTileImpl",
                "com.android.systemui.qs.tileimpl.QSTile"
        };

        for (String tileCls : tileCandidates) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(tileCls);

                // Hook handleClick
                try {
                    XposedHelpers.findAndHookMethod(cls, "handleClick", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (isLocked(param.thisObject)) {
                                param.setResult(null); // cancel the click
                                XposedBridge.log(TAG + ": blocked QS tile handleClick while locked");
                            }
                        }
                    });
                } catch (Throwable ignored) {}

                // Fallback hook for some ROMs
                try {
                    XposedHelpers.findAndHookMethod(cls, "click", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (isLocked(param.thisObject)) {
                                param.setResult(null);
                                XposedBridge.log(TAG + ": blocked QS tile click while locked");
                            }
                        }
                    });
                } catch (Throwable ignored) {}

            } catch (Throwable t) {
                XposedBridge.log(TAG + ": QS tile class not found: " + tileCls);
            }
        }
    }

    private void hookPowerMenu(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pwm = lpparam.classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            XposedHelpers.findAndHookMethod(
                    pwm,
                    "interceptKeyBeforeQueueing",
                    android.view.KeyEvent.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // Logic to block Power+VolUp shortcut
                            XposedBridge.log(TAG + ": interceptKeyBeforeQueueing called");
                            // You can leave the blocking code from your previous implementation here
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": PhoneWindowManager hook failed: " + t);
        }
    }

    private boolean isLocked(Object obj) {
        try {
            Context ctx = getContext(obj);
            if (ctx == null) return false;
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    private Context getContext(Object obj) {
        if (obj == null) return null;
        try {
            Field f = obj.getClass().getDeclaredField("mContext");
            f.setAccessible(true);
            Object val = f.get(obj);
            if (val instanceof Context) return (Context) val;
        } catch (Throwable ignored) {}
        try {
            Method m = obj.getClass().getMethod("getContext");
            Object ret = m.invoke(obj);
            if (ret instanceof Context) return (Context) ret;
        } catch (Throwable ignored) {}
        return null;
    }
}
