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
        val lines =
            text
                .lineSequence()
                .map { it.trim().replace(Regex("[\\t\\u00a0 ]+"), " ") }
                .filter(String::isNotEmpty)
                .toList()
        val merchant = merchant(lines)
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
                .mapIndexedNotNull { index, line ->
                    val label = totalLabel.find(line) ?: return@mapIndexedNotNull null
                    var remaining = line.substring(label.range.last + 1).trim()
                    if (remaining.isEmpty()) remaining = lines.getOrNull(index + 1).orEmpty()
                    // A VAT summary has several amounts; it is not a new purchase total.
                    val values = amount.findAll(remaining).toList()
                    if (
                        values.size != 1 ||
                            remaining
                                .replace(amount, "")
                                .replace(Regex("(?i)EUR|USD|GBP|[€$£():= -]"), "")
                                .isNotBlank()
                    )
                        return@mapIndexedNotNull null
                    if (currencies.size > 1) null else Money.parse(values.single().value, currency)
                }
                .distinct()
        val header = lines.indexOfFirst(::isTableHeader)
        val itemLines =
            if (header < 0) lines
            else lines.drop(header + 1).takeWhile { !tableEnd.containsMatchIn(it) }
        return ReceiptExtraction(
            merchant,
            dates.singleOrNull()?.let { ExtractedField(it, 0.65f) },
            totalCandidates.singleOrNull()?.let { ExtractedField(it, 0.85f) },
            itemLines
                .mapNotNull { item(it, currency, currencies.size <= 1, header >= 0) }
                .take(100),
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

    private fun merchant(lines: List<String>): ExtractedField<String>? {
        val header = lines.takeWhile { !isTableHeader(it) }.take(12)
        val company = Regex("(?i)^(.+?)[, ]+S\\s*\\.?\\s*[AL]\\s*\\.?(?:[ ,;].*)?$")
        header.forEach { line ->
            company.matchEntire(line)?.groupValues?.get(1)?.trim(' ', ',')?.let {
                if (it.length in 3..100 && it.count(Char::isLetter) >= 3)
                    return ExtractedField(it, .9f)
            }
        }
        return header
            .firstOrNull { line ->
                line.length in 3..100 &&
                    line.count(Char::isLetter) >= 3 &&
                    !line.any(Char::isDigit) &&
                    !Regex(
                            "(?i)^(?:av(?:da)?\\.? |avenida |c/|calle |plaza |pza\\.? |paseo |carretera |ctra\\.? |tel[eé]fono|tel[.: ]|factura|ticket|receipt|fecha|date|cif|nif|https?|abrir con|open with)"
                        )
                        .containsMatchIn(line) &&
                    !policy.containsMatchIn(line)
            }
            ?.let { ExtractedField(it, .55f) }
    }

    private fun item(
        line: String,
        currency: String,
        priceKnown: Boolean,
        table: Boolean,
    ): ReceiptItem? {
        if (Regex("[-−]\\s*\\d").containsMatchIn(line)) return null
        if (
            policy.containsMatchIn(line) ||
                (!table && excludedItem.containsMatchIn(line)) ||
                dates(line).isNotEmpty()
        )
            return null
        if (table) {
            val matches = amount.findAll(line).toList()
            if (matches.size !in 1..2) return null
            val first = matches.first()
            val tail =
                line
                    .substring(matches.last().range.last + 1)
                    .trim()
                    .replace(Regex("(?i)EUR|USD|GBP|[€$£]"), "")
                    .trim()
            if (tail.isNotEmpty()) return null
            var name = line.substring(0, first.range.first).trim()
            val quantityMatch = Regex("^(\\d{1,3}|[Il])\\s+(.+)$").matchEntire(name)
            val quantity =
                quantityMatch?.groupValues?.get(1)?.let {
                    if (it == "I" || it == "l") 1 else it.toInt()
                } ?: 1
            name = quantityMatch?.groupValues?.get(2)?.trim() ?: name
            if (name.length !in 3..200 || name.count(Char::isLetter) < 3 || quantity !in 1..999)
                return null
            val lineTotal = if (priceKnown) Money.parse(matches.last().value, currency) else null
            val each =
                if (matches.size == 2 && priceKnown) Money.parse(first.value, currency)
                else
                    lineTotal
                        ?.takeIf { it.minorUnits % quantity == 0L }
                        ?.let { Money(it.minorUnits / quantity, currency) }
            if (
                matches.size == 2 &&
                    (each == null ||
                        lineTotal == null ||
                        each.minorUnits * quantity != lineTotal.minorUnits)
            )
                return null
            return ReceiptItem(name, quantity, each)
        }
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
        private fun isTableHeader(line: String): Boolean =
            tableHeader.containsMatchIn(line) ||
                (Regex("(?i)\\b(?:cnt|cant|cantidad|qty)\\.?(?:\\s|$)").containsMatchIn(line) &&
                    Regex("(?i)\\bp\\.?\\s*unit\\b").containsMatchIn(line) &&
                    Regex("(?i)\\b(?:importe|amount)\\b").containsMatchIn(line))

        private val amount = Regex("(?<![\\d.,])\\d{1,9}[.,]\\d{2}(?!\\d)")
        private val totalLabel =
            Regex(
                "(?i)^(?:importe\\s+)?total\\b(?:\\s+a\\s+pagar)?\\s*(?:\\(?\\s*(?:EUR|USD|GBP|€|\\$|£)\\s*\\)?)?\\s*[:=–-]?\\s*"
            )
        private val tableHeader =
            Regex(
                "(?i)\\b(?:descripci[oó]n|description|art[ií]culo|producto|item)\\b.*(?:importe|amount|precio|p\\.?\\s*unit)"
            )
        private val tableEnd =
            Regex(
                "(?i)^(?:(?:importe\\s+)?total\\b|subtotal\\b|tarjeta\\b|efectivo\\b|iva\\b|base\\s+imp|pago\\b|payment\\b)"
            )
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
