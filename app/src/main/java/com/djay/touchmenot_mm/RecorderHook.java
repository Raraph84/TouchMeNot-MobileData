package com.djay.touchmenot_mm;

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
 * TouchMeNot_MM - Classname Recorder
 *
 * Logs all classes/methods of SystemUI + Keyguard related to:
 * - NotificationPanelView
 * - ShadeViewController
 * - StatusBar
 * - KeyguardHostView / SecurityContainer
 */
public class RecorderHook implements IXposedHookLoadPackage {
    // Standard tag for general logging
    private static final String TAG = "TouchMeNot_Recorder";
    // Unique tag for specific debugging logs
    private static final String DEBUG_TAG = "TouchMeNot_DEBUG";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)
                && !"com.android.keyguard".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + ": Loaded package: " + lpparam.packageName);

        String[] classesToCheck = {
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

        for (String clsName : classesToCheck) {
            hookClassMethods(lpparam, clsName);
        }
    }

    private void hookClassMethods(final XC_LoadPackage.LoadPackageParam lpparam, final String className) {
        try {
            Class<?> cls = XposedHelpers.findClass(className, lpparam.classLoader);
            XposedBridge.log(DEBUG_TAG + ": FOUND CLASS -> " + className);

            // Log all declared methods with their full signature
            Method[] methods = cls.getDeclaredMethods();
            for (Method m : methods) {
                XposedBridge.log(DEBUG_TAG + ": Method -> " + m.toString());
            }

            // Hook onTouchEvent and onInterceptTouchEvent with more detail
            tryHookBooleanMethod(className, lpparam, "onInterceptTouchEvent");
            tryHookBooleanMethod(className, lpparam, "onTouchEvent");
        } catch (Throwable t) {
            XposedBridge.log(DEBUG_TAG + ": CLASS NOT FOUND -> " + className + " : " + t.getMessage());
        }
    }

    private void tryHookBooleanMethod(final String className,
                                      final XC_LoadPackage.LoadPackageParam lpparam,
                                      final String methodName) {
        try {
            XposedHelpers.findAndHookMethod(className, lpparam.classLoader, methodName, MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    MotionEvent ev = (MotionEvent) param.args[0];
                    XposedBridge.log(DEBUG_TAG + ": TOUCH EVENT in " + className + "." + methodName + " -> " + ev.toString());
                }
            });
            XposedBridge.log(DEBUG_TAG + ": HOOKED TOUCH -> " + className + "." + methodName);
        } catch (Throwable t) {
            XposedBridge.log(DEBUG_TAG + ": FAILED HOOK -> " + className + "." + methodName + " : " + t.getMessage());
        }
    }
}