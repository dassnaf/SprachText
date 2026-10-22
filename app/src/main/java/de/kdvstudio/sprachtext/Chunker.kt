package de.kdvstudio.sprachtext

import kotlin.math.sqrt

/** Zerlegt das Audio an Sprechpausen in kurze Abschnitte, damit die Erkennung nicht vorzeitig abbricht. */
object Chunker {
    private const val FRAME = AudioDecoder.TARGET_RATE / 50   // 20 ms
    private const val MIN_SILENCE = 20                          // 400 ms Pause = Schnittstelle
    private const val MIN_LEN = 50                              // Abschnitt mind. 1 s
    private const val MAX_LEN = 750                             // Abschnitt max. 15 s
    private const val PAD_BEFORE = AudioDecoder.TARGET_RATE * 3 / 10
    private const val PAD_AFTER = AudioDecoder.TARGET_RATE * 6 / 10

    fun split(pcm: ShortArray): List<ShortArray> {
        val n = pcm.size / FRAME
        if (n < 5) return emptyList()
        val rms = DoubleArray(n) { f ->
            var s = 0.0
            for (i in f * FRAME until (f + 1) * FRAME) { val v = pcm[i].toDouble(); s += v * v }
            sqrt(s / FRAME)
        }
        val sorted = rms.sorted()
        val floor = sorted[(n * 0.15).toInt()]
        val peak = sorted[minOf((n * 0.95).toInt(), n - 1)]
        val thr = maxOf(floor * 2.5, floor + (peak - floor) * 0.08, 150.0)

        val cuts = ArrayList<Int>()
        var start = 0
        var silRun = 0
        for (f in 0 until n) {
            if (rms[f] < thr) silRun++ else silRun = 0
            val len = f - start
            if (silRun >= MIN_SILENCE && len >= MIN_LEN) {
                val cut = f - silRun / 2
                cuts.add(cut); start = cut; silRun = 0
            } else if (len >= MAX_LEN) {
                var best = f
                for (g in maxOf(start + 1, f - 150)..f) if (rms[g] < rms[best]) best = g
                cuts.add(best); start = best; silRun = 0
            }
        }

        val bounds = ArrayList<Int>().apply { add(0); addAll(cuts); add(n) }
        val out = ArrayList<ShortArray>()
        for (i in 0 until bounds.size - 1) {
            val fa = bounds[i]; val fb = bounds[i + 1]
            var speech = 0
            for (f in fa until fb) if (rms[f] >= thr) speech++
            if (speech < 5) continue
            val from = fa * FRAME
            val to = if (i + 1 == bounds.size - 1) pcm.size else fb * FRAME
            val chunk = ShortArray(PAD_BEFORE + (to - from) + PAD_AFTER)
            pcm.copyInto(chunk, PAD_BEFORE, from, to)
            out.add(chunk)
        }
        return out
    }
}
