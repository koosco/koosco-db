package com.koosco.storage

import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.file.Path

/**
 * fileName       : DbFile
 * author         : koo
 * date           : 2025. 12. 30. 오후 6:09
 * description    : pageId 기반 seek
 */
class DbFile(
    path: Path
) {
    private val raf = RandomAccessFile(path.toFile(), "rw")
    val channel: FileChannel = raf.channel

    fun size(): Long = channel.size()

    fun close() {
        channel.close()
        raf.close()
    }
}
