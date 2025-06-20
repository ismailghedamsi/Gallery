package com.brandonhxrr.gallery.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.brandonhxrr.gallery.AlbumFolder
import com.brandonhxrr.gallery.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File

/**
 * Adapter for native album selection using MediaStore buckets
 * Replaces file-based folder selection with bucket-based approach
 */
class NativeAlbumSelectionAdapter(
    private val albums: List<AlbumFolder>
) : RecyclerView.Adapter<NativeAlbumSelectionAdapter.AlbumViewHolder>() {
    
    private val selectedBucketIds = mutableSetOf<String>()
    private var isEnabled = false
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_native_album_selection, parent, false)
        return AlbumViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = albums[position]
        holder.bind(album)
    }
    
    override fun getItemCount(): Int = albums.size
    
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        notifyDataSetChanged()
    }
    
    fun getSelectedBucketIds(): Set<String> = selectedBucketIds.toSet()
    
    fun setSelectedBucketIds(bucketIds: Set<String>) {
        selectedBucketIds.clear()
        selectedBucketIds.addAll(bucketIds)
        notifyDataSetChanged()
    }
    
    inner class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkbox: CheckBox = itemView.findViewById(R.id.albumCheckBox)
        private val albumImage: ImageView = itemView.findViewById(R.id.albumImage)
        private val albumName: TextView = itemView.findViewById(R.id.albumName)
        private val albumCount: TextView = itemView.findViewById(R.id.albumCount)
        private val albumPath: TextView = itemView.findViewById(R.id.albumPath)
        
        fun bind(album: AlbumFolder) {
            albumName.text = album.name
            albumCount.text = "${album.imageCount} items"
            albumPath.text = album.path
            
            // Load album cover image
            Glide.with(itemView.context)
                .load(File(album.coverImagePath))
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(albumImage)
            
            checkbox.isEnabled = isEnabled
            checkbox.alpha = if (isEnabled) 1.0f else 0.5f
            albumName.alpha = if (isEnabled) 1.0f else 0.5f
            albumCount.alpha = if (isEnabled) 1.0f else 0.5f
            albumPath.alpha = if (isEnabled) 1.0f else 0.5f
            albumImage.alpha = if (isEnabled) 1.0f else 0.5f
            
            checkbox.isChecked = selectedBucketIds.contains(album.bucketId)
            
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isEnabled) {
                    if (isChecked) {
                        selectedBucketIds.add(album.bucketId)
                    } else {
                        selectedBucketIds.remove(album.bucketId)
                    }
                }
            }
            
            itemView.setOnClickListener {
                if (isEnabled) {
                    checkbox.isChecked = !checkbox.isChecked
                }
            }
        }
    }
}

