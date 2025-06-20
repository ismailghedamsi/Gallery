package com.brandonhxrr.gallery

import android.content.Context
import android.content.SharedPreferences

/**
 * Native preferences for album selection using MediaStore bucket IDs
 * Replaces file path based folder selection with bucket-based approach
 */
object NativeAlbumPreferences {
    private const val PREFS_NAME = "native_album_prefs"
    private const val KEY_FIRST_LAUNCH = "first_launch"
    private const val KEY_SELECTED_BUCKET_IDS = "selected_bucket_ids"
    private const val KEY_SHOW_ALL_ALBUMS = "show_all_albums"
    private const val KEY_USE_SAF_FOLDERS = "use_saf_folders"
    private const val KEY_SAF_FOLDER_URIS = "saf_folder_uris"
    
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
    
    fun shouldShowAllAlbums(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_ALL_ALBUMS, true)
    }
    
    fun setShowAllAlbums(context: Context, showAll: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_SHOW_ALL_ALBUMS, showAll)
            .apply()
    }
    
    /**
     * Get selected MediaStore bucket IDs
     */
    fun getSelectedBucketIds(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SELECTED_BUCKET_IDS, emptySet()) ?: emptySet()
    }
    
    /**
     * Set selected MediaStore bucket IDs
     */
    fun setSelectedBucketIds(context: Context, bucketIds: Set<String>) {
        getPrefs(context).edit()
            .putStringSet(KEY_SELECTED_BUCKET_IDS, bucketIds)
            .apply()
    }
    
    /**
     * Check if a bucket ID is selected for display
     */
    fun isBucketSelected(context: Context, bucketId: String): Boolean {
        if (shouldShowAllAlbums(context)) return true
        return getSelectedBucketIds(context).contains(bucketId)
    }
    
    /**
     * Add a bucket ID to selected albums
     */
    fun addSelectedBucket(context: Context, bucketId: String) {
        val currentBuckets = getSelectedBucketIds(context).toMutableSet()
        currentBuckets.add(bucketId)
        setSelectedBucketIds(context, currentBuckets)
    }
    
    /**
     * Remove a bucket ID from selected albums
     */
    fun removeSelectedBucket(context: Context, bucketId: String) {
        val currentBuckets = getSelectedBucketIds(context).toMutableSet()
        currentBuckets.remove(bucketId)
        setSelectedBucketIds(context, currentBuckets)
    }
    
    // SAF (Storage Access Framework) support
    fun isUsingSAFFolders(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_SAF_FOLDERS, false)
    }
    
    fun setUseSAFFolders(context: Context, useSAF: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_USE_SAF_FOLDERS, useSAF)
            .apply()
    }
    
    fun getSAFFolderUris(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SAF_FOLDER_URIS, emptySet()) ?: emptySet()
    }
    
    fun setSAFFolderUris(context: Context, uris: Set<String>) {
        getPrefs(context).edit()
            .putStringSet(KEY_SAF_FOLDER_URIS, uris)
            .apply()
    }
    
    fun addSAFFolderUri(context: Context, uri: String) {
        val currentUris = getSAFFolderUris(context).toMutableSet()
        currentUris.add(uri)
        setSAFFolderUris(context, currentUris)
    }
    
    /**
     * Clear all preferences (useful for reset/debugging)
     */
    fun clearAllPreferences(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
    
    /**
     * Migrate from old folder-based preferences to new bucket-based preferences
     */
    fun migrateFromFolderPreferences(context: Context, folderToBucketMap: Map<String, String>) {
        val oldPrefs = context.getSharedPreferences("folder_selection_prefs", Context.MODE_PRIVATE)
        val showAllFolders = oldPrefs.getBoolean("show_all_folders", true)
        val selectedFolders = oldPrefs.getStringSet("selected_folders", emptySet()) ?: emptySet()
        
        // Migrate to new preferences
        setShowAllAlbums(context, showAllFolders)
        
        if (!showAllFolders && selectedFolders.isNotEmpty()) {
            val bucketIds = selectedFolders.mapNotNull { folderPath ->
                folderToBucketMap[folderPath]
            }.toSet()
            setSelectedBucketIds(context, bucketIds)
        }
        
        setFirstLaunchCompleted(context)
        
        // Optionally clear old preferences
        // oldPrefs.edit().clear().apply()
    }
}

