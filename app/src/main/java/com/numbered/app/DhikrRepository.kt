package com.numbered.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class DhikrItem(
    val text: String,
    val count: Int,
    val category: String
)

object DhikrRepository {

    private const val CSV_PATH = "data/معدودات - الأذكار.csv"

    fun load(context: Context): List<DhikrItem> {
        return try {
            context.assets.open(CSV_PATH).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    parse(reader)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parse(reader: BufferedReader): List<DhikrItem> {
        val rows = mutableListOf<List<String>>()

        reader.forEachLine { line ->
            if (line.isNotBlank()) {
                val row = parseCsvLine(line)
                if (row.isNotEmpty()) rows.add(row)
            }
        }

        if (rows.isEmpty()) return emptyList()

        val header = rows.first().map {
            it.trim().lowercase()
        }

        val hasHeader = header.any {
            it.contains("ذكر") ||
            it.contains("نص") ||
            it.contains("text") ||
            it.contains("count") ||
            it.contains("عدد") ||
            it.contains("category") ||
            it.contains("تصنيف")
        }

        val start = if (hasHeader) 1 else 0

        var textIndex = 0
        var countIndex = 1
        var categoryIndex = 2

        if (hasHeader) {
            header.forEachIndexed { index, value ->
                when {
                    value.contains("ذكر") ||
                    value.contains("نص") ||
                    value.contains("text") ||
                    value.contains("dhikr") -> textIndex = index

                    value.contains("عدد") ||
                    value.contains("تكرار") ||
                    value.contains("count") ||
                    value.contains("repeat") -> countIndex = index

                    value.contains("تصنيف") ||
                    value.contains("قسم") ||
                    value.contains("category") ||
                    value.contains("cat") -> categoryIndex = index
                }
            }
        }

        return rows.drop(start).mapNotNull { row ->
            if (row.isEmpty()) return@mapNotNull null

            val text = row.getOrNull(textIndex)?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null

            val count = row
                .getOrNull(countIndex)
                ?.trim()
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: 1

            val category = row
                .getOrNull(categoryIndex)
                ?.trim()
                .orEmpty()
                .ifEmpty { "منوع" }

            DhikrItem(
                text = text,
                count = count,
                category = category
            )
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]

            when {
                c == '"' -> {
                    if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        insideQuotes = !insideQuotes
                    }
                }

                c == ',' && !insideQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }

                else -> current.append(c)
            }

            i++
        }

        result.add(current.toString())
        return result
    }
}