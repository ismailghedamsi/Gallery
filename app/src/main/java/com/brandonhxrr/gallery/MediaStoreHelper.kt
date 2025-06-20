package com.brandonhxrr.gallery

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper class for native Android MediaStore operations
 * Replaces custom folder selection with native Android approaches
 */
class MediaStoreHelper(private val context: Context) {

    /**
     * Get all album folders using MediaStore bucket queries
     * This is more efficient than scanning filesystem
     */
    suspend fun getAlbumFolders(): List<AlbumFolder> = withContext(Dispatchers.IO) {
        val albumFolders = mutableListOf<AlbumFolder>()
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val albumMap = mutableMapOf<String, AlbumFolder>()

        // Query images
        queryMediaStore(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            sortOrder
        ) { cursor ->
            val bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID))
            val bucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
            val imagePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
            val imageId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            
            if (bucketId != null && bucketName != null && imagePath != null) {
                val folderPath = File(imagePath).parent ?: return@queryMediaStore
                
                if (!albumMap.containsKey(bucketId)) {
                    albumMap[bucketId] = AlbumFolder(
                        bucketId = bucketId,
                        name = bucketName,
                        path = folderPath,
                        coverImagePath = imagePath,
                        coverImageId = imageId,
                        imageCount = 1
                    )
                } else {
                    albumMap[bucketId]?.let { album ->
                        album.imageCount++
                    }
                }
            }
        }

        // Query videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED
        )

        queryMediaStore(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            sortOrder
        ) { cursor ->
            val bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID))
            val bucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME))
            val videoPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
            val videoId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
            
            if (bucketId != null && bucketName != null && videoPath != null) {
                val folderPath = File(videoPath).parent ?: return@queryMediaStore
                
                if (!albumMap.containsKey(bucketId)) {
                    albumMap[bucketId] = AlbumFolder(
                        bucketId = bucketId,
                        name = bucketName,
                        path = folderPath,
                        coverImagePath = videoPath,
                        coverImageId = videoId,
                        imageCount = 1
                    )
                } else {
                    albumMap[bucketId]?.let { album ->
                        album.imageCount++
                    }
                }
            }
        }

        albumFolders.addAll(albumMap.values)
        albumFolders.sortedBy { it.name }
    }

    /**
     * Get images from specific album folders using MediaStore
     */
    suspend fun getImagesFromAlbums(selectedBucketIds: Set<String>): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaItems = mutableListOf<MediaItem>()
        
        if (selectedBucketIds.isEmpty()) {
            return@withContext mediaItems
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Images.Media.BUCKET_ID} IN (${selectedBucketIds.joinToString(",") { "?" }})"
        val selectionArgs = selectedBucketIds.toTypedArray()
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        // Query images
        queryMediaStore(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        ) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
            val bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID))
            val bucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
            val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE))
            
            mediaItems.add(MediaItem(
                id = id,
                path = path,
                bucketId = bucketId,
                bucketName = bucketName,
                dateAdded = dateAdded,
                mimeType = mimeType,
                isVideo = false
            ))
        }

        // Query videos with same folder filter
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE
        )

        queryMediaStore(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            selection,
            selectionArgs,
            sortOrder
        ) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
            val bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID))
            val bucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME))
            val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED))
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE))
            
            mediaItems.add(MediaItem(
                id = id,
                path = path,
                bucketId = bucketId,
                bucketName = bucketName,
                dateAdded = dateAdded,
                mimeType = mimeType,
                isVideo = true
            ))
        }

        mediaItems.sortedByDescending { it.dateAdded }
    }

    /**
     * Get all images from a specific album/folder
     */
    suspend fun getImagesFromFolder(bucketId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        getImagesFromAlbums(setOf(bucketId))
    }

    private inline fun queryMediaStore(
        uri: Uri,
        projection: Array<String>,
        sortOrder: String? = null,
        crossinline onEachRow: (Cursor) -> Unit
    ) {
        queryMediaStore(uri, projection, null, null, sortOrder, onEachRow)
    }

    private inline fun queryMediaStore(
        uri: Uri,
        projection: Array<String>,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
        crossinline onEachRow: (Cursor) -> Unit
    ) {
        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                onEachRow(cursor)
            }
        }
    }
}

/**
 * Represents an album folder
 */
data class AlbumFolder(
    val bucketId: String,
    val name: String,
    val path: String,
    val coverImagePath: String,
    val coverImageId: Long,
    var imageCount: Int
)

/**
 * Represents a media item (image or video)
 */
data class MediaItem(
    val id: Long,
    val path: String,
    val bucketId: String,
    val bucketName: String,
    val dateAdded: Long,
    val mimeType: String,
    val isVideo: Boolean
)

/**
 * Storage Access Framework helper for folder selection
 */
class SAFHelper {
    companion object {
        const val REQUEST_CODE_SELECT_FOLDER = 1001
        
        /**
         * Launch Storage Access Framework folder picker
         */
        fun selectFolderWithSAF(activity: AppCompatActivity, launcher: ActivityResultLauncher<Intent>) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            launcher.launch(intent)
        }
        
        /**
         * Get folder path from SAF result - FIXED for SD card support
         */
        fun getFolderPathFromSAF(uri: Uri): String? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val split = docId.split(":")
                if (split.size >= 2) {
                    val volumeId = split[0]
                    val path = split[1]
                    
                    // Check if it's SD card or internal storage
                    return when {
                        volumeId == "primary" -> "/storage/emulated/0/$path"
                        volumeId.matches(Regex("[0-9A-F]{4}-[0-9A-F]{4}")) -> "/storage/$volumeId/$path"
                        else -> "/storage/$volumeId/$path" // Fallback for other storage types
                    }
                }
            }
            return null
        }
    }
}

