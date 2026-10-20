package es.sergiotech.tickettracker.domain

/** Column semantics come from the printed header, never from a retailer name. */
internal class InvoiceRow(header: String) {
    private val position = Regex("(?i)^pos\\b|^line(?:a)?\\b").containsMatchIn(header)
    private val code =
        Regex("(?i)(?:n[°ºo.]?\\s*(?:producto|art[ií]culo)|c[oó]d(?:igo)?|sku|referencia)")
            .containsMatchIn(header)
    private val discount = Regex("(?i)descuento|discount|dto").containsMatchIn(header)

    fun namePrefix(value: String): String {
        var name = value.trim()
        if (position) name = name.replace(Regex("^\\d{1,4}\\s+"), "")
        if (code) {
            val first = name.substringBefore(' ')
            if (
                first.length in 3..30 &&
                    first.any(Char::isDigit) &&
                    first.all { it.isLetterOrDigit() || it in "-_/" }
            )
                name = name.substringAfter(' ', "")
        }
        return name.trim()
    }

    fun parse(
        line: String,
        currency: String,
        knownCurrency: Boolean,
        descriptionPrefix: String = "",
    ): ReceiptItem? {
        val prices =
            ReceiptFields.amount
                .findAll(line)
                .filter { !line.substring(it.range.last + 1).trimStart().startsWith('%') }
                .toList()
        if (prices.size !in 1..3) return null
        val after =
            line
                .substring(prices.last().range.last + 1)
                .replace(Regex("(?i)EUR|USD|GBP|[€$£]"), "")
                .trim()
        if (after.isNotBlank()) return null
        val prefix =
            namePrefix(
                line
                    .substring(0, prices.first().range.first)
                    .replace(Regex("\\d+(?:[.,]\\d+)?\\s*%"), "")
                    .trim()
            )
        val total = if (knownCurrency) ReceiptFields.money(prices.last().value, currency) else null
        val printed =
            if (knownCurrency) ReceiptFields.money(prices.first().value, currency) else null
        val quantityMatch = Regex("^(.*?)\\s*(?:^|\\s)(\\d{1,3}|[Il])$").matchEntire(prefix)
        val inferred =
            if (
                prices.size == 2 &&
                    !discount &&
                    printed != null &&
                    total != null &&
                    printed.minorUnits > 0L &&
                    total.minorUnits % printed.minorUnits == 0L
            )
                (total.minorUnits / printed.minorUnits).takeIf { it in 1L..999L }?.toInt()
            else null
        val quantity =
            quantityMatch?.groupValues?.get(2)?.let {
                if (it == "I" || it == "l") 1 else it.toInt()
            } ?: inferred ?: return null
        if (quantity !in 1..999) return null
        val name = (descriptionPrefix + " " + (quantityMatch?.groupValues?.get(1) ?: prefix)).trim()
        if (name.length !in 3..200 || name.count(Char::isLetter) < 3 || isCharge(name)) return null
        val unit =
            when {
                total == null -> null
                prices.size == 1 || discount ->
                    total
                        .takeIf { it.minorUnits % quantity == 0L }
                        ?.let { Money(it.minorUnits / quantity, currency) }
                printed != null && printed.minorUnits * quantity == total.minorUnits -> printed
                else -> null
            }
        return ReceiptItem(name, quantity, unit)
    }

    companion object {
        fun isPriceOnly(line: String): Boolean {
            if (!ReceiptFields.amount.containsMatchIn(line)) return false
            return line
                .replace(Regex("\\d+(?:[.,]\\d+)?\\s*%"), "")
                .replace(ReceiptFields.amount, "")
                .replace(Regex("(?i)EUR|USD|GBP|[€$£]"), "")
                .trim()
                .matches(Regex("\\d{1,3}|[Il]"))
        }

        fun matches(header: String): Boolean {
            val description =
                Regex("(?i)descripci[oó]n|description|concepto").find(header)?.range?.first
                    ?: return false
            val quantity =
                Regex("(?i)\\b(?:cant(?:idad)?|qty|quantity|uds?|unid(?:ades)?)\\b")
                    .find(header)
                    ?.range
                    ?.first ?: return false
            return quantity > description
        }

        fun isCharge(line: String) =
            Regex(
                    "(?i)^(?:gastos\\s+de\\s+env[ií]o|portes|shipping|delivery charge|descuento|discount|dto\\b)"
                )
                .containsMatchIn(line)

        fun isContinuation(line: String) =
            line.length in 2..160 &&
                line.any(Char::isLetter) &&
                !isCharge(line) &&
                !Regex(
                        "(?i)^(?:p[aá]gina|page|n[úu]mero|n[º°]|serie|serial|imei|subtotal|referencia|nota|cif|nif|https?|www|fecha|date|pedido|order|factura|invoice|datos|medio|m[eé]todo)\\b"
                    )
                    .containsMatchIn(line)
    }
}
