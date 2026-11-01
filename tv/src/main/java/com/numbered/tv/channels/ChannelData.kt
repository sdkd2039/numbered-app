package com.sutstudio.numberedtv.channels

import java.util.GregorianCalendar
import java.util.TimeZone

/** ذكر واحد من ملف "معدودات - الأذكار.csv". */
data class DhikrEntry(
    val category: String,
    val text: String,
    val repeat: Int,
    val id: String
)

/** مناسبة واحدة من ملف "معدودات - المتبقي.csv". */
data class UpcomingEntry(
    val name: String,
    val hijriDate: String,
    val gregorianText: String,
    val startMillis: Long
)

data class Countdown(val days: Long, val hours: Long, val minutes: Long)

/**
 * قراءة ملفي CSV وتجهيز نصوص البطاقات.
 * هذا الملف لا يستورد أي شيء من Android عمدًا، ليسهل اختباره خارج الجهاز.
 */
object ChannelData {

    const val DHIKR_CSV_PATH = "data/معدودات - الأذكار.csv"
    const val REMAINING_CSV_PATH = "data/معدودات - المتبقي.csv"

    /** نفس المنطقة الزمنية التي يستخدمها تطبيق الجوال للمتبقي. */
    val ZONE: TimeZone = TimeZone.getTimeZone("Asia/Riyadh")

    private const val TITLE_WORDS = 5
    private const val PREVIEW_MAX_CHARS = 160

    private val MONTHS = mapOf(
        "يناير" to 0,
        "فبراير" to 1,
        "مارس" to 2,
        "أبريل" to 3,
        "ابريل" to 3,
        "إبريل" to 3,
        "مايو" to 4,
        "يونيو" to 5,
        "يوليو" to 6,
        "أغسطس" to 7,
        "اغسطس" to 7,
        "سبتمبر" to 8,
        "أكتوبر" to 9,
        "اكتوبر" to 9,
        "نوفمبر" to 10,
        "ديسمبر" to 11
    )

    private val DATE_REGEX = Regex("""(\d{1,2})\s+(\S+)\s+(\d{4})""")
    private val YEAR_REGEX = Regex("""\d{4}""")
    private val SPACES_REGEX = Regex("""\s+""")

    // ---------------------------------------------------------------- CSV

    /** قارئ CSV يدعم الاقتباس والأسطر المتعددة وCRLF وBOM. */
    fun parseCsv(input: String): List<List<String>> {
        val text = input.removePrefix("﻿")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var hasContent = false
        var i = 0

        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> {
                        quoted = true
                        hasContent = true
                    }
                    ',' -> {
                        row.add(field.toString())
                        field.setLength(0)
                        hasContent = true
                    }
                    '\r' -> Unit
                    '\n' -> {
                        if (hasContent || field.isNotEmpty()) {
                            row.add(field.toString())
                            rows.add(row)
                        }
                        row = ArrayList()
                        field.setLength(0)
                        hasContent = false
                    }
                    else -> {
                        field.append(c)
                        hasContent = true
                    }
                }
            }
            i++
        }

        if (hasContent || field.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }

    /** الأعمدة: الفئة، نص الذكر، التكرار، المعرّف. صف العنوان (إن وُجد) يُتجاهل لأن عمود التكرار فيه ليس رقمًا. */
    fun parseDhikr(csv: String): List<DhikrEntry> {
        val result = ArrayList<DhikrEntry>()
        for ((index, row) in parseCsv(csv).withIndex()) {
            if (row.size < 3) continue
            val category = row[0].trim()
            val text = row[1].trim()
            val repeat = normalizeDigits(row[2].trim()).toIntOrNull() ?: continue
            if (category.isEmpty() || text.isEmpty() || repeat <= 0) continue
            val id = row.getOrNull(3)?.trim().orEmpty().ifEmpty { "row$index" }
            result.add(DhikrEntry(category, text, repeat, id))
        }
        return result
    }

    /** الأعمدة: اسم المناسبة، التاريخ الهجري، التاريخ الميلادي. مرتبة من الأقرب إلى الأبعد. */
    fun parseUpcoming(csv: String): List<UpcomingEntry> {
        val result = ArrayList<UpcomingEntry>()
        for (row in parseCsv(csv)) {
            if (row.size < 3) continue
            val name = row[0].trim()
            val hijri = row[1].trim()
            val gregorian = row[2].trim()
            if (name.isEmpty() || gregorian.isEmpty()) continue
            val millis = parseArabicDate(gregorian) ?: continue
            result.add(UpcomingEntry(name, hijri, gregorian, millis))
        }
        return result.sortedBy { it.startMillis }
    }

    /**
     * يحلل نصًا مثل "9 مارس 2027 م" إلى بداية ذلك اليوم بتوقيت الرياض.
     * لا يحذف حرف «م» من النص كله (فهو جزء من «مارس» و«مايو») بل يقرأ الأجزاء بالتعبير النمطي فقط.
     */
    fun parseArabicDate(value: String): Long? {
        val match = DATE_REGEX.find(normalizeDigits(value.trim())) ?: return null
        val day = match.groupValues[1].toInt()
        val month = MONTHS[match.groupValues[2]] ?: return null
        val year = match.groupValues[3].toInt()
        return try {
            val calendar = GregorianCalendar(ZONE)
            calendar.isLenient = false
            calendar.clear()
            calendar.set(year, month, day, 0, 0, 0)
            calendar.timeInMillis
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // ------------------------------------------------------------ Countdown

    /** الزمن المتبقي، أو null إذا انتهت المناسبة. */
    fun countdown(targetMillis: Long, nowMillis: Long): Countdown? {
        val diff = targetMillis - nowMillis
        if (diff <= 0L) return null
        val totalMinutes = diff / 60_000L
        return Countdown(
            days = totalMinutes / (24 * 60),
            hours = (totalMinutes % (24 * 60)) / 60,
            minutes = totalMinutes % 60
        )
    }

    fun countdownText(c: Countdown): String =
        "${c.days} يوم و${c.hours} ساعة و${c.minutes} دقيقة"

    // ----------------------------------------------------------- Card texts

    fun dhikrTitle(entry: DhikrEntry): String {
        val words = entry.text.split(SPACES_REGEX).filter { it.isNotEmpty() }
        val head = words.take(TITLE_WORDS).joinToString(" ")
        val ellipsis = if (words.size > TITLE_WORDS) "…" else ""
        return "${entry.category} · $head$ellipsis"
    }

    fun dhikrDescription(entry: DhikrEntry): String =
        "${preview(entry.text)}\nالتكرار: ${entry.repeat}"

    /** يستخرج السنة الهجرية من نص التاريخ، مع دعم الأرقام العربية. */
    fun hijriYear(value: String): Int? =
        YEAR_REGEX.find(normalizeDigits(value))?.value?.toIntOrNull()

    fun upcomingTitle(entry: UpcomingEntry): String {
        val hijriYear = YEAR_REGEX.find(normalizeDigits(entry.hijriDate))?.value
        return if (hijriYear != null) "${entry.name} $hijriYear هـ" else entry.name
    }

    fun upcomingDescription(entry: UpcomingEntry, countdown: Countdown): String {
        val dates = if (entry.hijriDate.isNotEmpty()) {
            "${entry.gregorianText} (${entry.hijriDate})"
        } else {
            entry.gregorianText
        }
        return "$dates\nالمتبقي: ${countdownText(countdown)}"
    }

    /** نص مختصر للمعاينة، يُقطع عند آخر مسافة قبل الحد الأقصى. */
    fun preview(text: String, maxChars: Int = PREVIEW_MAX_CHARS): String {
        val clean = text.trim().replace(SPACES_REGEX, " ")
        if (clean.length <= maxChars) return clean
        val cut = clean.lastIndexOf(' ', maxChars)
        val end = if (cut > maxChars / 2) cut else maxChars
        return clean.substring(0, end).trimEnd() + "…"
    }

    fun normalizeDigits(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            out.append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    else -> ch
                }
            )
        }
        return out.toString()
    }
}
