package de.kdvstudio.sprachtext

import kotlin.math.sqrt

/** Lokale, extraktive Zusammenfassung: wählt die aussagekräftigsten Sätze als Stichpunkte. */
object Summarizer {
    private const val MAX_BULLETS = 4
    private const val MAX_WORDS = 18

    private val STOP = setOf(
        "der", "die", "das", "und", "oder", "aber", "ich", "du", "er", "sie", "es", "wir", "ihr",
        "mich", "dich", "mir", "dir", "uns", "euch", "ein", "eine", "einen", "einem", "einer", "eines",
        "ist", "bin", "bist", "sind", "seid", "war", "waren", "hab", "habe", "hast", "hat", "haben",
        "hatte", "wird", "werde", "wirst", "werden", "nicht", "noch", "schon", "auch", "nur", "mal",
        "dann", "denn", "doch", "halt", "eben", "nee", "nein", "also", "wie", "was", "wer",
        "wenn", "weil", "dass", "zum", "zur", "mit", "von", "vom", "für", "auf", "aus", "bei",
        "nach", "über", "unter", "den", "dem", "des", "sich", "man", "kann", "kannst", "können",
        "muss", "musst", "müssen", "soll", "will", "willst", "wollte", "würde", "gerade", "eigentlich",
        "einfach", "irgendwie", "genau", "okay", "ähm", "naja", "hey", "hallo", "jetzt", "hier",
        "dort", "sehr", "ganz", "alles", "etwas", "mein", "meine", "dein", "deine", "keine", "kein"
    )

    fun bullets(segments: List<String>): List<String> {
        val units = segments
            .flatMap { it.split(Regex("(?<=[.!?])\\s+")) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { chunk(it) }
        if (units.size <= MAX_BULLETS) return units.map { tidy(it) }

        val freq = HashMap<String, Int>()
        units.forEach { u -> keywords(u).forEach { freq[it] = (freq[it] ?: 0) + 1 } }

        val picked = units.indices
            .map { i ->
                val w = keywords(units[i])
                val score = if (w.isEmpty()) 0.0 else w.sumOf { (freq[it] ?: 0).toDouble() } / sqrt(w.size.toDouble())
                i to score
            }
            .sortedByDescending { it.second }
            .take(MAX_BULLETS)
            .map { it.first }
            .sorted()
        return picked.map { tidy(units[it]) }
    }

    private fun keywords(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 && it !in STOP }

    /** Offline-Erkennung liefert oft keine Satzzeichen – lange Blöcke in Häppchen teilen. */
    private fun chunk(s: String): List<String> {
        val w = s.split(Regex("\\s+"))
        return if (w.size <= 30) listOf(s) else w.chunked(15).map { it.joinToString(" ") }
    }

    private fun tidy(s: String): String {
        val w = s.split(Regex("\\s+"))
        val t = if (w.size > MAX_WORDS) w.take(MAX_WORDS).joinToString(" ") + " …" else s
        return t.replaceFirstChar { it.uppercase() }
    }
}
