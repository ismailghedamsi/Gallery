package com.brandonhxrr.gallery.adapter.photo

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.brandonhxrr.gallery.Photo
import com.brandonhxrr.gallery.R
import com.bumptech.glide.RequestBuilder

class OptimizedPhotoAdapter(
    private val glide: RequestBuilder<Bitmap>,
    private val showDeleteMenu: (Boolean, Number) -> Unit
) : ListAdapter<Photo, PhotoViewHolder>(PhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return PhotoViewHolder(
            layoutInflater.inflate(R.layout.photo, parent, false), 
            this
        ) { show, items ->
            showDeleteMenu(show, items)
        }
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val item = getItem(position)
        holder.render(item, glide, currentList)
    }

    // Optimize for better performance
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Handle partial updates for better performance
            val item = getItem(position)
            holder.updateSelection(item)
        }
    }

    fun setSelectedItem(position: Int) {
        if (position < currentList.size) {
            val updatedList = currentList.toMutableList()
            updatedList[position] = updatedList[position].copy(selected = true)
            submitList(updatedList)
        }
    }

    fun removeSelectedItem(position: Int) {
        if (position < currentList.size) {
            val updatedList = currentList.toMutableList()
            updatedList[position] = updatedList[position].copy(selected = false)
            submitList(updatedList)
        }
    }

    fun resetItemsSelected() {
        val updatedList = currentList.map { it.copy(selected = false) }
        submitList(updatedList)
    }

    fun selectAllItems() {
        val updatedList = currentList.map { it.copy(selected = true) }
        submitList(updatedList)
    }

    // Enhanced method to update data efficiently
    fun updateData(newData: List<Photo>) {
        submitList(newData.toList()) // Create new list to trigger DiffUtil
    }
}

class PhotoDiffCallback : DiffUtil.ItemCallback<Photo>() {
    override fun areItemsTheSame(oldItem: Photo, newItem: Photo): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: Photo, newItem: Photo): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: Photo, newItem: Photo): Any? {
        return if (oldItem.selected != newItem.selected) {
            "selection_changed"
        } else null
    }
}

