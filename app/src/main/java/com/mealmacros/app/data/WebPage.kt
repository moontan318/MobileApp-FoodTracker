package com.mealmacros.app.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/** Downloads web pages (recipe import). */
object WebPage {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    fun download(url: String, userAgent: String = USER_AGENT, accept: String = "text/html,application/xhtml+xml"): String {
        var current = URL(url)
        repeat(5) { // follow redirects, including http -> https
            val conn = current.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("Accept-Language", "en-GB,en;q=0.9")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: error("redirect without a location")
                    current = URL(current, location)
                    return@repeat
                }
                if (code !in 200..299) error("the site answered HTTP $code")
                val bytes = conn.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0 || out.size() > MAX_BYTES) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                val charsetName = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                    .find(conn.contentType ?: "")?.groupValues?.get(1)
                val encoding = try {
                    charsetName?.let { Charset.forName(it) } ?: Charsets.UTF_8
                } catch (e: Exception) {
                    Charsets.UTF_8
                }
                return String(bytes, encoding)
            } finally {
                conn.disconnect()
            }
        }
        error("too many redirects")
    }
}
