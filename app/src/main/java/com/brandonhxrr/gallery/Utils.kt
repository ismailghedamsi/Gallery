package com.brandonhxrr.gallery

import android.annotation.SuppressLint
import android.app.RecoverableSecurityException
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import java.io.File
import java.util.*

val imageExtensions = arrayOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
val videoExtensions = arrayOf("mp4", "mkv", "avi", "wmv", "mov", "flv", "webm", "ogg", "ogv")
val fileExtensions = imageExtensions.plus(videoExtensions)
var selectable = false

var albumes: HashMap<File, List<File>>? = null
var itemsList = mutableListOf<Photo>()

fun sortImagesByFolder(files: List<File>): Map<File, List<File>> {
    val resultMap = mutableMapOf<File, MutableList<File>>()
    for (file in files) {
        if(file.totalSpace != 0L){
            (!resultMap.containsKey(file.parentFile!!)).let { resultMap.put(file.parentFile!!, mutableListOf()) }
            resultMap[file.parentFile!!]?.add(file)
        }
    }
    return resultMap
}

fun getImagesFromAlbum(folder: String): List<Photo> {
    return File(folder)
        .listFiles { file -> file.isFile && fileExtensions.contains(file.extension.lowercase()) }
        ?.sortedWith(naturalOrderComparator())
        ?.map { file -> Photo(path = file.absolutePath, position = 0, selected = false) }
        ?: emptyList()
}

// Natural sorting comparator for numerical ordering (1, 2, 3, ..., 10, 11, etc.)
fun naturalOrderComparator(): Comparator<File> {
    return Comparator { file1, file2 ->
        val name1 = file1.nameWithoutExtension
        val name2 = file2.nameWithoutExtension
        
        // Extract numbers and non-numeric parts
        val parts1 = splitAlphaNumeric(name1)
        val parts2 = splitAlphaNumeric(name2)
        
        val minLength = minOf(parts1.size, parts2.size)
        
        for (i in 0 until minLength) {
            val part1 = parts1[i]
            val part2 = parts2[i]
            
            val isNum1 = part1.all { it.isDigit() }
            val isNum2 = part2.all { it.isDigit() }
            
            when {
                isNum1 && isNum2 -> {
                    val num1 = part1.toLongOrNull() ?: 0
                    val num2 = part2.toLongOrNull() ?: 0
                    val result = num1.compareTo(num2)
                    if (result != 0) return@Comparator result
                }
                isNum1 && !isNum2 -> return@Comparator -1
                !isNum1 && isNum2 -> return@Comparator 1
                else -> {
                    val result = part1.compareTo(part2, ignoreCase = true)
                    if (result != 0) return@Comparator result
                }
            }
        }
        
        parts1.size.compareTo(parts2.size)
    }
}

// Split string into numeric and non-numeric parts
fun splitAlphaNumeric(input: String): List<String> {
    val result = mutableListOf<String>()
    var currentPart = StringBuilder()
    var isCurrentNumeric: Boolean? = null
    
    for (char in input) {
        val isDigit = char.isDigit()
        
        if (isCurrentNumeric == null || isCurrentNumeric == isDigit) {
            currentPart.append(char)
            isCurrentNumeric = isDigit
        } else {
            if (currentPart.isNotEmpty()) {
                result.add(currentPart.toString())
            }
            currentPart = StringBuilder(char.toString())
            isCurrentNumeric = isDigit
        }
    }
    
    if (currentPart.isNotEmpty()) {
        result.add(currentPart.toString())
    }
    
    return result
}

// Natural sorting comparator specifically for folder names
fun naturalFolderComparator(): Comparator<File> {
    return Comparator { folder1, folder2 ->
        val name1 = folder1.name
        val name2 = folder2.name
        
        // Split folder names into alphanumeric parts
        val parts1 = splitAlphaNumeric(name1)
        val parts2 = splitAlphaNumeric(name2)
        
        val minLength = minOf(parts1.size, parts2.size)
        
        for (i in 0 until minLength) {
            val part1 = parts1[i]
            val part2 = parts2[i]
            
            val isNum1 = part1.all { it.isDigit() }
            val isNum2 = part2.all { it.isDigit() }
            
            when {
                isNum1 && isNum2 -> {
                    // Compare as numbers, not strings
                    val num1 = part1.toLongOrNull() ?: 0
                    val num2 = part2.toLongOrNull() ?: 0
                    val result = num1.compareTo(num2)
                    if (result != 0) return@Comparator result
                }
                isNum1 && !isNum2 -> return@Comparator -1  // Numbers come before letters
                !isNum1 && isNum2 -> return@Comparator 1   // Letters come after numbers
                else -> {
                    // Compare as strings (case-insensitive)
                    val result = part1.compareTo(part2, ignoreCase = true)
                    if (result != 0) return@Comparator result
                }
            }
        }
        
        // If all compared parts are equal, shorter name comes first
        parts1.size.compareTo(parts2.size)
    }
}

fun getAllImages(context: Context): List<File> {
    val sortOrder = MediaStore.Images.Media.DATE_TAKEN + " ASC"
    val sortOrderVideos = MediaStore.Video.Media.DATE_TAKEN + " ASC"

    val imageList = queryUri(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, sortOrder)
        .use { it?.getResultsFromCursor() ?: listOf() }
    val videoList = queryUri(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null, sortOrderVideos)
        .use { it?.getResultsFromCursor() ?: listOf() }
    return videoList + imageList
}

// Cache for media list with timestamp
private var cachedMedia: List<Photo>? = null
private var cacheTimestamp: Long = 0
private const val CACHE_VALIDITY_MS = 30 * 1000L // 30 seconds

fun getCachedMediaOrLoad(context: Context): List<Photo> {
    val currentTime = System.currentTimeMillis()
    
    // Return cached data if it's still valid
    if (cachedMedia != null && (currentTime - cacheTimestamp) < CACHE_VALIDITY_MS) {
        return cachedMedia!!
    }
    
    // Load fresh data and cache it
    val freshMedia = getAllImagesAndVideosSortedByRecent(context)
    cachedMedia = freshMedia
    cacheTimestamp = currentTime
    
    return freshMedia
}

fun getAllImagesAndVideosSortedByRecent(context: Context): List<Photo> {
    val sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC"
    val sortOrderVideos = MediaStore.Video.Media.DATE_TAKEN + " DESC"

    val imageList = queryUri(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, sortOrder)
        .use { it?.getResultsFromCursor() ?: listOf() }
    val videoList = queryUri(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null, sortOrderVideos)
        .use { it?.getResultsFromCursor() ?: listOf() }

    val resultList = (imageList + videoList).sortedWith(compareByDescending { it.lastModified() })
    return resultList.map { file -> Photo(path = file.absolutePath, position = 0, selected = false) }
}

fun getImagesFromPage(page: Int, data: List<Photo>): List<Photo> {
    val startIndex = (page - 1) * 100
    val endIndex = startIndex + 100

    if (startIndex >= data.size) {
        return emptyList()
    }

    val end = if (endIndex > data.size) data.size else endIndex

    return data.subList(startIndex, end)
}

private fun queryUri(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?, sortOrder: String = ""): Cursor? {
    return context.contentResolver.query(
        uri,
        projection,
        selection,
        selectionArgs,
        sortOrder)
}

private fun Cursor.getResultsFromCursor(): List<File> {
    val results = mutableListOf<File>()

    while (this.moveToNext()) {
        results.add(File(this.getString(this.getColumnIndexOrThrow(DATA))))
    }
    return results
}

fun getImageVideoNumber(parent : File) : Int{
    var imageCount = 0
    var videoCount = 0

    for (file in parent.listFiles()!!) {
        if (file.isFile) {
            val fileExtension = file.extension.lowercase()
            if (imageExtensions.contains(fileExtension)) {
                imageCount++
            } else if (videoExtensions.contains(fileExtension)) {
                videoCount++
            }
        }
    }
    return imageCount + videoCount
}

@SuppressLint("Range")
fun getContentUri(context: Context, file: File): Uri? {
    val filePath = file.absolutePath
    val mimeType: String
    val contentUri: Uri
    val contentValues = ContentValues()

    if(file.extension.lowercase() in imageExtensions) {
        mimeType = "image/*"
        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
    }else{
        mimeType = "video/*"
        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
        contentValues.put(MediaStore.Video.Media.MIME_TYPE, mimeType)
    }

    val cursor: Cursor = context.contentResolver.query(
        contentUri, arrayOf(_ID),
        "$DATA =? ", arrayOf(filePath), null
    )!!

    return if (cursor.moveToFirst()) {
        val id: Int = cursor.getInt(cursor.getColumnIndex(_ID))
        cursor.close()
        Uri.withAppendedPath(contentUri, "" + id)
    } else {
        if (file.exists()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver: ContentResolver = context.contentResolver
                val contentCollection =
                    if (mimeType.startsWith("image/")) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/" + UUID.randomUUID().toString())
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 1)
                val finalUri = resolver.insert(contentCollection, contentValues)
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(contentCollection, contentValues, null, null)
                finalUri
            } else {
                contentValues.put(DATA, filePath)
                context.contentResolver.insert(contentUri, contentValues)
            }
        } else {
            null
        }
    }
}

fun deletePhotoFromExternal(context: Context, photoUri: Uri, intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>): Boolean{
    try {
        context.contentResolver.delete(photoUri, null, null)
        return true
    } catch (e: SecurityException) {
        val intentSender = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                MediaStore.createDeleteRequest(context.contentResolver, listOf(photoUri))
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val recoverableSecurityException = e as? RecoverableSecurityException
                recoverableSecurityException?.userAction?.actionIntent?.intentSender
            }
            else -> null
        }
        intentSender?.let { sender ->
            intentSenderLauncher.launch(
                IntentSenderRequest.Builder(sender as IntentSender).build()
            )
            return true
        }
    }
    return false
}

val projection = arrayOf(
    MediaStore.Files.FileColumns._ID,
    MediaStore.Files.FileColumns.DATA,
    MediaStore.Files.FileColumns.DATE_ADDED,
    MediaStore.Files.FileColumns.MIME_TYPE,
    MediaStore.Files.FileColumns.TITLE
)