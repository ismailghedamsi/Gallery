package com.brandonhxrr.gallery.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.brandonhxrr.gallery.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PhotoPagingSource(
    private val albumPath: String
) : PagingSource<Int, Photo>() {

    companion object {
        const val PAGE_SIZE = 50 // Load 50 images per page for optimal performance
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Photo> {
        return try {
            val page = params.key ?: 0
            val offset = page * PAGE_SIZE

            // Load photos in background thread
            val photos = withContext(Dispatchers.IO) {
                loadPhotosFromAlbum(albumPath, offset, PAGE_SIZE)
            }

            LoadResult.Page(
                data = photos,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (photos.isEmpty() || photos.size < PAGE_SIZE) null else page + 1
            )
        } catch (exception: Exception) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Photo>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    private fun loadPhotosFromAlbum(albumPath: String, offset: Int, limit: Int): List<Photo> {
        val folder = File(albumPath)
        if (!folder.exists() || !folder.isDirectory) {
            return emptyList()
        }

        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
        val videoExtensions = setOf("mp4", "mkv", "avi", "wmv", "mov", "flv", "webm", "ogg", "ogv")
        val allExtensions = imageExtensions + videoExtensions

        return folder.listFiles { file ->
            file.isFile && allExtensions.contains(file.extension.lowercase())
        }?.sortedWith(naturalOrderComparator())
            ?.drop(offset)
            ?.take(limit)
            ?.mapIndexed { index, file ->
                Photo(
                    path = file.absolutePath,
                    position = offset + index,
                    selected = false
                )
            } ?: emptyList()
    }

    private fun naturalOrderComparator(): Comparator<File> {
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

    private fun splitAlphaNumeric(input: String): List<String> {
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
}

