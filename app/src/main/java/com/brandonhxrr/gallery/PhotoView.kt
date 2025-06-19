package com.brandonhxrr.gallery

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.brandonhxrr.gallery.adapter.view_pager.ViewPagerAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class PhotoView : AppCompatActivity() {

    private var media: List<Photo>? = null
    private lateinit var viewPager: CustomViewPager
    private lateinit var viewPagerAdapter: ViewPagerAdapter
    private lateinit var photoName: TextView
    private lateinit var photoDatetime: TextView
    private lateinit var container: ConstraintLayout
    private lateinit var toolbar: Toolbar
    private lateinit var btnDelete: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnPlayPause: FloatingActionButton
    private lateinit var btnPlayPauseToolbar: ImageButton
    var position: Int = 0
    private var autoScrollJob: Job? = null
    private var isAutoScrolling = false
    private val autoScrollDelayMs = 3000L // 3 seconds between images
    private var isPaused = false // Track if slideshow is paused
    private lateinit var windowInsetsController : WindowInsetsControllerCompat
    private lateinit var operation: String
    private lateinit var currentFile: File
    private var deletedImageUri: Uri? = null
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    private val PERMISSION_PREFS_NAME = "permissions"
    private val SD_CARD_PERMISSION_GRANTED_KEY = "sd_card_permission_granted"
    private lateinit var destinationPath: String
    private lateinit var btnBackToMain: ImageButton
    private var isAutoResumed = false
    private var isNavigatingBack = false
    private var allAlbums: List<Pair<File, List<Photo>>> = emptyList()
    private var currentAlbumIndex = 0
    private var isCrossFolderMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        setContentView(R.layout.activity_photo_view)
        
        // Handle back button to clear last image state
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Set flag to indicate we're navigating back
                isNavigatingBack = true
                // Clear last image immediately to prevent auto-resume
                LastImagePreferences.clearLastImage(this@PhotoView)
                finish()
                // Add smooth transition
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        })

        photoName = findViewById(R.id.photo_name)
        photoDatetime = findViewById(R.id.photo_datetime)
        container = findViewById(R.id.constraintContainer)
        btnDelete = findViewById(R.id.btn_delete)
        btnShare = findViewById(R.id.btn_share)
        btnMenu = findViewById(R.id.btn_menu)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnPlayPauseToolbar = findViewById(R.id.btn_play_pause_toolbar)
        btnBackToMain = findViewById(R.id.btn_back_to_main)
        
        // Setup slideshow control button in toolbar - always visible and prominent
        setupSlideshowButton()

        toolbar = findViewById(R.id.toolbar)
        val params = toolbar.layoutParams as ViewGroup.MarginLayoutParams
        params.topMargin = getStatusBarHeight()
        toolbar.layoutParams = params
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        supportActionBar?.title = ""

        val bundle = intent.extras
        position = bundle?.getInt("position")!!
        isAutoResumed = bundle?.getBoolean("auto_resumed", false) ?: false
        val gson = Gson()
        val data = intent.getStringExtra("data")
        media = gson.fromJson(data, Array<Photo>::class.java).toList()
        
        // Initialize cross-folder navigation data
        initializeCrossFolderData()
        
        // Safeguard: ensure position is within valid bounds
        if (position >= media!!.size) {
            position = 0 // Default to first image if position is out of bounds
        }
        
        // Show back button if this was auto-resumed
        if (isAutoResumed) {
            btnBackToMain.visibility = View.VISIBLE
            btnBackToMain.setOnClickListener {
                // Set flag to indicate we're navigating back
                isNavigatingBack = true
                // Clear last image immediately to prevent auto-resume
                LastImagePreferences.clearLastImage(this@PhotoView)
                // Navigate back to main activity without restarting the task
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
                // Add smooth transition
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }

        viewPager = findViewById(R.id.viewPager)
        currentFile = File(media!![position].path)

        viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener(){
            override fun onPageSelected(pos: Int) {
                super.onPageSelected(pos)
                position = pos
                currentFile = File(media!![position].path)
                setDateTime()
                
                // Save the last viewed image if resume feature is enabled
                if (LastImagePreferences.isResumeEnabled(this@PhotoView)) {
                    LastImagePreferences.saveLastImage(this@PhotoView, currentFile.absolutePath, pos)
                }
                
                // Check if we've reached the end and should move to next folder
                if (pos == media!!.size - 1 && isCrossFolderMode && !isAutoScrolling) {
                    // Delay slightly to allow the user to see the last image
                    lifecycleScope.launch {
                        delay(500) // Half second delay
                        if (position == media!!.size - 1) { // Double-check we're still at the end
                            showNextFolderPrompt()
                        }
                    }
                }
            }
        })

        viewPagerAdapter = ViewPagerAdapter(this, media!!)
        viewPager.adapter = viewPagerAdapter
        setDateTime()
        viewPager.currentItem = position

        toolbar.visibility = View.GONE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        container.setBackgroundColor(Color.BLACK)

        btnDelete.setOnClickListener {
            showMenu(it, R.menu.menu_delete)
        }

        btnShare.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = if (imageExtensions.contains(currentFile.extension.lowercase())) "image/*" else "video/*"
            val uri = FileProvider.getUriForFile(this, "${this.packageName}.provider", currentFile)
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            startActivity(Intent.createChooser(intent, getString(R.string.menu_share)))
        }

        btnMenu.setOnClickListener {
            showSubmenu(it, R.menu.menu_submenu)
        }
        
        btnPlayPauseToolbar.setOnClickListener {
            toggleSlideshow()
        }

        intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if(it.resultCode == RESULT_OK) {
                if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    lifecycleScope.launch {
                        deletePhotoFromExternal(this@PhotoView, deletedImageUri ?: return@launch, intentSenderLauncher)
                    }
                }
            } else {
                Toast.makeText(this, getString(R.string.file_not_deleted), Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onSupportNavigateUp(): Boolean {
        // Set flag to indicate we're navigating back
        isNavigatingBack = true
        // Clear last image immediately to prevent auto-resume
        LastImagePreferences.clearLastImage(this)
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onPause() {
        super.onPause()
        // Pause slideshow when activity is paused
        if (isAutoScrolling) {
            pauseSlideshow()
        }
        
        // Save the last viewed image when app goes to background
        // This is the most reliable point for saving state before the app might be killed
        saveCurrentImageState()
    }
    
    override fun onStop() {
        super.onStop()
        // onStop is called after onPause, so we only save if we haven't saved recently
        // This prevents redundant saves while ensuring state is preserved
        saveCurrentImageState()
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // If we're navigating back, clear the last image to prevent auto-resume
        if (isNavigatingBack) {
            LastImagePreferences.clearLastImage(this)
        } else {
            // Final save point - only if we haven't navigated back
            saveCurrentImageState()
        }
    }
    
    private fun saveCurrentImageState() {
        // Only save if resume is enabled and we're not navigating back
        if (LastImagePreferences.isResumeEnabled(this) && !isNavigatingBack) {
            val currentTimestamp = System.currentTimeMillis()
            val lastSavedTimestamp = LastImagePreferences.getLastImageTimestamp(this)
            
            // Only save if it's been more than 1 second since last save to prevent excessive writes
            // or if the image has changed
            val lastSavedPath = LastImagePreferences.getLastImagePath(this)
            val currentPath = currentFile.absolutePath
            
            if (currentTimestamp - lastSavedTimestamp > 1000 || lastSavedPath != currentPath) {
                LastImagePreferences.saveLastImage(this, currentPath, position)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun showMenu(v: View, @MenuRes menuRes: Int) {
        val popup = PopupMenu(this, v)
        popup.menuInflater.inflate(menuRes, popup.menu)

        if (popup.menu is MenuBuilder) {
            val menuBuilder = popup.menu as MenuBuilder
            menuBuilder.setOptionalIconsVisible(true)
            for (item in menuBuilder.visibleItems) {
                val iconMarginPx =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics
                    )
                        .toInt()
                if (item.icon != null) {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                        item.icon = InsetDrawable(item.icon, iconMarginPx, 0, iconMarginPx, 0)
                    } else {
                        item.icon =
                            object : InsetDrawable(item.icon, iconMarginPx, 0, iconMarginPx, 0) {
                                override fun getIntrinsicWidth(): Int {
                                    return intrinsicHeight + iconMarginPx + iconMarginPx
                                }
                            }
                    }
                }
            }

            popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                when (menuItem.itemId) {
                    R.id.menu_delete -> {
                        if(currentFile.delete()){
                            removeImageFromAdapter()
                            Toast.makeText(this, getString(R.string.file_deleted), Toast.LENGTH_SHORT).show()
                        }else if(deletePhotoFromExternal(this, getContentUri(this, currentFile)!!, intentSenderLauncher)) {
                           removeImageFromAdapter()
                            Toast.makeText(this, getString(R.string.file_deleted), Toast.LENGTH_SHORT).show()
                        }else {
                            Toast.makeText(this, getString(R.string.file_not_deleted), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                true
            }
            popup.setOnDismissListener {
            }
            popup.show()
        }
    }

    private fun removeImageFromAdapter(){
        media = ArrayList(media!!).apply { removeAt(position) }

        if((media as ArrayList<Photo>).isNotEmpty()){
            viewPagerAdapter.updateData(media!!)
            viewPager.adapter = viewPagerAdapter
            viewPager.invalidate()
            viewPager.currentItem = position
            currentFile = File(media!![position].path)
            setDateTime()
        }else {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showSubmenu(v: View, @MenuRes menuRes: Int) {
        val popup = PopupMenu(this, v)
        popup.menuInflater.inflate(menuRes, popup.menu)

        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.menu_details-> {
                    //"dd/MM/yyyy HH:mm a"
                    val dateFormat = SimpleDateFormat(getString(R.string.time_format), Locale.getDefault())
                    val lastModified = currentFile.lastModified()
                    val fileSize = currentFile.length()

                    val fileSizeString: String = if (fileSize >= 1024 * 1024 * 1024) {
                        String.format(Locale.getDefault(), "%.2f GB", fileSize.toFloat() / (1024 * 1024 * 1024))
                    } else if (fileSize >= 1024 * 1024) {
                        String.format(Locale.getDefault(), "%.2f MB", fileSize.toFloat() / (1024 * 1024))
                    } else if (fileSize >= 1024) {
                        String.format(Locale.getDefault(), "%.2f KB", fileSize.toFloat() / 1024)
                    } else {
                        "$fileSize bytes"
                    }

                    MaterialAlertDialogBuilder(this)
                        //.setTitle((position + 1).toString() + "/" + media!!.size.toString())
                        .setMessage(getString(R.string.details_path) + ": " + currentFile.absolutePath
                                + "\n" + getString(R.string.details_type) + ": " + currentFile.extension
                                + "\n" + getString(R.string.details_size) + ": " + fileSizeString
                                + "\n" + getString(R.string.details_resolution) + ": " + getResolution(currentFile.path)
                                + "\n" + getString(R.string.details_date) + ": " + dateFormat.format(Date(lastModified)))
                        .setPositiveButton(getString(R.string.accept)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
                R.id.menu_move -> {
                    val selectionIntent = Intent(this, AlbumSelection::class.java)
                    resultLauncher.launch(selectionIntent)
                    operation = "MOVE"
                }
                R.id.menu_copy -> {

                    val mimeType: String = if (currentFile.extension in imageExtensions) "image/${currentFile.extension}" else "video/${currentFile.extension}"

                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType(mimeType)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setComponent(ComponentName(this, AlbumSelection::class.java))
                    resultLauncher.launch(intent)
                    operation = "COPY"
                }
                R.id.menu_rename -> {

                    val view = layoutInflater.inflate(R.layout.alert_edit_text, null)
                    val textInputLayout = view.findViewById<TextInputLayout>(R.id.text_input_layout)
                    val textInputEditText = view.findViewById<TextInputEditText>(R.id.text_input_edit_text)

                    textInputEditText.setText(currentFile.nameWithoutExtension)

                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.menu_rename))
                        .setView(textInputLayout)
                        .setPositiveButton(getString(R.string.menu_rename)) { _, _ ->
                            var newName = textInputEditText.text.toString()
                            newName += "." + currentFile.extension
                            val newFile = File(currentFile.parent!! + "/" + newName)
                            if (currentFile.renameTo(newFile)) {
                                currentFile = File(media!![position].path)
                                Toast.makeText(this, getString(R.string.file_renamed), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, getString(R.string.file_not_renamed), Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
            true
        }
        popup.setOnDismissListener {}
        popup.show()
    }

    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            destinationPath = data?.getStringExtra("RUTA")!!

            when(operation) {
                "MOVE" -> {
                    copyFileToUri(this, currentFile, destinationPath, true, requestPermissionLauncher, intentSenderLauncher)
                    removeImageFromAdapter()
                }
                "COPY" -> {
                    copyFileToUri(this, currentFile, destinationPath, false, requestPermissionLauncher, intentSenderLauncher)
                }
            }
        }
    }

    private fun getResolution(path: String): String {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        return options.outWidth.toString() + "x" + options.outHeight.toString()
    }

    private fun setDateTime() {
        val date = Date(currentFile.lastModified())
        val inputFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.getDefault())
        val outputFormatDate = SimpleDateFormat(getString(R.string.time_format), Locale.getDefault())

        val input = inputFormat.format(date)
        val dateParse = inputFormat.parse(input)

        photoName.text = currentFile.name

        photoDatetime.text = outputFormatDate.format(dateParse!!)
    }

    @SuppressLint("InternalInsetResource")
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId != 0) {
            resources.getDimensionPixelSize(resourceId)
        } else 0
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                val sharedPreferences = getSharedPreferences(PERMISSION_PREFS_NAME, Context.MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putBoolean(SD_CARD_PERMISSION_GRANTED_KEY, true)
                editor.apply()
                copyToExternal(this, currentFile, destinationPath, operation == "MOVE", intentSenderLauncher)
            }
        }
    }

    private fun toggleAutoScroll() {
        if (isAutoScrolling) {
            stopAutoScroll()
        } else {
            startAutoScroll()
        }
    }

    private fun startAutoScroll() {
        if (media == null || media!!.size <= 1) return
        
        isAutoScrolling = true
        btnPlayPause.setImageResource(R.drawable.ic_pause)
        Toast.makeText(this, getString(R.string.auto_scroll_started), Toast.LENGTH_SHORT).show()
        
        autoScrollJob = lifecycleScope.launch {
            while (isAutoScrolling && position < media!!.size - 1) {
                delay(autoScrollDelayMs)
                if (isAutoScrolling) {
                    runOnUiThread {
                        viewPager.currentItem = position + 1
                    }
                }
            }
            // Auto-scroll finished (reached end)
            if (isAutoScrolling) {
                stopAutoScroll()
            }
        }
    }

    private fun stopAutoScroll() {
        isAutoScrolling = false
        autoScrollJob?.cancel()
        autoScrollJob = null
        btnPlayPause.setImageResource(R.drawable.ic_play)
        Toast.makeText(this, getString(R.string.auto_scroll_stopped), Toast.LENGTH_SHORT).show()
    }

    private fun setupSlideshowButton() {
        // Setup toolbar slideshow button - bigger and prominently positioned
        btnPlayPauseToolbar.setImageResource(R.drawable.ic_play)
        btnPlayPauseToolbar.alpha = 1.0f // Full opacity for visibility
        
        // Set blue color for visibility on white background
        btnPlayPauseToolbar.setColorFilter(getColor(R.color.md_theme_dark_primary))
        
        // Add subtle animation when button appears
        btnPlayPauseToolbar.animate()
            .alpha(1.0f)
            .setDuration(300)
            .start()
    }
    
    private fun toggleSlideshow() {
        if (isAutoScrolling) {
            pauseSlideshow()
        } else if (isPaused) {
            resumeSlideshow()
        } else {
            startSlideshow()
        }
    }
    
    private fun startSlideshow() {
        if (media == null || media!!.size <= 1) {
            Toast.makeText(this, getString(R.string.slideshow_need_more_images), Toast.LENGTH_SHORT).show()
            return
        }
        
        isAutoScrolling = true
        isPaused = false
        updateSlideshowButton()
        Toast.makeText(this, getString(R.string.auto_scroll_started), Toast.LENGTH_SHORT).show()
        
        autoScrollJob = lifecycleScope.launch {
            while (isAutoScrolling) {
                delay(autoScrollDelayMs)
                if (isAutoScrolling) {
                    if (position < media!!.size - 1) {
                        // Move to next image in current album
                        runOnUiThread {
                            viewPager.currentItem = position + 1
                        }
                    } else {
                        // Reached end of current album, try to move to next folder
                        if (moveToNextFolder()) {
                            // Successfully moved to next folder, continue slideshow
                            continue
                        } else {
                            // No more folders, stop slideshow
                            break
                        }
                    }
                }
            }
            // Slideshow finished (reached end) or was stopped
            if (isAutoScrolling) {
                stopSlideshow()
                val message = if (isCrossFolderMode) getString(R.string.slideshow_all_folders_completed) else getString(R.string.slideshow_completed)
                Toast.makeText(this@PhotoView, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun pauseSlideshow() {
        isAutoScrolling = false
        isPaused = true
        autoScrollJob?.cancel()
        updateSlideshowButton()
        Toast.makeText(this, getString(R.string.slideshow_paused), Toast.LENGTH_SHORT).show()
    }
    
    private fun resumeSlideshow() {
        if (position >= media!!.size - 1) {
            // If at the end, restart from current position or ask user
            Toast.makeText(this, getString(R.string.slideshow_completed), Toast.LENGTH_SHORT).show()
            isPaused = false
            updateSlideshowButton()
            return
        }
        
        isAutoScrolling = true
        isPaused = false
        updateSlideshowButton()
        Toast.makeText(this, getString(R.string.slideshow_resumed), Toast.LENGTH_SHORT).show()
        
        autoScrollJob = lifecycleScope.launch {
            while (isAutoScrolling) {
                delay(autoScrollDelayMs)
                if (isAutoScrolling) {
                    if (position < media!!.size - 1) {
                        // Move to next image in current album
                        runOnUiThread {
                            viewPager.currentItem = position + 1
                        }
                    } else {
                        // Reached end of current album, try to move to next folder
                        if (moveToNextFolder()) {
                            // Successfully moved to next folder, continue slideshow
                            continue
                        } else {
                            // No more folders, stop slideshow
                            break
                        }
                    }
                }
            }
            if (isAutoScrolling) {
                stopSlideshow()
                val message = if (isCrossFolderMode) getString(R.string.slideshow_all_folders_completed) else getString(R.string.slideshow_completed)
                Toast.makeText(this@PhotoView, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun stopSlideshow() {
        isAutoScrolling = false
        isPaused = false
        autoScrollJob?.cancel()
        autoScrollJob = null
        updateSlideshowButton()
        Toast.makeText(this, getString(R.string.auto_scroll_stopped), Toast.LENGTH_SHORT).show()
    }
    
    private fun updateSlideshowButton() {
        when {
            isAutoScrolling -> {
                btnPlayPauseToolbar.setImageResource(R.drawable.ic_pause)
                btnPlayPauseToolbar.setColorFilter(getColor(R.color.md_theme_light_primary)) // Brighter blue when playing
                btnPlayPauseToolbar.alpha = 1.0f
                // Pulsing animation while playing
                btnPlayPauseToolbar.animate()
                    .scaleX(1.5f)
                    .scaleY(1.5f)
                    .setDuration(150)
                    .withEndAction {
                        btnPlayPauseToolbar.animate()
                            .scaleX(1.3f)
                            .scaleY(1.3f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
            isPaused -> {
                btnPlayPauseToolbar.setImageResource(R.drawable.ic_play)
                btnPlayPauseToolbar.setColorFilter(getColor(R.color.md_theme_light_inverseSurface)) // Gray when paused
                btnPlayPauseToolbar.alpha = 0.8f // Dimmed when paused
            }
            else -> {
                btnPlayPauseToolbar.setImageResource(R.drawable.ic_play)
                btnPlayPauseToolbar.setColorFilter(getColor(R.color.md_theme_dark_primary)) // Blue when ready
                btnPlayPauseToolbar.alpha = 1.0f // Normal state
            }
        }
    }

    private fun initializeCrossFolderData() {
        // Get all albums and their photos for cross-folder navigation
        lifecycleScope.launch {
            try {
                val albums = albumes ?: return@launch
                val albumList = mutableListOf<Pair<File, List<Photo>>>()
                
                // Sort albums by name using natural numeric ordering
                val sortedAlbums = albums.toSortedMap(naturalFolderComparator())
                
                for ((folder, _) in sortedAlbums) {
                    val photosInAlbum = getImagesFromAlbum(folder.absolutePath)
                    if (photosInAlbum.isNotEmpty()) {
                        albumList.add(Pair(folder, photosInAlbum))
                    }
                }
                
                allAlbums = albumList
                
                // Find current album index
                val currentImagePath = media?.get(position)?.path
                if (currentImagePath != null) {
                    val currentFolder = File(currentImagePath).parent
                    currentAlbumIndex = allAlbums.indexOfFirst { it.first.absolutePath == currentFolder }
                    if (currentAlbumIndex == -1) currentAlbumIndex = 0
                }
                
                isCrossFolderMode = allAlbums.size > 1
            } catch (e: Exception) {
                // Fallback to single album mode
                isCrossFolderMode = false
            }
        }
    }
    
    private fun moveToNextFolder(): Boolean {
        if (!isCrossFolderMode || currentAlbumIndex >= allAlbums.size - 1) {
            return false // No more folders
        }
        
        currentAlbumIndex++
        val nextAlbum = allAlbums[currentAlbumIndex]
        val nextAlbumPhotos = nextAlbum.second
        
        if (nextAlbumPhotos.isNotEmpty()) {
            // Update media list with next album's photos
            media = nextAlbumPhotos
            position = 0 // Start from first image of next album
            
            // Update ViewPager
            runOnUiThread {
                viewPagerAdapter = ViewPagerAdapter(this@PhotoView, media!!)
                viewPager.adapter = viewPagerAdapter
                viewPager.currentItem = 0
                currentFile = File(media!![0].path)
                setDateTime()
                
                // Show toast indicating folder change
                Toast.makeText(this@PhotoView, 
                    getString(R.string.slideshow_next_folder, nextAlbum.first.name), 
                    Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return false
    }

    private fun showNextFolderPrompt() {
        if (!isCrossFolderMode || currentAlbumIndex >= allAlbums.size - 1) {
            return // No more folders available
        }
        
        val nextAlbum = allAlbums[currentAlbumIndex + 1]
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.continue_to_next_folder))
            .setMessage(getString(R.string.reached_end_of_album, nextAlbum.first.name))
            .setPositiveButton(getString(R.string.continue_button)) { _, _ ->
                moveToNextFolder()
            }
            .setNegativeButton(getString(R.string.stay_here)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

}
