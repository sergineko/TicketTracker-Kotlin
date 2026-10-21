package es.sergiotech.tickettracker.ui

import es.sergiotech.tickettracker.domain.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal enum class DraftField {
    MERCHANT,
    DATE,
    TOTAL,
    DELIVERY,
    NAME,
    QUANTITY,
    PRICE,
    WARRANTY,
    RETURN,
}

internal data class DraftProblem(val field: DraftField, val line: Int? = null) {
    val error: EditorError
        get() =
            when (this.field) {
                DraftField.MERCHANT -> EditorError.MERCHANT
                DraftField.DATE -> EditorError.DATE
                DraftField.TOTAL -> EditorError.TOTAL
                DraftField.DELIVERY -> EditorError.DELIVERY
                DraftField.NAME,
                DraftField.QUANTITY,
                DraftField.PRICE -> EditorError.PRODUCT
                DraftField.WARRANTY,
                DraftField.RETURN -> EditorError.DEADLINE
            }
}

internal fun editorDate(value: String): LocalDate? {
    val normalized = value.trim()
    return runCatching {
            if (normalized.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) LocalDate.parse(normalized)
            else
                LocalDate.parse(
                    normalized.replace('.', '/'),
                    DateTimeFormatter.ofPattern("dd/MM/uuuu")
                        .withResolverStyle(ResolverStyle.STRICT),
                )
        }
        .getOrNull()
}

internal fun EditorState.problems(): List<DraftProblem> = buildList {
    if (merchant.isBlank() || merchant.length > 200) add(DraftProblem(DraftField.MERCHANT))
    val purchased = editorDate(date)
    if (purchased == null) add(DraftProblem(DraftField.DATE))
    if (Money.parse(total, currency) == null) add(DraftProblem(DraftField.TOTAL))
    if (
        delivered.isNotBlank() &&
            (editorDate(delivered) == null ||
                purchased?.let { editorDate(delivered)!! < it } == true)
    )
        add(DraftProblem(DraftField.DELIVERY))
    lines.forEachIndexed { i, line ->
        if (line.name.isBlank() || line.name.length > 200) add(DraftProblem(DraftField.NAME, i))
        if (line.quantity.toIntOrNull() !in 1..999) add(DraftProblem(DraftField.QUANTITY, i))
        if (line.price.isNotBlank() && Money.parse(line.price, currency) == null)
            add(DraftProblem(DraftField.PRICE, i))
        if (line.warrantyEnd.isNotBlank() && editorDate(line.warrantyEnd) == null)
            add(DraftProblem(DraftField.WARRANTY, i))
        if (line.returnEnd.isNotBlank() && editorDate(line.returnEnd) == null)
            add(DraftProblem(DraftField.RETURN, i))
    }
}

internal fun EditorState.lineSum(): Money? =
    runCatching {
            if (lines.isEmpty()) return null
            var sum = 0L
            for (line in lines) {
                val price = Money.parse(line.price, currency) ?: return null
                val quantity = line.quantity.toIntOrNull()?.takeIf { it in 1..999 } ?: return null
                sum = Math.addExact(sum, Math.multiplyExact(price.minorUnits, quantity.toLong()))
            }
            Money(sum, currency)
        }
        .getOrNull()
