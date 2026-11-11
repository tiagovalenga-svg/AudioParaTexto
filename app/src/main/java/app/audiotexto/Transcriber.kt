package app.audiotexto

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlin.math.abs
import kotlin.math.min

/** Whisper offline (sherpa-onnx). O Whisper processa no máximo 30 s por vez, então o áudio é fatiado. */
class Transcriber {
    private var recognizer: OfflineRecognizer? = null
    private var loadedModel: String? = null
    private var loadedLang: String? = null

    private fun ensureLoaded(model: String, files: ModelManager.Files, language: String) {
        if (recognizer != null && loadedModel == model && loadedLang == language) return
        recognizer?.release()
        val cfg = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = files.encoder.absolutePath,
                    decoder = files.decoder.absolutePath,
                    language = language,
                    task = "transcribe",
                ),
                tokens = files.tokens.absolutePath,
                modelType = "whisper",
                numThreads = min(4, Runtime.getRuntime().availableProcessors()),
                provider = "cpu",
            ),
            decodingMethod = "greedy_search",
        )
        recognizer = OfflineRecognizer(assetManager = null, config = cfg)
        loadedModel = model
        loadedLang = language
    }

    /** [language]: "pt", "en", "es"... ou "" para detectar automaticamente. */
    fun transcribe(
        model: String,
        files: ModelManager.Files,
        language: String,
        samples: FloatArray,
        onProgress: (Int, Int) -> Unit,
    ): String {
        ensureLoaded(model, files, language)
        val rec = recognizer!!
        val chunks = split(samples)
        val sb = StringBuilder()
        for ((i, range) in chunks.withIndex()) {
            onProgress(i, chunks.size)
            val part = samples.copyOfRange(range.first, range.last)
            val stream = rec.createStream()
            try {
                stream.acceptWaveform(part, AudioDecoder.TARGET_RATE)
                rec.decode(stream)
                val text = rec.getResult(stream).text.trim()
                if (text.isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(text)
                }
            } finally {
                stream.release()
            }
        }
        onProgress(chunks.size, chunks.size)
        return sb.toString()
    }

    /** Divide em janelas de até ~28 s, cortando no ponto mais silencioso dos últimos 4 s da janela. */
    private fun split(x: FloatArray): List<IntRange> {
        val rate = AudioDecoder.TARGET_RATE
        val maxLen = 28 * rate
        val search = 4 * rate
        val frame = rate / 50 // 20 ms
        val out = ArrayList<IntRange>()
        var start = 0
        while (start < x.size) {
            if (x.size - start <= maxLen) { out.add(start..x.size); break }
            val hardEnd = start + maxLen
            var bestPos = hardEnd
            var bestEnergy = Float.MAX_VALUE
            var p = hardEnd - search
            while (p + frame <= hardEnd) {
                var e = 0f
                for (k in p until p + frame) e += abs(x[k])
                if (e < bestEnergy) { bestEnergy = e; bestPos = p + frame / 2 }
                p += frame
            }
            out.add(start..bestPos)
            start = bestPos
        }
        return out
    }

    fun release() {
        recognizer?.release()
        recognizer = null
    }
}
