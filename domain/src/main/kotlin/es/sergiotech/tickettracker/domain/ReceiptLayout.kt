package es.sergiotech.tickettracker.domain

import kotlin.math.abs

/**
 * Provider-neutral positions, in one page's coordinate system. Never store mail or OCR tokens here.
 */
data class ReceiptTextBox(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

object ReceiptLayout {
    /** Rebuild physical rows before parsing: OCR block order often separates amount columns. */
    fun readingText(fragments: List<ReceiptTextBox>): String {
        data class Row(val members: MutableList<ReceiptTextBox>) {
            val center
                get() = members.map { (it.top + it.bottom) / 2 }.sorted()[members.size / 2]

            val height
                get() = members.map { it.bottom - it.top }.sorted()[members.size / 2]
        }
        val rows = mutableListOf<Row>()
        for (box in
            fragments
                .filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
                .sortedBy { (it.top + it.bottom) / 2 }) {
            val center = (box.top + box.bottom) / 2
            val row =
                rows
                    .filter {
                        abs(it.center - center) <= minOf(it.height, box.bottom - box.top) * .5f
                    }
                    .minByOrNull { abs(it.center - center) }
            if (row == null) rows += Row(mutableListOf(box)) else row.members += box
        }
        return rows
            .sortedBy { it.center }
            .joinToString("\n") { row ->
                row.members.sortedBy { it.left }.joinToString(" ") { it.text.trim() }
            }
    }
}
