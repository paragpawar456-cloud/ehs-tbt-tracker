package com.ehs.tbttracker.data.photo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the app-private folder where evidence photos wait for upload. */
interface PhotoStorage {
    /** Moves/copies [sourcePath] into permanent storage under [recordId]; returns the new path. */
    fun persist(sourcePath: String, recordId: String): String
    fun readBytes(path: String): ByteArray?
}

@Singleton
class FilePhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoStorage {
    private val dir: File get() = File(context.filesDir, "tbt_photos").apply { mkdirs() }

    override fun persist(sourcePath: String, recordId: String): String {
        val src = File(sourcePath)
        require(src.exists()) { "Photo file is missing" }
        val dest = File(dir, "$recordId.jpg")
        if (src.absolutePath == dest.absolutePath) return dest.absolutePath
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
        return dest.absolutePath
    }

    override fun readBytes(path: String): ByteArray? = File(path).takeIf { it.exists() }?.readBytes()
}
