package com.djay.touchmenot_mm;

import android.app.KeyguardManager;
import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class RecorderHook implements IXposedHookLoadPackage {

    private static final String SYSTEMUI = "com.android.systemui";

    private static final String[] GLOBALACTIONS_CLASSES = new String[]{
            "com.android.systemui.globalactions.GlobalActionsComponent",
            "com.android.systemui.globalactions.GlobalActionsDialogLite",
            "com.android.systemui.globalactions.GlobalActionsDialogLite$ActionsDialogCallback",
            "com.android.systemui.globalactions.GlobalActionsDialogLite$ActionsDialog",
            "com.android.systemui.globalactions.GlobalActionsDialogLite$MyAdapter",
            "com.android.systemui.globalactions.GlobalActionsDialogLite$MyCallback",
            "com.android.systemui.globalactions.pipeline.GlobalActionsViewModel",
            "com.android.systemui.globalactions.pipeline.GlobalActionsRepo",
    };

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (!lpparam.packageName.equals(SYSTEMUI)) return;

        XposedBridge.log("[FooterHunt] Loaded — hooking GlobalActions chain…");

        for (String cls : GLOBALACTIONS_CLASSES) {
            try {
                Class<?> c = XposedHelpers.findClass(cls, lpparam.classLoader);

                for (final java.lang.reflect.Method m : c.getDeclaredMethods()) {

                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                            XposedBridge.log("[FooterHunt] CALL → " + cls + "#" + m.getName());

                            for (StackTraceElement st : Thread.currentThread().getStackTrace()) {
                                if (st.toString().contains("systemui"))
                                    XposedBridge.log("[FooterHunt]   at " + st);
                            }
                        }
                    });
                }

                XposedBridge.log("[FooterHunt] Hooked all methods in: " + cls);

            } catch (Throwable ignore) {}
        }
    }
}
