package es.sergiotech.tickettracker.data.cloud

import es.sergiotech.tickettracker.BuildConfig
import es.sergiotech.tickettracker.core.documents.PrivateDocumentStore
import es.sergiotech.tickettracker.domain.CloudOperationException
import es.sergiotech.tickettracker.domain.DocumentVariant
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CloudApiException(val status: Int, val code: String) : CloudOperationException(code)

interface RemoteApi {
    val configured: Boolean

    suspend fun request(
        path: String,
        token: String? = null,
        method: String = "GET",
        body: JSONObject? = null,
    ): JSONObject

    suspend fun upload(
        path: String,
        token: String,
        variant: DocumentVariant,
        store: PrivateDocumentStore,
    )

    suspend fun download(
        path: String,
        token: String,
        variant: DocumentVariant,
        store: PrivateDocumentStore,
    )
}

class CloudflareApi(private val baseUrl: String = BuildConfig.API_BASE_URL) : RemoteApi {
    override val configured: Boolean
        get() = baseUrl.isNotBlank()

    override suspend fun request(
        path: String,
        token: String?,
        method: String,
        body: JSONObject?,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = connection(path, token, method)
            try {
                if (body != null) {
                    val bytes = body.toString().toByteArray(Charsets.UTF_8)
                    require(bytes.size <= 196608)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                response(connection)
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun upload(
        path: String,
        token: String,
        variant: DocumentVariant,
        store: PrivateDocumentStore,
    ) =
        withContext(Dispatchers.IO) {
            check(store.verify(variant))
            val connection = connection(path, token, "PUT")
            try {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", variant.mimeType)
                connection.setFixedLengthStreamingMode(variant.byteSize)
                connection.outputStream.use { output ->
                    store.file(variant).inputStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                        }
                    }
                }
                response(connection)
                Unit
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun download(
        path: String,
        token: String,
        variant: DocumentVariant,
        store: PrivateDocumentStore,
    ) =
        withContext(Dispatchers.IO) {
            val connection = connection(path, token, "GET")
            try {
                if (connection.responseCode !in 200..299) {
                    response(connection)
                    error("Download failed")
                }
                require(connection.getHeaderField("X-Checksum-Sha256") == variant.sha256)
                connection.inputStream.use { store.saveDownloaded(variant, it) }
            } finally {
                connection.disconnect()
            }
        }

    private fun connection(path: String, token: String?, method: String): HttpURLConnection {
        check(configured) { "Cloud API is not configured" }
        require(path.startsWith("/v1/") && !path.contains(".."))
        val uri = URI(baseUrl)
        require(
            uri.scheme == "https" &&
                uri.host != null &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null
        )
        return (URI(baseUrl + path).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }

    private suspend fun response(connection: HttpURLConnection): JSONObject {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val bytes = ByteArrayOutputStream()
        stream?.use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                require(bytes.size() + n <= 6 * 1024 * 1024)
                bytes.write(buffer, 0, n)
            }
        }
        val json = if (bytes.size() == 0) JSONObject() else JSONObject(bytes.toString("UTF-8"))
        if (status !in 200..299)
            throw CloudApiException(status, json.optString("error", "network_error"))
        return json
    }
}
