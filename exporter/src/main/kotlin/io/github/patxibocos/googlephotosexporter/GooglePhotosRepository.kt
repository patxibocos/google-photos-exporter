package io.github.patxibocos.googlephotosexporter

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
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

private object GooglePhotosItemForbidden : Exception()

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
    this.mediaMetadata.video != null && this.mediaMetadata.video.status != "READY"

private fun MediaItem.hasPhoto(): Boolean =
    this.mediaMetadata.photo != null

private fun MediaItem.hasVideo(): Boolean =
    this.mediaMetadata.video != null

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
        val response = httpClient.requestWithRetry(fullSizeUrl, HttpMethod.Get)
        if (response.status == HttpStatusCode.Forbidden) {
            throw GooglePhotosItemForbidden
        }
        val bytes: ByteArray = response.body()
        val creationTime = mediaItem.mediaMetadata.creationTime
        val instant = Instant.parse(creationTime)
        return Item(bytes = bytes, id = mediaItem.id, name = mediaItem.filename, creationTime = instant)
    }

    private suspend fun fetchItems(
        nextPageToken: String,
    ): ListMediaItemsResponse {
        val response = httpClient.requestWithRetry(
            "$BASE_PATH/v1/mediaItems?pageSize=100&pageToken=$nextPageToken",
            HttpMethod.Get,
        )
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

    fun download(
        itemTypes: List<ItemType>,
        lastSync: Sync?,
        startCallback: suspend (photos: Int, videos: Int) -> Unit,
    ): Flow<Item> =
        flow {
            suspend fun getItems(
                lastSync: Sync?,
                itemTypes: List<ItemType>,
            ): List<MediaItem> {
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
                        when {
                            lastSync == null -> false
                            it.id == lastSync.id -> false
                            else -> Instant.parse(it.mediaMetadata.creationTime).isAfter(lastSync.creationTime)
                        }
                    }
                    val newFilteredItems = newItems.filter { mediaItemFilter(itemTypes)(it) }
                    mediaItems.addAll(newFilteredItems)
                    if (nextPageToken.isEmpty() || newItems.size != items.size) {
                        break
                    }
                }
                return mediaItems.reversed()
            }

            var finished = false
            var lastEmitted = lastSync
            var emitted = false
            while (!finished) {
                val mediaItems = getItems(lastEmitted, itemTypes)
                if (!emitted) {
                    val photos = mediaItems.count { it.hasPhoto() }
                    val videos = mediaItems.count { it.hasVideo() }
                    startCallback(photos, videos)
                    emitted = true
                }
                try {
                    mediaItems.forEach { mediaItem ->
                        val item = try {
                            buildItem(mediaItem)
                        } catch (e: DownloadError) {
                            if (mediaItem.isNotReady()) {
                                logger.warn("Item ${mediaItem.filename} is not ready yet, stopping here")
                                return@flow
                            }
                            throw e
                        }
                        emit(item)
                        lastEmitted = Sync(mediaItem.id, Instant.parse(mediaItem.mediaMetadata.creationTime))
                    }
                    finished = true
                } catch (e: GooglePhotosItemForbidden) {
                    logger.warn("Google Photos returned 403 Forbidden, retrying")
                }
            }
        }
}
