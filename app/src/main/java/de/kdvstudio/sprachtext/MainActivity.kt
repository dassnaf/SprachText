package de.kdvstudio.sprachtext

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Einrichtungs-Bildschirm: Berechtigung, Sprachpaket, Gemini-Key, Testdatei. */
class MainActivity : Activity() {

    private lateinit var info: TextView
    private lateinit var keyField: EditText
    private var dl: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }
        col.addView(TextView(this).apply {
            text = "SprachText"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(TextView(this).apply {
            textSize = 15f
            setLineSpacing(dp(3).toFloat(), 1f)
            text = "So geht’s: In WhatsApp eine Sprachnachricht lange drücken → Teilen → „SprachText“. " +
                "Die Sprache wird auf deinem Handy in Text umgewandelt. Nur für die Zusammenfassung " +
                "wird der Text (nicht das Audio) an Gemini geschickt.\n\nEinmalig einrichten:"
        }, lp(16))

        col.addView(button("1. Berechtigung erteilen") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }, lp(12))
        col.addView(button("2. Deutsches Offline-Sprachpaket laden") {
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                info.text = "Dieses Gerät bietet keine Offline-Spracherkennung an."
            } else {
                dl?.destroy()
                dl = SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also {
                    it.triggerModelDownload(Transcriber.languageIntent())
                }
                Toast.makeText(this, "Download angestoßen", Toast.LENGTH_SHORT).show()
            }
        }, lp(8))

        col.addView(TextView(this).apply {
            text = "3. Gemini-API-Key (kostenlos)"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }, lp(20))
        col.addView(button("Kostenlosen Key bei Google holen") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        }, lp(8))
        keyField = EditText(this).apply {
            hint = "Key hier einfügen"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            isSingleLine = true
            setText(Settings.geminiKey(this@MainActivity))
        }
        col.addView(keyField, lp(8))
        col.addView(button("Key speichern") {
            Settings.setGeminiKey(this, keyField.text.toString())
            Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show()
            refresh()
        }, lp(4))

        col.addView(button("Test: Audiodatei auswählen") {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"), 2
            )
        }, lp(20))

        info = TextView(this).apply { textSize = 14f }
        col.addView(info, lp(16))
        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        if (!::info.isInitialized) return
        val perm = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val offline = SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        val key = Settings.geminiKey(this).isNotEmpty()
        info.text = "Berechtigung: ${if (perm) "✅" else "❌"}\n" +
            "Offline-Erkennung verfügbar: ${if (offline) "✅" else "❌"}\n" +
            "Gemini-Key hinterlegt: ${if (key) "✅" else "❌"}"
    }

    @Deprecated("Activity-API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val u = data?.data ?: return
        if (requestCode == 2 && resultCode == RESULT_OK) {
            startActivity(
                Intent(this, ShareActivity::class.java)
                    .putExtra(Intent.EXTRA_STREAM, u)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    override fun onDestroy() { dl?.destroy(); super.onDestroy() }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }
}
