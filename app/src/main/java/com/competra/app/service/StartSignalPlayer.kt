package com.competra.app.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Синтезирует и воспроизводит предстартовые сигналы.
 *
 * - Звонок за 10 секунд: восходящее арпеджио 1000 → 1250 → 1500 → 2000 Гц с колокольным затуханием.
 * - Отсчёт «3‑2‑1‑старт»: три коротких сигнала 1000 Гц через секунду и длинный стартовый
 *   2000 + 2500 Гц. Весь отсчёт — один заранее сгенерированный фрагмент, поэтому интервалы
 *   между сигналами точны до звукового отсчёта и не зависят от планировщика потоков.
 *
 * Звук идёт в поток будильника ([AudioAttributes.USAGE_ALARM]) — слышен в беззвучном режиме,
 * громкость регулируется ползунком будильника. Методы вызываются из одного потока.
 */
class StartSignalPlayer {

    private val chime = Clip(renderChime())
    private val countdown = Clip(renderCountdown())

    /** Воспроизводит звонок за 10 секунд до старта. */
    fun playChime() = chime.play(offsetMs = 0L)

    /**
     * Воспроизводит отсчёт «3‑2‑1‑старт».
     *
     * @param offsetMs Сколько миллисекунд от начала фрагмента (момента «−3 с») уже прошло:
     *   при запоздалом запуске фрагмент играется с этого места, чтобы сигналы совпали с секундами.
     */
    fun playCountdown(offsetMs: Long) = countdown.play(offsetMs)

    /** Освобождает аудиоресурсы. */
    fun release() {
        chime.release()
        countdown.release()
    }

    /** Статический [AudioTrack] с одним фрагментом, который можно перезапускать с любого места. */
    private class Clip(samples: ShortArray) {
        private val frameCount = samples.size
        private val track: AudioTrack? = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
                .also { it.write(samples, 0, samples.size) }
                .takeIf { it.state == AudioTrack.STATE_INITIALIZED }
        } catch (_: Exception) {
            null
        }

        fun play(offsetMs: Long) {
            val track = track ?: return
            val offsetFrames = (offsetMs.coerceAtLeast(0L) * SAMPLE_RATE / 1000L).toInt()
            if (offsetFrames >= frameCount) return
            try {
                // Статический трек после проигрывания стоит в конце буфера —
                // останавливаем, перематываем в начало и ставим на нужное место.
                track.stop()
                track.reloadStaticData()
                if (offsetFrames > 0) track.setPlaybackHeadPosition(offsetFrames)
                track.play()
            } catch (_: Exception) {
                // Звук не критичен для работы сервиса.
            }
        }

        fun release() {
            track?.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100

        /** Пиковая амплитуда сигнала (доля от максимума PCM) — с небольшим запасом от клиппинга. */
        const val PEAK = 0.9

        const val ATTACK_S = 0.004
        const val RELEASE_S = 0.012

        /** Амплитуды нечётных гармоник 1, 3, 5, 7 — звонкий «электронный» тембр. */
        val HARMONICS = doubleArrayOf(1.0, 0.33, 0.18, 0.08)

        /** Пик суммы гармоник за период — для нормировки волны к 1. */
        val WAVE_PEAK: Double = (0 until 1000).maxOf { abs(rawWave(2 * PI * it / 1000)) }

        const val SHORT_FREQ_HZ = 1000.0
        const val SHORT_DURATION_MS = 120
        val START_FREQS_HZ = listOf(2000.0, 2500.0)
        const val START_DURATION_MS = 800

        val CHIME_FREQS_HZ = listOf(1000.0, 1250.0, 1500.0, 2000.0)
        const val CHIME_STEP_MS = 160
        const val CHIME_NOTE_MS = 220
        const val CHIME_LAST_NOTE_MS = 600

        fun rawWave(phase: Double): Double =
            HARMONICS.withIndex().sumOf { (i, amp) -> amp * sin((2 * i + 1) * phase) }

        fun renderChime(): ShortArray {
            val lastStart = CHIME_STEP_MS * (CHIME_FREQS_HZ.size - 1)
            val buffer = DoubleArray(msToFrames(lastStart + CHIME_LAST_NOTE_MS))
            CHIME_FREQS_HZ.forEachIndexed { i, freq ->
                val isLast = i == CHIME_FREQS_HZ.lastIndex
                buffer.addTone(
                    startMs = i * CHIME_STEP_MS,
                    durationMs = if (isLast) CHIME_LAST_NOTE_MS else CHIME_NOTE_MS,
                    freqs = listOf(freq),
                    bell = true
                )
            }
            return buffer.toPcm()
        }

        fun renderCountdown(): ShortArray {
            val buffer = DoubleArray(msToFrames(3000 + START_DURATION_MS))
            for (second in 0 until 3) {
                buffer.addTone(second * 1000, SHORT_DURATION_MS, listOf(SHORT_FREQ_HZ), bell = false)
            }
            buffer.addTone(3000, START_DURATION_MS, START_FREQS_HZ, bell = false)
            return buffer.toPcm()
        }

        fun msToFrames(ms: Int): Int = ms * SAMPLE_RATE / 1000

        /** Добавляет в буфер тон из [freqs] с огибающей: ровной или колокольной (затухающей). */
        fun DoubleArray.addTone(startMs: Int, durationMs: Int, freqs: List<Double>, bell: Boolean) {
            val start = msToFrames(startMs)
            val frames = msToFrames(durationMs)
            val duration = durationMs / 1000.0
            val amplitude = PEAK / freqs.size
            for (i in 0 until frames) {
                val index = start + i
                if (index >= size) break
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = if (bell) bellEnvelope(t, duration) else flatEnvelope(t, duration)
                val sample = freqs.sumOf { rawWave(2 * PI * it * t) } / WAVE_PEAK
                this[index] += amplitude * envelope * sample
            }
        }

        fun flatEnvelope(t: Double, duration: Double): Double = when {
            t < ATTACK_S -> t / ATTACK_S
            t > duration - RELEASE_S -> ((duration - t) / RELEASE_S).coerceAtLeast(0.0)
            else -> 1.0
        }

        /** Быстрая атака, затем экспоненциальный спад до 25 % к 60 % длительности и почти до нуля к концу. */
        fun bellEnvelope(t: Double, duration: Double): Double {
            val knee = duration * 0.6
            return when {
                t < ATTACK_S -> t / ATTACK_S
                t < knee -> 0.25.pow((t - ATTACK_S) / (knee - ATTACK_S))
                else -> 0.25 * (0.0001 / 0.25).pow((t - knee) / (duration - knee))
            }
        }

        fun DoubleArray.toPcm(): ShortArray = ShortArray(size) { i ->
            (this[i].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort()
        }
    }
}
