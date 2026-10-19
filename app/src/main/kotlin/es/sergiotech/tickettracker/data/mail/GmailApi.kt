package es.sergiotech.tickettracker.data.mail

import android.util.Base64
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class MailError {
    AUTHORIZATION,
    PERMISSION,
    API_DISABLED,
    RATE_LIMIT,
    NETWORK,
    TOO_LARGE,
    INVALID,
    CANCELLED,
}

class MailImportException(val reason: MailError) : java.io.IOException(reason.name)

data class MailAttachment(
    val messageId: String,
    val attachmentId: String,
    val name: String,
    val mimeType: String,
    val bytes: Long,
)

data class MailReceipt(
    val id: String,
    val subject: String,
    val sender: String,
    val sentAt: String,
    val attachments: List<MailAttachment>,
)

data class MailPage(val receipts: List<MailReceipt>, val nextPage: String?)

fun interface GmailTransport {
    suspend fun get(path: String, token: String, maxBytes: Int): JSONObject
}

/** Every request stays on the fixed Google API origin. Never follow links from messages. */
class GoogleMailTransport : GmailTransport {
    override suspend fun get(path: String, token: String, maxBytes: Int): JSONObject =
        withContext(Dispatchers.IO) {
            require(path.startsWith("/") && !path.contains("\\") && !path.contains(".."))
            val connection =
                URI("https://gmail.googleapis.com/gmail/v1/users/me$path").toURL().openConnection()
                    as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                if (status != 200) {
                    val body =
                        connection.errorStream
                            ?.use { stream ->
                                val chunk = ByteArray(16 * 1024)
                                val count = stream.read(chunk)
                                if (count > 0) String(chunk, 0, count, Charsets.UTF_8) else ""
                            }
                            .orEmpty()
                    val reason =
                        when {
                            status == 401 -> MailError.AUTHORIZATION
                            body.contains("accessNotConfigured") ||
                                body.contains("SERVICE_DISABLED") ||
                                body.contains("serviceDisabled") -> MailError.API_DISABLED
                            status == 429 ||
                                body.contains("rateLimitExceeded") ||
                                body.contains("quotaExceeded") -> MailError.RATE_LIMIT
                            status == 403 -> MailError.PERMISSION
                            else -> MailError.NETWORK
                        }
                    throw MailImportException(reason)
                }
                val result = ByteArrayOutputStream()
                connection.inputStream.use { stream ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (result.size() + count > maxBytes)
                            throw MailImportException(MailError.TOO_LARGE)
                        result.write(buffer, 0, count)
                    }
                }
                JSONObject(result.toString(Charsets.UTF_8.name()))
            } finally {
                connection.disconnect()
            }
        }
}

class GmailApi(private val transport: GmailTransport = GoogleMailTransport()) {
    suspend fun email(token: String): String =
        transport.get("/profile?fields=emailAddress", token, 32 * 1024).getString("emailAddress")

    suspend fun search(token: String, query: String, nextPage: String? = null): MailPage =
        coroutineScope {
            require(query.length <= 200 && !query.any { it.isISOControl() })
            val filter =
                "has:attachment newer_than:1y ${query.ifBlank { "{ticket factura compra receipt}" }}"
            val page =
                transport.get(
                    "/messages?maxResults=15&fields=messages(id),nextPageToken&q=${encode(filter)}${nextPage?.let { "&pageToken=${encode(it)}" }.orEmpty()}",
                    token,
                    64 * 1024,
                )
            val ids = page.optJSONArray("messages")
            val slots = Semaphore(4)
            val receipts =
                (0 until (ids?.length() ?: 0))
                    .map { index ->
                        async {
                            slots.withPermit {
                                val id = ids!!.getJSONObject(index).getString("id")
                                requireId(id)
                                // The projection deliberately excludes all message body data and
                                // snippets.
                                val bodyFields = "mimeType,filename,body(attachmentId,size)"
                                val nested =
                                    "$bodyFields,parts($bodyFields,parts($bodyFields,parts($bodyFields)))"
                                val json =
                                    transport.get(
                                        "/messages/$id?format=full&fields=${encode("id,payload(headers(name,value),$nested)")}",
                                        token,
                                        256 * 1024,
                                    )
                                decodeMessage(json)
                            }
                        }
                    }
                    .awaitAll()
                    .filter { it.attachments.isNotEmpty() }
            MailPage(receipts, page.optString("nextPageToken").takeIf(String::isNotBlank))
        }

    suspend fun download(token: String, attachment: MailAttachment): ByteArray {
        if (attachment.bytes !in 1..PrivateDocumentStore.MAX_BYTES)
            throw MailImportException(MailError.TOO_LARGE)
        requireId(attachment.messageId)
        requireId(attachment.attachmentId)
        val json =
            transport.get(
                "/messages/${attachment.messageId}/attachments/${attachment.attachmentId}",
                token,
                36 * 1024 * 1024,
            )
        if (json.getLong("size") != attachment.bytes) throw MailImportException(MailError.INVALID)
        val bytes =
            try {
                Base64.decode(
                    json.getString("data"),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                )
            } catch (_: IllegalArgumentException) {
                throw MailImportException(MailError.INVALID)
            }
        if (bytes.size.toLong() != attachment.bytes) throw MailImportException(MailError.INVALID)
        return bytes
    }

    internal fun decodeMessage(json: JSONObject): MailReceipt {
        val id = json.getString("id")
        requireId(id)
        val payload = json.getJSONObject("payload")
        val headers = payload.optJSONArray("headers")
        fun header(name: String): String =
            (0 until (headers?.length() ?: 0))
                .map { headers!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").equals(name, true) }
                ?.optString("value")
                .orEmpty()
                .take(250)
        val attachments = mutableListOf<MailAttachment>()
        fun visit(part: JSONObject, depth: Int) {
            if (depth > 8 || attachments.size >= 50) return
            val mime = part.optString("mimeType").lowercase()
            val name = part.optString("filename").take(180)
            val body = part.optJSONObject("body")
            val attachmentId = body?.optString("attachmentId").orEmpty()
            val supported =
                mime == "application/pdf" ||
                    mime in
                        setOf(
                            "image/jpeg",
                            "image/png",
                            "image/webp",
                            "image/heic",
                            "image/heif",
                            "image/avif",
                        ) ||
                    (mime == "application/octet-stream" &&
                        Regex("(?i)\\.(pdf|jpe?g|png|webp|heic|heif|avif)$").containsMatchIn(name))
            if (name.isNotBlank() && attachmentId.isNotBlank() && supported) {
                requireId(attachmentId)
                attachments += MailAttachment(id, attachmentId, name, mime, body!!.optLong("size"))
            }
            val parts = part.optJSONArray("parts")
            for (index in 0 until minOf(parts?.length() ?: 0, 100)) visit(
                parts!!.getJSONObject(index),
                depth + 1,
            )
        }
        visit(payload, 0)
        return MailReceipt(
            id,
            header("Subject"),
            header("From"),
            header("Date"),
            attachments.distinctBy { it.attachmentId },
        )
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun requireId(value: String) {
        require(value.matches(Regex("[a-zA-Z0-9_-]{1,2048}")))
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    }
}
