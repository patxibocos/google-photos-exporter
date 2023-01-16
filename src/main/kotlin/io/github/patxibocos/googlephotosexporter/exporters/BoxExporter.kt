package io.github.patxibocos.googlephotosexporter.exporters

import io.github.patxibocos.googlephotosexporter.requestWithRetry
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.slf4j.Logger
import java.security.MessageDigest
import java.util.*
import kotlin.collections.set

internal class BoxExporter(
    private val httpClient: HttpClient,
    private val prefixPath: String,
    private val logger: Logger = KotlinLogging.logger {},
) : Exporter {
    private val foldersPath = "https://api.box.com/2.0"
    private val filesPath = "https://upload.box.com/api/2.0"

    private val foldersCache = mutableMapOf<String, Folder>()
    private val maxUploadSize = 50 * 1024 * 1024 // 50 MB

    override suspend fun get(filePath: String): ByteArray? {
        val folder = getFolderForPath("$prefixPath/$filePath", false) ?: return null
        val fileName = filePath.split("/").last()
        val file = folder.itemCollection.entries.find { it.name == fileName && it.type == "file" } ?: return null
        val response = httpClient.requestWithRetry("$foldersPath/files/${file.id}/content", HttpMethod.Get)
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
    private data class ItemCollection(val entries: MutableList<Entry>)

    @Serializable
    private data class Entry(val id: String, val name: String, val type: String)

    @Serializable
    private data class UploadSessionResponse(
        val id: String,
        @SerialName("part_size") val partSize: Int,
        @SerialName("total_parts") val totalParts: Int,
        @SerialName("session_endpoints") val sessionEndpoints: SessionEndpoints,
    )

    @Serializable
    private data class UploadPartResponse(val part: Part)

    @Serializable
    private data class Part(
        @SerialName("part_id") val partId: String,
        val offset: Int,
        val size: Int,
        val sha1: String,
    )

    @Serializable
    private data class SessionEndpoints(@SerialName("upload_part") val uploadPart: String, val commit: String)

    private suspend fun getFolderForPath(filePath: String, createIfNotExists: Boolean): Folder? {
        suspend fun createFolder(parentFolder: Folder, name: String): Folder {
            return httpClient.requestWithRetry("$foldersPath/folders", HttpMethod.Post) {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"$name","parent":{"id":"${parentFolder.id}"}}""")
            }.body<Folder>().also {
                foldersCache[it.id] = it
                parentFolder.itemCollection.entries.add(Entry(it.id, name, "folder"))
            }
        }

        suspend fun getFolder(id: String): Folder {
            return foldersCache[id] ?: httpClient.requestWithRetry("$foldersPath/folders/$id", HttpMethod.Get) {
                contentType(ContentType.Application.Json)
            }.body<Folder>().also {
                foldersCache[id] = it
            }
        }

        val folderPath = filePath.split("/").dropLast(1).joinToString("/")
        val pathParts = folderPath.split("/")
        var currentFolder = getFolder("0")
        pathParts.forEach { part ->
            val folder = currentFolder.itemCollection.entries.find { it.name == part && it.type == "folder" }
            if (folder == null && !createIfNotExists) {
                return null
            }
            currentFolder = folder?.let { getFolder(it.id) } ?: createFolder(currentFolder, part)
        }
        return currentFolder
    }

    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        suspend fun uploadFile(path: String, folderId: String, fileName: String): HttpResponse {
            return httpClient.requestWithRetry(path, HttpMethod.Post, dontRetryFor = listOf(HttpStatusCode.Conflict)) {
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

        suspend fun uploadSingle(folder: Folder, fileName: String) {
            val response = uploadFile("$filesPath/files/content", folder.id, fileName)
            if (response.status == HttpStatusCode.Conflict && response.body<UploadResponse>().code == "item_name_in_use") {
                if (overrideContent) {
                    val fileId = response.body<UploadResponse>().contextInfo?.conflicts?.id
                    uploadFile("$filesPath/files/$fileId/content", folder.id, fileName)
                } else {
                    logger.warn("File $filePath already exists")
                }
            } else if (!response.status.isSuccess()) {
                throw Exception("Box upload failed: ${response.body<String>()}")
            }
        }

        suspend fun uploadChunked(folder: Folder, fileName: String) {
            // Create upload session ->  POST /files/upload_sessions
            // If conflict && overrideContent -> POST /files/:id/upload_sessions
            // Upload each of the parts
            var uploadSessionResponse = httpClient.requestWithRetry(
                "$filesPath/files/upload_sessions",
                HttpMethod.Post,
                dontRetryFor = listOf(HttpStatusCode.Conflict),
            ) {
                contentType(ContentType.Application.Json)
                setBody("""{"file_name":"$fileName","folder_id":"${folder.id}","file_size":${data.size}}""")
            }
            if (uploadSessionResponse.status == HttpStatusCode.Conflict) {
                if (!overrideContent) {
                    logger.warn("File $filePath already exists")
                    return
                }
                val fileId = uploadSessionResponse.body<UploadResponse>().contextInfo?.conflicts?.id
                uploadSessionResponse =
                    httpClient.requestWithRetry("$filesPath/files/$fileId/upload_sessions", HttpMethod.Post) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"file_size":${data.size}}""")
                    }
            }
            val body = uploadSessionResponse.body<UploadSessionResponse>()
            data.inputStream().use { inputStream ->
                val chunks = inputStream.buffered().iterator().asSequence().chunked(body.partSize)
                var offset = 0
                val partResponses = chunks.toList().map { bytes ->
                    val chunk = bytes.toByteArray()
                    val sha = Base64.getEncoder().encode(MessageDigest.getInstance("SHA-1").digest(chunk))
                        .toString(Charsets.UTF_8)
                    val response = httpClient.requestWithRetry(body.sessionEndpoints.uploadPart, HttpMethod.Put) {
                        contentType(ContentType.Application.OctetStream)
                        setBody(chunk)
                        header("Digest", "sha=$sha")
                        header("Content-Range", "bytes $offset-${offset + chunk.size - 1}/${data.size}")
                    }
                    offset += chunk.size
                    response.body<UploadPartResponse>()
                }

                val sha = Base64.getEncoder().encode(MessageDigest.getInstance("SHA-1").digest(data))
                    .toString(Charsets.UTF_8)
                val requestBody =
                    """{"parts":[${partResponses.joinToString(",") { """{"part_id":"${it.part.partId}","offset":${it.part.offset},"size":${it.part.size},"sha1":"${it.part.sha1}"}""" }}]}"""
                httpClient.requestWithRetry(body.sessionEndpoints.commit, HttpMethod.Post) {
                    header("Digest", "sha=$sha")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            }
        }

        val folder = requireNotNull(getFolderForPath("$prefixPath/$filePath", true))
        val fileName = filePath.split("/").last()
        // Use chunk upload API for files larger than 50 MB
        // https://developer.box.com/reference/post-files-content/
        if (data.size > maxUploadSize) {
            uploadChunked(folder, fileName)
        } else {
            uploadSingle(folder, fileName)
        }
    }
}
