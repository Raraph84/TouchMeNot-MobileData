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

        XposedBridge.log("QSBlocker: Loaded into SystemUI");

        /* ---------------------------------------------------------------
         * 1) BLOCK BASE TILE CLICKS (INTERNET TILE TARGETED)
         * --------------------------------------------------------------- */
        try {
            Class<?> qstileImpl = XposedHelpers.findClass(
                    "com.android.systemui.qs.tileimpl.QSTileImpl",
                    lpparam.classLoader
            );

            for (Method m : qstileImpl.getDeclaredMethods()) {
                if (!m.getName().equals("click")) continue;

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object tileObj = param.thisObject;
                            String tileClass = tileObj.getClass().getName();
                            Context ctx = getContextFromAny(tileObj);

                            if (ctx != null && isKeyguardLocked(ctx)) {
                                // Block Internet tile only
                                if (tileClass.contains("InternetTile")) {
                                    XposedBridge.log("QSBlocker: Blocked InternetTile click");
                                    param.setResult(null);
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("QSBlocker: Error in QSTileImpl#click hook: " + t.getMessage());
                        }
                    }
                });

                XposedBridge.log("QSBlocker: Hooked QSTileImpl#click");
                break;
            }

        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: FAILED to hook QSTileImpl#click: " + t.getMessage());
        }


        /* ---------------------------------------------------------------
         * 2) BLOCK INTERNET DIALOG OPEN (SECONDARY SAFETY NET)
         * --------------------------------------------------------------- */
        try {
            Class<?> dialogCtrl = XposedHelpers.findClass(
                    "com.android.systemui.qs.tiles.dialog.InternetDialogControllerImpl",
                    lpparam.classLoader
            );

            Method target = null;
            for (Method m : dialogCtrl.getDeclaredMethods()) {
                if (m.getName().equals("onUserClickedInternetDialog")) {
                    target = m;
                    break;
                }
            }

            if (target != null) {
                XposedBridge.hookMethod(target, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Context ctx = getContextFromAny(param.thisObject);
                        if (ctx != null && isKeyguardLocked(ctx)) {
                            XposedBridge.log("QSBlocker: Blocked InternetDialogControllerImpl#onUserClickedInternetDialog");
                            param.setResult(null);
                        }
                    }
                });

                XposedBridge.log("QSBlocker: Hooked InternetDialogControllerImpl#onUserClickedInternetDialog");
            } else {
                XposedBridge.log("QSBlocker: onUserClickedInternetDialog not found");
            }

        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: FAILED to hook InternetDialogControllerImpl: " + t.getMessage());
        }


        /* ---------------------------------------------------------------
         * 3) BLOCK FOOTER POWER MENU BUTTON
         *    — Confirmed classname from your foothunt logs:
         *      com.android.systemui.globalactions.GlobalActionsDialogLite
         * --------------------------------------------------------------- */
        try {
            Class<?> globalActionsLite = XposedHelpers.findClass(
                    "com.android.systemui.globalactions.GlobalActionsDialogLite",
                    lpparam.classLoader
            );

            // Methods we will block
            String[] methodNames = new String[]{
                    "showOrHideDialog",
                    "showDialog"
            };

            for (String mname : methodNames) {
                try {
                    Method mm = null;
                    for (Method m : globalActionsLite.getDeclaredMethods()) {
                        if (m.getName().equals(mname)) {
                            mm = m;
                            break;
                        }
                    }

                    if (mm != null) {
                        XposedBridge.hookMethod(mm, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Context ctx = getContextFromAny(param.thisObject);
                                if (ctx != null && isKeyguardLocked(ctx)) {
                                    XposedBridge.log("QSBlocker: BLOCKED GlobalActionsDialogLite#" + mname + " (Footer Power Menu)");
                                    param.setResult(null); // stop dialog from opening
                                }
                            }
                        });

                        XposedBridge.log("QSBlocker: Hooked GlobalActionsDialogLite#" + mname);
                    } else {
                        XposedBridge.log("QSBlocker: Method not found: " + mname);
                    }

                } catch (Throwable inner) {
                    XposedBridge.log("QSBlocker: failed hooking power menu method " + mname + " : " + inner.getMessage());
                }
            }

        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: GlobalActionsDialogLite class NOT FOUND: " + t.getMessage());
        }


        /* ---------------------------------------------------------------
         * 4) Keep existing working tile hooks for airplane + bluetooth
         * --------------------------------------------------------------- */
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.AirplaneModeTile", "handleClick");
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.BluetoothTile", "handleClickWithSatelliteCheck");
        } catch (Throwable ignored) {}
    }


    /* =====================================================================
     * SIMPLE TILE HOOK HELPER
     * ===================================================================== */
    private void hookSimpleTile(XC_LoadPackage.LoadPackageParam lpparam, String className, String methodName) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);

            Method m = null;
            for (Method mm : clazz.getDeclaredMethods()) {
                if (mm.getName().equals(methodName)) {
                    m = mm;
                    break;
                }
            }

            if (m != null) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Context ctx = getContextFromAny(param.thisObject);
                        if (ctx != null && isKeyguardLocked(ctx)) {
                            XposedBridge.log("QSBlocker: BLOCKED " + className + "#" + methodName);
                            param.setResult(null);
                        }
                    }
                });

                XposedBridge.log("QSBlocker: Hooked " + className + "#" + methodName);
            } else {
                XposedBridge.log("QSBlocker: Method NOT found: " + className + "#" + methodName);
            }

        } catch (Throwable t) {
            XposedBridge.log("QSBlocker: FAILED simpleTile hook: " + t.getMessage());
        }
    }


    /* =====================================================================
     * CONTEXT HELPERS
     * ===================================================================== */
    private boolean isKeyguardLocked(Context ctx) {
        try {
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    private Context getContextFromAny(Object obj) {
        if (obj == null) return null;
        try {
            Object ctx = XposedHelpers.getObjectField(obj, "mContext");
            if (ctx instanceof Context) return (Context) ctx;
        } catch (Throwable ignored) {}

        try {
            Object ctx = XposedHelpers.callMethod(obj, "getContext");
            if (ctx instanceof Context) return (Context) ctx;
        } catch (Throwable ignored) {}

        return null;
    }
}
