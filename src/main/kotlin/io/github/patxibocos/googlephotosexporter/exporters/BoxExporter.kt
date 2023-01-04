package io.github.patxibocos.googlephotosexporter.exporters

import com.box.sdk.BoxAPIConnection
import com.box.sdk.BoxAPIResponseException
import com.box.sdk.BoxFile
import com.box.sdk.BoxFolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import org.slf4j.Logger

internal class BoxExporter(
    private val api: BoxAPIConnection,
    private val httpClient: HttpClient,
    private val prefixPath: String,
    private val logger: Logger = KotlinLogging.logger {}
) : Exporter {
    override suspend fun get(filePath: String): ByteArray? {
        val folder = getFolder("$prefixPath/$filePath", false) ?: return null
        val fileName = filePath.split("/").last()
        val file =
            folder.children.filterIsInstance<BoxFile.Info>().find { it.name == fileName }?.resource ?: return null
        val response = httpClient.get(file.downloadURL.toString())
        if (!response.status.isSuccess()) {
            return null
        }
        return response.body()
    }

    private fun getFolder(filePath: String, createIfNotExists: Boolean): BoxFolder? {
        val rootFolder = BoxFolder.getRootFolder(api)
        val pathParts = filePath.split("/").dropLast(1)
        var currentFolder = rootFolder
        pathParts.forEach { part ->
            val subfolder = currentFolder.children.filterIsInstance<BoxFolder.Info>().find { it.name == part }?.resource
            if (currentFolder == null && !createIfNotExists) {
                return null
            }
            currentFolder = subfolder ?: currentFolder.createFolder(part).resource
        }
        return currentFolder
    }

    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        val folder = requireNotNull(getFolder("$prefixPath/$filePath", true))
        val fileName = filePath.split("/").last()
        data.inputStream().use { dataInputStream ->
            try {
                folder.uploadFile(dataInputStream, fileName)
            } catch (e: BoxAPIResponseException) {
                val responseJson = Json.parseToJsonElement(e.response).jsonObject
                if ((responseJson["code"] as? JsonPrimitive)?.contentOrNull == "item_name_in_use") {
                    if (overrideContent) {
                        // Override
                        val fileId =
                            folder.children.filterIsInstance<BoxFile.Info>().find { it.name == fileName }?.resource?.id
                        val boxFile = BoxFile(api, fileId)
                        data.inputStream().use {
                            boxFile.uploadNewVersion(it)
                        }
                    } else {
                        logger.warn("File $filePath already exists")
                    }
                } else {
                    throw e
                }
            }
        }
    }
}
