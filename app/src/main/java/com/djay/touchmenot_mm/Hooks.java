package com.djay.touchmenot_mm;

import android.app.KeyguardManager;
import android.content.Context;
import android.view.MotionEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hooks implements IXposedHookLoadPackage {
    private static final String TAG = "TouchMeNot_MM";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.android.systemui".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + ": loaded SystemUI");
            hookSystemUI(lpparam);
        } else if ("android".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + ": loaded system_server (android)");
            hookSystemServer(lpparam);
        }
    }

    private void hookSystemUI(final XC_LoadPackage.LoadPackageParam lpparam) {
        final XC_MethodHook blockTouches = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (isLocked(param.thisObject)) {
                    param.setResult(false);
                    XposedBridge.log(TAG + ": blocked touch while locked");
                }
            }
        };

        String[] classes = new String[]{
                "com.android.systemui.shade.ShadeViewController",
                "com.android.systemui.statusbar.phone.NotificationPanelView"
        };

        for (String clsName : classes) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(clsName);
                XposedHelpers.findAndHookMethod(cls, "onInterceptTouchEvent", MotionEvent.class, blockTouches);
                XposedHelpers.findAndHookMethod(cls, "onTouchEvent", MotionEvent.class, blockTouches);
                XposedBridge.log(TAG + ": hooked " + clsName);
            } catch (Throwable ignored) {}
        }
    }

    private void hookSystemServer(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pwm = lpparam.classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            XposedHelpers.findAndHookMethod(pwm, "showGlobalActions", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Context ctx = getContext(param.thisObject);
                    if (ctx != null) {
                        KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
                        if (km != null && km.isKeyguardLocked()) {
                            param.setResult(null);
                            XposedBridge.log(TAG + ": blocked power menu while locked");
                        }
                    }
                }
            });
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
