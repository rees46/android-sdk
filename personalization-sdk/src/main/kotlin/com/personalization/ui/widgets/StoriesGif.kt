package com.personalization.ui.widgets

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Длина одного прохода GIF по его байтам (G2): сумма задержек кадров, задержка короче 20 мс
 * (и нулевая) считается за 100 мс — как в браузерах. GIF узнаём по подписи, не по адресу (G1).
 */
internal object StoriesGif {

    private const val MIN_DELAY_MS = 20L
    private const val FALLBACK_DELAY_MS = 100L

    /** Байты начинаются с подписи GIF87a или GIF89a. */
    fun isGif(bytes: ByteBuffer): Boolean {
        val data = bytes.duplicate()
        data.position(0)
        if (data.remaining() < 6) return false
        val head = ByteArray(6).also { data.get(it) }
        val signature = String(head, Charsets.US_ASCII)
        return signature == "GIF87a" || signature == "GIF89a"
    }

    /** Задержки кадров, мс, уже с поправкой на короткие; `null` — не GIF или файл битый. */
    fun frameDelaysMs(bytes: ByteBuffer): List<Long>? {
        if (!isGif(bytes)) return null
        val data = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        data.position(6)
        return runCatching { readFrames(data) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Длина одного прохода, мс; `null` — не GIF или файл битый. */
    fun loopMs(bytes: ByteBuffer): Long? = frameDelaysMs(bytes)?.sum()

    private fun readFrames(data: ByteBuffer): List<Long> {
        data.short // ширина
        data.short // высота
        val packed = data.get().toInt() and 0xFF
        data.get() // фон
        data.get() // пропорции пикселя
        if (packed and 0x80 != 0) skip(data, 3 * (1 shl ((packed and 0x07) + 1)))

        val delays = ArrayList<Long>()
        var pendingDelayCs = 0
        while (data.hasRemaining()) {
            when (data.get().toInt() and 0xFF) {
                0x21 -> {
                    val label = data.get().toInt() and 0xFF
                    if (label == 0xF9) {
                        val size = data.get().toInt() and 0xFF
                        val start = data.position()
                        data.get() // флаги
                        pendingDelayCs = data.short.toInt() and 0xFFFF
                        data.position(start + size)
                    }
                    skipSubBlocks(data)
                }
                0x2C -> {
                    skip(data, 8) // положение и размер кадра
                    val flags = data.get().toInt() and 0xFF
                    if (flags and 0x80 != 0) skip(data, 3 * (1 shl ((flags and 0x07) + 1)))
                    data.get() // минимальный размер кода LZW
                    skipSubBlocks(data)
                    delays += delayMs(pendingDelayCs)
                    pendingDelayCs = 0
                }
                0x3B -> return delays
                else -> return delays
            }
        }
        return delays
    }

    private fun delayMs(centiseconds: Int): Long {
        val ms = centiseconds * 10L
        return if (ms < MIN_DELAY_MS) FALLBACK_DELAY_MS else ms
    }

    private fun skip(data: ByteBuffer, count: Int) {
        data.position(data.position() + count)
    }

    private fun skipSubBlocks(data: ByteBuffer) {
        while (true) {
            val size = data.get().toInt() and 0xFF
            if (size == 0) return
            skip(data, size)
        }
    }
}
