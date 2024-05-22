package com.personalizatio.stories

import android.content.Context

@UnstableApi
internal class Player(context: Context?) {
    init {
        if (player == null) {
            player = Builder(context).build()
            player.setHandleAudioBecomingNoisy(true)
        }

        //Подготавливаем кеш
        if (cache == null) {
            val file: File = File(context.getCacheDir(), "stories")
            val limit: LeastRecentlyUsedCacheEvictor = LeastRecentlyUsedCacheEvictor(50 * 1024 * 1024)
            cache = SimpleCache(file, limit, StandaloneDatabaseProvider(context))
        }
    }

    fun prepare(url: String?) {
        val mediaSource: ProgressiveMediaSource = Factory(
            Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(Factory().setUserAgent(SDK.userAgent()))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ).createMediaSource(MediaItem.fromUri(url))
        player.setMediaSource(mediaSource)
        //		player.setMediaItem(MediaItem.fromUri(url));
        player.prepare()
        player.setPlayWhenReady(true)
    }

    fun release() {
        cache.release()
        cache = null
        player.release()
        player = null
    }

    companion object {
        var player: ExoPlayer? = null
        var cache: SimpleCache? = null
    }
}
