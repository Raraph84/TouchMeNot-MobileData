package com.djay.touchmenot_mm

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log

object PrefsBridge {
    private const val PREFS_NAME = "tmn_prefs"
    private const val AUTH = "com.djay.touchmenot_mm.preferences"
    val FLAGS_URI: Uri = Uri.parse("content://$AUTH/flags")

    fun makeWorldReadable(ctx: Context) {
        // For ContentProvider approach, ensure prefs live in device-protected storage for early access.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val dp = ctx.applicationContext.createDeviceProtectedStorageContext()
                // Move SharedPreferences from credential-protected storage (app) to device-protected storage (dp)
                dp.moveSharedPreferencesFrom(ctx.applicationContext, PREFS_NAME)
            } catch (t: Throwable) {
                Log.w("PrefsBridge", "DPS move failed: ${t.message}")
            }
        }
        // Initialize defaults if missing
        val p = prefs(ctx)
        if (!p.contains("tmn_initialized")) {
            p.edit()
                .putBoolean("tmn_initialized", true)
                .putBoolean("tmn_block_airplane", true)
                .putBoolean("tmn_block_hotspot", true)
                .putBoolean("tmn_block_internet", true)
                .putBoolean("tmn_block_bluetooth", true)
                .putBoolean("tmn_block_power_controls", true)
                .apply()
            notifyChange(ctx)
        }
    }

    fun prefs(ctx: Context) =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ctx.applicationContext.createDeviceProtectedStorageContext() else ctx.applicationContext)
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(key, value).apply()
        notifyChange(ctx)
    }

    fun notifyChange(ctx: Context) {
        try {
            ctx.applicationContext.contentResolver.notifyChange(FLAGS_URI, null)
        } catch (_: Throwable) {}
    }
}
