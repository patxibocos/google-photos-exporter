package io.github.patxibocos.googlephotosexporter.exporters

import io.github.patxibocos.googlephotosexporter.boxHttpClient
import io.github.patxibocos.googlephotosexporter.cli.ExporterSubcommands
import io.github.patxibocos.googlephotosexporter.dropboxHttpClient
import io.github.patxibocos.googlephotosexporter.exporters.decorators.LoggingDecorator
import io.github.patxibocos.googlephotosexporter.exporters.decorators.RetryDecorator
import io.github.patxibocos.googlephotosexporter.exporters.decorators.SplitDecorator
import io.github.patxibocos.googlephotosexporter.githubHttpClient
import io.github.patxibocos.googlephotosexporter.oneDriveHttpClient

interface Exporter {
    suspend fun get(filePath: String): ByteArray?
    suspend fun upload(
        data: ByteArray,
        name: String,
        filePath: String,
        overrideContent: Boolean
    )

    private fun decorate(maxChunkSize: Int?): Exporter {
        return RetryDecorator(LoggingDecorator(this), 3).let { decorator ->
            if (maxChunkSize != null) {
                SplitDecorator(decorator, maxChunkSize)
            } else {
                decorator
            }
        }
    }

    companion object {
        internal fun from(exporter: ExporterSubcommands<*>, prefixPath: String, maxChunkSize: Int?): Exporter {
            return when (exporter) {
                ExporterSubcommands.Box -> {
                    val boxClientId = System.getenv("BOX_CLIENT_ID")
                    val boxClientSecret = System.getenv("BOX_CLIENT_SECRET")
                    val boxUserId = System.getenv("BOX_USER_ID")
//                    val client = boxClient(boxClientId, boxClientSecret, boxUserId)
                    val httpClient = boxHttpClient(boxClientId, boxClientSecret, boxUserId)
//                    BoxExporter(client, httpClient, prefixPath)
                    BoxExporter(httpClient, prefixPath)
                }

                ExporterSubcommands.Dropbox -> {
                    val dropboxRefreshToken = System.getenv("DROPBOX_REFRESH_TOKEN")
                    val dropboxAppKey = System.getenv("DROPBOX_APP_KEY")
                    val dropboxAppSecret = System.getenv("DROPBOX_APP_SECRET")
                    val dropboxClient = dropboxHttpClient(dropboxAppKey, dropboxAppSecret, dropboxRefreshToken)
                    DropboxExporter(dropboxClient, prefixPath)
                }

                ExporterSubcommands.GitHub -> {
                    val githubAccessToken = System.getenv("GITHUB_ACCESS_TOKEN")
                    val githubRepositoryOwner = System.getenv("GITHUB_REPOSITORY_OWNER")
                    val githubRepositoryName = System.getenv("GITHUB_REPOSITORY_NAME")
                    val httpClient = githubHttpClient(githubAccessToken)
                    GitHubExporter(
                        httpClient,
                        githubRepositoryOwner,
                        githubRepositoryName,
                        prefixPath
                    )
                }

                ExporterSubcommands.OneDrive -> {
                    val oneDriveClientId = System.getenv("ONEDRIVE_CLIENT_ID")
                    val oneDriveClientSecret = System.getenv("ONEDRIVE_CLIENT_SECRET")
                    val oneDriveRefreshToken = System.getenv("ONEDRIVE_REFRESH_TOKEN")
                    val httpClient = oneDriveHttpClient(oneDriveClientId, oneDriveClientSecret, oneDriveRefreshToken)
                    OneDriveExporter(httpClient, prefixPath)
                }
            }.decorate(maxChunkSize)
        }
    }
}
