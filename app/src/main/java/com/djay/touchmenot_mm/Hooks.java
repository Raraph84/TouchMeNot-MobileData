package com.djay.touchmenot_mm;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * TouchMeNot_MM - Safe, robust hooks
 * - Blocks quick settings tile actions while device is locked (multiple method names)
 * - Provides safe feedback: vibration + toast + immediate unlock prompt (dispatched on main thread)
 * - Blocks the power menu (showGlobalActions) when locked, without blocking power button presses
 */
public class Hooks implements IXposedHookLoadPackage {
    private static final String TAG = "TouchMeNot_MM";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.android.systemui".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + ": SystemUI loaded — hooking QS tiles");
            hookQSTiles(lpparam);
        } else if ("android".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + ": android (system_server) loaded — hooking power menu");
            hookPowerMenu(lpparam);
        }
    }

    /**
     * Hook QS tiles and cancel their actions when the device is locked.
     * Cancel is immediate (param.setResult(null)). Feedback/Prompt dispatched to main thread.
     */
    private void hookQSTiles(final XC_LoadPackage.LoadPackageParam lpparam) {
        String[] tileCandidates = new String[]{
                "com.android.systemui.qs.tileimpl.QSTileImpl",
                "com.android.systemui.qs.tileimpl.QSTile"
        };

        // methods to try — covers a wide range of ROMs/versions
        final String[] methods = new String[]{
                "handleClick",
                "click",
                "handleSecondaryClick",
                "handleLongClick"
        };

        for (String tileCls : tileCandidates) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(tileCls);
                XposedBridge.log(TAG + ": found tile class candidate: " + tileCls);

                // single XC_MethodHook instance used for all method names (safer memory usage)
                XC_MethodHook blocker = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            if (isLocked(param.thisObject)) {
                                // 1) Cancel the tile action immediately so SystemUI doesn't toggle state
                                param.setResult(null);

                                // 2) Find a Context (defensive)
                                Context ctx = getContextFromObject(param.thisObject);
                                if (ctx != null) {
                                    // 3) Dispatch feedback & prompt on UI thread (safe)
                                    dispatchFeedback(ctx, "Unlock to toggle Quick Setting");
                                } else {
                                    XposedBridge.log(TAG + ": blocked tile but no context found for feedback");
                                }

                                // 4) Log which method blocked it (helps debugging)
                                XposedBridge.log(TAG + ": blocked QS tile interaction in " +
                                        param.method.getName());
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": error in QS tile blocker: " + t);
                        }
                    }
                };

                // Try each method name
                for (String m : methods) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, m, blocker);
                        XposedBridge.log(TAG + ": hooked " + tileCls + "." + m);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": method not present " + tileCls + "." + m + " -> " + t.getMessage());
                    }
                }
            } catch (Throwable e) {
                XposedBridge.log(TAG + ": tile class not found: " + tileCls + " -> " + e.getMessage());
            }
        }

        // Extra precaution: some tiles start activities dismissing keyguard — block those too
        try {
            Class<?> host = lpparam.classLoader.loadClass("com.android.systemui.qs.QSTileHost");
            try {
                XposedHelpers.findAndHookMethod(host,
                        "startActivityDismissingKeyguard",
                        android.content.Intent.class,
                        boolean.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    if (isLocked(param.thisObject)) {
                                        param.setResult(null);
                                        XposedBridge.log(TAG + ": blocked QSTileHost.startActivityDismissingKeyguard while locked");
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + ": error in QSTileHost hook: " + t);
                                }
                            }
                        });
                XposedBridge.log(TAG + ": hooked QSTileHost.startActivityDismissingKeyguard");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": QSTileHost.startActivityDismissingKeyguard not present: " + t.getMessage());
            }
        } catch (Throwable ignore) {
            // class not available on some ROMs — that's ok
            XposedBridge.log(TAG + ": QSTileHost class not present on this ROM");
        }
    }

    /**
     * Hook the PhoneWindowManager.showGlobalActions to cancel power menu while locked.
     * This blocks the menu but does NOT block the power button behavior itself.
     */
    private void hookPowerMenu(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pwm = lpparam.classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            try {
                XposedHelpers.findAndHookMethod(pwm, "showGlobalActions", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Context ctx = getContextFromObject(param.thisObject);
                            if (ctx != null) {
                                KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
                                if (km != null && km.isKeyguardLocked()) {
                                    // Cancel the power menu (safe)
                                    param.setResult(null);

                                    // Feedback (UI-thread)
                                    dispatchFeedback(ctx, "Unlock to access Power Menu");
                                    XposedBridge.log(TAG + ": blocked showGlobalActions while locked");
                                }
                            } else {
                                XposedBridge.log(TAG + ": showGlobalActions - no context found");
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": error in showGlobalActions hook: " + t);
                        }
                    }
                });
                XposedBridge.log(TAG + ": hooked PhoneWindowManager.showGlobalActions");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": showGlobalActions hook failed: " + t.getMessage());
            }
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": PhoneWindowManager class not found: " + e.getMessage());
        }
    }

    // ---------- Helpers ----------

    // Determine whether device is keyguard-locked using a Context extracted from obj
    private boolean isLocked(Object obj) {
        try {
            Context ctx = getContextFromObject(obj);
            if (ctx == null) return false;
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": isLocked error: " + t.getMessage());
            return false;
        }
    }

    // Reflection attempts to extract a Context from an object
    private Context getContextFromObject(Object obj) {
        if (obj == null) return null;
        try {
            String[] names = new String[] { "mContext", "context", "mContextImpl" };
            for (String n : names) {
                try {
                    Field f = obj.getClass().getDeclaredField(n);
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v instanceof Context) return (Context) v;
                } catch (NoSuchFieldException ignored) {}
            }
            try {
                Method gm = obj.getClass().getMethod("getContext");
                Object r = gm.invoke(obj);
                if (r instanceof Context) return (Context) r;
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": getContextFromObject error: " + t.getMessage());
        }
        return null;
    }

    // Dispatch toast/vibration/unlock prompt on main UI thread safely
    private void dispatchFeedback(Context ctx, String message) {
        if (ctx == null) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // Toast
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();

                // Vibration
                Vibrator vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (vib != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vib.vibrate(70);
                    }
                }

                // Prompt confirm-device-credential (PIN/Pattern/biometric)
                try {
                    KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
                    if (km != null && km.isKeyguardLocked()) {
                        Intent intent = km.createConfirmDeviceCredentialIntent(null, null);
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            ctx.startActivity(intent);
                        }
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": start credential intent failed: " + t.getMessage());
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": dispatchFeedback failed: " + t.getMessage());
            }
        });
    }
}
