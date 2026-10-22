package de.kdvstudio.sprachtext

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Zusammenfassung über die kostenlose Gemini API. Es wird nur der Text gesendet, kein Audio. */
object Gemini {
    private val MODELS = listOf("gemini-3.1-flash-lite", "gemini-flash-lite-latest", "gemini-flash-latest")

    private const val PROMPT =
        "Du bekommst die automatische Transkription einer WhatsApp-Sprachnachricht. Sie kann Erkennungsfehler " +
        "und fehlende Satzzeichen enthalten. Fasse den Inhalt auf Deutsch in 1 bis 5 sehr kurzen Stichpunkten " +
        "zusammen (höchstens 12 Wörter pro Punkt). Nenne konkrete Infos wie Termine, Uhrzeiten, Orte, Bitten " +
        "und Fragen an mich. Lass Begrüßung, Füllwörter und Smalltalk weg. Antworte nur mit den Stichpunkten, " +
        "jeder in einer eigenen Zeile mit \"- \" am Anfang.\n\nTranskription:\n"

    private class ModelNotFound : Exception()

    fun summarize(key: String, transcript: String): List<String> {
        for (m in MODELS) {
            try { return call(key, m, transcript) } catch (e: ModelNotFound) { /* nächstes Modell */ }
        }
        throw IOException("Kein passendes Gemini-Modell gefunden.")
    }

    private fun call(key: String, model: String, transcript: String): List<String> {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 10_000
        c.readTimeout = 40_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", key)

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject()
                .put("parts", JSONArray().put(JSONObject().put("text", PROMPT + transcript)))))
            .put("generationConfig", JSONObject().put("temperature", 0.2))
        c.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()

        if (code == 404) throw ModelNotFound()
        if (code !in 200..299) {
            throw IOException(
                when {
                    code == 429 -> "Gemini-Limit kurz erreicht, bitte gleich nochmal versuchen."
                    txt.contains("API_KEY_INVALID") || txt.contains("API key not valid") -> "Gemini-API-Key ist ungültig."
                    code == 403 -> "Gemini-Zugriff verweigert (Key prüfen)."
                    else -> "Gemini-Fehler $code."
                }
            )
        }

        val parts = JSONObject(txt).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
        }
        val lines = sb.toString().lines()
            .map { it.replace("**", "").trim().trimStart('-', '•', '*', ' ').trim() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw IOException("Gemini hat keine Zusammenfassung geliefert.")
        return lines
    }
}
