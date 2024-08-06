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

@SuppressLint("UnsafeOptInUsageError")
class Player(context: Context) {
    init {
        if (player == null) {
            player = ExoPlayer.Builder(context).build()
            player?.setHandleAudioBecomingNoisy(true)
        }

        if (cache == null) {
            val file = File(context.cacheDir, "stories")
            val limit = LeastRecentlyUsedCacheEvictor((50 * 1024 * 1024).toLong())
            cache = SimpleCache(file, limit, StandaloneDatabaseProvider(context))
        }
    }

    fun prepare(url: String) {
        if (cache == null) return
        player?.let { player ->
            val mediaSource = ProgressiveMediaSource.Factory(
                CacheDataSource.Factory()
                    .setCache(cache!!)
                    .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory().setUserAgent(SDK.userAgent()))
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            ).createMediaSource(MediaItem.fromUri(url))
            player.setMediaSource(mediaSource)
            //		player.setMediaItem(MediaItem.fromUri(url));
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

    companion object {
		var player: ExoPlayer? = null
        private var cache: SimpleCache? = null
    }
}
