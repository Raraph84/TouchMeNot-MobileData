package com.djay.touchmenot_mm

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build

/**
 * Read-only ContentProvider exposing module feature toggles.
 * Authority: com.djay.touchmenot_mm.preferences
 * Paths:
 *  - content://com.djay.touchmenot_mm.preferences/flags           -> rows: (key, value)
 *  - content://com.djay.touchmenot_mm.preferences/flags/<key>     -> single row
 */
class PreferencesProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.djay.touchmenot_mm.preferences"
        private const val PREFS_NAME = "tmn_prefs"
        private val ALL_KEYS = arrayOf(
            "tmn_block_internet",          // unified: tile + dialog
            "tmn_block_power_controls",    // unified: global actions + power+volup + footer
            "tmn_block_airplane",
            "tmn_block_bluetooth",
            "tmn_block_hotspot"
        )
    }

    override fun onCreate(): Boolean {
        // Ensure preferences live in device-protected storage for early availability
        val app = context?.applicationContext ?: return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val dpCtx = app.createDeviceProtectedStorageContext()
            // No-op move if already present
            app.moveSharedPreferencesFrom(dpCtx, PREFS_NAME)
        }
        // Migrate legacy keys into unified ones if missing
        try {
            val prefs = getPrefs(app)
            val edit = prefs.edit()
            if (!prefs.contains("tmn_block_internet")) {
                val v = prefs.getBoolean("tmn_block_internet_tile", true) ||
                        prefs.getBoolean("tmn_block_internet_dialog", true)
                edit.putBoolean("tmn_block_internet", v)
            }
            if (!prefs.contains("tmn_block_power_controls")) {
                val v = prefs.getBoolean("tmn_block_android_power_menu", true) ||
                        prefs.getBoolean("tmn_block_power_volup", true) ||
                        prefs.getBoolean("tmn_block_footer_power_menu", true)
                edit.putBoolean("tmn_block_power_controls", v)
            }
            edit.apply()
        } catch (_: Throwable) {}
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val app = context?.applicationContext ?: return null
        val prefs = getPrefs(app)
        val segments = uri.pathSegments
        val cursor = MatrixCursor(arrayOf("key", "value"))
        if (segments.isEmpty()) return cursor
        return try {
            when (segments[0]) {
                "flags" -> {
                    if (segments.size >= 2) {
                        val key = segments[1]
                        val v = prefs.getBoolean(key, defaultForKey(key))
                        cursor.addRow(arrayOf(key, if (v) 1 else 0))
                    } else {
                        for (k in ALL_KEYS) {
                            val v = prefs.getBoolean(k, defaultForKey(k))
                            cursor.addRow(arrayOf(k, if (v) 1 else 0))
                        }
                    }
                    cursor
                }
                else -> cursor
            }
        } catch (_: Throwable) {
            cursor
        }
    }

    override fun getType(uri: Uri): String? {
        val segments = uri.pathSegments
        return if (segments.isNotEmpty() && segments[0] == "flags") {
            if (segments.size >= 2) "vnd.android.cursor.item/flag" else "vnd.android.cursor.dir/flags"
        } else null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getPrefs(ctx: Context): SharedPreferences {
        val dpCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ctx.createDeviceProtectedStorageContext() else ctx
        return dpCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun defaultForKey(key: String): Boolean {
        // Default: all blocking features enabled
        return true
    }
}
