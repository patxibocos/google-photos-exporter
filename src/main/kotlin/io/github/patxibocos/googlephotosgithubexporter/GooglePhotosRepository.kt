package io.github.patxibocos.googlephotosgithubexporter

import com.google.photos.library.v1.PhotosLibraryClient
import com.google.photos.library.v1.internal.InternalPhotosLibraryClient
import com.google.photos.library.v1.proto.ListMediaItemsRequest
import com.google.photos.types.proto.MediaItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

class GooglePhotosRepository(
    private val photosLibraryClient: PhotosLibraryClient,
    private val httpClient: HttpClient
) {

    private suspend fun buildPhoto(mediaItem: MediaItem): Photo {
        val creationTime = mediaItem.mediaMetadata.creationTime
        val fullSizeUrl = "${mediaItem.baseUrl}=d"
        val response = httpClient.get(fullSizeUrl)
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
        mediaItems.takeLast(limit).reversed().forEach {
            emit(buildPhoto(it))
        }
    }
}
