package app.audiotexto

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/** Decodifica qualquer áudio suportado pelo Android (opus/ogg do WhatsApp, m4a, mp3, amr...) para PCM float 16 kHz mono. */
object AudioDecoder {
    const val TARGET_RATE = 16000

    fun decode(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i; format = f; break
                }
            }
            require(track >= 0 && format != null) { "O arquivo não tem faixa de áudio." }
            extractor.selectTrack(track)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 48000
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

            val info = MediaCodec.BufferInfo()
            var pcm = ShortArray(1 shl 20)
            var n = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    out >= 0 -> {
                        val ob = codec.getOutputBuffer(out)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val cnt = sb.remaining()
                        if (n + cnt > pcm.size) pcm = pcm.copyOf(max(pcm.size * 2, n + cnt))
                        sb.get(pcm, n, cnt)
                        n += cnt
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
            return toMono16k(pcm, n, channels, sampleRate)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun toMono16k(pcm: ShortArray, n: Int, channels: Int, rate: Int): FloatArray {
        val ch = max(1, channels)
        val frames = n / ch
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var s = 0f
            for (c in 0 until ch) s += pcm[i * ch + c]
            mono[i] = s / ch / 32768f
        }
        if (rate == TARGET_RATE) return mono

        // Reamostragem por média de janela (filtro simples, suficiente para voz)
        val ratio = rate.toDouble() / TARGET_RATE
        val outLen = (frames / ratio).toInt()
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val start = (i * ratio).toInt()
            val end = min(frames, max(start + 1, ((i + 1) * ratio).toInt()))
            var s = 0f
            for (j in start until end) s += mono[j]
            out[i] = s / (end - start)
        }
        return out
    }
}
