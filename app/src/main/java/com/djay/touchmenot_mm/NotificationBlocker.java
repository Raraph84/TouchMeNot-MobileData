package com.djay.touchmenot_mm;

import android.app.KeyguardManager;
import android.content.Context;
import android.view.MotionEvent;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NotificationBlocker implements IXposedHookLoadPackage {
    private static final String TAG = "TouchMeNot_Blocker";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + ": SystemUI loaded, installing precise hooks.");

        final String[] classesToHook = {
                "com.android.systemui.statusbar.phone.StatusBar",
                "com.android.systemui.statusbar.phone.StatusBarWindowView",
                "com.android.systemui.statusbar.phone.PhoneStatusBar",
                "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
                "com.android.systemui.statusbar.NotificationPanelView",
                "com.android.systemui.statusbar.phone.NotificationPanelViewController",
                "com.android.systemui.qs.QSPanel",
                "com.android.systemui.qs.QSContainerImpl",
                "com.android.systemui.qs.QSFragment",
                "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout",
                "com.android.systemui.statusbar.phone.PanelView"
        };

        // Method names that handle touch events for panel expansion.
        final String[] touchMethods = {
                "onInterceptTouchEvent",
                "onTouchEvent"
        };

        for (final String className : classesToHook) {
            for (final String methodName : touchMethods) {
                try {
                    XposedHelpers.findAndHookMethod(className, lpparam.classLoader, methodName, MotionEvent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Context ctx = getContextFromAny(param.thisObject);
                            if (isKeyguardLocked(ctx)) {
                                XposedBridge.log(TAG + ": ⛔ BLOCKED " + className + "." + methodName);
                                param.setResult(true); // Suppress the touch event
                            }
                        }
                    });
                    XposedBridge.log(TAG + ": ✅ HOOKED " + className + "." + methodName);
                } catch (Throwable t) {
                    // This is expected for some classes that don't have this method.
                    XposedBridge.log(TAG + ": ❌ FAILED to hook " + className + "." + methodName + ": " + t.getMessage());
                }
            }
        }

        XposedBridge.log(TAG + ": ✅ All identified classes hooked successfully.");
    }

    /**
     * Finds and returns a Context object from the hooked instance.
     * This utility is needed for the KeyguardManager check.
     */
    private Context getContextFromAny(Object obj) {
        if (obj == null) return null;
        try {
            return (Context) XposedHelpers.callMethod(obj, "getContext");
        } catch (Throwable t) {
            try {
                return (Context) XposedHelpers.getObjectField(obj, "mContext");
            } catch (Throwable e) {
                return null;
            }
        }
    }

    /**
     * Checks if the device's keyguard is currently locked.
     */
    private boolean isKeyguardLocked(Context ctx) {
        if (ctx == null) return true;
        try {
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": isKeyguardLocked error: " + t.getMessage());
            return true;
        }
    }
}