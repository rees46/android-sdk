package com.personalization.ui.widgets

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.personalization.SDK
import java.io.File
import java.util.concurrent.Executors

/**
 * Дисковый кэш видео сторис V2 (C6): LRU на 50 МБ в `cacheDir/personalization_stories`,
 * один на процесс — его делят все просмотры. Старые сторис держат свой кэш в другой папке:
 * две `SimpleCache` на одной папке не уживаются.
 *
 * Чтение идёт с [CacheDataSource.FLAG_BLOCK_ON_CACHE]: пока предзагрузка качает начало
 * ролика, плеер ждёт её, а не тянет те же байты второй раз.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal object StoriesMediaCache {

    private const val FOLDER = "personalization_stories"
    private const val MAX_BYTES = 50L * 1024 * 1024

    /** Сколько начала ролика качать заранее: хватает на первые секунды. */
    private const val PRELOAD_BYTES = 1024L * 1024

    private var cache: SimpleCache? = null
    private val preloading = HashSet<String>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "personalization-stories-preload").apply { isDaemon = true }
    }

    @Synchronized
    private fun cache(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.applicationContext.cacheDir, FOLDER),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        StandaloneDatabaseProvider(context.applicationContext)
    ).also { cache = it }

    private fun dataSourceFactory(context: Context): CacheDataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(SDK.userAgent())
            .setAllowCrossProtocolRedirects(true)
        return CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context.applicationContext, http))
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Источник для плеера: сеть через кэш. */
    fun mediaSourceFactory(context: Context): MediaSource.Factory =
        DefaultMediaSourceFactory(dataSourceFactory(context))

    /** Начало ролика — в кэш заранее, чтобы следующий слайд пошёл сразу. Дубли не качаются. */
    fun preloadVideo(context: Context, url: String) {
        if (!url.startsWith("http")) return
        synchronized(preloading) { if (!preloading.add(url)) return }
        val factory = dataSourceFactory(context)
        executor.execute {
            try {
                val spec = DataSpec.Builder().setUri(Uri.parse(url)).setLength(PRELOAD_BYTES).build()
                CacheWriter(factory.createDataSourceForDownloading(), spec, null, null).cache()
            } catch (_: Throwable) {
                // Предзагрузка — только ускорение: не вышло — ролик докачается при показе.
            } finally {
                synchronized(preloading) { preloading.remove(url) }
            }
        }
    }
}

/**
 * Аудиофокус видео сторис (C7). Пока звук выключен, фокус не берётся — музыка других
 * приложений играет дальше. Включённый звук берёт временный фокус: чужой плеер встаёт
 * на паузу и сам продолжит, когда просмотр отдаст фокус (выключил звук, закрыл).
 * Потеря фокуса (звонок, другой плеер) выключает звук — [onLoss].
 */
internal class StoriesAudioFocus(context: Context, private val onLoss: () -> Unit) {

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var held = false
    private var request: Any? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            abandon()
            onLoss()
        }
    }

    fun request() {
        val manager = audioManager ?: return
        if (held) return
        held = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Api26.request(manager, listener).also { request = it.second }.first
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandon() {
        val manager = audioManager ?: return
        if (!held) return
        held = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (request as? AudioFocusRequest)?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(listener)
        }
        request = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private object Api26 {
        fun request(manager: AudioManager, listener: AudioManager.OnAudioFocusChangeListener): Pair<Boolean, AudioFocusRequest> {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            return (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) to request
        }
    }
}
