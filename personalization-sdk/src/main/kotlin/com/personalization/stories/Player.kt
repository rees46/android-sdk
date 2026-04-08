package com.personalization.stories

import android.annotation.SuppressLint
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.personalization.SDK
import java.io.File

private const val STORIES_CACHE_DIR_PREFIX = "stories_"
private const val STORIES_CACHE_DIR_DEFAULT = "stories"
private const val CACHE_SIZE_BYTES = 50L * 1024 * 1024

@SuppressLint("UnsafeOptInUsageError")
class Player(context: Context, shopId: String = "") {

    var player: ExoPlayer? = null
        private set

    private var cache: SimpleCache? = null

    init {
        player = ExoPlayer.Builder(context).build()
        player?.setHandleAudioBecomingNoisy(true)

        val cacheDirName = if (shopId.isNotEmpty()) {
            STORIES_CACHE_DIR_PREFIX + shopId
        } else {
            STORIES_CACHE_DIR_DEFAULT
        }
        val file = File(context.cacheDir, cacheDirName)
        val limit = LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES)
        cache = SimpleCache(file, limit, StandaloneDatabaseProvider(context))
    }

    fun prepare(url: String) {
        if (cache == null) return
        player?.let { player ->
            val mediaSource = ProgressiveMediaSource.Factory(
                CacheDataSource.Factory()
                    .setCache(cache!!)
                    .setUpstreamDataSourceFactory(
                        DefaultHttpDataSource.Factory().setUserAgent(SDK.userAgent())
                    )
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            ).createMediaSource(MediaItem.fromUri(url))
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun release() {
        cache?.release()
        cache = null
        player?.release()
        player = null
    }
}
