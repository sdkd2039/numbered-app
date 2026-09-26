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

    /*
     * مكان ملف CSV داخل التطبيق:
     *
     * app/src/main/assets/data/معدودات - الأذكار.csv
     */
    private const val CSV_PATH =
        "data/معدودات - الأذكار.csv"

    /*
     * قراءة جميع الأذكار من ملف CSV.
     */
    fun load(
        context: Context
    ): List<DhikrItem> {

        return try {

            context.assets
                .open(CSV_PATH)
                .use { inputStream ->

                    BufferedReader(
                        InputStreamReader(
                            inputStream,
                            Charsets.UTF_8
                        )
                    ).use { reader ->

                        parse(reader)
                    }
                }

        } catch (_: Exception) {

            /*
             * إذا لم يتم العثور على الملف
             * أو حدث خطأ في القراءة.
             */
            emptyList()
        }
    }

    /*
     * تحليل ملف CSV.
     *
     * ترتيب الأعمدة المتوقع:
     *
     * الفئة
     * الذكر
     * العدد
     * المعرّف
     */
    private fun parse(
        reader: BufferedReader
    ): List<DhikrItem> {

        val rows =
            mutableListOf<List<String>>()

        reader.forEachLine { line ->

            if (line.isNotBlank()) {

                val row =
                    parseCsvLine(line)

                if (row.isNotEmpty()) {
                    rows.add(row)
                }
            }
        }

        if (rows.isEmpty()) {
            return emptyList()
        }

        /*
         * قراءة الصف الأول لمعرفة هل هو Header.
         */
        val header =
            rows.first().map {
                cleanValue(it).lowercase()
            }

        val hasHeader =
            header.any { value ->

                value.contains("الفئة") ||
                value.contains("فئة") ||
                value.contains("category") ||

                value.contains("الذكر") ||
                value.contains("ذكر") ||
                value.contains("text") ||
                value.contains("dhikr") ||

                value.contains("العدد") ||
                value.contains("عدد") ||
                value.contains("count") ||

                value.contains("المعرف") ||
                value.contains("المعرّف") ||
                value.contains("معرف") ||
                value.contains("id")
            }

        /*
         * ترتيب CSV الحقيقي عندك:
         *
         * 0 = الفئة
         * 1 = الذكر
         * 2 = العدد
         * 3 = المعرّف
         */
        var categoryIndex = 0
        var textIndex = 1
        var countIndex = 2

        /*
         * إذا كان هناك Header،
         * نبحث عن الأعمدة بالاسم.
         */
        if (hasHeader) {

            header.forEachIndexed { index, value ->

                when {

                    /*
                     * عمود الفئة.
                     */
                    value.contains("الفئة") ||
                    value == "فئة" ||
                    value.contains("category") ||
                    value == "cat" -> {

                        categoryIndex = index
                    }

                    /*
                     * عمود الذكر.
                     */
                    value.contains("الذكر") ||
                    value == "ذكر" ||
                    value.contains("text") ||
                    value.contains("dhikr") -> {

                        textIndex = index
                    }

                    /*
                     * عمود العدد.
                     */
                    value.contains("العدد") ||
                    value == "عدد" ||
                    value.contains("تكرار") ||
                    value.contains("count") ||
                    value.contains("repeat") -> {

                        countIndex = index
                    }
                }
            }
        }

        /*
         * إذا كان Header موجودًا نتجاهل أول صف.
         */
        val startIndex =
            if (hasHeader) {
                1
            } else {
                0
            }

        val result =
            mutableListOf<DhikrItem>()

        /*
         * تحويل كل صف إلى DhikrItem.
         */
        rows.drop(startIndex).forEach { row ->

            /*
             * نتأكد أن الصف يحتوي على
             * الأعمدة المطلوبة.
             */
            if (row.size <= maxOf(
                    categoryIndex,
                    textIndex,
                    countIndex
                )
            ) {
                return@forEach
            }

            /*
             * الفئة.
             */
            val category =
                cleanValue(
                    row.getOrNull(categoryIndex)
                        .orEmpty()
                ).ifEmpty {
                    "منوعة"
                }

            /*
             * نص الذكر.
             */
            val text =
                cleanValue(
                    row.getOrNull(textIndex)
                        .orEmpty()
                )

            /*
             * إذا لم يوجد نص للذكر
             * نتجاهل الصف.
             */
            if (text.isEmpty()) {
                return@forEach
            }

            /*
             * العدد المطلوب.
             *
             * يدعم الأرقام العادية
             * والأرقام العربية.
             */
            val count =
                parseCount(
                    row.getOrNull(countIndex)
                        .orEmpty()
                )

            result.add(
                DhikrItem(
                    text = text,
                    count = count,
                    category = category
                )
            )
        }

        return result
    }

    /*
     * تحويل قيمة العدد إلى Int.
     *
     * أمثلة:
     *
     * 3
     * 10
     * ٣
     * ١٠
     */
    private fun parseCount(
        value: String
    ): Int {

        val normalized =
            cleanValue(value)
                .replace("٠", "0")
                .replace("١", "1")
                .replace("٢", "2")
                .replace("٣", "3")
                .replace("٤", "4")
                .replace("٥", "5")
                .replace("٦", "6")
                .replace("٧", "7")
                .replace("٨", "8")
                .replace("٩", "9")

        return normalized
            .toIntOrNull()
            ?.takeIf { it > 0 }
            ?: 1
    }

    /*
     * تنظيف قيمة من CSV.
     */
    private fun cleanValue(
        value: String
    ): String {

        return value
            .trim()
            .removePrefix("\uFEFF")
            .trim('"')
            .trim()
    }

    /*
     * محلل CSV يدعم:
     *
     * - الفواصل ,
     * - النصوص بين ""
     * - الفاصلة داخل النص
     * - "" داخل النص
     */
    private fun parseCsvLine(
        line: String
    ): List<String> {

        val result =
            mutableListOf<String>()

        val current =
            StringBuilder()

        var insideQuotes =
            false

        var i =
            0

        while (i < line.length) {

            val character =
                line[i]

            when {

                /*
                 * بداية أو نهاية النص المحاط بعلامات اقتباس.
                 */
                character == '"' -> {

                    /*
                     * "" داخل النص تعني "
                     */
                    if (
                        insideQuotes &&
                        i + 1 < line.length &&
                        line[i + 1] == '"'
                    ) {

                        current.append('"')
                        i++

                    } else {

                        insideQuotes =
                            !insideQuotes
                    }
                }

                /*
                 * الفاصلة خارج علامات الاقتباس
                 * تعني الانتقال إلى عمود جديد.
                 */
                character == ',' &&
                    !insideQuotes -> {

                    result.add(
                        current.toString()
                    )

                    current.clear()
                }

                /*
                 * أي حرف عادي.
                 */
                else -> {

                    current.append(
                        character
                    )
                }
            }

            i++
        }

        /*
         * إضافة آخر عمود.
         */
        result.add(
            current.toString()
        )

        return result
    }
}