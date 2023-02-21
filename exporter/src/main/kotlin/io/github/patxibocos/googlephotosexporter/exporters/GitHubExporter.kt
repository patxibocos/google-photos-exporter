package io.github.patxibocos.googlephotosexporter.exporters

import io.github.patxibocos.googlephotosexporter.requestWithRetry
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.slf4j.Logger
import java.util.*

internal class GitHubExporter(
    private val httpClient: HttpClient,
    repoOwner: String,
    repoName: String,
    prefixPath: String,
    private val logger: Logger = KotlinLogging.logger {},
) : Exporter {

    @Serializable
    private data class RequestBody(val message: String, val content: String, val sha: String? = null)

    @Serializable
    private data class ResponseBody(@SerialName("download_url") val downloadUrl: String, val sha: String)

    private val basePath = "https://api.github.com/repos/$repoOwner/$repoName/contents/$prefixPath"

    override suspend fun get(filePath: String): ByteArray? {
        val response = httpClient.requestWithRetry(
            "$basePath/$filePath",
            HttpMethod.Get,
            dontRetryFor = listOf(HttpStatusCode.NotFound),
        ) {
            contentType(ContentType.Application.Json)
        }
        if (response.status == HttpStatusCode.NotFound) {
            return null
        }
        val fileResponse = httpClient.requestWithRetry(response.body<ResponseBody>().downloadUrl, HttpMethod.Get) {
            contentType(ContentType.Application.Json)
        }
        return fileResponse.body()
    }

    override suspend fun upload(
        data: ByteArray,
        name: String,
        filePath: String,
        overrideContent: Boolean,
    ) {
        val commitMessage = "Upload $name"
        val sha: String? = if (overrideContent) {
            val response = httpClient.requestWithRetry(
                "$basePath/$filePath",
                HttpMethod.Get,
                dontRetryFor = listOf(HttpStatusCode.NotFound),
            ) {
                contentType(ContentType.Application.Json)
            }
            if (response.status == HttpStatusCode.Companion.NotFound) {
                null
            } else {
                response.body<ResponseBody>().sha
            }
        } else {
            null
        }
        val base64 = String(Base64.getEncoder().encode(data))
        val response = httpClient.requestWithRetry(
            "$basePath/${filePath.replace(" ", "%20")}",
            HttpMethod.Put,
            dontRetryFor = listOf(HttpStatusCode.UnprocessableEntity),
        ) {
            contentType(ContentType.Application.Json)
            setBody(RequestBody(commitMessage, base64, sha))
        }
        if (response.status == HttpStatusCode.UnprocessableEntity) {
            logger.warn("File $filePath already exists")
        }
    }
}
