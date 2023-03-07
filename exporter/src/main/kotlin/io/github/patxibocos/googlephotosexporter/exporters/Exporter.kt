package io.github.patxibocos.googlephotosexporter.exporters

import io.github.patxibocos.googlephotosexporter.boxHttpClient
import io.github.patxibocos.googlephotosexporter.dropboxHttpClient
import io.github.patxibocos.googlephotosexporter.exporters.decorators.LoggingDecorator
import io.github.patxibocos.googlephotosexporter.exporters.decorators.RetryDecorator
import io.github.patxibocos.googlephotosexporter.exporters.decorators.SplitDecorator
import io.github.patxibocos.googlephotosexporter.githubHttpClient
import io.github.patxibocos.googlephotosexporter.oneDriveHttpClient
import kotlin.time.Duration

interface Exporter {
    suspend fun get(filePath: String): ByteArray?
    suspend fun upload(
        data: ByteArray,
        name: String,
        filePath: String,
        overrideContent: Boolean,
    )

    enum class ExporterType {
        BOX,
        DROPBOX,
        GITHUB,
        ONEDRIVE,
    }
}

private fun Exporter.decorate(maxChunkSize: Int?): Exporter {
    return RetryDecorator(LoggingDecorator(this), 3).let { decorator ->
        if (maxChunkSize != null) {
            SplitDecorator(decorator, maxChunkSize)
        } else {
            decorator
        }
    }
}

class ExporterScope(
    private val prefixPath: String,
    private val requestTimeout: Duration,
    var exporter: Exporter? = null,
) {
    fun box(boxClientId: String, boxClientSecret: String, boxUserId: String) {
        val httpClient = boxHttpClient(boxClientId, boxClientSecret, boxUserId, requestTimeout)
        exporter = BoxExporter(httpClient, prefixPath)
    }

    fun dropbox(dropboxRefreshToken: String, dropboxAppKey: String, dropboxAppSecret: String) {
        val dropboxClient =
            dropboxHttpClient(dropboxAppKey, dropboxAppSecret, dropboxRefreshToken, requestTimeout)
        exporter = DropboxExporter(dropboxClient, prefixPath)
    }

    fun github(
        githubAccessToken: String,
        githubRepositoryOwner: String,
        githubRepositoryName: String,
    ) {
        val httpClient = githubHttpClient(githubAccessToken, requestTimeout)
        exporter = GitHubExporter(
            httpClient,
            githubRepositoryOwner,
            githubRepositoryName,
            prefixPath,
        )
    }

    fun onedrive(
        oneDriveClientId: String,
        oneDriveClientSecret: String,
        oneDriveRefreshToken: String,
    ) {
        val httpClient =
            oneDriveHttpClient(oneDriveClientId, oneDriveClientSecret, oneDriveRefreshToken, requestTimeout)
        exporter = OneDriveExporter(httpClient, prefixPath)
    }
}

fun exporter(prefixPath: String, maxChunkSize: Int?, requestTimeout: Duration, f: ExporterScope.() -> Unit): Exporter {
    val exporterScope = ExporterScope(prefixPath, requestTimeout)
    exporterScope.f()
    return requireNotNull(exporterScope.exporter).decorate(maxChunkSize)
}
