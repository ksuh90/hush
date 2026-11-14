package app.hush

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Model files on the phone. A file is in place only after its SHA-256 matches the catalog. */
class ModelStore(private val root: File) {
    fun file(spec: ModelSpec, file: ModelFile) = File(File(root, spec.id), file.name)

    fun vadFile() = File(root, Catalog.vad.name)

    /** All files that [spec] needs, with their place on disk. */
    fun parts(spec: ModelSpec): List<Pair<ModelFile, File>> =
        spec.files.map { it to file(spec, it) } + (Catalog.vad to vadFile())

    fun isReady(spec: ModelSpec) = parts(spec).all { (model, file) -> file.length() == model.bytes }

    fun missingBytes(spec: ModelSpec) = parts(spec).filter { (model, file) -> file.length() != model.bytes }.sumOf { it.first.bytes }

    fun delete(spec: ModelSpec) {
        File(root, spec.id).deleteRecursively()
    }

    companion object {
        fun totalBytes(spec: ModelSpec) = spec.bytes + Catalog.vad.bytes

        fun of(context: Context) = ModelStore(File(context.noBackupFilesDir, "models"))
    }
}

fun interface HttpSource {
    class Response(val code: Int, val body: InputStream)

    /** Opens [url] from byte [offset]. */
    fun open(url: String, offset: Long): Response
}

object UrlSource : HttpSource {
    override fun open(url: String, offset: Long): HttpSource.Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
        val code = connection.responseCode
        val body = if (code in 200..299) connection.inputStream else connection.errorStream ?: InputStream.nullInputStream()
        return HttpSource.Response(code, body)
    }
}

/** Downloads one file. It continues a partial download and checks the SHA-256. */
class Downloader(private val source: HttpSource = UrlSource) {
    fun fetch(model: ModelFile, dest: File, progress: (Long) -> Unit, cancelled: () -> Boolean) {
        if (dest.length() == model.bytes) return
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        if (part.length() >= model.bytes) part.delete()
        var offset = part.length()
        var response = source.open(model.url, offset)
        if (offset > 0 && response.code == 416) {
            response.body.close()
            part.delete()
            offset = 0
            response = source.open(model.url, 0)
        }
        when (response.code) {
            206 -> Unit
            200 -> offset = 0
            else -> {
                response.body.close()
                throw IOException("HTTP ${response.code} for ${model.name}")
            }
        }
        response.body.use { input ->
            java.io.FileOutputStream(part, offset > 0).use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = offset
                progress(done)
                while (true) {
                    if (cancelled()) throw CancellationException()
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    progress(done)
                }
            }
        }
        if (part.length() != model.bytes) {
            throw IOException("${model.name}: got ${part.length()} bytes, expected ${model.bytes}")
        }
        if (sha256(part) != model.sha256) {
            part.delete()
            throw IOException("${model.name}: checksum does not match")
        }
        if (!part.renameTo(dest)) throw IOException("cannot move ${part.name}")
    }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
