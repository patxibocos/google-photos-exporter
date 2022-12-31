package io.github.patxibocos.googlephotosgithubexporter

interface ExportRepository {
    suspend fun get(filePath: String): ByteArray?
    suspend fun upload(
        data: ByteArray,
        name: String,
        filePath: String,
        overrideContent: Boolean = false
    )

    fun decorate(maxChunkSize: Int?): ExportRepository {
        return RetryDecorator(LoggingDecorator(this), 3).let { decorator ->
            if (maxChunkSize != null) {
                SplitDecorator(decorator, maxChunkSize)
            } else {
                decorator
            }
        }
    }

    companion object {
        fun forExporter(exporter: Subcommands<*>, prefixPath: String, maxChunkSize: Int?): ExportRepository {
            return when (exporter) {
                Subcommands.Box -> {
                    val boxClientId = System.getenv("BOX_CLIENT_ID")
                    val boxClientSecret = System.getenv("BOX_CLIENT_SECRET")
                    val boxUserId = System.getenv("BOX_USER_ID")
                    val client = boxClient(boxClientId, boxClientSecret, boxUserId)
                    val httpClient = boxHttpClient()
                    BoxRepository(client, httpClient, prefixPath)
                }

                Subcommands.Dropbox -> {
                    val dropboxRefreshToken = System.getenv("DROPBOX_REFRESH_TOKEN")
                    val dropboxAppKey = System.getenv("DROPBOX_APP_KEY")
                    val dropboxAppSecret = System.getenv("DROPBOX_APP_SECRET")
                    val dropboxClient = dropboxClient(dropboxAppKey, dropboxAppSecret, dropboxRefreshToken)
                    DropboxRepository(dropboxClient, prefixPath)
                }

                Subcommands.GitHub -> {
                    val githubAccessToken = System.getenv("GITHUB_ACCESS_TOKEN")
                    val httpClient = githubHttpClient(githubAccessToken)
                    val githubRepositoryOwner = System.getenv("GITHUB_REPOSITORY_OWNER")
                    val githubRepositoryName = System.getenv("GITHUB_REPOSITORY_NAME")
                    GitHubRepository(
                        httpClient,
                        githubRepositoryOwner,
                        githubRepositoryName,
                        prefixPath
                    )
                }
            }.decorate(maxChunkSize)
        }
    }
}
