package com.djay.touchmenot_mm;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
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
 * - Blocks the power menu (showGlobalActions) when locked, without blocking power button presses
 * - Intercepts the Power + Volume Up shortcut to bring up the main lock screen
 */
public class Hooks implements IXposedHookLoadPackage {
    private static final String TAG = "TouchMeNot_MM";

    // Static flag to track if the power button is being held down
    private static boolean isPowerKeyHeld = false;
    private static boolean isVolumeUpKeyHeld = false;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("android".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + ": android (system_server) loaded — hooking power menu and key events");
            hookPowerMenu(lpparam);
            hookKeyCombination(lpparam);
        }
    }

    /**
     * Hook the PhoneWindowManager.showGlobalActions to cancel power menu while locked.
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
                                    param.setResult(null);
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

    /**
     * Hook interceptKeyBeforeQueueing to handle key combinations.
     */
    private void hookKeyCombination(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pwm = lpparam.classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            try {
                XposedHelpers.findAndHookMethod(pwm, "interceptKeyBeforeQueueing", KeyEvent.class, int.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        KeyEvent event = (KeyEvent) param.args[0];
                        Context ctx = getContextFromObject(param.thisObject);

                        if (ctx != null && isLocked(param.thisObject)) {
                            int keyCode = event.getKeyCode();

                            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                if (keyCode == KeyEvent.KEYCODE_POWER) {
                                    isPowerKeyHeld = true;
                                    XposedBridge.log(TAG + ": Power key down");
                                }
                                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                    isVolumeUpKeyHeld = true;
                                    XposedBridge.log(TAG + ": Volume Up key down");
                                }

                                // Check for the combination
                                if (isPowerKeyHeld && isVolumeUpKeyHeld) {
                                    XposedBridge.log(TAG + ": Power + Volume Up combination detected while locked. Launching lock screen.");

                                    // Prevent default action (fingerprint pop-up)
                                    param.setResult(0);

                                    // Launch the main lock screen activity
                                    KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
                                    if (km != null) {
                                        Intent unlockIntent = km.createConfirmDeviceCredentialIntent(null, null);
                                        if (unlockIntent != null) {
                                            unlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                            ctx.startActivity(unlockIntent);
                                        }
                                    }
                                }
                            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                                if (keyCode == KeyEvent.KEYCODE_POWER) {
                                    isPowerKeyHeld = false;
                                    XposedBridge.log(TAG + ": Power key up");
                                }
                                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                    isVolumeUpKeyHeld = false;
                                    XposedBridge.log(TAG + ": Volume Up key up");
                                }
                            }
                        }
                    }
                });
                XposedBridge.log(TAG + ": hooked PhoneWindowManager.interceptKeyBeforeQueueing");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": interceptKeyBeforeQueueing hook failed: " + t.getMessage());
            }
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": PhoneWindowManager class not found: " + e.getMessage());
        }
    }

    // ---------- Helpers ----------

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

    /**
     * Dispatch user feedback: toast + robust reject-style double-tap vibration.
     * Uses two scheduled one-shot vibrations for maximum compatibility.
     */
    private void dispatchFeedback(Context ctx, String message) {
        if (ctx == null) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // Unified toast message (use passed message if non-empty)
                String toastText = (message != null && !message.isEmpty()) ? message : "Action blocked — unlock to access";
                Toast.makeText(ctx, toastText, Toast.LENGTH_SHORT).show();

                final Vibrator vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (vib == null) {
                    XposedBridge.log(TAG + ": dispatchFeedback - vibrator service null");
                    return;
                }

                try {
                    // Robust double-tap: two short vibrates separated by a short gap.
                    // Timings (ms): first pulse 45ms, gap ~120ms, second pulse 45ms.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        VibrationEffect first = VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE);
                        VibrationEffect second = VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE);

                        vib.vibrate(first); // first pulse
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                vib.vibrate(second); // second pulse
                                XposedBridge.log(TAG + ": dispatchFeedback - double-tap vib executed");
                            } catch (Throwable ignored) {}
                        }, 120); // delay between pulses (ms)
                    } else {
                        // Pre-O: fallback to pattern
                        long[] pattern = new long[]{0, 45, 120, 45};
                        vib.vibrate(pattern, -1);
                        XposedBridge.log(TAG + ": dispatchFeedback - pattern vib executed (pre-O)");
                    }
                } catch (Throwable vibErr) {
                    // Final fallback: single short vibration
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vib.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vib.vibrate(60);
                        }
                        XposedBridge.log(TAG + ": dispatchFeedback - fallback single vib executed");
                    } catch (Throwable ignored) {
                        XposedBridge.log(TAG + ": dispatchFeedback - all vib attempts failed: " + ignored);
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": dispatchFeedback failed: " + t.getMessage());
            }
        });
    }
}
