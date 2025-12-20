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
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class VolPwrBlocker implements IXposedHookLoadPackage {
    private static final String TAG = "TouchMeNot_MM";
    private static final String ANDROID_PKG = "android";

    private static boolean isPowerKeyHeld = false;
    private static boolean isVolumeUpKeyHeld = false;
    // Feature flags come from PreferencesProvider via FeatureFlags

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!ANDROID_PKG.equals(lpparam.packageName)) return;
        Logger.hookSuccess("VolPwrBlocker:init");
        hookPowerMenu(lpparam);
        hookKeyCombination(lpparam);
    }

    private void hookPowerMenu(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pwm = lpparam.classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            XposedHelpers.findAndHookMethod(pwm, "showGlobalActions", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Context ctx = getContextFromObject(param.thisObject);
                        if (ctx == null) return;
                        FeatureFlags.ensureInitialized(ctx);
                        if (!FeatureFlags.blockPowerControls()) return;
                        KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
                        if (km != null && km.isKeyguardLocked()) {
                            param.setResult(null);
                            dispatchFeedback(ctx, "Unlock to access Power Menu");
                            Logger.blocked("PhoneWindowManager#showGlobalActions", "keyguard_locked");
                        }
                    } catch (Throwable t) {
                        Logger.error("PhoneWindowManager#showGlobalActions", t.getMessage());
                    }
                }
            });
            Logger.hookSuccess("PhoneWindowManager#showGlobalActions hooked");
        } catch (Throwable t) {
            Logger.hookFail("PhoneWindowManager#showGlobalActions", t.getMessage());
        }
    }

    private void hookKeyCombination(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pwm = lpparam.classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            XposedHelpers.findAndHookMethod(pwm, "interceptKeyBeforeQueueing", KeyEvent.class, int.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        KeyEvent event = (KeyEvent) param.args[0];
                        if (event == null) return;

                        if (!isLocked(param.thisObject)) return;

                        int keyCode = event.getKeyCode();
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            if (keyCode == KeyEvent.KEYCODE_POWER) isPowerKeyHeld = true;
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) isVolumeUpKeyHeld = true;

                            if (isPowerKeyHeld && isVolumeUpKeyHeld) {
                                Context ctx = getContextFromObject(param.thisObject);
                                if (ctx != null) FeatureFlags.ensureInitialized(ctx);
                                if (!FeatureFlags.blockPowerControls()) return;
                                param.setResult(0); // prevent default fingerprint/pop
                                Logger.blocked("Power+VolUp", "combination_detected_while_locked");
                                if (ctx != null) {
                                    KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
                                    if (km != null) {
                                        Intent i = km.createConfirmDeviceCredentialIntent(null, null);
                                        if (i != null) {
                                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                            ctx.startActivity(i);
                                            Logger.hookSuccess("ConfirmDeviceCredentialIntent started");
                                        }
                                    }
                                }
                            }
                        } else if (event.getAction() == KeyEvent.ACTION_UP) {
                            if (keyCode == KeyEvent.KEYCODE_POWER) isPowerKeyHeld = false;
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) isVolumeUpKeyHeld = false;
                        }
                    } catch (Throwable t) {
                        Logger.error("interceptKeyBeforeQueueing", t.getMessage());
                    }
                }
            });
            Logger.hookSuccess("PhoneWindowManager#interceptKeyBeforeQueueing hooked");
        } catch (Throwable t) {
            Logger.hookFail("PhoneWindowManager#interceptKeyBeforeQueueing", t.getMessage());
        }
    }

    private boolean isLocked(Object obj) {
        try {
            Context ctx = getContextFromObject(obj);
            if (ctx == null) return false;
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            Logger.error("isLocked", t.getMessage());
            return false;
        }
    }

    private Context getContextFromObject(Object obj) {
        if (obj == null) return null;
        try {
            String[] fields = new String[]{"mContext", "context", "mContextImpl"};
            for (String f : fields) {
                try {
                    Field field = obj.getClass().getDeclaredField(f);
                    field.setAccessible(true);
                    Object v = field.get(obj);
                    if (v instanceof Context) return (Context) v;
                } catch (Throwable ignored) {}
            }
            try {
                Method gm = obj.getClass().getMethod("getContext");
                Object r = gm.invoke(obj);
                if (r instanceof Context) return (Context) r;
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Logger.error("getContextFromObject", t.getMessage());
        }
        return null;
    }

    // No XSharedPreferences: flags read via FeatureFlags

    private void dispatchFeedback(Context ctx, String message) {
        if (ctx == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(ctx, message != null && !message.isEmpty() ? message : "Action blocked — unlock to access", Toast.LENGTH_SHORT).show();
                Vibrator vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (vib == null) return;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        VibrationEffect first = VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE);
                        VibrationEffect second = VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE);
                        vib.vibrate(first);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try { vib.vibrate(second); } catch (Throwable ignored) {}
                        }, 120);
                    } else {
                        long[] pattern = new long[]{0, 45, 120, 45};
                        vib.vibrate(pattern, -1);
                    }
                } catch (Throwable vibErr) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vib.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vib.vibrate(60);
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                Logger.error("dispatchFeedback", t.getMessage());
            }
        });
    }
}
