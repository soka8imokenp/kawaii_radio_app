package uz.kawaii.radio.kr01.audio

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * One disk cache for everything streamed from the cloud: songs replay without downloading again,
 * and the waveform reader shares the bytes the player already fetched.
 */
@OptIn(UnstableApi::class)
object MediaData {
    private var cache: SimpleCache? = null

    @Synchronized
    fun cache(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.cacheDir, "media"),
        LeastRecentlyUsedCacheEvictor(400L * 1024 * 1024),
        StandaloneDatabaseProvider(context),
    ).also { cache = it }

    /** http(s) goes through the cache; bundled assets and local files are read directly. */
    fun factory(context: Context): DataSource.Factory {
        val app = context.applicationContext
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("KawaiiRadio/Android")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val cached = CacheDataSource.Factory()
            .setCache(cache(app))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(app, http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val plain = DefaultDataSource.Factory(app, http)
        return DataSource.Factory { Routing(cached.createDataSource(), plain.createDataSource()) }
    }

    private class Routing(private val cached: DataSource, private val plain: DataSource) : DataSource {
        private var active: DataSource? = null
        override fun addTransferListener(l: TransferListener) {
            cached.addTransferListener(l)
            plain.addTransferListener(l)
        }
        override fun open(spec: DataSpec): Long {
            val s = spec.uri.scheme
            val ds = if (s == "http" || s == "https") cached else plain
            active = ds
            return ds.open(spec)
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int) = active!!.read(buffer, offset, length)
        override fun getUri(): Uri? = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
        override fun close() {
            active?.close()
            active = null
        }
    }

    /** Lets Android's MediaExtractor read through our cached data sources (random access by reopening). */
    class Reader(private val factory: DataSource.Factory, private val uri: Uri) : MediaDataSource() {
        private var ds: DataSource? = null
        private var pos = -1L
        private var length = -2L

        private fun openAt(p: Long) {
            ds?.close()
            val d = factory.createDataSource()
            val n = d.open(DataSpec.Builder().setUri(uri).setPosition(p).build())
            if (length == -2L) length = if (n == C.LENGTH_UNSET.toLong()) -1L else p + n
            ds = d
            pos = p
        }

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size == 0) return 0
            if (length >= 0 && position >= length) return -1
            if (ds == null || position != pos) openAt(position)
            var read = 0
            while (read < size) {
                val r = ds!!.read(buffer, offset + read, size - read)
                if (r == C.RESULT_END_OF_INPUT) break
                read += r
            }
            pos += read
            return if (read == 0) -1 else read
        }

        override fun getSize(): Long {
            if (length == -2L) openAt(0)
            return length
        }

        override fun close() {
            ds?.close()
            ds = null
        }
    }
}
