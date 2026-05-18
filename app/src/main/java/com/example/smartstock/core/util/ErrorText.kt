package com.example.smartstock.core.util

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns a raw exception into something safe and human-readable.
 *
 * supabase-kt / ktor failures expose a `message` that prepends the JSON
 * response body and then dumps the request URL, every header (including
 * the Bearer JWT) and the HTTP method. Surfacing that verbatim (as the
 * old `it.message ?: "..."` did) leaked credentials on screen and was
 * unreadable. This pulls out only the server's error text, maps the
 * common offline cases to a friendly line, and never echoes headers.
 */
object ErrorText {

    private const val OFFLINE =
        "You appear to be offline. Check your internet connection and try again."

    fun friendly(t: Throwable?, fallback: String): String {
        if (t == null) return fallback

        if (isOffline(t)) return OFFLINE

        val raw = t.message?.trim().orEmpty()
        if (raw.isEmpty()) return fallback

        // Edge functions / GoTrue return {"error":"..."} or {"msg":"..."}.
        extractJsonField(raw)?.let { return it }

        // ktor: "HTTP request to <url> failed with message: <reason>"
        if (raw.contains("failed with message:", ignoreCase = true)) {
            val tail = raw.substringAfter("failed with message:")
                .trim()
                .lineSequence()
                .firstOrNull()
                .orEmpty()
            return if (tail.isBlank()) OFFLINE else tail.take(160)
        }

        // Last resort: first line only, and never past URL:/Headers: so a
        // JWT or header dump can't reach the UI.
        val safe = raw.lineSequence().first()
            .substringBefore("URL:")
            .substringBefore("Headers:")
            .trim()
        return safe.ifBlank { fallback }.take(160)
    }

    private fun isOffline(t: Throwable): Boolean {
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth++ < 8) {
            if (cur is UnknownHostException ||
                cur is ConnectException ||
                cur is SocketTimeoutException
            ) {
                return true
            }
            val m = cur.message?.lowercase().orEmpty()
            if (m.contains("unable to resolve host") ||
                m.contains("failed to connect") ||
                m.contains("connection refused") ||
                m.contains("network is unreachable") ||
                m.contains("timeout")
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }

    private fun extractJsonField(s: String): String? {
        val start = s.indexOf('{')
        if (start < 0) return null
        val end = s.indexOf('}', start + 1)
        if (end < 0) return null
        val obj = s.substring(start, end + 1)
        for (key in listOf("error", "message", "msg", "error_description")) {
            val marker = "\"$key\""
            val ki = obj.indexOf(marker)
            if (ki < 0) continue
            val colon = obj.indexOf(':', ki + marker.length)
            if (colon < 0) continue
            val q1 = obj.indexOf('"', colon + 1)
            val q2 = if (q1 >= 0) obj.indexOf('"', q1 + 1) else -1
            if (q1 in 0 until q2) {
                val value = obj.substring(q1 + 1, q2).trim()
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }
}
