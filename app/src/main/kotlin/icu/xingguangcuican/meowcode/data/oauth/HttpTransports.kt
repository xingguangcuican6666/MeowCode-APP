package icu.xingguangcuican.meowcode.data.oauth

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Production transport shapes. It is deliberately unused by the stub build — wiring
 * it requires the Android browser round-trip (Custom Tabs + an app-link redirect that
 * the MeowAPI administrator has registered) and is tracked in HANDOFF.md.
 */
object HttpTransports {
    /** POSTs a form body; returns the body on 2xx, throws [IOException] otherwise. */
    fun postForm(url: String, form: Map<String, String>): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Accept", "application/json")
            val body = form.entries.joinToString("&") { (k, v) ->
                "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}: $text")
            return text
        } finally {
            connection.disconnect()
        }
    }

    fun postJson(url: String, accessToken: String, body: String): String =
        send("POST", url, accessToken, body)

    fun deleteBearer(url: String, accessToken: String): String = send("DELETE", url, accessToken, null)

    private fun send(method: String, url: String, accessToken: String, jsonBody: String?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            if (jsonBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}: $text")
            return text
        } finally {
            connection.disconnect()
        }
    }

    fun getBearer(url: String, accessToken: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}: $text")
            return text
        } finally {
            connection.disconnect()
        }
    }
}
