package com.brandonhxrr.gallery

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brandonhxrr.gallery.adapter.album.AlbumAdapter
import com.brandonhxrr.gallery.adapter.NativeAlbumSelectionAdapter
import com.brandonhxrr.gallery.adapter.FolderSelectionAdapter
import com.brandonhxrr.gallery.databinding.FragmentAlbumsBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AlbumsFragment : Fragment() {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding
        get() = _binding!!
    private lateinit var albums: HashMap<File, List<File>>
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var builder: RequestBuilder<Bitmap>
    private lateinit var recyclerView: RecyclerView
    private lateinit var mediaStoreHelper: MediaStoreHelper
    
    // SAF folder picker launcher
    private val safFolderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Handle SAF folder selection
                val folderPath = SAFHelper.getFolderPathFromSAF(uri)
                folderPath?.let {
                    // Clear previous selections and add the selected folder
                    FolderSelectionPreferences.setShowAllFolders(requireContext(), false)
                    // Clear any previous selections first
                    FolderSelectionPreferences.setSelectedFolders(requireContext(), emptySet())
                    // Add the selected folder and all its subfolders
                    addFolderAndSubfolders(it)
                    FolderSelectionPreferences.setFirstLaunchCompleted(requireContext())
                    
                    // Load albums with new settings
                    loadAlbumsLazily()
                }
            }
        } else {
            // User cancelled, default to show all folders
            FolderSelectionPreferences.setShowAllFolders(requireContext(), true)
            FolderSelectionPreferences.setFirstLaunchCompleted(requireContext())
            loadAlbumsLazily()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize empty albums - will be loaded lazily in onResume
        albums = HashMap()
        mediaStoreHelper = MediaStoreHelper(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        initRecyclerView(requireContext())
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initRecyclerView(context: Context) {
        recyclerView = binding.gridRecyclerView
        recyclerView.layoutManager = GridLayoutManager(context, 2)

        val glide = Glide.with(this)
        builder = glide.asBitmap()

        albumAdapter = AlbumAdapter(builder)
        albumAdapter.setItems(albums)

        recyclerView.adapter = albumAdapter
    }

    override fun onResume() {
        super.onResume()
        
        // Check if this is first launch and show simple folder picker
        if (FolderSelectionPreferences.isFirstLaunch(requireContext())) {
            showSimpleFolderPicker()
        } else {
            loadAlbumsLazily()
        }
    }
    
    private fun loadAlbumsLazily() {
        // Show loading state immediately
        binding.progressBar?.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Check if we have valid cached albums data first
                if (albumes != null && albumes!!.isNotEmpty()) {
                    albums = albumes!!
                } else {
                    // Load albums with progress feedback
                    albums = loadAlbumsOptimized()
                    // Cache the result for future use
                    albumes = albums
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar?.visibility = View.GONE
                    
                    if(albums.isNotEmpty()){
                        albumAdapter.setItems(albums)
                        albumAdapter.notifyDataSetChanged()
                    } else {
                        // Show empty state instead of going back
                        binding.emptyStateText?.visibility = View.VISIBLE
                        binding.emptyStateText?.text = "No albums found"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar?.visibility = View.GONE
                    binding.emptyStateText?.visibility = View.VISIBLE
                    binding.emptyStateText?.text = "Error loading albums"
                }
            }
        }
    }
    
    private suspend fun loadAlbumsOptimized(): HashMap<File, List<File>> {
        // TRUE LAZY LOADING: Only get one image per folder, don't load all images
        return loadOnlyFolderCovers()
    }
    
    /**
     * TRUE LAZY LOADING: Only scan folders and get one cover image per folder
     * This is much faster than loading all images first
     */
    private suspend fun loadOnlyFolderCovers(): HashMap<File, List<File>> {
        val resultMap = HashMap<File, List<File>>()
        
        // Get all possible media folders from MediaStore without loading all files
        val mediaFolders = getMediaFoldersFromMediaStore()
        
        for (folderPath in mediaFolders) {
            val folder = File(folderPath)
            
            // Apply folder filtering based on user preferences
            if (!isFolderSelected(folder.absolutePath)) {
                continue
            }
            
            // Get only the first image from this folder (lazy loading)
            val firstImage = getFirstImageFromFolder(folder)
            if (firstImage != null) {
                resultMap[folder] = listOf(firstImage)
            }
        }
        
        return resultMap
    }
    
    /**
     * Get all media folders from MediaStore without loading all files
     */
    private suspend fun getMediaFoldersFromMediaStore(): Set<String> {
        val folders = mutableSetOf<String>()
        
        try {
            // Query MediaStore for unique folder paths
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            
            requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val imagePath = cursor.getString(dataColumn)
                    val folder = File(imagePath).parent
                    if (folder != null) {
                        folders.add(folder)
                    }
                }
            }
            
            // Also check video folders
            requireContext().contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media.DATA),
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val videoPath = cursor.getString(dataColumn)
                    val folder = File(videoPath).parent
                    if (folder != null) {
                        folders.add(folder)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to filesystem scan if MediaStore fails
        }
        
        return folders
    }
    
    /**
     * Get only the first (most recent) image from a folder - true lazy loading
     */
    private fun getFirstImageFromFolder(folder: File): File? {
        return try {
            folder.listFiles { file ->
                file.isFile && fileExtensions.contains(file.extension.lowercase())
            }?.maxByOrNull { it.lastModified() }
        } catch (e: SecurityException) {
            null
        }
    }
    
    private fun showFolderSelectionDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Load all folders first
            val allFolders = loadAllFolders()
            
            withContext(Dispatchers.Main) {
                val dialogView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_folder_selection, null)
                
                val checkShowAll = dialogView.findViewById<CheckBox>(R.id.checkShowAllFolders)
                val recyclerView = dialogView.findViewById<RecyclerView>(R.id.folderSelectionRecyclerView)
                
                // Setup folder list
                val folderSelectionAdapter = FolderSelectionAdapter(allFolders)
                recyclerView.layoutManager = LinearLayoutManager(requireContext())
                recyclerView.adapter = folderSelectionAdapter
                
                checkShowAll.isChecked = true // Default to show all
                
                // Handle "Show All" checkbox
                checkShowAll.setOnCheckedChangeListener { _, isChecked ->
                    folderSelectionAdapter.setEnabled(!isChecked)
                }
                
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogView)
                    .setTitle("Choose Folders")
                    .setPositiveButton("Continue") { _, _ ->
                        val showAll = checkShowAll.isChecked
                        val selectedFolders = if (showAll) {
                            emptySet<String>()
                        } else {
                            folderSelectionAdapter.getSelectedFolders()
                        }
                        
                        // Save preferences
                        FolderSelectionPreferences.setShowAllFolders(requireContext(), showAll)
                        if (!showAll) {
                            FolderSelectionPreferences.setSelectedFolders(requireContext(), selectedFolders)
                        }
                        FolderSelectionPreferences.setFirstLaunchCompleted(requireContext())
                        
                        // Load albums with new settings
                        loadAlbumsLazily()
                    }
                    .setNegativeButton("Show All") { _, _ ->
                        // Default to show all
                        FolderSelectionPreferences.setShowAllFolders(requireContext(), true)
                        FolderSelectionPreferences.setFirstLaunchCompleted(requireContext())
                        loadAlbumsLazily()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }
    
    private suspend fun loadAllFolders(): List<File> {
        val allImages = getCachedMediaOrLoad(requireContext())
            .map { File(it.path) }
            .filter { it.exists() && it.isFile }
        
        return allImages.mapNotNull { it.parentFile }
            .distinct()
            .sortedBy { it.name }
    }
    
    private fun hasOldFolderPreferences(): Boolean {
        val oldPrefs = requireContext().getSharedPreferences("folder_selection_prefs", Context.MODE_PRIVATE)
        return oldPrefs.contains("first_launch")
    }
    
    private fun migrateFromOldPreferences() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get all album folders to create mapping
                val albumFolders = mediaStoreHelper.getAlbumFolders()
                val folderToBucketMap = albumFolders.associate { it.path to it.bucketId }
                
                // Migrate preferences
                NativeAlbumPreferences.migrateFromFolderPreferences(requireContext(), folderToBucketMap)
                
                withContext(Dispatchers.Main) {
                    loadAlbumsLazily()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Fallback to showing selection dialog
                    showNativeAlbumSelectionDialog()
                }
            }
        }
    }
    
    private fun showNativeAlbumSelectionDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Load all albums using MediaStore
            val albumFolders = mediaStoreHelper.getAlbumFolders()
            
            withContext(Dispatchers.Main) {
                val dialogView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_native_album_selection, null)
                
                val checkShowAll = dialogView.findViewById<CheckBox>(R.id.checkShowAllAlbums)
                val recyclerView = dialogView.findViewById<RecyclerView>(R.id.albumSelectionRecyclerView)
                val btnSAF = dialogView.findViewById<Button>(R.id.btnSelectFoldersWithSAF)
                val btnAdvanced = dialogView.findViewById<Button>(R.id.btnShowAdvanced)
                
                // Setup album list
                val albumSelectionAdapter = NativeAlbumSelectionAdapter(albumFolders)
                recyclerView.layoutManager = LinearLayoutManager(requireContext())
                recyclerView.adapter = albumSelectionAdapter
                
                checkShowAll.isChecked = true // Default to show all
                albumSelectionAdapter.setEnabled(false)
                
                // Handle "Show All" checkbox
                checkShowAll.setOnCheckedChangeListener { _, isChecked ->
                    albumSelectionAdapter.setEnabled(!isChecked)
                }
                
                // Handle SAF folder picker
                btnSAF.setOnClickListener {
                    SAFHelper.selectFolderWithSAF(requireActivity() as androidx.appcompat.app.AppCompatActivity, safFolderPickerLauncher)
                }
                
                // Handle advanced options (placeholder)
                btnAdvanced.setOnClickListener {
                    // Could show additional options like refresh MediaStore, etc.
                }
                
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogView)
                    .setTitle("Choose Albums")
                    .setPositiveButton("Continue") { _, _ ->
                        val showAll = checkShowAll.isChecked
                        val selectedBucketIds = if (showAll) {
                            emptySet()
                        } else {
                            albumSelectionAdapter.getSelectedBucketIds()
                        }
                        
                        // Save preferences
                        NativeAlbumPreferences.setShowAllAlbums(requireContext(), showAll)
                        if (!showAll) {
                            NativeAlbumPreferences.setSelectedBucketIds(requireContext(), selectedBucketIds)
                        }
                        NativeAlbumPreferences.setFirstLaunchCompleted(requireContext())
                        
                        // Load albums with new settings
                        loadAlbumsLazily()
                    }
                    .setNegativeButton("Show All") { _, _ ->
                        // Default to show all
                        NativeAlbumPreferences.setShowAllAlbums(requireContext(), true)
                        NativeAlbumPreferences.setFirstLaunchCompleted(requireContext())
                        loadAlbumsLazily()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }
    
    private fun showSimpleFolderPicker() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose Folders")
            .setMessage("Select how you want to choose which folders to display in your gallery.")
            .setPositiveButton("Browse Folders") { _, _ ->
                // Launch SAF folder picker directly
                SAFHelper.selectFolderWithSAF(
                    requireActivity() as androidx.appcompat.app.AppCompatActivity,
                    safFolderPickerLauncher
                )
            }
            .setNegativeButton("Show All Folders") { _, _ ->
                // Default to show all folders
                FolderSelectionPreferences.setShowAllFolders(requireContext(), true)
                FolderSelectionPreferences.setFirstLaunchCompleted(requireContext())
                loadAlbumsLazily()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Add the selected folder and all its subfolders to the preferences
     */
    private fun addFolderAndSubfolders(selectedFolderPath: String) {
        val selectedFolder = File(selectedFolderPath)
        val foldersToAdd = mutableSetOf<String>()
        
        // Add the selected folder itself
        foldersToAdd.add(selectedFolderPath)
        
        // Add all subfolders recursively
        addSubfoldersRecursively(selectedFolder, foldersToAdd)
        
        // Save all folders to preferences
        FolderSelectionPreferences.setSelectedFolders(requireContext(), foldersToAdd)
    }
    
    /**
     * Recursively add all subfolders of a given folder
     */
    private fun addSubfoldersRecursively(folder: File, foldersSet: MutableSet<String>) {
        try {
            folder.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    foldersSet.add(file.absolutePath)
                    // Recursively add subfolders
                    addSubfoldersRecursively(file, foldersSet)
                }
            }
        } catch (e: SecurityException) {
            // Some folders might not be accessible, skip them
        }
    }
    
    /**
     * Check if a folder is selected, including checking if any of its parent folders are selected
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
}
