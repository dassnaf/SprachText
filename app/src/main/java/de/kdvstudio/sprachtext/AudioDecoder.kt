package de.kdvstudio.sprachtext

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Dekodiert eine WhatsApp-Sprachnachricht (Opus/OGG, AAC …) zu 16 kHz Mono PCM. */
object AudioDecoder {
    const val TARGET_RATE = 16000

    fun decode(ctx: Context, uri: Uri): ShortArray {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var track = -1
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) { track = i; break }
        }
        require(track >= 0) { "Keine Audiospur gefunden" }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()

        val chunks = ArrayList<ShortArray>()
        var total = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o >= 0) {
                    if (info.size > 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val arr = ShortArray(sb.remaining())
                        sb.get(arr)
                        chunks.add(arr)
                        total += arr.size
                    }
                    codec.releaseOutputBuffer(o, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        } finally {
            codec.stop(); codec.release(); ex.release()
        }

        val pcm = ShortArray(total)
        var pos = 0
        for (c in chunks) { c.copyInto(pcm, pos); pos += c.size }
        return resample(toMono(pcm, channels), rate, TARGET_RATE)
    }

    private fun toMono(s: ShortArray, ch: Int): ShortArray {
        if (ch <= 1) return s
        val n = s.size / ch
        return ShortArray(n) { i ->
            var sum = 0
            for (c in 0 until ch) sum += s[i * ch + c]
            (sum / ch).toShort()
        }
    }

    private fun resample(s: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || s.isEmpty()) return s
        val n = (s.size.toLong() * to / from).toInt()
        val r = from.toDouble() / to
        return ShortArray(n) { i ->
            val a = (i * r).toInt().coerceAtMost(s.size - 1)
            val b = maxOf(a + 1, minOf(((i + 1) * r).toInt(), s.size))
            var sum = 0L
            for (k in a until b) sum += s[k]
            (sum / (b - a)).toShort()
        }
    }
}
