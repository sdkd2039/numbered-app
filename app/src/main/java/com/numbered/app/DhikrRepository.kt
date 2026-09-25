package com.numbered.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL

internal data class DhikrEntry(
    val group: String,
    val text: String,
    val count: String,
    val id: String
)

internal object DhikrRepository {
    private const val CSV_URL = "https://raw.githubusercontent.com/sdkd2039/numbered-app/refs/heads/main/data/%D9%85%D8%B9%D8%AF%D9%88%D8%AF%D8%A7%D8%AA%20-%20%D8%A7%D9%84%D8%A3%D8%B0%D9%83%D8%A7%D8%B1.csv"
    private const val PREFS = "dhikr_data_cache"
    private const val KEY_ROWS = "rows"

    fun loadCached(context: Context): List<DhikrEntry> {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ROWS, null) ?: return emptyList()
        return encoded.split('\n').mapNotNull { line ->
            val fields = line.split('|', limit = 4)
            if (fields.size == 4) {
                DhikrEntry(decode(fields[0]), decode(fields[1]), decode(fields[2]), decode(fields[3]))
            } else null
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
                    parseCsv(input.bufferedReader(Charsets.UTF_8).readText())
                }
            } catch (_: Exception) {
                emptyList()
            }

            if (fresh.isNotEmpty()) {
                val serialized = fresh.joinToString("\n") { entry ->
                    listOf(entry.group, entry.text, entry.count, entry.id)
                        .joinToString("|") { encode(it) }
                }
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_ROWS, serialized)
                    .apply()
            }

            Handler(Looper.getMainLooper()).post {
                callback(if (fresh.isNotEmpty()) fresh else loadCached(context))
            }
        }.start()
    }

    /*
     * The repository CSV has no header row. Its actual columns are:
     * group, dhikr text, repetition count, stable id.
     * This parser supports commas in quoted fields and escaped quotes.
     */
    private fun parseCsv(csv: String): List<DhikrEntry> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0

        fun finishField() {
            row += field.toString()
            field.setLength(0)
        }

        fun finishRow() {
            if (row.isNotEmpty() || field.isNotEmpty()) {
                finishField()
                rows += row.toList()
                row.clear()
            }
        }

        while (index < csv.length) {
            val character = csv[index]
            when {
                character == '"' && quoted && index + 1 < csv.length && csv[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> finishField()
                character == '\n' && !quoted -> finishRow()
                character == '\r' && !quoted -> Unit
                else -> field.append(character)
            }
            index++
        }
        if (quoted || field.isNotEmpty() || row.isNotEmpty()) finishRow()

        return rows.mapNotNull { columns ->
            if (columns.size < 2) return@mapNotNull null
            val group = columns[0].trim()
            val text = columns[1].trim()
            if (group.isEmpty() || text.isEmpty()) return@mapNotNull null
            DhikrEntry(
                group = group,
                text = text,
                count = columns.getOrNull(2)?.trim().orEmpty(),
                id = columns.getOrNull(3)?.trim().orEmpty()
            )
        }
    }

    private fun encode(value: String) =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decode(value: String) = try {
        String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Exception) {
        ""
    }
}
