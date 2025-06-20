package com.brandonhxrr.gallery

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.recyclerview.widget.RecyclerView
import com.brandonhxrr.gallery.adapter.photo.PhotoAdapter
import com.brandonhxrr.gallery.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomNavView: BottomNavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var selectableToolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var deleteButton: ImageButton
    private lateinit var shareButton: ImageButton
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var deletedImageUri: Uri? = null
    
    // SAF folder picker launcher
    private val safFolderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                handleFolderSelection(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_content_main)

        bottomNavView = findViewById(R.id.bottomNavigationView)
        toolbar = findViewById(R.id.toolbar)
        selectableToolbar = findViewById(R.id.selectable_toolbar)
        deleteButton = findViewById(R.id.btn_delete)
        shareButton = findViewById(R.id.btn_share)
        
        setSupportActionBar(toolbar)

        // Photos option removed - only albums view available

        bottomNavView.setItemOnTouchListener(R.id.menu_album) { v, _ ->
            if (navController.currentDestination?.id == R.id.ViewAlbumFragment) {
                navController.popBackStack()
            }
            v.performClick()
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(selectableToolbar.visibility == View.VISIBLE){
                    disableSelectable()
                }else {
                    when(navController.currentDestination?.id) {
                        R.id.ViewAlbumFragment -> {
                            bottomNavView.selectedItemId = R.id.menu_album
                        }
                    }
                    navController.navigateUp()
                }
            }
        })

        shareButton.setOnClickListener {
            val intentShare = Intent(Intent.ACTION_SEND_MULTIPLE)
            intentShare.type = "image/*"

            val uriList = arrayListOf<Uri>()
            for(item in itemsList){
                uriList.add(FileProvider.getUriForFile(this, "${this.packageName}.provider", File(item.path)))
            }
            intentShare.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
            startActivity(intentShare)
        }

        intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if(it.resultCode == RESULT_OK) {
                if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    lifecycleScope.launch {
                        deletePhotoFromExternal(this@MainActivity, deletedImageUri ?: return@launch, intentSenderLauncher)
                    }
                }
            } else {
                Toast.makeText(this, getString(R.string.file_not_deleted), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        val appBarConfiguration = AppBarConfiguration(navController.graph)

        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowHomeEnabled(false)

        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun disableSelectable(){
        recyclerView = findViewById(R.id.gridRecyclerView)
        selectableToolbar.visibility = View.GONE
        toolbar.visibility = View.VISIBLE
        itemsList.clear()
        selectable = false
        (recyclerView.adapter as PhotoAdapter).resetItemsSelected()
        recyclerView.adapter?.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        updateResumeMenuVisibility(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        updateResumeMenuVisibility(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun updateResumeMenuVisibility(menu: Menu) {
        val resumeItem = menu.findItem(R.id.menu_resume)
        resumeItem?.isVisible = LastImagePreferences.isResumeEnabled(this) && 
                                 LastImagePreferences.hasLastImage(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_resume -> {
                resumeLastImage()
                true
            }
            R.id.menu_search -> {
                openSearchActivity()
                true
            }
            R.id.menu_folder_settings -> {
                showFolderSettings()
                true
            }
            R.id.menu_settings -> {
                showResumeSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun resumeLastImage() {
        if (!LastImagePreferences.isResumeEnabled(this)) {
            Toast.makeText(this, getString(R.string.resume_feature_disabled), Toast.LENGTH_SHORT).show()
            return
        }

        if (!LastImagePreferences.hasLastImage(this)) {
            Toast.makeText(this, getString(R.string.no_last_image), Toast.LENGTH_SHORT).show()
            return
        }

        val lastImagePath = LastImagePreferences.getLastImagePath(this)
        val lastImagePosition = LastImagePreferences.getLastImagePosition(this)

        if (lastImagePath == null || !File(lastImagePath).exists()) {
            Toast.makeText(this, getString(R.string.last_image_not_found), Toast.LENGTH_SHORT).show()
            LastImagePreferences.clearLastImage(this)
            invalidateOptionsMenu()
            return
        }

        // Get the album folder containing the last image
        val lastImageFile = File(lastImagePath)
        val albumPath = lastImageFile.parent ?: return
        
        // Get images from the album containing the last image
        lifecycleScope.launch {
            val albumImages = getImagesFromAlbum(albumPath)
            val currentPosition = albumImages.indexOfFirst { it.path == lastImagePath }
            
            if (currentPosition >= 0) {
                // Launch PhotoView with the correct position within the album
                val intent = Intent(this@MainActivity, PhotoView::class.java)
                val gson = com.google.gson.Gson()
                val limit = if (albumImages.size > 1000) 1000 else albumImages.size
                val data = gson.toJson(albumImages.subList(0, limit))
                
                intent.putExtra("path", lastImagePath)
                intent.putExtra("data", data)
                intent.putExtra("position", currentPosition)
                intent.putExtra("auto_resumed", true)
                startActivity(intent)
            } else {
                Toast.makeText(this@MainActivity, getString(R.string.last_image_not_found), Toast.LENGTH_SHORT).show()
                LastImagePreferences.clearLastImage(this@MainActivity)
                invalidateOptionsMenu()
            }
        }
    }

    private fun openSearchActivity() {
        val intent = Intent(this, SearchActivity::class.java)
        startActivity(intent)
    }
    
    private fun showFolderSettings() {
        // Directly launch the folder picker without showing a dialog
        launchFolderPicker()
    }
    
    private fun launchFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        safFolderPickerLauncher.launch(intent)
    }
    
    private fun handleFolderSelection(uri: Uri) {
        val folderPath = SAFHelper.getFolderPathFromSAF(uri)
        android.util.Log.d("FolderDebug", "SAF URI: $uri")
        android.util.Log.d("FolderDebug", "Converted folder path: $folderPath")
        
        folderPath?.let {
            android.util.Log.d("FolderDebug", "Processing folder: $it")
            
            // Clear previous selections and set new folder
            FolderSelectionPreferences.setShowAllFolders(this, false)
            FolderSelectionPreferences.setSelectedFolders(this, emptySet())
            
            // Add the selected folder and all its subfolders
            addFolderAndSubfolders(it)
            
            // Clear cache and reload albums
            albumes = null
            
            // Show confirmation
            Toast.makeText(this, "Folder selected: ${File(it).name}", Toast.LENGTH_SHORT).show()
            
            // Restart activity to reload with new folder selection
            recreate()
        } ?: run {
            android.util.Log.e("FolderDebug", "Could not convert SAF URI to folder path")
            Toast.makeText(this, "Could not access selected folder", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addFolderAndSubfolders(selectedFolderPath: String) {
        val selectedFolder = File(selectedFolderPath)
        val foldersToAdd = mutableSetOf<String>()
        
        android.util.Log.d("FolderDebug", "Adding folder and subfolders for: $selectedFolderPath")
        android.util.Log.d("FolderDebug", "Folder exists: ${selectedFolder.exists()}")
        android.util.Log.d("FolderDebug", "Folder is directory: ${selectedFolder.isDirectory}")
        
        // Add the selected folder itself
        foldersToAdd.add(selectedFolderPath)
        
        // Add all subfolders recursively
        addSubfoldersRecursively(selectedFolder, foldersToAdd)
        
        android.util.Log.d("FolderDebug", "Total folders added: ${foldersToAdd.size}")
        foldersToAdd.forEach { folder ->
            android.util.Log.d("FolderDebug", "Added folder: $folder")
        }
        
        // Save all folders to preferences
        FolderSelectionPreferences.setSelectedFolders(this, foldersToAdd)
    }
    
    private fun addSubfoldersRecursively(folder: File, foldersSet: MutableSet<String>) {
        try {
            val subFiles = folder.listFiles()
            android.util.Log.d("FolderDebug", "Scanning folder: ${folder.absolutePath}")
            android.util.Log.d("FolderDebug", "Found ${subFiles?.size ?: 0} items")
            
            subFiles?.forEach { file ->
                if (file.isDirectory) {
                    android.util.Log.d("FolderDebug", "Found subfolder: ${file.absolutePath}")
                    foldersSet.add(file.absolutePath)
                    // Recursively add subfolders
                    addSubfoldersRecursively(file, foldersSet)
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("FolderDebug", "Security exception accessing folder: ${folder.absolutePath} - ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("FolderDebug", "Error accessing folder: ${folder.absolutePath} - ${e.message}")
        }
    }
    
    private fun showResumeSettings() {
        val view = layoutInflater.inflate(android.R.layout.simple_list_item_multiple_choice, null)
        val checkBox = CheckBox(this)
        checkBox.text = getString(R.string.enable_resume_feature)
        checkBox.isChecked = LastImagePreferences.isResumeEnabled(this)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.resume_settings))
            .setMessage(getString(R.string.resume_feature_description))
            .setView(checkBox)
            .setPositiveButton(getString(R.string.accept)) { _, _ ->
                LastImagePreferences.setResumeEnabled(this, checkBox.isChecked)
                invalidateOptionsMenu()
                Toast.makeText(this, 
                    if (checkBox.isChecked) "Resume feature enabled" else "Resume feature disabled", 
                    Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        invalidateOptionsMenu()
    }
    
    override fun onPause() {
        super.onPause()
        // Save any current state when app goes to background
        // This ensures the last viewed state is preserved when Android kills the app
    }
    
    override fun onStop() {
        super.onStop()
        // Additional save point when activity stops
        // This helps preserve state when the app is killed by the OS
    }
}
