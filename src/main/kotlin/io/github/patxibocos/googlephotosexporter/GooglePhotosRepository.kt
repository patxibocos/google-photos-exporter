package io.github.patxibocos.googlephotosexporter

import com.google.photos.library.v1.PhotosLibraryClient
import com.google.photos.library.v1.internal.InternalPhotosLibraryClient
import com.google.photos.library.v1.proto.ListMediaItemsRequest
import com.google.photos.types.proto.MediaItem
import com.google.photos.types.proto.VideoProcessingStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import org.slf4j.Logger
import java.time.Instant

class Item(val bytes: ByteArray, val id: String, val name: String, val creationTime: Instant)

enum class ItemType {
    PHOTO, VIDEO
}

object GooglePhotosItemForbidden : Exception()

private fun MediaItem.isNotReady(): Boolean =
    this.mediaMetadata.hasVideo() && this.mediaMetadata.video.status != VideoProcessingStatus.READY

class GooglePhotosRepository(
    private val photosLibraryClient: PhotosLibraryClient,
    private val httpClient: HttpClient,
    private val logger: Logger = KotlinLogging.logger {}
) {

    private suspend fun buildItem(mediaItem: MediaItem): Item {
        val creationTime = mediaItem.mediaMetadata.creationTime
        val suffix = when {
            mediaItem.mediaMetadata.hasVideo() -> "dv"
            mediaItem.mediaMetadata.hasPhoto() -> "d"
            else -> IllegalStateException("Unknown type, is this MediaItem a video or a photo?")
        }
        val fullSizeUrl = "${mediaItem.baseUrl}=$suffix"
        val response = httpClient.get(fullSizeUrl)
        if (response.status == HttpStatusCode.Forbidden) {
            throw GooglePhotosItemForbidden
        }
        if (!response.status.isSuccess()) {
            throw Exception("Could not download photo from Google Photos (status: ${response.status}): ${response.body<String>()}")
        }
        val bytes: ByteArray = response.body()
        val instant = Instant.ofEpochSecond(creationTime.seconds, creationTime.nanos.toLong())
        return Item(bytes = bytes, id = mediaItem.id, name = mediaItem.filename, creationTime = instant)
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

    private fun mediaItemFilter(itemTypes: List<ItemType>): (MediaItem) -> Boolean = { mediaItem: MediaItem ->
        itemTypes.any {
            when (it) {
                ItemType.PHOTO -> mediaItem.mediaMetadata.hasPhoto()
                ItemType.VIDEO -> mediaItem.mediaMetadata.hasVideo()
            }
        }
    }

    fun download(itemTypes: List<ItemType>, lastItemId: String? = null): Flow<Item> = flow {
        fun getItems(lastItemId: String?): List<MediaItem> {
            // listMediaItems API doesn't support ordering, so this will start fetching recent pages until:
            //  - lastItemId is null -> every page
            //  - lastItemId not null -> every page until a page contains the given id
            val mediaItems = ArrayDeque<MediaItem>()
            var nextPageToken = ""
            photosLibraryClient.use { client ->
                while (true) {
                    val googlePhotosResponse = fetchItems(client, nextPageToken)
                    nextPageToken = googlePhotosResponse.nextPageToken
                    val items = googlePhotosResponse.page.response.mediaItemsList
                    val newItems = items.takeWhile {
                        it.id != lastItemId
                    }
                    val newFilteredItems = newItems.filter { mediaItemFilter(itemTypes)(it) }
                    mediaItems.addAll(newFilteredItems)
                    logger.info("Collecting items: ${mediaItems.size}")
                    if (nextPageToken.isEmpty() || newItems.size != items.size) {
                        break
                    }
                }
            }
            return mediaItems.reversed()
        }

        var finished = false
        var lastEmittedId = lastItemId
        while (!finished) {
            val mediaItems = getItems(lastEmittedId)
            logger.info("${mediaItems.size} new items identified")
            try {
                mediaItems.forEach { mediaItem ->
                    if (mediaItem.isNotReady()) {
                        logger.warn("Item ${mediaItem.filename} is not ready yet, stopping here")
                        return@flow
                    }
                    val item = buildItem(mediaItem)
                    emit(item)
                    lastEmittedId = mediaItem.id
                }
                finished = true
            } catch (e: GooglePhotosItemForbidden) {
                logger.warn("Google Photos returned 403 Forbidden, retrying")
            }
        }
    }
}
