package es.sergiotech.tickettracker.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class ExtractedField<T>(val value: T, val confidence: Float)

data class ReceiptExtraction(
    val merchant: ExtractedField<String>? = null,
    val date: ExtractedField<LocalDate>? = null,
    val total: ExtractedField<Money>? = null,
)

/** Conservative, deterministic extraction. Unlabelled prices and ambiguous dates stay empty. */
class ReceiptExtractor {
    fun extract(text: String): ReceiptExtraction {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val merchant =
            lines
                .take(3)
                .firstOrNull {
                    it.length in 3..100 &&
                        it.count(Char::isLetter) >= 3 &&
                        !it.contains(
                            Regex("(?i)factura|ticket|receipt|fecha|date|cif|nif|https?")
                        ) &&
                        !it.any(Char::isDigit)
                }
                ?.let { ExtractedField(it, 0.5f) }
        val dates =
            Regex("(?<!\\d)(\\d{2})[/-](\\d{2})[/-](\\d{4})(?!\\d)")
                .findAll(text)
                .mapNotNull { match ->
                    runCatching {
                            LocalDate.parse(
                                match.value.replace('-', '/'),
                                DateTimeFormatter.ofPattern("dd/MM/uuuu")
                                    .withResolverStyle(ResolverStyle.STRICT),
                            )
                        }
                        .getOrNull()
                }
                .distinct()
                .toList()
        val totalCandidates =
            lines
                .mapNotNull { line ->
                    if (
                        !line.contains(
                            Regex(
                                "(?i)^\\s*(?:importe\\s+)?total(?:\\s+(?:a\\s+pagar|eur|€))?\\s*[:=-]?\\s*\\d"
                            )
                        )
                    )
                        return@mapNotNull null
                    Regex("(?<![\\d.,])(\\d{1,9}[.,]\\d{2})(?!\\d)")
                        .findAll(line)
                        .lastOrNull()
                        ?.groupValues
                        ?.get(1)
                        ?.let { Money.parse(it) }
                }
                .distinct()
        return ReceiptExtraction(
            merchant,
            dates.singleOrNull()?.let { ExtractedField(it, 0.65f) },
            totalCandidates.singleOrNull()?.let { ExtractedField(it, 0.85f) },
        )
    }
}
