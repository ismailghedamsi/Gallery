package com.brandonhxrr.gallery

import android.content.Context
import android.content.SharedPreferences

object LastImagePreferences {
    private const val PREFS_NAME = "last_image_prefs"
    private const val KEY_LAST_IMAGE_PATH = "last_image_path"
    private const val KEY_LAST_IMAGE_POSITION = "last_image_position"
    private const val KEY_LAST_IMAGE_TIMESTAMP = "last_image_timestamp"
    private const val KEY_RESUME_ENABLED = "resume_enabled"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveLastImage(context: Context, imagePath: String, position: Int) {
        val prefs = getPreferences(context)
        prefs.edit().apply {
            putString(KEY_LAST_IMAGE_PATH, imagePath)
            putInt(KEY_LAST_IMAGE_POSITION, position)
            putLong(KEY_LAST_IMAGE_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }

    fun getLastImagePath(context: Context): String? {
        return getPreferences(context).getString(KEY_LAST_IMAGE_PATH, null)
    }

    fun getLastImagePosition(context: Context): Int {
        return getPreferences(context).getInt(KEY_LAST_IMAGE_POSITION, -1)
    }

    fun getLastImageTimestamp(context: Context): Long {
        return getPreferences(context).getLong(KEY_LAST_IMAGE_TIMESTAMP, 0)
    }

    fun setResumeEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_RESUME_ENABLED, enabled).apply()
    }

    fun isResumeEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_RESUME_ENABLED, true)
    }

    fun clearLastImage(context: Context) {
        val prefs = getPreferences(context)
        prefs.edit().apply {
            remove(KEY_LAST_IMAGE_PATH)
            remove(KEY_LAST_IMAGE_POSITION)
            remove(KEY_LAST_IMAGE_TIMESTAMP)
            apply()
        }
    }

    fun hasLastImage(context: Context): Boolean {
        val path = getLastImagePath(context)
        return !path.isNullOrEmpty() && java.io.File(path).exists()
    }
}

