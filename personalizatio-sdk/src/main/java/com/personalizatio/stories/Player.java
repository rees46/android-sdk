package com.personalizatio.stories;

import android.content.Context;

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import com.personalizatio.SDK;

import java.io.File;

@UnstableApi
final class Player {

	final ExoPlayer player;
	final SimpleCache cache;

	public Player(Context context) {
		player = new ExoPlayer.Builder(context).build();
		player.setHandleAudioBecomingNoisy(true);

		//Подготавливаем кеш
		File file = new File(context.getCacheDir(), "stories");
		LeastRecentlyUsedCacheEvictor limit = new LeastRecentlyUsedCacheEvictor(50 * 1024 * 1024);
		cache = new SimpleCache(file, limit, new StandaloneDatabaseProvider(context));
	}

	public void prepare(String url) {
		ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(
				new CacheDataSource.Factory()
						.setCache(cache)
						.setUpstreamDataSourceFactory(new DefaultHttpDataSource.Factory().setUserAgent(SDK.userAgent()))
						.setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
		).createMediaSource(MediaItem.fromUri(url));
		player.setMediaSource(mediaSource);
//		player.setMediaItem(MediaItem.fromUri(url));
		player.prepare();
		player.setPlayWhenReady(true);
	}

	public void release() {
		cache.release();
		player.release();
	}
}
