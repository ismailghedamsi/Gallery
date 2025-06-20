package com.brandonhxrr.gallery

import android.content.Context
import android.content.SharedPreferences
import java.io.File

object FolderSelectionPreferences {
    private const val PREFS_NAME = "folder_selection_prefs"
    private const val KEY_FIRST_LAUNCH = "first_launch"
    private const val KEY_SELECTED_FOLDERS = "selected_folders"
    private const val KEY_SHOW_ALL_FOLDERS = "show_all_folders"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun isFirstLaunch(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FIRST_LAUNCH, true)
    }
    
    fun setFirstLaunchCompleted(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_FIRST_LAUNCH, false)
            .apply()
    }
    
    fun shouldShowAllFolders(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_ALL_FOLDERS, true)
    }
    
    fun setShowAllFolders(context: Context, showAll: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_SHOW_ALL_FOLDERS, showAll)
            .apply()
    }
    
    fun getSelectedFolders(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SELECTED_FOLDERS, emptySet()) ?: emptySet()
    }
    
    fun setSelectedFolders(context: Context, folders: Set<String>) {
        getPrefs(context).edit()
            .putStringSet(KEY_SELECTED_FOLDERS, folders)
            .apply()
    }
    
    fun addSelectedFolder(context: Context, folderPath: String) {
        val currentFolders = getSelectedFolders(context).toMutableSet()
        currentFolders.add(folderPath)
        setSelectedFolders(context, currentFolders)
    }
    
    fun removeSelectedFolder(context: Context, folderPath: String) {
        val currentFolders = getSelectedFolders(context).toMutableSet()
        currentFolders.remove(folderPath)
        setSelectedFolders(context, currentFolders)
    }
    
    fun isSelectedFolder(context: Context, folderPath: String): Boolean {
        if (shouldShowAllFolders(context)) return true
        return getSelectedFolders(context).contains(folderPath)
    }
}

