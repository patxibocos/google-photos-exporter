package io.github.patxibocos.googlephotosgithubexporter

import com.google.photos.library.v1.PhotosLibraryClient
import com.google.photos.library.v1.internal.InternalPhotosLibraryClient
import com.google.photos.library.v1.proto.ListMediaItemsRequest
import com.google.photos.types.proto.MediaItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import org.slf4j.Logger
import java.time.Instant

class Photo(val bytes: ByteArray, val id: String, val name: String, val creationTime: Instant)

class GooglePhotosRepository(
    private val photosLibraryClient: PhotosLibraryClient,
    private val httpClient: HttpClient,
    private val logger: Logger = KotlinLogging.logger {}
) {

    private suspend fun buildPhoto(mediaItem: MediaItem): Photo {
        val creationTime = mediaItem.mediaMetadata.creationTime
        val fullSizeUrl = "${mediaItem.baseUrl}=d"
        val response = httpClient.get(fullSizeUrl)
        if (!response.status.isSuccess()) {
            throw Exception("Could not download photo from Google Photos (status: ${response.status}): ${response.body<String>()}")
        }
        val bytes: ByteArray = response.body()
        val instant = Instant.ofEpochSecond(creationTime.seconds, creationTime.nanos.toLong())
        return Photo(bytes = bytes, id = mediaItem.id, name = mediaItem.filename, creationTime = instant)
    }

    private fun fetchItems(
        client: PhotosLibraryClient,
        nextPageToken: String
    ): InternalPhotosLibraryClient.ListMediaItemsPagedResponse {
        val request = ListMediaItemsRequest.newBuilder().setPageSize(100)
        if (nextPageToken.isNotEmpty()) {
            request.pageToken = nextPageToken
        }
        return client.listMediaItems(request.build())
    }

    fun download(lastPhotoId: String? = null, limit: Int = Int.MAX_VALUE): Flow<Photo> = flow {
        // listMediaItems API doesn't support ordering, so this will start fetching recent pages until:
        //  - lastPhotoId is null -> every page
        //  - lastPhotoId not null -> every page until a page contains the given id
        val mediaItems = ArrayDeque<MediaItem>()
        var nextPageToken = ""
        photosLibraryClient.use { client ->
            while (true) {
                val googlePhotosResponse = fetchItems(client, nextPageToken)
                nextPageToken = googlePhotosResponse.nextPageToken
                val photoItems = googlePhotosResponse.page.response.mediaItemsList.filter {
                    it.mediaMetadata.hasPhoto()
                }
                val newItems = photoItems.takeWhile {
                    it.id != lastPhotoId
                }
                mediaItems.addAll(newItems)
                if (newItems.size != photoItems.size || nextPageToken.isEmpty()) {
                    break
                }
            }
        }
        logger.info("${mediaItems.size} new photos identified")
        mediaItems.takeLast(limit).reversed().forEach {
            emit(buildPhoto(it))
        }
    }
}
