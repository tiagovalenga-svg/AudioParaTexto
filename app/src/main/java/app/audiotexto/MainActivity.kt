package app.audiotexto

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.AdapterView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val worker = Executors.newSingleThreadExecutor()
    private val transcriber = Transcriber()
    private lateinit var models: ModelManager

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var result: TextView
    private lateinit var btnPick: Button
    private lateinit var btnCopy: Button
    private lateinit var btnShare: Button
    private lateinit var modelSpinner: Spinner
    private lateinit var langSpinner: Spinner

    private val modelNames = ModelManager.MODELS.keys.toList()
    private val langs = listOf("Português" to "pt", "English" to "en", "Español" to "es", "Detectar automaticamente" to "")
    private var busy = false

    private val prefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        models = ModelManager(this)
        buildUi()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val title = TextView(this).apply { text = "Áudio para Texto"; textSize = 24f }
        val sub = TextView(this).apply {
            text = "No WhatsApp: segure o áudio → Compartilhar → Áudio para Texto. Tudo roda no celular, sem internet (depois de baixar o modelo uma vez)."
            setPadding(0, dp(4), 0, dp(12))
        }

        modelSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                modelNames.map { "Modelo $it (~${ModelManager.MODELS[it]} MB)" + if (it == "small") " – melhor qualidade" else if (it == "tiny") " – mais rápido" else "" },
            )
            setSelection(modelNames.indexOf(prefs.getString("model", "small")).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    prefs.edit().putString("model", modelNames[pos]).apply()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        langSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, langs.map { it.first })
            setSelection(prefs.getInt("lang", 0).coerceIn(0, langs.size - 1))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    prefs.edit().putInt("lang", pos).apply()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        btnPick = Button(this).apply { text = "Escolher áudio…"; setOnClickListener { pickFile() } }
        status = TextView(this).apply { text = "Pronto."; setPadding(0, dp(8), 0, dp(4)) }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE
        }
        result = TextView(this).apply {
            textSize = 17f
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, dp(12))
        }
        btnCopy = Button(this).apply { text = "Copiar"; isEnabled = false; setOnClickListener { copy() } }
        btnShare = Button(this).apply { text = "Compartilhar texto"; isEnabled = false; setOnClickListener { share() } }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btnCopy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnShare, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        listOf(title, sub, modelSpinner, langSpinner, btnPick, status, progress, result, row).forEach { col.addView(it) }
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "application/ogg"))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(i, 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) data?.data?.let { start(it) }
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        val uri: Uri? = when (i.action) {
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> i.data
            else -> null
        }
        if (uri != null) start(uri)
    }

    private fun setBusy(b: Boolean) {
        busy = b
        btnPick.isEnabled = !b
        modelSpinner.isEnabled = !b
        langSpinner.isEnabled = !b
    }

    private fun ui(block: () -> Unit) = runOnUiThread(block)

    private fun start(uri: Uri) {
        if (busy) { Toast.makeText(this, "Aguarde terminar o áudio atual.", Toast.LENGTH_SHORT).show(); return }
        setBusy(true)
        result.text = ""
        btnCopy.isEnabled = false
        btnShare.isEnabled = false
        val model = modelNames[modelSpinner.selectedItemPosition]
        val lang = langs[langSpinner.selectedItemPosition].second

        worker.execute {
            try {
                if (!models.isReady(model)) {
                    ui { progress.visibility = View.VISIBLE; progress.isIndeterminate = false; status.text = "Baixando modelo $model (só na primeira vez)…" }
                    models.download(model) { name, done, total ->
                        ui {
                            status.text = "Baixando $name… ${done / 1_000_000} MB" + if (total > 0) " / ${total / 1_000_000} MB" else ""
                            if (total > 0) progress.progress = (done * 100 / total).toInt()
                        }
                    }
                }
                ui { progress.visibility = View.VISIBLE; progress.isIndeterminate = true; status.text = "Lendo o áudio…" }
                val samples = AudioDecoder.decode(this, uri)
                require(samples.size > 1600) { "Áudio vazio ou curto demais." }
                val secs = samples.size / AudioDecoder.TARGET_RATE
                ui { status.text = "Transcrevendo ${secs}s de áudio…"; progress.isIndeterminate = false; progress.progress = 0 }

                val text = transcriber.transcribe(model, models.files(model), lang, samples) { i, n ->
                    ui { progress.progress = i * 100 / n; status.text = "Transcrevendo… trecho ${minOf(i + 1, n)}/$n" }
                }
                ui {
                    result.text = if (text.isBlank()) "(Não encontrei fala no áudio.)" else text
                    status.text = "Pronto."
                    btnCopy.isEnabled = text.isNotBlank()
                    btnShare.isEnabled = text.isNotBlank()
                }
            } catch (e: Throwable) {
                ui { status.text = "Erro: ${e.message ?: e.javaClass.simpleName}" }
            } finally {
                ui { progress.visibility = View.GONE; setBusy(false) }
            }
        }
    }

    private fun copy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("transcrição", result.text))
        Toast.makeText(this, "Texto copiado.", Toast.LENGTH_SHORT).show()
    }

    private fun share() {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, result.text.toString()) }
        startActivity(Intent.createChooser(i, "Compartilhar texto"))
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
        if (isFinishing) transcriber.release()
    }
}
