package com.brandonhxrr.gallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

open class Splash : AppCompatActivity() {
    private val REQUEST_PERMISSIONS = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
    }

    override fun onStart() {
        super.onStart()
        checkPermissions()
    }

    private fun checkPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()){
            openSettingsAllFilesAccess(this)
        }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()){
            loadData()
        } else{
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) || !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) || !hasPermission(Manifest.permission.MANAGE_DOCUMENTS) || !hasPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) || !hasPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.MANAGE_DOCUMENTS, Manifest.permission.ACCESS_MEDIA_LOCATION, Manifest.permission.MANAGE_EXTERNAL_STORAGE), REQUEST_PERMISSIONS)
            }else {
                loadData()
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                REQUEST_PERMISSIONS -> loadData()
            }
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.permission_denied))
                .setMessage(getString(R.string.permission_denied_exp))
                .setPositiveButton(getString(R.string.retry)){ _, _ ->
                    checkPermissions()
                }.setNegativeButton(getString(R.string.exit)){ _, _ ->
                    finish()
                }.setCancelable(false).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun loadData() {
        // Don't load all images at startup - this will be done lazily in fragments
        // Just initialize the albums variable as null to signal lazy loading is needed
        albumes = null

        // Check if we should auto-resume from last image
        if (shouldAutoResumeFromLastImage()) {
            autoResumeFromLastImage()
        } else {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun shouldAutoResumeFromLastImage(): Boolean {
        // Check if this is a true app launch (not return from another activity)
        if (!isTaskRoot) {
            return false
        }
        
        if (!LastImagePreferences.isResumeEnabled(this) || 
            !LastImagePreferences.hasLastImage(this)) {
            return false
        }

        val lastImageTimestamp = LastImagePreferences.getLastImageTimestamp(this)
        // Only auto-resume if the last image was viewed recently (within 1 hour)
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        return lastImageTimestamp > oneHourAgo
    }

    private fun autoResumeFromLastImage() {
        val lastImagePath = LastImagePreferences.getLastImagePath(this)
        
        if (lastImagePath == null || !File(lastImagePath).exists()) {
            LastImagePreferences.clearLastImage(this)
            // Fall back to normal main activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // Get the album folder containing the last image
        val lastImageFile = java.io.File(lastImagePath)
        val albumPath = lastImageFile.parent ?: return
        
        // Get images from the album containing the last image
        val albumImages = getImagesFromAlbum(albumPath)
        val currentPosition = albumImages.indexOfFirst { it.path == lastImagePath }
        
        if (currentPosition >= 0) {
            val intent = Intent(this, PhotoView::class.java)
            val gson = com.google.gson.Gson()
            val limit = if (albumImages.size > 1000) 1000 else albumImages.size
            
            // If the last image position is beyond our limit, we need to handle it
            val limitedImages = albumImages.subList(0, limit)
            val adjustedPosition = if (currentPosition >= limit) {
                // Find the image in the limited list, or fallback to 0
                val positionInLimitedList = limitedImages.indexOfFirst { it.path == lastImagePath }
                if (positionInLimitedList >= 0) positionInLimitedList else 0
            } else {
                currentPosition
            }
            
            val data = gson.toJson(limitedImages)
            
            intent.putExtra("path", lastImagePath)
            intent.putExtra("data", data)
            intent.putExtra("position", adjustedPosition)
            intent.putExtra("auto_resumed", true)
            startActivity(intent)
            finish()
        } else {
            LastImagePreferences.clearLastImage(this)
            // Fall back to normal main activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun openSettingsAllFilesAccess(activity: AppCompatActivity) {
        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        activity.startActivity(intent)
    }
}