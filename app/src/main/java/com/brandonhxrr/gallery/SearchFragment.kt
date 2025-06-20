package com.brandonhxrr.gallery

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brandonhxrr.gallery.adapter.photo.SearchPhotoAdapter
import com.brandonhxrr.gallery.databinding.FragmentSearchBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchEditText: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var photoAdapter: SearchPhotoAdapter
    private lateinit var builder: RequestBuilder<Bitmap>
    
    private var searchJob: Job? = null
    private var allPhotos: List<Photo> = emptyList()
    private var filteredPhotos: List<Photo> = emptyList()
    private var currentAlbumPath: String? = null // For album-specific search

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        
        // Get album path if searching within specific album
        currentAlbumPath = arguments?.getString("album_path")
        
        initViews()
        setupRecyclerView()
        setupSearch()
        loadPhotos()
        
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }

    private fun initViews() {
        searchEditText = binding.searchEditText
        recyclerView = binding.searchRecyclerView
    }

    private fun setupRecyclerView() {
        val glide = Glide.with(this)
        builder = glide.asBitmap()
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .skipMemoryCache(false)

        photoAdapter = SearchPhotoAdapter(emptyList(), builder)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
        recyclerView.adapter = photoAdapter
        recyclerView.setHasFixedSize(true)
        
        // Handle photo click to open PhotoView
        photoAdapter.setOnItemClickListener { photo, position ->
            openPhotoView(photo, position)
        }
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                searchPhotos(query)
            }
        })
    }

    private fun loadPhotos() {
        // For database-based search, we don't need to preload all photos
        // Just initialize with empty list - search will query database directly
        allPhotos = emptyList()
        filteredPhotos = emptyList()
        updateAdapter()
    }

    private fun searchPhotos(query: String) {
        // Cancel previous search job
        searchJob?.cancel()
        
        if (query.isEmpty()) {
            filteredPhotos = emptyList()
            updateAdapter()
            return
        }
        
        searchJob = lifecycleScope.launch(Dispatchers.IO) {
            // Add small delay for better UX (debouncing)
            delay(300)
            
            Log.d("SearchDebug", "Starting database search for: '$query'")
            
            // Use database-based search - much faster!
            val searchResults = searchInMediaStore(query.trim())
            
            Log.d("SearchDebug", "Database search completed. Found ${searchResults.size} results")
            searchResults.forEach { photo ->
                Log.d("SearchDebug", "Result: ${File(photo.path).nameWithoutExtension}")
            }
            
            withContext(Dispatchers.Main) {
                filteredPhotos = searchResults
                updateAdapter()
            }
        }
    }

    private fun updateAdapter() {
        photoAdapter.updatePhotos(filteredPhotos)
        
        // Show/hide empty state
        if (filteredPhotos.isEmpty() && searchEditText.text.isNotEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.emptyStateText.text = "No images found for '${searchEditText.text}'"
        } else if (filteredPhotos.isEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.emptyStateText.text = "No images available"
        } else {
            binding.emptyStateText.visibility = View.GONE
        }
    }

    private fun openPhotoView(photo: Photo, position: Int) {
        val intent = Intent(requireContext(), PhotoView::class.java)
        val gson = Gson()
        
        // Get all images from the same folder as the selected photo
        val selectedFile = File(photo.path)
        val folderPath = selectedFile.parentFile?.absolutePath ?: ""
        val folderPhotos = getImagesFromAlbum(folderPath)
        
        // Find the actual position of the selected photo in the folder
        val actualPosition = folderPhotos.indexOfFirst { 
            File(it.path).absolutePath == selectedFile.absolutePath 
        }.takeIf { it >= 0 } ?: 0
        
        // Pass the full folder contents for navigation
        val data = gson.toJson(folderPhotos)
        
        intent.putExtra("path", photo.path)
        intent.putExtra("data", data)
        intent.putExtra("position", actualPosition)
        intent.putExtra("from_search", true)
        startActivity(intent)
    }

    private fun getSearchPriority(fileName: String, searchQuery: String): Int {
        return when {
            fileName == searchQuery -> 1 // Exact match - highest priority
            fileName.startsWith(searchQuery) -> 2 // Starts with
            fileName.endsWith(searchQuery) -> 3 // Ends with
            fileName.matches(Regex(".*\\b${Regex.escape(searchQuery)}\\b.*")) -> 4 // Word boundary
            else -> 5 // Contains match - lowest priority
        }
    }
    
    private fun compareNumerically(fileName1: String, fileName2: String, searchQuery: String): Int {
        // Extract numbers from filenames for comparison
        val num1 = extractNumber(fileName1)
        val num2 = extractNumber(fileName2)
        
        return when {
            // If both are numbers, compare numerically
            num1 != null && num2 != null -> {
                val searchNum = searchQuery.toIntOrNull()
                if (searchNum != null) {
                    // Sort by distance from search number
                    val diff1 = kotlin.math.abs(num1 - searchNum)
                    val diff2 = kotlin.math.abs(num2 - searchNum)
                    diff1.compareTo(diff2)
                } else {
                    num1.compareTo(num2)
                }
            }
            // If only one is a number, prioritize the number
            num1 != null && num2 == null -> -1
            num1 == null && num2 != null -> 1
            // If neither is a number, sort alphabetically
            else -> fileName1.compareTo(fileName2)
        }
    }
    
    private fun extractNumber(fileName: String): Int? {
        // Try to extract the main number from filename
        val numberRegex = Regex("\\d+")
        val numbers = numberRegex.findAll(fileName).map { it.value.toInt() }.toList()
        
        return when {
            numbers.isEmpty() -> null
            numbers.size == 1 -> numbers[0]
            else -> {
                // If multiple numbers, return the largest one (likely the main identifier)
                numbers.maxOrNull()
            }
        }
    }

    /**
     * DATABASE-BASED SEARCH - Query MediaStore directly for much faster results
     * FIXED: Now properly supports SD card and all storage locations
     */
    private suspend fun searchInMediaStore(query: String): List<Photo> {
        val results = mutableListOf<Photo>()
        
        try {
            // FALLBACK: Use the original working method to ensure SD card support
            val allPhotos = if (currentAlbumPath != null) {
                // Load photos from specific album (preserves original working logic)
                getImagesFromAlbum(currentAlbumPath!!)
            } else {
                // Load all photos globally (preserves original working logic)
                getCachedMediaOrLoad(requireContext())
            }
            
            Log.d("SearchDebug", "Loaded ${allPhotos.size} photos for search (including SD card)")
            
            // Apply search filtering on the loaded photos
            val searchResults = allPhotos.filter { photo ->
                val fileName = File(photo.path).nameWithoutExtension
                val searchQuery = query.trim()
                
                val isMatch = when {
                    // If search query is a pure number, match ONLY exact numerical filenames
                    searchQuery.matches(Regex("^\\d+$")) -> {
                        val numericMatch = fileName == searchQuery || 
                        (fileName.matches(Regex("^\\d+$")) && safeIntegerEquals(fileName, searchQuery))
                        
                        if (numericMatch) {
                            Log.d("SearchDebug", "NUMERIC MATCH: $fileName matches $searchQuery")
                        }
                        numericMatch
                    }
                    
                    // For text searches, do comprehensive matching
                    else -> {
                        val fileNameLower = fileName.lowercase()
                        val searchLower = searchQuery.lowercase()
                        
                        val textMatch = fileNameLower == searchLower || 
                        fileNameLower.startsWith(searchLower) ||
                        (searchQuery.length >= 3 && fileNameLower.contains(searchLower))
                        
                        if (textMatch) {
                            Log.d("SearchDebug", "TEXT MATCH: $fileName matches $searchQuery")
                        }
                        textMatch
                    }
                }
                
                isMatch
            }
            
            // Apply folder filtering (preserves original folder selection logic)
            val filteredResults = if (currentAlbumPath != null) {
                searchResults.filter { File(it.path).parent == currentAlbumPath }
            } else {
                // Apply global folder preferences
                searchResults.filter { photo ->
                    val folderPath = File(photo.path).parent ?: return@filter false
                    isFolderSelected(folderPath)
                }
            }
            
            // Sort results for better relevance
            return filteredResults.sortedWith { photo1, photo2 ->
                val fileName1 = File(photo1.path).nameWithoutExtension
                val fileName2 = File(photo2.path).nameWithoutExtension
                val searchQuery = query.trim()
                val searchNumber = searchQuery.toLongOrNull()
                
                val fileNumber1 = fileName1.toLongOrNull()
                val fileNumber2 = fileName2.toLongOrNull()
                
                when {
                    // If search is a number and both files are numbers
                    searchNumber != null && fileNumber1 != null && fileNumber2 != null -> {
                        // Exact match first, then closest numbers
                        val isExact1 = fileNumber1 == searchNumber
                        val isExact2 = fileNumber2 == searchNumber
                        
                        when {
                            isExact1 && !isExact2 -> -1
                            !isExact1 && isExact2 -> 1
                            else -> {
                                // Both exact or both not exact, sort by numerical distance
                                val diff1 = kotlin.math.abs(fileNumber1 - searchNumber)
                                val diff2 = kotlin.math.abs(fileNumber2 - searchNumber)
                                diff1.compareTo(diff2)
                            }
                        }
                    }
                    
                    // Numbers come before non-numbers
                    fileNumber1 != null && fileNumber2 == null -> -1
                    fileNumber1 == null && fileNumber2 != null -> 1
                    
                    // Both are numbers, sort numerically
                    fileNumber1 != null && fileNumber2 != null -> fileNumber1.compareTo(fileNumber2)
                    
                    // Both are text, sort alphabetically
                    else -> fileName1.compareTo(fileName2, ignoreCase = true)
                }
            }
            
        } catch (e: Exception) {
            Log.e("SearchDebug", "Error in search: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * Build SQL WHERE clause and arguments for different search types
     */
    private fun buildSearchQuery(query: String): Pair<String, Array<String>> {
        return when {
            // For numeric searches, be very specific
            query.matches(Regex("^\\d+$")) -> {
                // Match files that start with the number
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("$query.%", "$query")
                Pair(selection, selectionArgs)
            }
            
            // For text searches
            query.length >= 2 -> {
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("%$query%")
                Pair(selection, selectionArgs)
            }
            
            // For single character searches, be more restrictive
            else -> {
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("$query%")
                Pair(selection, selectionArgs)
            }
        }
    }
    
    /**
     * Check if a folder is selected based on user preferences
     */
    private fun isFolderSelected(folderPath: String): Boolean {
        if (FolderSelectionPreferences.shouldShowAllFolders(requireContext())) {
            return true
        }
        
        val selectedFolders = FolderSelectionPreferences.getSelectedFolders(requireContext())
        
        // Check if this exact folder is selected
        if (selectedFolders.contains(folderPath)) {
            return true
        }
        
        // Check if any parent folder is selected
        for (selectedFolder in selectedFolders) {
            if (folderPath.startsWith(selectedFolder)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Safely compare two numeric strings without throwing NumberFormatException
     * for numbers that are too large for Int
     */
    private fun safeIntegerEquals(str1: String, str2: String): Boolean {
        return try {
            // First try string comparison (fastest for exact matches)
            if (str1 == str2) return true
            
            // Try integer comparison only if both can be safely converted
            val num1 = str1.toLongOrNull()
            val num2 = str2.toLongOrNull()
            
            // If both can be converted to Long, compare as numbers
            if (num1 != null && num2 != null) {
                num1 == num2
            } else {
                // If either can't be converted, fall back to string comparison
                str1 == str2
            }
        } catch (e: Exception) {
            // If anything goes wrong, fall back to string comparison
            str1 == str2
        }
    }

    companion object {
        fun newInstance(albumPath: String? = null): SearchFragment {
            val fragment = SearchFragment()
            val args = Bundle()
            albumPath?.let { args.putString("album_path", it) }
            fragment.arguments = args
            return fragment
        }
    }
}

