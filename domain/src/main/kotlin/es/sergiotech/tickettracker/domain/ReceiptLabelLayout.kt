package es.sergiotech.tickettracker.domain

import java.text.Normalizer

/**
 * Reflow nearby label/value cells without mixing adjacent customer, invoice and delivery columns.
 */
object ReceiptLabelLayout {
    private val labels =
        Regex(
            "(?i)fecha\\s+(?:de\\s+)?(?:factura|compra|emisi[oó]n|venta|pago|entrega)|invoice\\s+(?:date|number)|purchase\\s+date|delivery\\s+date|n[úu]mero\\s+de\\s+factura|pedido(?:\\s+online)?(?:\\s+n[úu]mero)?|order\\s+number|datos\\s+de\\s+cliente|referencia|m[eé]todo\\s+de\\s+pago|medio\\s+de\\s+entrega|n[úu]mero\\s+de\\s+identificaci[oó]n\\s+fiscal"
        )

    private data class Anchor(
        val text: String,
        val left: Float,
        val right: Float,
        val bottom: Float,
        val height: Float,
    )

    fun readingText(fragments: List<ReceiptTextBox>): String {
        val rows = ReceiptLayout.rows(fragments)
        val result = mutableListOf<String>()
        rows.forEachIndexed { index, row ->
            val words = row.map { Normalizer.normalize(it.text, Normalizer.Form.NFKC) }
            val ranges = mutableListOf<IntRange>()
            var offset = 0
            words.forEach { word ->
                ranges += offset until offset + word.length
                offset += word.length + 1
            }
            val text = words.joinToString(" ")
            val anchors =
                labels
                    .findAll(text)
                    .mapNotNull { match ->
                        val boxes =
                            row.filterIndexed { wordIndex, _ ->
                                ranges[wordIndex].first <= match.range.last &&
                                    ranges[wordIndex].last >= match.range.first
                            }
                        if (boxes.isEmpty()) null
                        else
                            Anchor(
                                match.value,
                                boxes.minOf { it.left },
                                boxes.maxOf { it.right },
                                boxes.maxOf { it.bottom },
                                boxes.maxOf { it.bottom - it.top },
                            )
                    }
                    .sortedBy { it.left }
                    .toList()
            anchors.forEachIndexed anchorLoop@{ anchorIndex, anchor ->
                val dateLabel = Regex("(?i)fecha|date$").containsMatchIn(anchor.text)
                val referenceLabel =
                    Regex("(?i)^(?:pedido|order|n[úu]mero de factura|invoice number)")
                        .containsMatchIn(anchor.text)
                if (!dateLabel && !referenceLabel) return@anchorLoop
                val left = anchor.left - anchor.height * .5f
                val right =
                    anchors.getOrNull(anchorIndex + 1)?.left?.minus(anchor.height * .3f)
                        ?: Float.MAX_VALUE
                fun cell(boxes: List<ReceiptTextBox>, inline: Boolean) =
                    boxes
                        .filter {
                            val center = (it.left + it.right) / 2
                            center >= left && center < right && (!inline || it.left >= anchor.right)
                        }
                        .joinToString(" ") { it.text.trim() }
                var value = cell(row, true)
                if (value.isBlank()) {
                    value =
                        rows
                            .drop(index + 1)
                            .takeWhile { next ->
                                next.minOf { it.top } - anchor.bottom <= anchor.height * 4f
                            }
                            .map { next -> cell(next, false) }
                            .firstOrNull(String::isNotBlank)
                            .orEmpty()
                }
                val normalized =
                    if (dateLabel) ReceiptFields.dates(value).singleOrNull()?.toString()
                    else
                        value.takeIf {
                            it.length in 3..100 &&
                                it.any(Char::isDigit) &&
                                ReceiptFields.dates(it).isEmpty() &&
                                it.matches(Regex("[\\p{L}\\d][\\p{L}\\d/_-]*"))
                        }
                if (normalized != null) result += "${anchor.text}: $normalized"
            }
        }
        return result.distinct().joinToString("\n")
    }
}
