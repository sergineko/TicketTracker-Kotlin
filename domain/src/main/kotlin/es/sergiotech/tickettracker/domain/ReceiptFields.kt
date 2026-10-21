package es.sergiotech.tickettracker.domain

import java.time.LocalDate

/** Shared lexical rules for labelled fields; payment balances are not purchase totals. */
internal object ReceiptFields {
    val amount =
        Regex("(?<![\\d.,])(?:\\d{1,3}(?:[.,][ \\t]*\\d{3})+|\\d{1,9})[.,][ \\t]*\\d{2}(?![\\d.,])")
    private val date = Regex(ReceiptDates.pattern)
    private val primaryDate =
        Regex(
            "(?i)(?:fecha\\s+(?:de\\s+)?(?:factura|compra|emisi[oó]n|venta)|invoice\\s+date|purchase\\s+date|issued\\s+on)\\s*[:=-]?\\s*"
        )
    private val anyDateLabel =
        Regex(
            "(?i)fecha(?:\\s+de\\s+\\p{L}+)?|invoice date|purchase date|delivery date|payment date"
        )
    private val nonPurchaseDate =
        Regex(
            "(?i)garant[ií]a|warranty|devoluci[oó]n|returns?|entrega|delivery|caducidad|expiry|fecha de pago|payment date|vencimiento|due date"
        )

    fun dates(text: String): List<LocalDate> = ReceiptDates.read(text)

    private fun labelledDates(lines: List<String>, label: Regex): List<LocalDate> =
        lines
            .flatMapIndexed { index, line ->
                label
                    .findAll(line)
                    .mapNotNull { match ->
                        val rest = line.substring(match.range.last + 1)
                        val nextLabel = anyDateLabel.find(rest)?.range?.first ?: rest.length
                        val inline = dates(rest.take(nextLabel))
                        if (inline.size == 1) inline.single()
                        else if (
                            inline.isEmpty() &&
                                !date.containsMatchIn(rest.take(nextLabel)) &&
                                !anyDateLabel.containsMatchIn(lines.getOrNull(index + 1).orEmpty())
                        )
                            dates(lines.getOrNull(index + 1).orEmpty()).singleOrNull()
                        else null
                    }
                    .toList()
            }
            .distinct()

    fun purchaseDate(lines: List<String>): LocalDate? {
        val primary = labelledDates(lines, primaryDate)
        if (lines.any { primaryDate.containsMatchIn(it) }) return primary.singleOrNull()
        val labelled = labelledDates(lines, Regex("(?i)^(?:fecha|date)\\s*[:=-]?\\s*(?=\\d|$)"))
        if (labelled.isNotEmpty()) return labelled.singleOrNull()
        return lines
            .filterIndexed { index, line ->
                !nonPurchaseDate.containsMatchIn(line) &&
                    !(index > 0 &&
                        nonPurchaseDate.containsMatchIn(lines[index - 1]) &&
                        dates(lines[index - 1]).isEmpty())
            }
            .flatMap(::dates)
            .distinct()
            .singleOrNull()
    }

    fun deliveryDate(lines: List<String>): LocalDate? =
        labelledDates(lines, Regex("(?i)(?:fecha\\s+de\\s+entrega|delivery\\s+date)\\s*[:=-]?\\s*"))
            .singleOrNull()

    fun reference(lines: List<String>): String? {
        val labels =
            listOf(
                Regex(
                    "(?i)^(?:n[úu]mero\\s+(?:de\\s+)?)?pedido(?:\\s+online)?(?:\\s+n[úu]mero)?\\s*[:#=-]?\\s*"
                ),
                Regex(
                    "(?i)^(?:n[úu]mero\\s+de\\s+factura|factura(?:\\s+simplificada)?|invoice\\s+(?:number|no\\.?))\\s*[:#=-]?\\s*"
                ),
            )
        for (label in labels) {
            val candidates =
                lines
                    .mapIndexedNotNull { index, line ->
                        val match = label.find(line) ?: return@mapIndexedNotNull null
                        val rest =
                            line.substring(match.range.last + 1).trim().ifBlank {
                                lines.getOrNull(index + 1).orEmpty()
                            }
                        rest.takeIf {
                            it.length in 3..100 &&
                                it.any(Char::isDigit) &&
                                dates(it).isEmpty() &&
                                it.matches(Regex("[\\p{L}\\d][\\p{L}\\d/_-]*"))
                        }
                    }
                    .distinct()
            if (candidates.isNotEmpty()) return candidates.singleOrNull()
        }
        return null
    }

    fun money(text: String, currency: String): Money? {
        val value = text.filterNot(Char::isWhitespace)
        val decimal = value.indexOfLast { it == ',' || it == '.' }
        if (decimal < 0 || value.length - decimal != 3) return null
        val whole = value.take(decimal)
        if (whole.contains(value[decimal])) return null
        return Money.parse(
            whole.replace(",", "").replace(".", "") + "." + value.takeLast(2),
            currency,
        )
    }

    fun total(lines: List<String>, currency: String, knownCurrency: Boolean): Money? {
        if (!knownCurrency) return null
        val strong =
            Regex(
                "(?i)^(?:importe\\s+total|total\\s+(?:factura|ticket|venta|de\\s+(?:la\\s+)?factura|compra|general|documento|con\\s+iva|iva\\s+incluido)|grand\\s+total|invoice\\s+total)\\b\\s*[:=-]?\\s*"
            )
        val standard = Regex("(?i)^(?:importe\\s+)?total\\b\\s*[:=-]?\\s*")
        val balance =
            Regex(
                "(?i)^(?:importe\\s+a\\s+pagar|(?:total\\s+)?a\\s+(?:pagar|cobrar|abonar)|amount\\s+due|balance\\s+due)\\b\\s*[:=-]?\\s*"
            )
        val candidates =
            lines.mapIndexedNotNull { index, line ->
                val low = balance.find(line)
                val high = strong.find(line)
                var label = high ?: low ?: standard.find(line)
                var rank = if (high != null) 3 else if (low != null) 1 else 2
                // A summary heading may share its physical row with a separate grand total.
                if (
                    label == null &&
                        Regex("(?i)^(?:tipo\\s+iva|iva|base\\s+imp|tax)").containsMatchIn(line)
                ) {
                    label = Regex("(?i)\\btotal\\b").findAll(line).lastOrNull()
                    rank = 2
                }
                label ?: return@mapIndexedNotNull null
                var rest = line.substring(label.range.last + 1).trim()
                rest =
                    rest.replace(
                        Regex("(?i)^\\(?\\s*(?:EUR|USD|GBP|€|\\$|£)\\s*\\)?\\s*[:=-]?\\s*"),
                        "",
                    )
                if (rest.isEmpty()) rest = lines.getOrNull(index + 1).orEmpty()
                val values = amount.findAll(rest).toList()
                if (
                    values.size != 1 ||
                        rest
                            .replace(amount, "")
                            .replace(Regex("(?i)EUR|USD|GBP|[€$£():= -]"), "")
                            .isNotBlank()
                )
                    return@mapIndexedNotNull null
                money(values.single().value, currency)?.let { rank to it }
            }
        val rank = candidates.maxOfOrNull { it.first } ?: return null
        return candidates.filter { it.first == rank }.map { it.second }.distinct().singleOrNull()
    }
}
