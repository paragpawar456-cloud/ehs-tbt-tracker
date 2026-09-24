package com.ehs.tbttracker.data.photo

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.ehs.tbttracker.data.remote.BackendConfig
import com.ehs.tbttracker.data.remote.SheetsWebAppApi
import com.ehs.tbttracker.domain.parsing.DriveLinkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.File
import java.util.Base64
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/** Coil model for a Drive-hosted TBT photo. */
data class DrivePhoto(val fileId: String, val widthPx: Int = 800)

/**
 * Loads Drive photos in three tiers:
 *  1. on-disk thumbnail cache (works offline),
 *  2. public lh3.googleusercontent.com/d/ID URL (files shared "anyone with link"),
 *  3. Apps Script `thumb` proxy, which reads private Form uploads with the owner's credentials.
 */
class DrivePhotoFetcher(
    private val data: DrivePhoto,
    private val options: Options,
    private val deps: Factory,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val cached = deps.cacheFile(data)
        if (cached.exists() && cached.length() > 0) {
            return@withContext result(cached.readBytes(), "image/jpeg", DataSource.DISK)
        }
        val (bytes, mime) = fetchPublic() ?: fetchViaProxy()
            ?: error("Drive photo ${data.fileId} is not accessible")
        runCatching { cached.parentFile?.mkdirs(); cached.writeBytes(bytes) }
        result(bytes, mime, DataSource.NETWORK)
    }

    private fun fetchPublic(): Pair<ByteArray, String>? {
        if (data.fileId in deps.privateIds) return null
        val request = Request.Builder().url(DriveLinkResolver.thumbnailUrl(data.fileId, data.widthPx)).build()
        return runCatching {
            deps.okHttp.newCall(request).execute().use { resp ->
                val type = resp.header("Content-Type").orEmpty()
                if (resp.isSuccessful && type.startsWith("image/")) resp.body!!.bytes() to type else null
            }
        }.getOrNull().also { if (it == null) deps.privateIds += data.fileId }
    }

    private suspend fun fetchViaProxy(): Pair<ByteArray, String>? {
        if (!deps.config.isConfigured) return null
        val r = deps.api.thumbnail(deps.config.webAppUrl, deps.config.token, data.fileId, data.widthPx)
        if (!r.ok || r.data.isNullOrEmpty()) return null
        return Base64.getDecoder().decode(r.data) to (r.mime ?: "image/jpeg")
    }

    private fun result(bytes: ByteArray, mime: String, source: DataSource) = SourceFetchResult(
        source = ImageSource(Buffer().write(bytes), options.fileSystem),
        mimeType = mime,
        dataSource = source,
    )

    @Singleton
    class Factory @Inject constructor(
        val okHttp: OkHttpClient,
        val api: SheetsWebAppApi,
        val config: BackendConfig,
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    ) : Fetcher.Factory<DrivePhoto> {
        internal val privateIds: MutableSet<String> = Collections.synchronizedSet(HashSet())

        internal fun cacheFile(p: DrivePhoto) = File(context.cacheDir, "drive_thumbs/${p.fileId}_${p.widthPx}.img")

        override fun create(data: DrivePhoto, options: Options, imageLoader: ImageLoader): Fetcher =
            DrivePhotoFetcher(data, options, this)
    }

    class Key : Keyer<DrivePhoto> {
        override fun key(data: DrivePhoto, options: Options): String = "drive:${data.fileId}:${data.widthPx}"
    }
}
