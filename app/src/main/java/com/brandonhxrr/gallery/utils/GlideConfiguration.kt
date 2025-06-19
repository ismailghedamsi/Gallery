package com.brandonhxrr.gallery.utils

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class GlideConfiguration : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Increase memory cache size for better performance
        val memoryCacheSizeBytes = 1024 * 1024 * 40 // 40MB
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes.toLong()))

        // Increase disk cache size for offline browsing
        val diskCacheSizeBytes = 1024 * 1024 * 200 // 200MB
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes.toLong()))

        // Use PREFER_RGB_565 for better memory efficiency
        builder.setDefaultRequestOptions(
            RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
        )
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Can register custom components here if needed
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}

object ImageLoader {
    fun getOptimizedGlideRequest(context: Context) = 
        Glide.with(context)
            .asBitmap()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .format(DecodeFormat.PREFER_RGB_565)
            .centerCrop()
            .skipMemoryCache(false)
}

