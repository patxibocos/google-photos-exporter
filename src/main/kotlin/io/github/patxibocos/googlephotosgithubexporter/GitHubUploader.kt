package io.github.patxibocos.googlephotosgithubexporter

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import java.time.ZoneOffset
import java.util.*

class GitHubUploader(
    private val accessToken: String,
    private val httpClient: HttpClient,
    repoOwner: String,
    repoName: String,
) {

    @Serializable
    private data class RequestBody(val message: String, val content: String)

    private val basePath = "https://api.github.com/repos/$repoOwner/$repoName/contents"

    private fun pathForPhoto(photo: Photo): String {
        val date = photo.creationTime.atOffset(ZoneOffset.UTC).toLocalDate()
        val year = date.year.toString()
        val month = "%02d".format(date.monthValue)
        val day = "%02d".format(date.dayOfMonth)
        return "$basePath/$year/$month/$day/${photo.name}"
    }

    suspend fun upload(photo: Photo) {
        val base64 = String(Base64.getEncoder().encode(photo.bytes))
        val path = pathForPhoto(photo)
        httpClient.put(path) {
            contentType(ContentType.Application.Json)
            setBody(RequestBody("Upload ${photo.name}", base64))
            headers {
                append(
                    HttpHeaders.Authorization,
                    "Bearer $accessToken"
                )
            }
        }
    }
}
