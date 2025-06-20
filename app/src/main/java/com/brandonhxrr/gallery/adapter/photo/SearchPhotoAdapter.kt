package com.brandonhxrr.gallery.adapter.photo

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.brandonhxrr.gallery.Photo
import com.brandonhxrr.gallery.R
import com.bumptech.glide.RequestBuilder
import java.io.File

class SearchPhotoAdapter(
    private var photos: List<Photo>,
    private val builder: RequestBuilder<Bitmap>
) : RecyclerView.Adapter<SearchPhotoAdapter.SearchPhotoViewHolder>() {

    private var onItemClickListener: ((Photo, Int) -> Unit)? = null

    fun setOnItemClickListener(listener: (Photo, Int) -> Unit) {
        onItemClickListener = listener
    }

    fun updatePhotos(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchPhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.search_photo_item, parent, false)
        return SearchPhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchPhotoViewHolder, position: Int) {
        holder.bind(photos[position], position)
    }

    override fun getItemCount(): Int = photos.size

    inner class SearchPhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.photoImageView)
        private val filenameTextView: TextView = itemView.findViewById(R.id.filenameTextView)

        fun bind(photo: Photo, position: Int) {
            // Load image with Glide
            builder.load(photo.path)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .into(imageView)

            // Set filename
            val filename = File(photo.path).nameWithoutExtension
            filenameTextView.text = filename

            // Set click listener
            itemView.setOnClickListener {
                onItemClickListener?.invoke(photo, position)
            }
        }
    }
}

