package io.github.patxibocos.googlephotosgithubexporter

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.slf4j.Logger
import java.util.*

class GitHubContentsRepository(
    private val httpClient: HttpClient,
    repoOwner: String,
    repoName: String,
    private val logger: Logger = KotlinLogging.logger {}
) {

    @Serializable
    private data class RequestBody(val message: String, val content: String, val sha: String? = null)

    @Serializable
    private data class ResponseBody(@SerialName("download_url") val downloadUrl: String, val sha: String)

    private val basePath = "https://api.github.com/repos/$repoOwner/$repoName/contents"

    suspend fun get(filePath: String): ByteArray? {
        val response = httpClient.get("$basePath/$filePath") {
            contentType(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            return null
        }
        val fileResponse = httpClient.get(response.body<ResponseBody>().downloadUrl) {
            contentType(ContentType.Application.Json)
        }
        return fileResponse.body()
    }

    suspend fun upload(data: ByteArray, filePath: String, commitMessage: String, overrideContent: Boolean = false) {
        val sha: String? = if (overrideContent) {
            val response = httpClient.get("$basePath/$filePath") {
                contentType(ContentType.Application.Json)
            }
            if (response.status.isSuccess()) {
                response.body<ResponseBody>().sha
            } else {
                null
            }
        } else {
            null
        }
        val base64 = String(Base64.getEncoder().encode(data))
        val response = httpClient.put("$basePath/${filePath.replace(" ", "%20")}") {
            contentType(ContentType.Application.Json)
            setBody(RequestBody(commitMessage, base64, sha))
        }
        if (!response.status.isSuccess()) {
            logger.info(filePath)
            throw Exception("Could not upload content to GitHub: ${response.body<String>()}")
        }
    }
}
