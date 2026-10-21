package es.sergiotech.tickettracker.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal object ReceiptDates {
    private val months =
        listOf(
                listOf("enero", "ene", "january", "jan"),
                listOf("febrero", "feb", "february"),
                listOf("marzo", "mar", "march"),
                listOf("abril", "abr", "april", "apr"),
                listOf("mayo", "may"),
                listOf("junio", "jun", "june"),
                listOf("julio", "jul", "july"),
                listOf("agosto", "ago", "august", "aug"),
                listOf("septiembre", "setiembre", "sep", "sept", "september"),
                listOf("octubre", "oct", "october"),
                listOf("noviembre", "nov", "november"),
                listOf("diciembre", "dic", "december", "dec"),
            )
            .flatMapIndexed { i, names -> names.map { it to i + 1 } }
            .toMap()
    private val named =
        "\\d{1,2}\\s+(?:de\\s+)?(?:" +
            months.keys.sortedByDescending(String::length).joinToString("|") +
            ")\\.?\\s+(?:de\\s+)?\\d{4}"
    val pattern =
        "(?i)(?<![\\p{L}\\d])(?:\\d{4}\\s*-\\s*\\d{2}\\s*-\\s*\\d{2}|\\d{2}\\s*[./-]\\s*\\d{2}\\s*[./-]\\s*\\d{4}|" +
            named +
            ")(?![\\p{L}\\d])"
    private val civil =
        DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

    fun read(text: String): List<LocalDate> =
        Regex(pattern)
            .findAll(text)
            .mapNotNull { match ->
                runCatching {
                        if (match.value.any(Char::isLetter)) {
                            val parts =
                                match.value
                                    .lowercase()
                                    .replace(Regex("\\bde\\b|\\."), "")
                                    .trim()
                                    .split(Regex("\\s+"))
                            LocalDate.of(
                                parts[2].toInt(),
                                months.getValue(parts[1]),
                                parts[0].toInt(),
                            )
                        } else {
                            val value = match.value.filterNot(Char::isWhitespace)
                            if (value[4] == '-') LocalDate.parse(value)
                            else LocalDate.parse(value.replace('.', '/').replace('-', '/'), civil)
                        }
                    }
                    .getOrNull()
            }
            .toList()
}
