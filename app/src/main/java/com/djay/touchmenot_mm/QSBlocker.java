package com.djay.touchmenot_mm;

import android.app.KeyguardManager;
import android.content.Context;
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

        XposedBridge.log("QSBlocker: loaded into SystemUI");

        // 1) Hook the base tile click: QSTileImpl#click()
        try {
            Class<?> qstileImpl = XposedHelpers.findClass("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader);
            for (Method m : qstileImpl.getDeclaredMethods()) {
                if (!m.getName().equals("click")) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object thisObj = param.thisObject;
                            String instName = (thisObj != null) ? thisObj.getClass().getName() : "null";
                            Context ctx = getContextFromAny(thisObj);
                            if (ctx != null && isKeyguardLocked(ctx)) {
                                // If the tile instance is InternetTile, block it
                                if (instName != null && instName.contains("InternetTile")) {
                                    XposedBridge.log("QSBlocker: Blocking QSTileImpl#click for " + instName);
                                    param.setResult(null);
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("QSBlocker: error in QSTileImpl#click hook: " + t.getMessage());
                        }
                    }
                });
                XposedBridge.log("QSBlocker: Hooked QSTileImpl#click");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: failed to hook QSTileImpl#click: " + t.getMessage());
        }

        // 2) Hook InternetDialogControllerImpl#onUserClickedInternetDialog as a backup
        try {
            Class<?> internetController = XposedHelpers.findClass("com.android.systemui.qs.tiles.dialog.InternetDialogControllerImpl", lpparam.classLoader);
            Method target = null;
            for (Method m : internetController.getDeclaredMethods()) {
                if (m.getName().equals("onUserClickedInternetDialog")) { target = m; break; }
            }
            if (target != null) {
                Method methodToHook = target;
                XposedBridge.hookMethod(methodToHook, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Context ctx = getContextFromAny(param.thisObject);
                        if (ctx != null && isKeyguardLocked(ctx)) {
                            XposedBridge.log("QSBlocker: Blocking InternetDialogControllerImpl#onUserClickedInternetDialog");
                            param.setResult(null);
                        }
                    }
                });
                XposedBridge.log("QSBlocker: Hooked InternetDialogControllerImpl#onUserClickedInternetDialog");
            } else {
                XposedBridge.log("QSBlocker: InternetDialogControllerImpl#onUserClickedInternetDialog not found");
            }
        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: failed to hook InternetDialogControllerImpl: " + t.getMessage());
        }

        // 3) Hook android.view.View.performClick() globally inside SystemUI and check ancestry for footer/power button
        try {
            Class<?> viewClass = Class.forName("android.view.View");
            Method performClick = null;
            for (Method m : viewClass.getDeclaredMethods()) {
                if ("performClick".equals(m.getName()) && m.getParameterTypes().length == 0) {
                    performClick = m;
                    break;
                }
            }
            if (performClick != null) {
                Method finalPerformClick = performClick;
                XposedBridge.hookMethod(finalPerformClick, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object thisObj = param.thisObject;
                            if (!(thisObj instanceof View)) return;
                            View v = (View) thisObj;
                            Context ctx = v.getContext();
                            if (ctx != null && isKeyguardLocked(ctx)) {
                                // Check parent chain or owner class name for footer/power menu hints
                                String viewClassName = v.getClass().getName();
                                // Heuristic: power menu footer view class contains 'StatusBarFooter' or 'FooterView'
                                if (viewClassName != null && (viewClassName.contains("StatusBarFooter") || viewClassName.contains("FooterView") || viewClassName.contains("LargeScreenShadeFooterView"))) {
                                    XposedBridge.log("QSBlocker: Blocking footer power click on view class: " + viewClassName);
                                    param.setResult(false); // cancel click (performClick returns boolean)
                                    return;
                                }

                                // Also inspect parent (best-effort)
                                try {
                                    Object parent = XposedHelpers.callMethod(v, "getParent");
                                    if (parent != null) {
                                        String pcn = parent.getClass().getName();
                                        if (pcn != null && (pcn.contains("StatusBarFooter") || pcn.contains("FooterView") || pcn.contains("LargeScreenShadeFooterView"))) {
                                            XposedBridge.log("QSBlocker: Blocking footer click via parent class: " + pcn);
                                            param.setResult(false);
                                            return;
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("QSBlocker: error in View.performClick hook: " + t.getMessage());
                        }
                    }
                });
                XposedBridge.log("QSBlocker: Hooked View.performClick globally");
            } else {
                XposedBridge.log("QSBlocker: View.performClick method not found");
            }
        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: failed to hook View.performClick: " + t.getMessage());
        }

        // Keep existing working tile hooks for airplane & bluetooth (unchanged)
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.AirplaneModeTile", "handleClick");
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.BluetoothTile", "handleClickWithSatelliteCheck");
        } catch (Throwable ignored) {}
    }

    private void hookSimpleTile(XC_LoadPackage.LoadPackageParam lpparam, String className, String methodName) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            Method m = null;
            for (Method mm : clazz.getDeclaredMethods()) {
                if (mm.getName().equals(methodName)) { m = mm; break; }
            }
            if (m != null) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Context ctx = getContextFromAny(param.thisObject);
                        if (ctx != null && isKeyguardLocked(ctx)) {
                            XposedBridge.log("QSBlocker: Blocking " + className + "#" + methodName + " [LOCKED]");
                            param.setResult(null);
                        }
                    }
                });
                XposedBridge.log("QSBlocker: Hooked " + className + "#" + methodName);
            } else {
                XposedBridge.log("QSBlocker: method not found " + className + "#" + methodName);
            }
        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: failed to hookSimpleTile " + className + ": " + t.getMessage());
        }
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
            if (context instanceof Context) return (Context) context;
            try {
                Object possible = XposedHelpers.callMethod(obj, "getContext");
                if (possible instanceof Context) return (Context) possible;
            } catch (Throwable ignored) {}
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
