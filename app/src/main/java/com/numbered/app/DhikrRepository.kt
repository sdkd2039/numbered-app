package com.numbered.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal data class DhikrEntry(val group: String, val text: String, val count: String, val id: String)

internal object DhikrRepository {
    private const val CSV_URL = "https://raw.githubusercontent.com/sdkd2039/numbered-app/refs/heads/main/data/%D9%85%D8%B9%D8%AF%D9%88%D8%AF%D8%A7%D8%AA%20-%20%D8%A7%D9%84%D8%A3%D8%B0%D9%83%D8%A7%D8%B1.csv"
    private const val PREFS = "dhikr_data_cache"
    private const val KEY_ROWS = "rows"

    fun loadCached(context: Context): List<DhikrEntry> {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROWS, null) ?: return emptyList()
        return encoded.split('\n').mapNotNull { line ->
            val fields = line.split('|', limit = 4)
            if (fields.size == 4) DhikrEntry(decode(fields[0]), decode(fields[1]), decode(fields[2]), decode(fields[3])) else null
        }
    }

    fun refresh(context: Context, callback: (List<DhikrEntry>) -> Unit) {
        Thread {
            val fresh = try {
                val connection = URL(CSV_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                connection.requestMethod = "GET"
                connection.inputStream.use { input ->
                    parseCsv(BufferedReader(InputStreamReader(input, Charsets.UTF_8)))
                }
            } catch (_: Exception) {
                emptyList()
            }
            if (fresh.isNotEmpty()) {
                val serialized = fresh.joinToString("\n") {
                    listOf(it.group, it.text, it.count, it.id).joinToString("|") { value -> encode(value) }
                }
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_ROWS, serialized).apply()
            }
            Handler(Looper.getMainLooper()).post { callback(if (fresh.isNotEmpty()) fresh else loadCached(context)) }
        }.start()
    }

    private fun parseCsv(reader: BufferedReader): List<DhikrEntry> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var escapedQuote = false
        fun finishField() { row += field.toString(); field.setLength(0) }
        fun finishRow() { if (row.isNotEmpty() || field.isNotEmpty()) { finishField(); rows += row.toList(); row.clear() } }
        reader.forEachLine { line ->
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '"') {
                    if (quoted && i + 1 < line.length && line[i + 1] == '"') { field.append('"'); i++ }
                    else { quoted = !quoted }
                } else if (c == ',' && !quoted) finishField()
                else field.append(c)
                i++
            }
            if (!quoted) finishRow() else field.append('\n')
        }
        if (quoted || field.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows.mapNotNull { columns ->
            if (columns.size < 2) return@mapNotNull null
            val group = columns[0].trim()
            val text = columns[1].trim()
            if (group.isEmpty() || text.isEmpty()) return@mapNotNull null
            DhikrEntry(group, text, columns.getOrNull(2)?.trim().orEmpty(), columns.getOrNull(3)?.trim().orEmpty())
        }
    }

    private fun encode(value: String) = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    private fun decode(value: String) = try { String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8) } catch (_: Exception) { "" }
}
