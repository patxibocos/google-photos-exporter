package io.github.patxibocos.googlephotosgithubexporter

import java.time.Instant

class Photo(val bytes: ByteArray, val id: String, val name: String, val creationTime: Instant)
