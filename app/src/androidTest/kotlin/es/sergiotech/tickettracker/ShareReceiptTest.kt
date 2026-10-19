package es.sergiotech.tickettracker

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import es.sergiotech.tickettracker.data.sharedReceipt
import org.junit.Assert.*
import org.junit.Test

class ShareReceiptTest {
    @Test
    fun streamAndClipDataAreDeduplicatedAndRemoteLinksAreNotFetched() {
        val content = Uri.parse("content://fixture/ticket.pdf")
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, content)
        intent.clipData =
            ClipData.newRawUri("ticket", content).apply {
                addItem(ClipData.Item(Uri.parse("https://example.test/ticket.pdf")))
            }
        assertEquals(listOf(content), sharedReceipt(intent).uris)
        assertTrue(
            sharedReceipt(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.test/ticket.pdf")))
                .uris
                .isEmpty()
        )
    }

    @Test
    fun multipleAttachmentsAndOpenWithRetainOnlyContentUris() {
        val files = arrayListOf(Uri.parse("content://fixture/1"), Uri.parse("content://fixture/2"))
        assertEquals(
            files,
            sharedReceipt(
                    Intent(Intent.ACTION_SEND_MULTIPLE)
                        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)
                )
                .uris,
        )
        assertEquals(listOf(files[0]), sharedReceipt(Intent(Intent.ACTION_VIEW, files[0])).uris)
    }

    @Test
    fun selectedPlainTextNeedsNoFabricatedFileAndIsBounded() {
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "TOTAL 22,07")
        assertEquals("TOTAL 22,07", sharedReceipt(intent).text)
        assertTrue(sharedReceipt(intent).uris.isEmpty())
        intent.putExtra(Intent.EXTRA_TEXT, "a".repeat(100_001))
        assertEquals(100_000, sharedReceipt(intent).text?.length)
        intent.action = Intent.ACTION_MAIN
        assertNull(sharedReceipt(intent).text)
    }
}
