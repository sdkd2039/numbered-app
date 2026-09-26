package com.numbered.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class RemainingItem(
    val name: String,
    val hijriDate: String,
    val gregorianDate: String,
    val gregorianMillis: Long
)

object RemainingRepository {

    private const val CSV_PATH =
        "data/معدودات - المتبقي.csv"

    private val timeZone =
        TimeZone.getTimeZone("Asia/Riyadh")

    private val locale =
        Locale("ar", "SA")

    fun load(context: Context): List<RemainingItem> {

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

    fun nextUpcoming(
        context: Context,
        nowMillis: Long
    ): RemainingItem? {

        return load(context)
            .filter {
                it.gregorianMillis > nowMillis
            }
            .minByOrNull {
                it.gregorianMillis
            }
    }

    fun previousEvent(
        context: Context,
        nextEvent: RemainingItem
    ): RemainingItem? {

        return load(context)
            .filter {
                it.gregorianMillis < nextEvent.gregorianMillis
            }
            .maxByOrNull {
                it.gregorianMillis
            }
    }

    private fun parseCsv(
        reader: BufferedReader
    ): List<RemainingItem> {

        val result =
            mutableListOf<RemainingItem>()

        var firstLine = true

        reader.forEachLine { rawLine ->

            val line =
                rawLine
                    .removePrefix("\uFEFF")
                    .trim()

            if (line.isBlank()) {
                return@forEachLine
            }

            val columns =
                parseCsvLine(line)

            if (columns.size < 3) {
                return@forEachLine
            }

            val name =
                columns[0]
                    .trim()

            val hijriDate =
                columns[1]
                    .trim()

            val gregorianDate =
                columns[2]
                    .trim()

            if (firstLine) {

                firstLine = false

                val header =
                    "$name $hijriDate $gregorianDate"

                if (
                    header.contains("الاسم") ||
                    header.contains("الهجري") ||
                    header.contains("الميلادي")
                ) {
                    return@forEachLine
                }
            }

            if (
                name.isBlank() ||
                hijriDate.isBlank() ||
                gregorianDate.isBlank()
            ) {
                return@forEachLine
            }

            val millis =
                parseArabicDate(
                    gregorianDate
                )

            if (millis == null) {
                return@forEachLine
            }

            result.add(
                RemainingItem(
                    name = name,
                    hijriDate = hijriDate,
                    gregorianDate = gregorianDate,
                    gregorianMillis = millis
                )
            )
        }

        return result.sortedBy {
            it.gregorianMillis
        }
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

    private fun parseArabicDate(
        value: String
    ): Long? {

        var dateText =
            value
                .trim()
                .replace("م", "")
                .trim()

        dateText =
            dateText
                .replace(
                    "يناير",
                    "January"
                )
                .replace(
                    "فبراير",
                    "February"
                )
                .replace(
                    "مارس",
                    "March"
                )
                .replace(
                    "أبريل",
                    "April"
                )
                .replace(
                    "ابريل",
                    "April"
                )
                .replace(
                    "مايو",
                    "May"
                )
                .replace(
                    "يونيو",
                    "June"
                )
                .replace(
                    "يوليو",
                    "July"
                )
                .replace(
                    "أغسطس",
                    "August"
                )
                .replace(
                    "اغسطس",
                    "August"
                )
                .replace(
                    "سبتمبر",
                    "September"
                )
                .replace(
                    "أكتوبر",
                    "October"
                )
                .replace(
                    "اكتوبر",
                    "October"
                )
                .replace(
                    "نوفمبر",
                    "November"
                )
                .replace(
                    "ديسمبر",
                    "December"
                )

        dateText =
            dateText
                .replace(
                    Regex("[٠-٩]")
                ) {
                    when (it.value) {
                        "٠" -> "0"
                        "١" -> "1"
                        "٢" -> "2"
                        "٣" -> "3"
                        "٤" -> "4"
                        "٥" -> "5"
                        "٦" -> "6"
                        "٧" -> "7"
                        "٨" -> "8"
                        "٩" -> "9"
                        else -> it.value
                    }
                }

        val formats =
            listOf(
                "d MMMM yyyy",
                "dd MMMM yyyy",
                "d MMM yyyy",
                "dd MMM yyyy"
            )

        for (format in formats) {

            val formatter =
                SimpleDateFormat(
                    format,
                    Locale.ENGLISH
                ).apply {

                    timeZone =
                        this@RemainingRepository.timeZone

                    isLenient = false
                }

            val position =
                ParsePosition(0)

            val date =
                formatter.parse(
                    dateText,
                    position
                )

            if (
                date != null &&
                position.index == dateText.length
            ) {

                val calendar =
                    Calendar.getInstance(
                        timeZone,
                        locale
                    )

                calendar.time =
                    date

                calendar.set(
                    Calendar.HOUR_OF_DAY,
                    0
                )

                calendar.set(
                    Calendar.MINUTE,
                    0
                )

                calendar.set(
                    Calendar.SECOND,
                    0
                )

                calendar.set(
                    Calendar.MILLISECOND,
                    0
                )

                return calendar.timeInMillis
            }
        }

        return null
    }
}