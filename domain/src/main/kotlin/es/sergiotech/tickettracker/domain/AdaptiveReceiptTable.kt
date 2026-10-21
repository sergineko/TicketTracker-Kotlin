package es.sergiotech.tickettracker.domain

/**
 * Builds a column plan from printed labels. A retailer, sector or document filename is never a
 * rule. Unknown/ambiguous columns fail closed; the original text remains available for review.
 */
internal class AdaptiveReceiptTable private constructor(private val columns: List<Column>) {
    private enum class Column {
        DESCRIPTION,
        QUANTITY,
        UNIT,
        AMOUNT,
        CODE,
        POSITION,
        TAX,
        DISCOUNT,
    }

    val legacyCompatible: Boolean
        get() {
            val description = columns.indexOf(Column.DESCRIPTION)
            val firstPrice = columns.indexOfFirst { it == Column.UNIT || it == Column.AMOUNT }
            return description < firstPrice &&
                (Column.UNIT !in columns ||
                    Column.AMOUNT !in columns ||
                    columns.indexOf(Column.UNIT) < columns.indexOf(Column.AMOUNT))
        }

    fun mayUseLegacy(line: String): Boolean {
        if (!legacyCompatible) return false
        val quantity = columns.indexOf(Column.QUANTITY)
        if (quantity < 0) return true
        val description = columns.indexOf(Column.DESCRIPTION)
        val firstPrice = ReceiptFields.amount.find(line)?.range?.first ?: return true
        val candidate =
            if (quantity < description) line.trim().split(Regex("\\s+")).getOrNull(quantity)
            else line.take(firstPrice).trim().substringAfterLast(' ')
        // A printed fraction is not a missing integer glyph and must not be inferred from prices.
        return candidate?.matches(Regex("\\d+[.,]\\d+")) != true
    }

    fun parse(line: String, currency: String, knownCurrency: Boolean): ReceiptItem? {
        if (line.length > 1000) return null
        val tokens = line.trim().split(Regex("\\s+"))
        if (tokens.size > 80) return null
        val matches = mutableListOf<ReceiptItem>()
        var visits = 0
        fun walk(column: Int, token: Int, cells: Map<Column, String>) {
            if (++visits > 600) return
            if (column == columns.size) {
                if (token == tokens.size) item(cells, currency, knownCurrency)?.let(matches::add)
                return
            }
            val kind = columns[column]
            val remaining = columns.size - column - 1
            val max =
                minOf(tokens.size - token - remaining, if (kind == Column.DESCRIPTION) 50 else 3)
            for (count in 1..max) {
                val value = tokens.subList(token, token + count).joinToString(" ")
                if (accepts(kind, value)) walk(column + 1, token + count, cells + (kind to value))
            }
        }
        walk(0, 0, emptyMap())
        // Different interpretations are not resolved by guessing.
        return matches.distinct().singleOrNull()
    }

    private fun accepts(column: Column, value: String): Boolean =
        when (column) {
            Column.DESCRIPTION ->
                value.length in 3..200 &&
                    value.count(Char::isLetter) >= 3 &&
                    !ReceiptFields.amount.containsMatchIn(value) &&
                    !InvoiceRow.isCharge(value)
            Column.QUANTITY ->
                value.matches(Regex("\\d{1,3}|[Il]")) &&
                    (value == "I" || value == "l" || value.toIntOrNull() in 1..999)
            Column.UNIT,
            Column.AMOUNT -> amount(value) != null
            Column.CODE ->
                value.length in 2..40 &&
                    value.any(Char::isDigit) &&
                    value.matches(Regex("[\\p{L}\\d/_-]+"))
            Column.POSITION -> value.matches(Regex("\\d{1,4}"))
            Column.TAX,
            Column.DISCOUNT -> value.matches(Regex("\\d{1,3}(?:[.,]\\d{1,2})?\\s*%?"))
        }

    private fun amount(value: String): String? {
        val normalized = value.replace(Regex("(?i)EUR|USD|GBP|[€$£]"), "").trim()
        return normalized.takeIf { ReceiptFields.amount.matches(it) }
    }

    private fun item(cells: Map<Column, String>, currency: String, known: Boolean): ReceiptItem? {
        val name = cells[Column.DESCRIPTION] ?: return null
        if (ReceiptFields.dates(name).isNotEmpty() || InvoiceRow.isCharge(name)) return null
        val quantity =
            cells[Column.QUANTITY]?.let { if (it == "I" || it == "l") 1 else it.toInt() } ?: 1
        val unit =
            cells[Column.UNIT]?.let {
                if (known) ReceiptFields.money(amount(it)!!, currency) else null
            }
        val total =
            cells[Column.AMOUNT]?.let {
                if (known) ReceiptFields.money(amount(it)!!, currency) else null
            }
        val price =
            when {
                !known -> null
                unit != null && total != null ->
                    unit.takeIf { it.minorUnits * quantity == total.minorUnits }
                unit != null -> unit
                total != null && total.minorUnits % quantity == 0L ->
                    Money(total.minorUnits / quantity, currency)
                else -> null
            }
        return ReceiptItem(name, quantity, price)
    }

    companion object {
        // Longer alternatives precede their component words.
        private val labels =
            Regex(
                "(?i)\\b(?:n[°ºo.]?\\s*(?:producto|art[ií]culo)|precio\\s+unitario|unit\\s+price|p\\.?\\s*v\\.?\\s*p\\.?\\s*unitario|p\\.?\\s*unit\\.?|line\\s+total|tipo\\s+iva|descripci[oó]n|description|concepto|detalle|servicio|service|art[ií]culo|producto|product|item|cantidad|cant\\.?|cnt\\.?|unidades|uds?\\.?|qty|quantity|importe|amount|total|precio|price|pvp|c[oó]digo|c[oó]d\\.?|referencia|ref\\.?|sku|pos\\.?|iva|vat|tax|descuento|discount|dto\\.?)\\b"
            )

        fun from(header: String): AdaptiveReceiptTable? {
            if (ReceiptFields.amount.containsMatchIn(header)) return null
            val found = labels.findAll(header).toList()
            if (found.isEmpty()) return null
            val residue =
                labels
                    .replace(header, "")
                    .replace(Regex("[\\s.()€$£/%:|_-]|EUR|USD|GBP", RegexOption.IGNORE_CASE), "")
            if (residue.isNotBlank()) return null
            val columns =
                found.map { match ->
                    val value = match.value.lowercase()
                    when {
                        value.matches(Regex("n[°ºo.]?\\s*.*|c[oó]d.*|referencia|ref\\.?|sku")) ->
                            Column.CODE
                        value.startsWith("pos") -> Column.POSITION
                        value.matches(
                            Regex("cantidad|cant\\.?|cnt\\.?|unidades|uds?\\.?|qty|quantity")
                        ) -> Column.QUANTITY
                        value.matches(Regex("tipo\\s+iva|iva|vat|tax")) -> Column.TAX
                        value.matches(Regex("descuento|discount|dto\\.?")) -> Column.DISCOUNT
                        value.matches(Regex("line\\s+total|importe|amount|total")) -> Column.AMOUNT
                        value.matches(Regex("precio.*|unit\\s+price|p\\..*|pvp.*|price")) ->
                            Column.UNIT
                        else -> Column.DESCRIPTION
                    }
                }
            if (
                columns.distinct().size != columns.size ||
                    Column.DESCRIPTION !in columns ||
                    columns.none { it == Column.UNIT || it == Column.AMOUNT }
            )
                return null
            return AdaptiveReceiptTable(columns)
        }
    }
}
