package es.sergiotech.tickettracker.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class ExtractedField<T>(val value: T, val confidence: Float)

data class ReceiptItem(val name: String, val quantity: Int = 1, val unitPrice: Money? = null)

enum class PrintedDeadlineKind {
    WARRANTY,
    RETURN,
}

data class PrintedDeadline(
    val kind: PrintedDeadlineKind,
    val date: LocalDate,
    val evidence: String,
    val ruleVersion: String = "printed_absolute_date_v1",
)

data class ReceiptExtraction(
    val merchant: ExtractedField<String>? = null,
    val date: ExtractedField<LocalDate>? = null,
    val total: ExtractedField<Money>? = null,
    val items: List<ReceiptItem> = emptyList(),
    val deadlines: List<PrintedDeadline> = emptyList(),
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
        val purchaseLines = lines.filterNot { policy.containsMatchIn(it) }
        val labelledDates =
            purchaseLines
                .filter {
                    Regex(
                            "(?i)^(?:fecha(?: de (?:compra|emisi[oó]n))?|purchase date|date)\\s*[:=-]?\\s*\\d"
                        )
                        .containsMatchIn(it)
                }
                .flatMap(::dates)
                .distinct()
        val dates =
            if (labelledDates.isNotEmpty()) labelledDates
            else purchaseLines.flatMap(::dates).distinct()
        val currencies =
            Regex("(?i)\\b(?:EUR|USD|GBP)\\b|[€$£]")
                .findAll(text)
                .map {
                    when (it.value.uppercase()) {
                        "USD",
                        "$" -> "USD"
                        "GBP",
                        "£" -> "GBP"
                        else -> "EUR"
                    }
                }
                .distinct()
                .toList()
        val currency = currencies.singleOrNull() ?: "EUR"
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
                        ?.let { if (currencies.size > 1) null else Money.parse(it, currency) }
                }
                .distinct()
        return ReceiptExtraction(
            merchant,
            dates.singleOrNull()?.let { ExtractedField(it, 0.65f) },
            totalCandidates.singleOrNull()?.let { ExtractedField(it, 0.85f) },
            lines.mapNotNull { item(it, currency, currencies.size <= 1) }.take(100),
            printedDeadlines(lines),
        )
    }

    private fun dates(text: String): List<LocalDate> =
        datePattern
            .findAll(text)
            .mapNotNull {
                runCatching { LocalDate.parse(it.value.replace('-', '/'), dateFormat) }.getOrNull()
            }
            .toList()

    private fun item(line: String, currency: String, priceKnown: Boolean): ReceiptItem? {
        if (Regex("[-−]\\s*\\d").containsMatchIn(line)) return null
        if (
            policy.containsMatchIn(line) ||
                excludedItem.containsMatchIn(line) ||
                dates(line).isNotEmpty()
        )
            return null
        val match =
            Regex(
                    "^(.+?)\\s+(\\d{1,9}[.,]\\d{2})\\s*(?:EUR|USD|GBP|€|\\$|£)?$",
                    RegexOption.IGNORE_CASE,
                )
                .matchEntire(line) ?: return null
        var name = match.groupValues[1].trim()
        var quantity = 1
        var price = if (priceKnown) Money.parse(match.groupValues[2], currency) else null
        val unit =
            Regex("^(.+?)\\s+(\\d{1,3})\\s*[x×]\\s*(\\d{1,9}[.,]\\d{2})$", RegexOption.IGNORE_CASE)
                .matchEntire(name)
        val prefix =
            Regex("^(\\d{1,3})\\s*(?:[x×]\\s*)?(.+)$", RegexOption.IGNORE_CASE).matchEntire(name)
        if (unit != null) {
            name = unit.groupValues[1].trim()
            quantity = unit.groupValues[2].toInt()
            val each = Money.parse(unit.groupValues[3], currency) ?: return null
            if (
                quantity !in 1..999 ||
                    price == null ||
                    each.minorUnits * quantity != price.minorUnits
            )
                return null
            price = each
        } else if (prefix != null) {
            quantity = prefix.groupValues[1].toInt()
            name = prefix.groupValues[2].trim()
            // A quantity followed by one amount does not say whether that amount is per unit.
            if (quantity != 1) price = null
        }
        if (name.length !in 3..200 || name.count(Char::isLetter) < 3 || quantity !in 1..999)
            return null
        // Reject bare prices, reference numbers, payment summaries and ambiguous amount columns.
        if (Regex("\\d[.,]\\d{2}").containsMatchIn(name)) return null
        return ReceiptItem(name, quantity, price)
    }

    private fun printedDeadlines(lines: List<String>): List<PrintedDeadline> =
        PrintedDeadlineKind.entries.mapNotNull { kind ->
            val pattern =
                when (kind) {
                    PrintedDeadlineKind.WARRANTY ->
                        Regex(
                            "(?i)(?:garant[ií]a|warranty).*(?:hasta|fin|vence|vencimiento|until|expires)"
                        )
                    PrintedDeadlineKind.RETURN ->
                        Regex(
                            "(?i)(?:devoluci[oó]n(?:es)?|returns?).*(?:hasta|l[ií]mite|fin|until|by)"
                        )
                }
            val candidates =
                lines
                    .filter { pattern.containsMatchIn(it) && !conditional.containsMatchIn(it) }
                    .flatMap { line ->
                        dates(line).map { PrintedDeadline(kind, it, line.take(300)) }
                    }
            candidates.distinctBy { it.date }.singleOrNull()
        }

    companion object {
        private val datePattern = Regex("(?<!\\d)\\d{2}[/-]\\d{2}[/-]\\d{4}(?!\\d)")
        private val dateFormat =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        private val policy =
            Regex(
                "(?i)garant[ií]a|warranty|devoluci[oó]n|returns?|entrega|delivery|caducidad|expiry"
            )
        private val conditional =
            Regex("(?i)\\b(?:no|not|excepto|except|salvo|si|if|laborables|h[aá]biles)\\b")
        private val excludedItem =
            Regex(
                "(?i)\\b(?:total|subtotal|iva|tax|impuesto|base|efectivo|cash|cambio|change|tarjeta|card|pago|payment|descuento|discount|dto|ahorro|saldo|balance|importe|amount|cif|nif|vat|tel|telefono|phone|cliente|customer|pedido|order|factura|invoice|ticket|receipt|cajero|caja|www|http)\\b|%"
            )
    }
}
