package app.hush

object Chunks {
    /** Joins speech segments in order into chunks of at most [maxSamples], with [gapSamples] of silence between them. */
    fun pack(segments: List<FloatArray>, maxSamples: Int, gapSamples: Int): List<FloatArray> {
        val chunks = mutableListOf<FloatArray>()
        val current = mutableListOf<FloatArray>()
        var length = 0
        fun flush() {
            if (current.isEmpty()) return
            val chunk = FloatArray(length)
            var at = 0
            current.forEachIndexed { i, segment ->
                if (i > 0) at += gapSamples
                segment.copyInto(chunk, at)
                at += segment.size
            }
            chunks += chunk
            current.clear()
            length = 0
        }
        for (segment in segments) {
            if (segment.isEmpty()) continue
            val added = if (current.isEmpty()) segment.size else length + gapSamples + segment.size
            if (current.isNotEmpty() && added > maxSamples) flush()
            length = if (current.isEmpty()) segment.size else length + gapSamples + segment.size
            current += segment
        }
        flush()
        return chunks
    }
}
