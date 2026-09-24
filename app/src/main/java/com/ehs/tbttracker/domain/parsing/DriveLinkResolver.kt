package com.ehs.tbttracker.domain.parsing

import com.ehs.tbttracker.domain.model.PhotoRef

/**
 * Converts the Drive links Google Forms writes ("https://drive.google.com/open?id=FILE_ID")
 * and other common Drive URL shapes into something an image loader can render.
 */
object DriveLinkResolver {
    private val ID_PATTERNS = listOf(
        Regex("""[?&]id=([\w-]{10,})"""),          // open?id=, uc?id=, thumbnail?id=
        Regex("""/file/d/([\w-]{10,})"""),          // /file/d/ID/view
        Regex("""/d/([\w-]{10,})"""),               // lh3.googleusercontent.com/d/ID
    )
    private val DRIVE_HOSTS = listOf("drive.google.com", "docs.google.com", "googleusercontent.com")

    fun extractFileId(url: String?): String? {
        val s = url?.trim().orEmpty()
        if (s.isEmpty() || DRIVE_HOSTS.none { s.contains(it) }) return null
        // Some cells hold several comma-separated uploads; the first one is the TBT photo.
        val first = s.split(',', ' ', '\n').first { it.isNotBlank() }
        return ID_PATTERNS.firstNotNullOfOrNull { it.find(first)?.groupValues?.get(1) }
    }

    /** Direct-render URL. Works for files shared "anyone with the link"; private files need the Apps Script proxy. */
    fun thumbnailUrl(fileId: String, widthPx: Int = 800): String =
        "https://lh3.googleusercontent.com/d/$fileId=w$widthPx"

    fun driveThumbnailUrl(fileId: String, widthPx: Int = 800): String =
        "https://drive.google.com/thumbnail?id=$fileId&sz=w$widthPx"

    fun viewUrl(fileId: String): String = "https://drive.google.com/file/d/$fileId/view"

    fun toPhotoRef(raw: String?): PhotoRef? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        extractFileId(s)?.let { return PhotoRef.Drive(it, s) }
        return if (s.startsWith("https://") || s.startsWith("http://")) PhotoRef.Url(s) else null
    }
}
