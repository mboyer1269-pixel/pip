package ca.michael.pip.core.brain

import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Platform HTTPS transport (no third-party HTTP client for the brain). */
class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
) : HttpTransport {
    @Volatile private var current: HttpsURLConnection? = null

    override fun postJson(url: String, headers: Map<String, String>, body: String): HttpResponse {
        val connection = URL(url).openConnection() as? HttpsURLConnection
            ?: throw IOException("https only")
        current = connection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return HttpResponse(code, text)
        } finally {
            connection.disconnect()
            current = null
        }
    }

    override fun cancel() {
        current?.disconnect()
    }
}
