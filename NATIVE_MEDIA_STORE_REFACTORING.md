# Native MediaStore Refactoring

This document explains the refactoring from custom folder selection to Android's native MediaStore-based album detection.

## Overview

The original implementation used a custom folder scanning approach that was:
- Inefficient (scanned filesystem manually)
- Not following Android best practices
- Missing out on Android's optimized media indexing

## New Implementation

### Key Components

1. **MediaStoreHelper.kt** - Core class for MediaStore operations
   - Uses MediaStore.Images.Media.BUCKET_ID for album detection
   - Leverages Android's native media indexing
   - Supports both images and videos
   - Much more efficient than filesystem scanning

2. **NativeAlbumPreferences.kt** - Preferences management
   - Uses bucket IDs instead of file paths
   - Supports migration from old preferences
   - Includes SAF (Storage Access Framework) support

3. **NativeAlbumSelectionAdapter.kt** - UI for album selection
   - Shows album covers with metadata
   - Uses bucket-based selection instead of folder paths

4. **SAFHelper.kt** - Storage Access Framework integration
   - Allows users to select specific folders using Android's native folder picker
   - Provides proper permissions handling

### Benefits of Native Approach

1. **Performance**: Uses Android's optimized MediaStore instead of manual filesystem scanning
2. **Reliability**: Works with Android's media indexing system
3. **Compatibility**: Better support for different storage types (internal, SD cards, cloud)
4. **Future-proof**: Follows Android's recommended practices for media access

### Key Differences

| Old Approach | New Approach |
|-------------|-------------|
| Manual folder scanning | MediaStore bucket queries |
| File path based | Bucket ID based |
| Custom folder selection | Native album detection + SAF |
| Inefficient filesystem I/O | Optimized database queries |

### Migration Process

1. **Automatic Migration**: Existing users' folder preferences are automatically converted to bucket IDs
2. **Preference Mapping**: Old folder paths are mapped to corresponding MediaStore bucket IDs
3. **Fallback**: If migration fails, users see the new album selection dialog

### Usage

The refactoring is transparent to end users but provides:
- Faster album loading
- Better album organization (uses Android's native album names)
- More reliable folder detection
- Support for Storage Access Framework for custom folder selection

### Backward Compatibility

- Old preferences are automatically migrated
- Users can still access the same albums
- Album selection dialog now shows native album names with cover images
- Maintains the same UI/UX flow

### Future Enhancements

With this native approach, we can easily add:
- Better album metadata (creation dates, thumbnail generation)
- Support for different media types
- Integration with Android's scoped storage
- Cloud storage album detection
- Better performance optimizations

## Implementation Notes

- Uses coroutines for non-blocking MediaStore queries
- Implements proper error handling for permission issues
- Supports Android 10+ scoped storage requirements
- Maintains compatibility with older Android versions
- Includes SAF integration for advanced users who want custom folder selection

This refactoring makes the gallery app more efficient, reliable, and aligned with Android's best practices while maintaining the same user experience.

