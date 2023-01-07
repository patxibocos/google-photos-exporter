package io.github.patxibocos.googlephotosexporter.exporters

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.slf4j.Logger

internal class BoxExporter(
    private val httpClient: HttpClient,
    private val prefixPath: String,
    private val logger: Logger = KotlinLogging.logger {},
) : Exporter {
    private val foldersPath = "https://api.box.com/2.0"
    private val filesPath = "https://upload.box.com/api/2.0"

    private val foldersCache = mutableMapOf<String, Folder?>()

    override suspend fun get(filePath: String): ByteArray? {
        val folder = getFolderForPath("$prefixPath/$filePath", false) ?: return null
        val fileName = filePath.split("/").last()
        val file = folder.itemCollection.entries.find { it.name == fileName && it.type == "file" } ?: return null
        val response = httpClient.get("$filesPath/files/${file.id}/content")
        if (!response.status.isSuccess()) {
            throw Exception("Failed to get file $filePath: ${response.body<String>()}")
        }
        return response.body()
    }

    @Serializable
    private data class UploadResponse(
        val code: String? = null,
        @SerialName("context_info") val contextInfo: ContextInfo? = null,
    )

    @Serializable
    private data class ContextInfo(val conflicts: Conflicts)

    @Serializable
    private data class Conflicts(val id: String)

    @Serializable
    private data class Folder(val id: String, @SerialName("item_collection") val itemCollection: ItemCollection)

    @Serializable
    private data class ItemCollection(val entries: List<Entry>)

    @Serializable
    private data class Entry(val id: String, val name: String, val type: String)

    private suspend fun getFolderForPath(filePath: String, createIfNotExists: Boolean): Folder? {
        suspend fun createFolder(parentId: String, name: String): Folder {
            return httpClient.post("$foldersPath/folders") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"$name","parent":{"id":"$parentId"}}""")
            }.body()
        }

        suspend fun getFolder(id: String): Folder {
            return httpClient.get("$foldersPath/folders/$id") {
                contentType(ContentType.Application.Json)
            }.body()
        }

        val folderPath = filePath.split("/").dropLast(1).joinToString("/")
        if (foldersCache.containsKey(filePath)) {
            return foldersCache[filePath]
        }
        val pathParts = folderPath.split("/")
        var currentFolder = getFolder("0")
        pathParts.forEach { part ->
            val folder = currentFolder.itemCollection.entries.find { it.name == part && it.type == "folder" }
            if (folder == null && !createIfNotExists) {
                return null
            }
            currentFolder = folder?.let { getFolder(it.id) } ?: createFolder(currentFolder.id, part)
        }
        foldersCache[folderPath] = currentFolder
        return currentFolder
    }

    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        suspend fun uploadFile(path: String, folderId: String, fileName: String): HttpResponse {
            return httpClient.post(path) {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("attributes", """{"name":"$fileName","parent":{"id":"$folderId"}}""")
                            append(
                                "file",
                                data,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "application/octet-stream")
                                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                },
                            )
                        },
                    ),
                )
            }
        }

        // TODO Use chunk upload API for files larger than 50 MB?
        // https://developer.box.com/reference/post-files-content/
        val folder = requireNotNull(getFolderForPath("$prefixPath/$filePath", true))
        val fileName = filePath.split("/").last()
        val response = uploadFile("$filesPath/files/content", folder.id, fileName)
        if (response.status == HttpStatusCode.Conflict && response.body<UploadResponse>().code == "item_name_in_use") {
            // TODO Check if should override
            if (overrideContent) {
                val fileId = response.body<UploadResponse>().contextInfo?.conflicts?.id
                val uploadVersionResponse = uploadFile("$filesPath/files/$fileId/content", folder.id, fileName)
                if (!uploadVersionResponse.status.isSuccess()) {
                    throw Exception("Box upload (version) failed: ${response.body<String>()}")
                }
            } else {
                logger.warn("File $filePath already exists")
            }
        } else if (!response.status.isSuccess()) {
            throw Exception("Box upload failed: ${response.body<String>()}")
        }
    }
}
