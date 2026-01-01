package com.koosco.storage

import com.koosco.common.PageId
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

/**
 * fileName       : FileDiskManager
 * author         : koo
 * date           : 2025. 12. 30. 오후 6:10
 * description    : pageId, disk offset 변환기
 * - DB 상위 계층에 disk offset을 추상화하고, pageId만을 노출
 * - offset 처리는 DiskManager가 담당
 */
class FileDiskManager(
    dbPath: Path
): DiskManager {
    private val dbFile: DbFile

    init {
        Files.createDirectories(dbPath.parent)
        dbFile = DbFile(dbPath)
    }

    /**
     * pageId에 해당하는 페이지를 buffer에 읽어들임
     * buffer에 정확히 하나의 페이지를 채움
     */
    override fun readPage(pageId: PageId, buffer: ByteBuffer) {
        require(buffer.capacity() == Page.PAGE_SIZE)

        buffer.clear()
        val offset = pageId * Page.PAGE_SIZE
        dbFile.channel.read(buffer, offset)
        buffer.flip()
    }

    /**
     * buffer의 내용을 pageId에 해당하는 페이지에 기록
     */
    override fun writePage(pageId: PageId, buffer: ByteBuffer) {
        require(buffer.capacity() == Page.PAGE_SIZE)

        buffer.rewind()
        val offset = pageId * Page.PAGE_SIZE
        dbFile.channel.write(buffer, offset)
    }

    /**
     * 새 페이지 할당 (append to file)
     */
    override fun allocatePage(): PageId {
        val newPageId = dbFile.size() / Page.PAGE_SIZE
        val zero = ByteBuffer.allocate(Page.PAGE_SIZE)
        dbFile.channel.write(zero, newPageId * Page.PAGE_SIZE)

        return newPageId
    }

    fun close() {
        dbFile.close()
    }
}
