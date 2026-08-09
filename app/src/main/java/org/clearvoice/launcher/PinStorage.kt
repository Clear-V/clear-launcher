package org.clearvoice.launcher

import android.content.Context

object PinStorage {
    private const val PREFS_NAME = "clear_launcher_prefs"
    private const val KEY_PIN = "caregiver_pin"
    private const val KEY_ENABLED_APPS = "enabled_apps"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    private const val KEY_RECOVERY_CODE = "recovery_code"
    private const val KEY_PERMANENTLY_LOCKED = "permanently_locked"
    private const val KEY_PIN_SCRAMBLE = "pin_scramble"
    private const val KEY_ICON_SIZE = "icon_size"
    private const val KEY_TEXT_SIZE = "text_size"
    private const val KEY_ANIMATIONS = "animations"
    private const val KEY_WALLPAPER_TYPE = "wallpaper_type"
    private const val KEY_WALLPAPER_COLOR = "wallpaper_color"
    private const val KEY_WALLPAPER_URI = "wallpaper_uri"
    private const val KEY_THEME = "theme"
    private const val KEY_SCRIM_OPACITY = "scrim_opacity"
    private const val KEY_PIN_CHANGE_COUNT = "pin_change_count"
    private const val KEY_PIN_CHANGE_WINDOW_START = "pin_change_window_start"
    private const val KEY_GRID_COLUMNS = "grid_columns"

    private const val MAX_ATTEMPTS_BEFORE_PERMANENT = 10
    private const val MIN_PIN_LENGTH = 6
    private const val MAX_PIN_CHANGES_PER_DAY = 3

    // ── PIN ───────────────────────────────────────────────────────────────────

    fun getPin(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PIN, "") ?: ""
    }

    fun setPin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun isPinSet(context: Context): Boolean = getPin(context).length >= MIN_PIN_LENGTH

    fun getMinPinLength(): Int = MIN_PIN_LENGTH

    // ── Apps ──────────────────────────────────────────────────────────────────

    fun getEnabledApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_ENABLED_APPS, emptySet()) ?: emptySet()
    }

    fun setEnabledApps(context: Context, packageNames: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_ENABLED_APPS, packageNames).apply()
    }

    fun isFirstRun(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_ENABLED_APPS, null) == null
    }

    // ── Lockout ───────────────────────────────────────────────────────────────

    fun getFailedAttempts(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    fun recordFailedAttempt(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val attempts = getFailedAttempts(context) + 1
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()
        val lockoutMs = when (attempts) {
            4 -> 30_000L
            5 -> 120_000L
            6 -> 300_000L
            7 -> 900_000L
            in 8..9 -> 3_600_000L
            else -> 0L
        }
        if (lockoutMs > 0) {
            prefs.edit().putLong(KEY_LOCKOUT_UNTIL,
                System.currentTimeMillis() + lockoutMs).apply()
        }
        if (attempts >= MAX_ATTEMPTS_BEFORE_PERMANENT) {
            prefs.edit().putBoolean(KEY_PERMANENTLY_LOCKED, true).apply()
        }
        return attempts
    }

    fun resetFailedAttempts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .putBoolean(KEY_PERMANENTLY_LOCKED, false)
            .apply()
    }

    fun isPermanentlyLocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PERMANENTLY_LOCKED, false)
    }

    fun getLockoutUntil(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
    }

    fun isLockedOut(context: Context): Boolean {
        if (isPermanentlyLocked(context)) return true
        return System.currentTimeMillis() < getLockoutUntil(context)
    }

    fun getRemainingLockoutMs(context: Context): Long =
        maxOf(0L, getLockoutUntil(context) - System.currentTimeMillis())

    fun shouldShowRecoveryOption(context: Context): Boolean {
        if (isPermanentlyLocked(context)) return true
        return getFailedAttempts(context) >= 6 && isLockedOut(context)
    }

    // ── Recovery Code ─────────────────────────────────────────────────────────

    fun generateRecoveryCode(context: Context): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = "CLEAR-" +
                (1..4).map { chars.random() }.joinToString("") + "-" +
                (1..4).map { chars.random() }.joinToString("")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RECOVERY_CODE, code).apply()
        return code
    }

    fun validateRecoveryCode(context: Context, input: String): Boolean =
        input.trim().uppercase() == context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECOVERY_CODE, "") ?: ""

    // ── Device ID ─────────────────────────────────────────────────────────────

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
                .replace("-", "").take(12).uppercase()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    // ── PIN Change Rate Limiting ───────────────────────────────────────────────

    fun canChangePin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val windowStart = prefs.getLong(KEY_PIN_CHANGE_WINDOW_START, 0L)
        val count = prefs.getInt(KEY_PIN_CHANGE_COUNT, 0)
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        return if (now - windowStart > oneDayMs) {
            prefs.edit()
                .putLong(KEY_PIN_CHANGE_WINDOW_START, now)
                .putInt(KEY_PIN_CHANGE_COUNT, 0).apply()
            true
        } else count < MAX_PIN_CHANGES_PER_DAY
    }

    fun recordPinChange(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val windowStart = prefs.getLong(KEY_PIN_CHANGE_WINDOW_START, 0L)
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        if (now - windowStart > oneDayMs) {
            prefs.edit()
                .putLong(KEY_PIN_CHANGE_WINDOW_START, now)
                .putInt(KEY_PIN_CHANGE_COUNT, 1).apply()
        } else {
            val count = prefs.getInt(KEY_PIN_CHANGE_COUNT, 0)
            prefs.edit().putInt(KEY_PIN_CHANGE_COUNT, count + 1).apply()
        }
    }

    fun getPinChangesRemaining(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val windowStart = prefs.getLong(KEY_PIN_CHANGE_WINDOW_START, 0L)
        val count = prefs.getInt(KEY_PIN_CHANGE_COUNT, 0)
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        return if (now - windowStart > oneDayMs) MAX_PIN_CHANGES_PER_DAY
        else MAX_PIN_CHANGES_PER_DAY - count
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    fun hasCompletedSetup(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("setup_complete", false)
    }

    fun markSetupComplete(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("setup_complete", true).apply()
    }

    // ── Appearance ────────────────────────────────────────────────────────────

    fun getPinScramble(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PIN_SCRAMBLE, false)
    }

    fun setPinScramble(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PIN_SCRAMBLE, enabled).apply()
    }

    fun getAnimations(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ANIMATIONS, true)
    }

    fun setAnimations(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ANIMATIONS, enabled).apply()
    }

    fun getIconSize(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ICON_SIZE, "medium") ?: "medium"
    }

    fun setIconSize(context: Context, size: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ICON_SIZE, size).apply()
    }

    fun getTextSize(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TEXT_SIZE, "medium") ?: "medium"
    }

    fun setTextSize(context: Context, size: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TEXT_SIZE, size).apply()
    }

    fun getGridColumns(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_GRID_COLUMNS, 3)
    }

    fun setGridColumns(context: Context, columns: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_GRID_COLUMNS, columns).apply()
    }

    fun getWallpaperType(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WALLPAPER_TYPE, "solid") ?: "solid"
    }

    fun getWallpaperColor(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_WALLPAPER_COLOR, 0xFF2C2416L)
    }

    fun setWallpaperSolid(context: Context, colorLong: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_WALLPAPER_TYPE, "solid")
            .putLong(KEY_WALLPAPER_COLOR, colorLong)
            .apply()
    }

    fun getWallpaperUri(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WALLPAPER_URI, "") ?: ""
    }

    fun setWallpaperGallery(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_WALLPAPER_TYPE, "gallery")
            .putString(KEY_WALLPAPER_URI, uri)
            .apply()
    }

    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, "dark") ?: "dark"
    }

    fun setTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun getScrimOpacity(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_SCRIM_OPACITY, 0f)
    }

    fun setScrimOpacity(context: Context, opacity: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_SCRIM_OPACITY, opacity).apply()
    }
// ── Onboarding ────────────────────────────────────────────────────────────

    fun hasSeenOnboarding(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("onboarding_seen", false)
    }

    fun markOnboardingSeen(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_seen", true).apply()
    }

} 
