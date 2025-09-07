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

/**
 * Robust hooks for TouchMeNot_MM:
 *  - Block power menu (system_server / PhoneWindowManager.showGlobalActions)
 *  - Block notification shade expand + touch expansion (SystemUI)
 *
 * This file tries multiple candidate classes and hooks methods reflectively,
 * logging successes/failures so you can refine for your Pixel A16 build.
 */
public class Hooks implements IXposedHookLoadPackage {
    private static final String TAG = "TouchMeNot_MM";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.android.systemui".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + ": SystemUI loaded - setting up shade hooks");
            hookSystemUI(lpparam);
        } else if ("android".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + ": system_server (android) loaded - setting up policy hooks");
            hookSystemServer(lpparam);
        }
    }

    // ---------- SystemUI hooks (notification shade) ----------
    private void hookSystemUI(final XC_LoadPackage.LoadPackageParam lpparam) {
        // 1) Hook expand-like methods on NotificationPanelViewController (robust)
        String[] controllerCandidates = new String[] {
                "com.android.systemui.shade.NotificationPanelViewController",
                "com.android.systemui.statusbar.phone.NotificationPanelViewController", // older variants
                "com.android.systemui.statusbar.phone.NotificationPanelView" // fallback
        };

        for (String clsName : controllerCandidates) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(clsName);
                XposedBridge.log(TAG + ": found controller class: " + clsName);
                // Hook all methods named "expand", "expandToFullShade", "flingToFullShade"
                Method[] methods = cls.getDeclaredMethods();
                for (Method m : methods) {
                    String mName = m.getName();
                    if ("expand".equals(mName) || "expandToFullShade".equals(mName) || "flingToFullShade".equals(mName)) {
                        try {
                            XposedBridge.hookMethod(m, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    if (isLocked(param.thisObject)) {
                                        XposedBridge.log(TAG + ": blocked " + m.getName() + " on " + clsName + " while locked");
                                        // cancel expansion
                                        param.setResult(null);
                                    }
                                }
                            });
                            XposedBridge.log(TAG + ": hooked method " + mName + " in " + clsName);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": failed to hook method " + mName + " in " + clsName + " -> " + t);
                        }
                    }
                }
            } catch (Throwable t) {
                // Not present on this ROM/version
                XposedBridge.log(TAG + ": controller candidate not found: " + clsName);
            }
        }

        // 2) Hook touch-handling methods on view classes to swallow gestures while locked
        String[] touchCandidates = new String[] {
                "com.android.systemui.shade.ShadeView",
                "com.android.systemui.shade.ShadeViewController",
                "com.android.systemui.statusbar.phone.NotificationPanelView",
                "com.android.systemui.statusbar.phone.PanelView",
                "com.android.systemui.shade.NotificationPanelView"
        };

        for (String clsName : touchCandidates) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(clsName);
                // Try onInterceptTouchEvent(MotionEvent) and onTouchEvent(MotionEvent)
                try {
                    XposedHelpers.findAndHookMethod(cls, "onInterceptTouchEvent", MotionEvent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (isLocked(param.thisObject)) {
                                // consume the intercept so the shade can't start expanding
                                param.setResult(true);
                                XposedBridge.log(TAG + ": consumed onInterceptTouchEvent in " + clsName);
                            }
                        }
                    });
                    XposedBridge.log(TAG + ": hooked onInterceptTouchEvent in " + clsName);
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(cls, "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (isLocked(param.thisObject)) {
                                param.setResult(false);
                                XposedBridge.log(TAG + ": blocked onTouchEvent in " + clsName);
                            }
                        }
                    });
                    XposedBridge.log(TAG + ": hooked onTouchEvent in " + clsName);
                } catch (Throwable ignored) {}
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": touch candidate not found: " + clsName);
            }
        }

        // 3) Also try blocking QS tile clicks (defensive)
        String[] tileCandidates = new String[]{
                "com.android.systemui.qs.tileimpl.QSTileImpl",
                "com.android.systemui.qs.tileimpl.QSTile"
        };

        for (String tileCls : tileCandidates) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(tileCls);
                try {
                    XposedHelpers.findAndHookMethod(cls, "handleClick", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (isLocked(param.thisObject)) {
                                param.setResult(null);
                                XposedBridge.log(TAG + ": blocked QS tile handleClick while locked");
                            }
                        }
                    });
                    XposedBridge.log(TAG + ": hooked handleClick in " + tileCls);
                } catch (Throwable t) {
                    // fallback to click()
                    try {
                        XposedHelpers.findAndHookMethod(cls, "click", new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (isLocked(param.thisObject)) {
                                    param.setResult(null);
                                    XposedBridge.log(TAG + ": blocked QS tile click while locked");
                                }
                            }
                        });
                        XposedBridge.log(TAG + ": hooked click in " + tileCls);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {
                XposedBridge.log(TAG + ": QS tile class not found: " + tileCls);
            }
        }
    }

    // ---------- system_server hooks (power menu) ----------
    private void hookSystemServer(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pwm = lpparam.classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            XposedHelpers.findAndHookMethod(pwm, "showGlobalActions", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Object thisObj = param.thisObject;
                        Context ctx = getContextFromObject(thisObj);
                        if (ctx != null) {
                            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
                            if (km != null && km.isKeyguardLocked()) {
                                param.setResult(null);
                                XposedBridge.log(TAG + ": blocked showGlobalActions while locked");
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": error in showGlobalActions hook: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + ": hooked PhoneWindowManager.showGlobalActions");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": PhoneWindowManager hook failed: " + t);
        }
    }

    // Helper: is device locked (attempt to obtain Context and KeyguardManager)
    private boolean isLocked(Object any) {
        try {
            Context ctx = getContextFromObject(any);
            if (ctx == null) return false;
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    // Reflection helper to get Context from an object (common fields/getContext())
    private Context getContextFromObject(Object obj) {
        if (obj == null) return null;
        try {
            String[] fieldNames = new String[] { "mContext", "context", "mContextImpl" };
            for (String f : fieldNames) {
                try {
                    Field field = obj.getClass().getDeclaredField(f);
                    field.setAccessible(true);
                    Object val = field.get(obj);
                    if (val instanceof Context) return (Context) val;
                } catch (NoSuchFieldException ignored) {}
            }
            try {
                Method m = obj.getClass().getMethod("getContext");
                Object ret = m.invoke(obj);
                if (ret instanceof Context) return (Context) ret;
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }
}
