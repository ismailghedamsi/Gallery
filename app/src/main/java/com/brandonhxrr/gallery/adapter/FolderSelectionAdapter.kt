package com.brandonhxrr.gallery.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.brandonhxrr.gallery.R
import java.io.File

class FolderSelectionAdapter(
    private val folders: List<File>
) : RecyclerView.Adapter<FolderSelectionAdapter.FolderViewHolder>() {
    
    private val selectedFolders = mutableSetOf<String>()
    private var isEnabled = false
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder_selection, parent, false)
        return FolderViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.bind(folder)
    }
    
    override fun getItemCount(): Int = folders.size
    
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        notifyDataSetChanged()
    }
    
    fun getSelectedFolders(): Set<String> = selectedFolders.toSet()
    
    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkbox: CheckBox = itemView.findViewById(R.id.folderCheckBox)
        private val folderName: TextView = itemView.findViewById(R.id.folderName)
        private val folderPath: TextView = itemView.findViewById(R.id.folderPath)
        
        fun bind(folder: File) {
            folderName.text = folder.name
            folderPath.text = folder.absolutePath
            
            checkbox.isEnabled = isEnabled
            checkbox.alpha = if (isEnabled) 1.0f else 0.5f
            folderName.alpha = if (isEnabled) 1.0f else 0.5f
            folderPath.alpha = if (isEnabled) 1.0f else 0.5f
            
            checkbox.isChecked = selectedFolders.contains(folder.absolutePath)
            
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isEnabled) {
                    if (isChecked) {
                        selectedFolders.add(folder.absolutePath)
                    } else {
                        selectedFolders.remove(folder.absolutePath)
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

