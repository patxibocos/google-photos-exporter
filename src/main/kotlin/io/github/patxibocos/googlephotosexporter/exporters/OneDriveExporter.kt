package io.github.patxibocos.googlephotosexporter.exporters

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.slf4j.Logger

internal class OneDriveExporter(
    private val httpClient: HttpClient,
    prefixPath: String,
    private val logger: Logger = KotlinLogging.logger {},
) : Exporter {
    // https://learn.microsoft.com/en-us/graph/api/driveitem-put-content
    private val maxUploadSize = 4 * 1024 * 1024 // 4 MB

    @Serializable
    private data class ResponseBody(@SerialName("@microsoft.graph.downloadUrl") val downloadUrl: String)

    @Serializable
    private data class UploadSessionRequestBody(
        val item: Item,
    ) {
        @Serializable
        data class Item(
            @SerialName("@microsoft.graph.conflictBehavior") val conflictBehavior: String,
            @SerialName("fileSize") val fileSize: Int,
        )
    }

    @Serializable
    private data class UploadSessionResponseBody(@SerialName("uploadUrl") val uploadUrl: String)

    private val basePath = "https://graph.microsoft.com/v1.0/me/drive/root:/$prefixPath"
    override suspend fun get(filePath: String): ByteArray? {
        val response = httpClient.get("$basePath/$filePath")
        if (!response.status.isSuccess()) {
            return null
        }
        val fileResponse = httpClient.get(response.body<ResponseBody>().downloadUrl)
        return fileResponse.body()
    }

    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        val conflictBehaviourValue = if (overrideContent) "replace" else "fail"
        // If data is larger than 4 MB, an upload session must be created
        val response = if (data.size > maxUploadSize) {
            val sessionResponse = httpClient.post("$basePath/$filePath:/createUploadSession") {
                contentType(ContentType.Application.Json)
                setBody(UploadSessionRequestBody(UploadSessionRequestBody.Item(conflictBehaviourValue, data.size)))
            }
            if (!sessionResponse.status.isSuccess()) {
                sessionResponse
            } else {
                val uploadUrl = sessionResponse.body<UploadSessionResponseBody>().uploadUrl
                httpClient.put(uploadUrl) {
                    setBody(data)
                }
            }
        } else {
            httpClient.put("$basePath/$filePath:/content?@microsoft.graph.conflictBehavior=$conflictBehaviourValue") {
                setBody(data)
            }
        }
        if (response.status == HttpStatusCode.Conflict) {
            logger.warn("File $filePath already exists")
        } else if (!response.status.isSuccess()) {
            throw Exception("OneDrive upload failed: ${response.body<String>()}")
        }
    }
}
