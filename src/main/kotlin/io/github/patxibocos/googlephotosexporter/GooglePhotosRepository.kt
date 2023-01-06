package io.github.patxibocos.googlephotosexporter

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.slf4j.Logger
import java.time.Instant

class Item(val bytes: ByteArray, val id: String, val name: String, val creationTime: Instant)

enum class ItemType {
    PHOTO, VIDEO
}

object GooglePhotosItemForbidden : Exception()

private const val BASE_PATH = "https://photoslibrary.googleapis.com"

@Serializable
data class ListMediaItemsResponse(val mediaItems: List<MediaItem>, val nextPageToken: String? = null)

@Serializable
data class MediaItem(
    val id: String,
    val baseUrl: String,
    val mediaMetadata: MediaMetadata,
    val filename: String,
)

@Serializable
data class MediaMetadata(val creationTime: String, val photo: Photo? = null, val video: Video? = null)

@Serializable
object Photo

@Serializable
data class Video(val status: String)

private fun MediaItem.isNotReady(): Boolean =
    this.hasVideoNotReady()

private fun MediaItem.hasPhoto(): Boolean =
    this.mediaMetadata.photo != null

private fun MediaItem.hasVideo(): Boolean =
    this.mediaMetadata.video != null

private fun MediaItem.hasVideoNotReady(): Boolean =
    this.mediaMetadata.video != null && this.mediaMetadata.video.status != "READY"

class GooglePhotosRepository(
    private val httpClient: HttpClient,
    private val logger: Logger = KotlinLogging.logger {},
) {

    private suspend fun buildItem(mediaItem: MediaItem): Item {
        val suffix = when {
            mediaItem.hasVideo() -> "dv"
            mediaItem.hasPhoto() -> "d"
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
        val creationTime = mediaItem.mediaMetadata.creationTime
        val instant = Instant.parse(creationTime)
        return Item(bytes = bytes, id = mediaItem.id, name = mediaItem.filename, creationTime = instant)
    }

    private suspend fun fetchItems(
        nextPageToken: String,
    ): ListMediaItemsResponse {
        val response = httpClient.get("$BASE_PATH/v1/mediaItems?pageSize=100&pageToken=$nextPageToken")
        return response.body()
    }

    private fun mediaItemFilter(itemTypes: List<ItemType>): (MediaItem) -> Boolean = { mediaItem: MediaItem ->
        itemTypes.any {
            when (it) {
                ItemType.PHOTO -> mediaItem.hasPhoto()
                ItemType.VIDEO -> mediaItem.hasVideo()
            }
        }
    }

    fun download(itemTypes: List<ItemType>, lastItemId: String? = null): Flow<Item> = flow {
        suspend fun getItems(lastItemId: String?): List<MediaItem> {
            // listMediaItems API doesn't support ordering, so this will start fetching recent pages until:
            //  - lastItemId is null -> every page
            //  - lastItemId not null -> every page until a page contains the given id
            val mediaItems = ArrayDeque<MediaItem>()
            var nextPageToken = ""
            while (true) {
                val googlePhotosResponse = fetchItems(nextPageToken)
                nextPageToken = googlePhotosResponse.nextPageToken ?: ""
                val items = googlePhotosResponse.mediaItems
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
