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
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.slf4j.Logger
import java.io.File
import java.util.*

class GitHubContentsRepository(
    private val httpClient: HttpClient,
    repoOwner: String,
    repoName: String,
    maxChunkSizeMBs: Int,
    prefixPath: String,
    private val logger: Logger = KotlinLogging.logger {}
) {
    private val maxSizeInBytes = maxChunkSizeMBs * 1024 * 1024

    @Serializable
    private data class RequestBody(val message: String, val content: String, val sha: String? = null)

    @Serializable
    private data class ResponseBody(@SerialName("download_url") val downloadUrl: String, val sha: String)

    private val basePath = "https://api.github.com/repos/$repoOwner/$repoName/contents/$prefixPath"

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

    private class ZipSplit(val name: String, val data: ByteArray)

    private fun zipAndSplit(name: String, content: ByteArray): List<ZipSplit> {
        val file = File(name).apply {
            createNewFile()
            writeBytes(content)
        }
        val zipFile = ZipFile("$name.zip")
        zipFile.createSplitZipFile(listOf(file), ZipParameters(), true, maxSizeInBytes.toLong())
        val zipSplits = zipFile.splitZipFiles.map {
            ZipSplit(it.name, it.readBytes())
        }
        file.delete()
        zipFile.splitZipFiles.forEach(File::delete)
        return zipSplits
    }

    suspend fun upload(
        data: ByteArray,
        name: String,
        filePath: String,
        commitMessage: String,
        overrideContent: Boolean = false
    ) {
        if (data.size > maxSizeInBytes) {
            val fileName = filePath.split("/").last()
            zipAndSplit(fileName, data).forEach {
                val path = "$filePath/${it.name}"
                upload(it.data, it.name, path, "$commitMessage (split)", overrideContent)
            }
            return
        }
        val sizeInMBs = data.size.toFloat() / 1024 / 1024
        logger.info("Uploading $name (${"%.2f".format(sizeInMBs)} MBs)")
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
            logger.warn("Could not upload content to GitHub ($filePath), retrying...")
            upload(data, name, filePath, commitMessage, overrideContent)
        }
    }
}
