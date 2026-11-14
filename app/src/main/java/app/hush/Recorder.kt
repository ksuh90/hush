package app.hush

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.sqrt

/** Records 16 kHz mono audio. [MicService] starts it after the service is in the foreground. */
object Recorder {
    interface Listener {
        fun onLevel(level: Float)
        fun onRecordFailed(session: Int, notice: Notice)
        fun onLimit(session: Int)
    }

    const val SAMPLE_RATE = 16_000
    private const val MAX_SECONDS = 180
    private const val TAG = "Hush"

    @Volatile var listener: Listener? = null
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var wanted = 0
    private var thread: Thread? = null
    @Volatile private var running = false
    private var buffer = FloatArray(0)
    private var size = 0

    /** Call before the service starts, so that a late start for a discarded session does nothing. */
    fun expect(session: Int) = synchronized(lock) {
        wanted = session
        size = 0
    }

    @SuppressLint("MissingPermission")
    fun start(session: Int) = synchronized(lock) {
        if (session != wanted || running) return
        running = true
        thread = Thread({ loop(session) }, "hush-recorder").apply { start() }
    }

    fun fail(session: Int, notice: Notice) {
        main.post { listener?.onRecordFailed(session, notice) }
    }

    /** Stops the recording and returns the samples. */
    fun stop(): FloatArray {
        val t = synchronized(lock) {
            wanted = 0
            running = false
            thread.also { thread = null }
        }
        t?.join(1_000)
        return synchronized(lock) { buffer.copyOf(size) }
    }

    @SuppressLint("MissingPermission")
    private fun loop(session: Int) {
        val chunk = ShortArray(SAMPLE_RATE / 10)
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBytes, chunk.size * 4),
            )
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord failed", e)
            null
        }
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            running = false
            fail(session, Notice.MIC_FAILED)
            return
        }
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                running = false
                fail(session, Notice.MIC_FAILED)
                return
            }
            synchronized(lock) { buffer = FloatArray(SAMPLE_RATE * 10) }
            while (running) {
                val n = record.read(chunk, 0, chunk.size)
                if (n < 0) {
                    Log.w(TAG, "AudioRecord.read returned $n")
                    running = false
                    fail(session, Notice.MIC_FAILED)
                    break
                }
                val full = append(chunk, n)
                val level = rms(chunk, n)
                main.post { listener?.onLevel(level) }
                if (full) {
                    running = false
                    main.post { listener?.onLimit(session) }
                }
            }
        } finally {
            record.stop()
            record.release()
        }
    }

    private fun append(chunk: ShortArray, n: Int): Boolean = synchronized(lock) {
        val limit = SAMPLE_RATE * MAX_SECONDS
        if (size + n > buffer.size) buffer = buffer.copyOf(minOf(limit, maxOf(buffer.size * 2, size + n)))
        val count = minOf(n, buffer.size - size)
        for (i in 0 until count) buffer[size + i] = chunk[i] / 32768f
        size += count
        size >= limit
    }

    private fun rms(chunk: ShortArray, n: Int): Float {
        if (n == 0) return 0f
        var sum = 0.0
        for (i in 0 until n) {
            val v = chunk[i] / 32768.0
            sum += v * v
        }
        return sqrt(sum / n).toFloat()
    }
}
