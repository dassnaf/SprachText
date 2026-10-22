package de.kdvstudio.sprachtext

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Das Pop-up über WhatsApp: Stichpunkte zuerst, Volltext zum Aufklappen. */
class ShareActivity : Activity(), Transcriber.Callback {

    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var bulletsView: TextView
    private lateinit var toggle: Button
    private lateinit var fullScroll: View
    private lateinit var fullView: TextView
    private lateinit var actions: LinearLayout

    private var uri: Uri? = null
    private var transcriber: Transcriber? = null
    private var fullText = ""
    private var expanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (uri == null) { onError("Keine Sprachnachricht empfangen."); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else begin()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) begin()
        else onError("Androids Spracherkennung braucht die Mikrofon-Berechtigung. Die App nimmt nichts auf.")
    }

    private fun begin() {
        status.text = "Sprachnachricht wird gelesen …"
        val u = uri ?: return
        Thread {
            try {
                val pcm = AudioDecoder.decode(this, u)
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    status.text = "Wird transkribiert …"
                    transcriber = Transcriber(this, this).also { it.start(pcm) }
                }
            } catch (e: Exception) {
                runOnUiThread { onError("Audio konnte nicht gelesen werden: ${e.message}") }
            }
        }.start()
    }

    override fun onProgress(done: Int, total: Int) {
        status.text = if (total > 1) "Wird transkribiert … Teil $done von $total" else "Wird transkribiert …"
    }

    override fun onDone(segments: List<String>) {
        fullText = segments.joinToString(" ")
        fullView.text = fullText
        toggle.visibility = View.VISIBLE
        actions.visibility = View.VISIBLE

        val key = Settings.geminiKey(this)
        if (key.isEmpty()) {
            showSummary(Summarizer.bullets(segments), "Zusammenfassung (lokal – kein Gemini-Key hinterlegt)")
            return
        }
        status.text = "Wird zusammengefasst …"
        Thread {
            val result = try {
                Gemini.summarize(key, fullText) to "Zusammenfassung"
            } catch (e: Exception) {
                Summarizer.bullets(segments) to "Zusammenfassung (lokal – ${e.message ?: "Gemini nicht erreichbar"})"
            }
            runOnUiThread {
                if (!isFinishing) showSummary(result.first, result.second)
            }
        }.start()
    }

    private fun showSummary(bullets: List<String>, title: String) {
        progress.visibility = View.GONE
        status.text = title
        status.setTypeface(null, Typeface.BOLD)
        bulletsView.text = bullets.joinToString("\n") { "•  $it" }
        bulletsView.visibility = View.VISIBLE
    }

    override fun onError(msg: String) {
        progress.visibility = View.GONE
        status.text = msg
        actions.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        transcriber?.destroy()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val dark = isDark()
        val fg = if (dark) 0xFFF2F2F2.toInt() else 0xFF161616.toInt()
        val muted = if (dark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt()
        val bg = if (dark) 0xFF1F1F1F.toInt() else 0xFFFFFFFF.toInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { finish() }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            isClickable = true
            val r = dp(24).toFloat()
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        root.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))

        card.addView(TextView(this).apply {
            text = "🎙️  Sprachnachricht"
            textSize = 18f
            setTextColor(fg)
            setTypeface(null, Typeface.BOLD)
        })

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }
        card.addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })

        status = TextView(this).apply { textSize = 14f; setTextColor(muted) }
        card.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })

        bulletsView = TextView(this).apply {
            textSize = 16f
            setTextColor(fg)
            setLineSpacing(dp(4).toFloat(), 1f)
            visibility = View.GONE
            setTextIsSelectable(true)
        }
        card.addView(bulletsView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        toggle = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Ganzen Text anzeigen ▾"
            isAllCaps = false
            visibility = View.GONE
            setOnClickListener {
                expanded = !expanded
                fullScroll.visibility = if (expanded) View.VISIBLE else View.GONE
                text = if (expanded) "Ganzen Text ausblenden ▴" else "Ganzen Text anzeigen ▾"
            }
        }
        card.addView(toggle, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4) })

        fullView = TextView(this).apply {
            textSize = 15f
            setTextColor(fg)
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextIsSelectable(true)
        }
        fullScroll = MaxHeightScrollView(this, (resources.displayMetrics.heightPixels * 0.45).toInt()).apply {
            addView(fullView)
            visibility = View.GONE
        }
        card.addView(fullScroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            visibility = View.GONE
        }
        actions.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Text kopieren"
            isAllCaps = false
            setOnClickListener {
                if (fullText.isNotEmpty()) {
                    getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Sprachnachricht", fullText))
                }
            }
        })
        actions.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Schließen"
            isAllCaps = false
            setOnClickListener { finish() }
        })
        card.addView(actions, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        return root
    }
}
