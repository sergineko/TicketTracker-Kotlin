package es.sergiotech.tickettracker

import android.util.Base64
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.data.mail.*
import java.net.URLDecoder
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GmailImportTest {
    private fun message(id: String = "msg1") =
        JSONObject(
            """{"id":"$id","payload":{"mimeType":"multipart/mixed","headers":[{"name":"Subject","value":"Ticket de ejemplo"},{"name":"From","value":"tienda@example.test"}],"parts":[{"mimeType":"text/html","body":{"data":"ignored"}},{"mimeType":"application/pdf","filename":"../../ticket.pdf","body":{"attachmentId":"pdf1","size":4}},{"mimeType":"image/png","filename":"imagen.png","body":{"attachmentId":"img1","size":4}}]}}"""
        )

    @Test
    fun searchUsesHeadersAndAttachmentMetadataWithoutDownloadingBodiesOrFiles() = runTest {
        val paths = mutableListOf<String>()
        val api = GmailApi { path, _, _ ->
            paths += path
            when {
                path.startsWith("/profile") ->
                    JSONObject("""{"emailAddress":"lector@example.test"}""")
                path.startsWith("/messages?") ->
                    JSONObject("""{"messages":[{"id":"msg1"}],"nextPageToken":"page2"}""")
                else -> message()
            }
        }
        assertEquals("lector@example.test", api.email("fixture-access"))
        val page = api.search("fixture-access", "from:tienda", "page1")
        assertEquals(2, page.receipts.single().attachments.size)
        assertEquals("page2", page.nextPage)
        val query = URLDecoder.decode(paths[1], "UTF-8")
        assertTrue(query.contains("has:attachment newer_than:1y from:tienda"))
        assertTrue(query.contains("pageToken=page1"))
        val projection = URLDecoder.decode(paths[2], "UTF-8")
        assertTrue(projection.contains("body(attachmentId,size)"))
        assertFalse(projection.contains("data"))
        assertFalse(projection.contains("snippet"))
        assertEquals(3, paths.size)
    }

    @Test
    fun onlyTheSelectedAttachmentIsDownloadedAndItsSizeIsVerified() = runTest {
        val requests = mutableListOf<String>()
        val bytes = byteArrayOf(1, 2, 3, 4)
        val api = GmailApi { path, _, limit ->
            requests += path
            assertEquals(36 * 1024 * 1024, limit)
            JSONObject()
                .put("size", 4)
                .put("data", Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP))
        }
        val chosen = api.decodeMessage(message()).attachments.first()
        assertArrayEquals(bytes, api.download("fixture-access", chosen))
        assertEquals(listOf("/messages/msg1/attachments/pdf1"), requests)
        val invalid = GmailApi { _, _, _ -> JSONObject("""{"size":4,"data":"AQI"}""") }
        try {
            invalid.download("fixture-access", chosen)
            fail("Truncated attachment accepted")
        } catch (e: MailImportException) {
            assertEquals(MailError.INVALID, e.reason)
        }
    }

    @Test
    fun oversizedAttachmentsAreRejectedBeforeAnyRequestAndLinksAreNotAttachments() = runTest {
        val api = GmailApi { _, _, _ ->
            fail("Unexpected download")
            JSONObject()
        }
        try {
            api.download(
                "fixture-access",
                MailAttachment(
                    "msg1",
                    "pdf1",
                    "ticket.pdf",
                    "application/pdf",
                    PrivateDocumentStore.MAX_BYTES + 1,
                ),
            )
            fail("Oversize accepted")
        } catch (e: MailImportException) {
            assertEquals(MailError.TOO_LARGE, e.reason)
        }
        val links =
            JSONObject(
                """{"id":"msg1","payload":{"mimeType":"text/html","body":{"data":"link"}}}"""
            )
        assertTrue(api.decodeMessage(links).attachments.isEmpty())
    }

    @Test
    fun authorizationErrorsDoNotBecomeFakeReceipts() = runTest {
        val api = GmailApi { _, _, _ -> throw MailImportException(MailError.AUTHORIZATION) }
        try {
            api.search("fixture-access", "")
            fail("Missing authorization accepted")
        } catch (e: MailImportException) {
            assertEquals(MailError.AUTHORIZATION, e.reason)
        }
    }
}
