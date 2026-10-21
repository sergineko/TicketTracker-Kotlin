package es.sergiotech.tickettracker.domain

import java.text.Normalizer
import java.time.LocalDate

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
    val reference: ExtractedField<String>? = null,
    val deliveredOn: ExtractedField<LocalDate>? = null,
    val unreadableTableRows: Int = 0,
)

/** Conservative, deterministic extraction. Unlabelled prices and ambiguous dates stay empty. */
class ReceiptExtractor {
    fun extract(text: String): ReceiptExtraction {
        val lines =
            Normalizer.normalize(text, Normalizer.Form.NFKC)
                .lineSequence()
                .map { it.trim().replace(Regex("[\\t\\u00a0 ]+"), " ") }
                .filter(String::isNotEmpty)
                .toList()
        val merchant = merchant(lines)
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
        val header = lines.indexOfFirst(::isTableHeader)
        val table = if (header < 0) null else tableItems(lines, currency, currencies.size <= 1)
        return ReceiptExtraction(
            merchant,
            ReceiptFields.purchaseDate(lines)?.let { ExtractedField(it, 0.75f) },
            ReceiptFields.total(lines, currency, currencies.size <= 1)?.let {
                ExtractedField(it, 0.85f)
            },
            if (header < 0)
                lines.mapNotNull { item(it, currency, currencies.size <= 1, false) }.take(100)
            else table!!.items,
            printedDeadlines(lines),
            ReceiptFields.reference(lines)?.let { ExtractedField(it, .8f) },
            ReceiptFields.deliveryDate(lines)?.let { ExtractedField(it, .75f) },
            table?.unreadable ?: 0,
        )
    }

    private fun dates(text: String): List<LocalDate> = ReceiptFields.dates(text)

    private data class TableRead(val items: List<ReceiptItem>, val unreadable: Int)

    private fun tableItems(
        lines: List<String>,
        currency: String,
        knownCurrency: Boolean,
    ): TableRead {
        var unreadable = 0
        var adaptive: AdaptiveReceiptTable? = null
        val result = mutableListOf<ReceiptItem>()
        var active = false
        var invoice: InvoiceRow? = null
        var pending = ""
        for ((index, line) in lines.withIndex()) {
            if (isTableHeader(line)) {
                active = true
                invoice = if (InvoiceRow.matches(line)) InvoiceRow(line) else null
                adaptive = AdaptiveReceiptTable.from(line)
                pending = ""
                continue
            }
            if (!active) continue
            if (tableEnd.containsMatchIn(line)) {
                active = false
                continue
            }
            if (InvoiceRow.isCharge(line)) continue
            val layout = invoice
            val planned =
                adaptive?.takeIf { pending.isBlank() }?.parse(line, currency, knownCurrency)
            val parsed =
                planned
                    ?: if (adaptive?.mayUseLegacy(line) != false) {
                        if (layout == null) item(line, currency, knownCurrency, true)
                        else layout.parse(line, currency, knownCurrency, pending)
                    } else null
            if (parsed != null) {
                result += parsed
                pending = ""
            } else if (
                (layout != null || adaptive != null) &&
                    !amount.containsMatchIn(line) &&
                    InvoiceRow.isContinuation(line)
            ) {
                if (
                    Regex("^\\d+\\s+").containsMatchIn(line) ||
                        (pending.isBlank() &&
                            InvoiceRow.isPriceOnly(lines.getOrNull(index + 1).orEmpty()))
                )
                    pending = layout?.namePrefix(line) ?: line
                else if (pending.isNotBlank()) pending = (pending + " " + line).take(200)
                else if (result.isEmpty()) pending = layout?.namePrefix(line) ?: line
                else
                    result[result.lastIndex] =
                        result.last().copy(name = (result.last().name + " " + line).take(200))
            } else if (amount.containsMatchIn(line) && line.count(Char::isLetter) >= 3) {
                unreadable++
                pending = ""
            }
        }
        return TableRead(result.take(100), unreadable + maxOf(0, result.size - 100))
    }

    private fun merchant(lines: List<String>): ExtractedField<String>? {
        val header =
            lines
                .takeWhile {
                    !isTableHeader(it) &&
                        !Regex("(?i)^(?:datos\\s+de\\s+cliente|cliente\\b|bill\\s+to|customer\\b)")
                            .containsMatchIn(it)
                }
                .take(20)
        val issuer =
            Regex(
                "(?i)^(?:establecimiento|comercio|proveedor|emisor|vendedor|supplier|seller|issued by)\\s*[:=-]\\s*(.+)$"
            )
        header
            .mapNotNull { issuer.matchEntire(it)?.groupValues?.get(1)?.trim() }
            .distinct()
            .singleOrNull()
            ?.takeIf { it.length in 3..100 }
            ?.let {
                return ExtractedField(it, .9f)
            }
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
                    dates(line).isEmpty() &&
                    !amount.containsMatchIn(line) &&
                    !line.first().isDigit() &&
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
        if (Regex("(?<![\\p{L}\\d])[-−]\\s*\\d").containsMatchIn(line)) return null
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
            val lineTotal =
                if (priceKnown) ReceiptFields.money(matches.last().value, currency) else null
            val each =
                if (matches.size == 2 && priceKnown) ReceiptFields.money(first.value, currency)
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
                    "^(.+?)\\s+(${amount.pattern})\\s*(?:EUR|USD|GBP|€|\\$|£)?$",
                    RegexOption.IGNORE_CASE,
                )
                .matchEntire(line) ?: return null
        var name = match.groupValues[1].trim()
        var quantity = 1
        var price = if (priceKnown) ReceiptFields.money(match.groupValues[2], currency) else null
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
            AdaptiveReceiptTable.from(line) != null ||
                (!ReceiptFields.amount.containsMatchIn(line) &&
                    (tableHeader.containsMatchIn(line) ||
                        (Regex("(?i)\\b(?:cnt|cant|cantidad|qty)\\.?(?:\\s|$)")
                            .containsMatchIn(line) &&
                            Regex("(?i)\\bp\\.?\\s*unit\\b").containsMatchIn(line) &&
                            Regex("(?i)\\b(?:importe|amount)\\b").containsMatchIn(line))))

        private val amount = ReceiptFields.amount
        private val tableHeader =
            Regex(
                "(?i)\\b(?:descripci[oó]n|description|concepto|art[ií]culo|producto|item)\\b.*(?:importe|amount|precio|p\\.?\\s*unit|p\\.?\\s*v\\.?\\s*p|total)"
            )
        private val tableEnd =
            Regex(
                "(?i)^(?:(?:importe\\s+)?total\\b|subtotal\\b|tarjeta\\b|efectivo\\b|(?:tipo\\s+)?iva\\b|base\\s+imp|cuota\\s+iva|importe\\s+a\\s+pagar|(?:total\\s+)?a\\s+(?:pagar|cobrar|abonar)\\b|pago\\b|payment\\b)"
            )
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
