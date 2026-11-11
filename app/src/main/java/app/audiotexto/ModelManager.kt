package app.audiotexto

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Baixa (uma única vez) e guarda os modelos Whisper. Depois disso, tudo roda 100% offline. */
class ModelManager(context: Context) {

    data class Files(val encoder: File, val decoder: File, val tokens: File)

    private val root = File(context.filesDir, "models")

    companion object {
        // Nome do modelo -> tamanho aproximado (MB) em int8
        val MODELS = linkedMapOf("tiny" to 110, "base" to 200, "small" to 360)
        private const val BASE = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-%s/resolve/main/%s"
    }

    private fun names(m: String) = listOf("$m-encoder.int8.onnx", "$m-decoder.int8.onnx", "$m-tokens.txt")

    fun files(m: String): Files {
        val d = File(root, m)
        return Files(File(d, "$m-encoder.int8.onnx"), File(d, "$m-decoder.int8.onnx"), File(d, "$m-tokens.txt"))
    }

    fun isReady(m: String): Boolean {
        val f = files(m)
        return f.encoder.exists() && f.decoder.exists() && f.tokens.exists()
    }

    /** Baixa os arquivos que faltam. [onProgress] recebe (arquivo atual, bytes baixados, total de bytes ou -1). */
    fun download(m: String, onProgress: (String, Long, Long) -> Unit) {
        val dir = File(root, m).apply { mkdirs() }
        for (name in names(m)) {
            val target = File(dir, name)
            if (target.exists()) continue
            val tmp = File(dir, "$name.part")
            val conn = URL(BASE.format(m, name)).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode !in 200..299) error("Falha ao baixar $name (HTTP ${conn.responseCode})")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReport = 0L
                        while (true) {
                            val r = input.read(buf)
                            if (r < 0) break
                            output.write(buf, 0, r)
                            done += r
                            if (done - lastReport > 512 * 1024) { onProgress(name, done, total); lastReport = done }
                        }
                        onProgress(name, done, total)
                    }
                }
                if (!tmp.renameTo(target)) error("Não consegui salvar $name")
            } finally {
                conn.disconnect()
                if (tmp.exists() && !target.exists()) tmp.delete()
            }
        }
    }
}
