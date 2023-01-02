package io.github.patxibocos.googlephotosgithubexporter

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.slf4j.Logger

class OneDriveRepository(
    private val httpClient: HttpClient,
    prefixPath: String,
    private val logger: Logger = KotlinLogging.logger {}
) : ExportRepository {

    @Serializable
    private data class ResponseBody(@SerialName("@microsoft.graph.downloadUrl") val downloadUrl: String)

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
        val response =
            httpClient.put("$basePath/$filePath:/content?@microsoft.graph.conflictBehavior=$conflictBehaviourValue") {
                setBody(data)
            }
        if (response.status == HttpStatusCode.Conflict) {
            logger.warn("File $filePath already exists")
        } else if (!response.status.isSuccess()) {
            throw Exception("OneDrive upload failed: ${response.body<String>()}")
        }
    }
}
