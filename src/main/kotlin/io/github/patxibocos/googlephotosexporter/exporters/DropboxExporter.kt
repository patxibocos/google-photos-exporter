package io.github.patxibocos.googlephotosexporter.exporters

import io.github.patxibocos.googlephotosexporter.requestWithRetry
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.slf4j.Logger

internal class DropboxExporter(
    private val httpClient: HttpClient,
    private val prefixPath: String,
    private val logger: Logger = KotlinLogging.logger {},
) : Exporter {
    private val basePath = "https://content.dropboxapi.com/2"

    // https://www.dropbox.com/developers/documentation/http/documentation#files-upload
    private val maxUploadSize = 150 * 1024 * 1024 // 150 MB
    private val chunkSize = 148 * 1024 * 1024 // 148MB (because it needs to be multiple of 4 MB)

    @Serializable
    private data class DownloadResponse(val error: Error)

    @Serializable
    private data class Error(val path: Path)

    @Serializable
    private data class Path(@SerialName(".tag") val tag: String)

    @Serializable
    private data class UploadSessionResponse(@SerialName("session_id") val sessionId: String)

    override suspend fun get(filePath: String): ByteArray? {
        val response = httpClient.requestWithRetry(
            "$basePath/files/download",
            HttpMethod.Get,
            dontRetryFor = listOf(HttpStatusCode.Conflict),
        ) {
            contentType(ContentType.Application.OctetStream)
            header("Dropbox-API-Arg", """{"path": "/$prefixPath/$filePath"}""")
        }
        if (response.status == HttpStatusCode.Conflict && response.body<DownloadResponse>().error.path.tag == "not_found") {
            return null
        }
        throw Exception("Dropbox download failed: ${response.body<String>()}")
    }

    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        // If data is larger than 150 MB, an upload session must be created
        val response = if (data.size > maxUploadSize) {
            val uploadSessionResponse =
                httpClient.requestWithRetry("$basePath/files/upload_session/start", HttpMethod.Post) {
                    contentType(ContentType.Application.OctetStream)
                }
            val sessionId = uploadSessionResponse.body<UploadSessionResponse>().sessionId
            // Split by chunks of 148 MB
            // Each call must be multiple of 4194304 bytes (except for last)
            // https://www.dropbox.com/developers/documentation/http/documentation#files-upload_session-start
            data.inputStream().use { inputStream ->
                val chunks = inputStream.buffered().iterator().asSequence().chunked(chunkSize)
                val finalOffset = chunks.fold(0) { offset, bytes ->
                    val chunk = bytes.toByteArray()
                    httpClient.requestWithRetry("$basePath/files/upload_session/append_v2", HttpMethod.Post) {
                        contentType(ContentType.Application.OctetStream)
                        header(
                            "Dropbox-API-Arg",
                            """{"close":false,"cursor":{"offset":$offset,"session_id":"$sessionId"}}""",
                        )
                        setBody(chunk)
                    }
                    offset + chunk.size
                }
                httpClient.requestWithRetry("$basePath/files/upload_session/finish", HttpMethod.Post) {
                    contentType(ContentType.Application.OctetStream)
                    header(
                        "Dropbox-API-Arg",
                        """{"cursor":{"offset":$finalOffset,"session_id":"$sessionId"},"commit":{"path":"/$prefixPath/$filePath","strict_conflict":${!overrideContent}}}""",
                    )
                }
            }
        } else {
            httpClient.requestWithRetry("$basePath/files/upload", HttpMethod.Post) {
                contentType(ContentType.Application.OctetStream)
                header("Dropbox-API-Arg", """{"path":"/$prefixPath/$filePath","strict_conflict":${!overrideContent}}""")
                setBody(data)
            }
        }
        if (response.status == HttpStatusCode.Conflict && response.body<DownloadResponse>().error.path.tag == "conflict") {
            logger.warn("File $filePath already exists")
        } else if (!response.status.isSuccess()) {
            throw Exception("Dropbox upload failed: ${response.body<String>()}")
        }
    }
}
