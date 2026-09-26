package com.numbered.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class DhikrItem(
    val category: String,
    val text: String,
    val count: Int,
    val id: String
)

object DhikrRepository {

    private const val CSV_PATH =
        "data/معدودات - الأذكار.csv"

    fun load(context: Context): List<DhikrItem> {

        return try {

            context.assets
                .open(CSV_PATH)
                .use { inputStream ->

                    BufferedReader(
                        InputStreamReader(
                            inputStream,
                            StandardCharsets.UTF_8
                        )
                    ).use { reader ->

                        parseCsv(reader)
                    }
                }

        } catch (e: Exception) {

            emptyList()
        }
    }

    private fun parseCsv(
        reader: BufferedReader
    ): List<DhikrItem> {

        val result =
            mutableListOf<DhikrItem>()

        var firstLine = true

        reader.forEachLine { rawLine ->

            var line =
                rawLine.removePrefix("\uFEFF").trim()

            if (line.isBlank()) {
                return@forEachLine
            }

            val columns =
                parseCsvLine(line)

            if (columns.size < 3) {
                return@forEachLine
            }

            val category =
                columns.getOrNull(0)
                    ?.trim()
                    .orEmpty()

            val text =
                columns.getOrNull(1)
                    ?.trim()
                    .orEmpty()

            val countText =
                columns.getOrNull(2)
                    ?.trim()
                    .orEmpty()

            val id =
                columns.getOrNull(3)
                    ?.trim()
                    .orEmpty()

            if (firstLine) {

                firstLine = false

                val header =
                    "$category $text $countText $id"

                if (
                    header.contains("الفئة") ||
                    header.contains("الذكر") ||
                    header.contains("العدد")
                ) {
                    return@forEachLine
                }
            }

            if (
                category.isBlank() ||
                text.isBlank()
            ) {
                return@forEachLine
            }

            val count =
                arabicNumberToInt(countText)

            if (count <= 0) {
                return@forEachLine
            }

            result.add(
                DhikrItem(
                    category = category,
                    text = text,
                    count = count,
                    id = id
                )
            )
        }

        return result
    }

    private fun parseCsvLine(
        line: String
    ): List<String> {

        val result =
            mutableListOf<String>()

        val current =
            StringBuilder()

        var insideQuotes = false

        var index = 0

        while (index < line.length) {

            val char =
                line[index]

            when {

                char == '"' -> {

                    if (
                        insideQuotes &&
                        index + 1 < line.length &&
                        line[index + 1] == '"'
                    ) {

                        current.append('"')
                        index++

                    } else {

                        insideQuotes =
                            !insideQuotes
                    }
                }

                char == ',' && !insideQuotes -> {

                    result.add(
                        current.toString()
                    )

                    current.setLength(0)
                }

                else -> {
                    current.append(char)
                }
            }

            index++
        }

        result.add(
            current.toString()
        )

        return result
    }

    private fun arabicNumberToInt(
        value: String
    ): Int {

        val normalized =
            value
                .trim()
                .replace('٠', '0')
                .replace('١', '1')
                .replace('٢', '2')
                .replace('٣', '3')
                .replace('٤', '4')
                .replace('٥', '5')
                .replace('٦', '6')
                .replace('٧', '7')
                .replace('٨', '8')
                .replace('٩', '9')
                .replace("٫", ".")
                .replace("٬", "")
                .replace(",", "")

        return normalized
            .toIntOrNull()
            ?: 0
    }
}