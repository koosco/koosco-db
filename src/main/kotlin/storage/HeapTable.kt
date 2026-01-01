package com.koosco.storage

import com.koosco.buffer.BufferPool
import com.koosco.common.PageId
import com.koosco.common.Rid

/**
 * fileName       : HeapTable
 * author         : koo
 * date           : 2025. 12. 31. 오전 5:28
 * description    :
 */
class HeapTable(
    private val diskManager: DiskManager,
    private val bufferPool: BufferPool
) {
    private val pageIds = mutableListOf<PageId>() // TODO : catalog 관리 필요

    /**
     * 새로운 페이지 할당 및 초기화
     *
     * @return 할당된 새로운 페이지의 PageId
     */
    private fun allocateNewPage(): PageId {
        val pageId = diskManager.allocatePage()
        val buffer = bufferPool.getPage(pageId)

        val page = SlottedPage(buffer)
        page.init()

        bufferPool.markDirty(pageId)
        pageIds.add(pageId)

        return pageId
    }

    /**
     * 페이지에 레코드를 삽입하고, 해당 레코드의 Rid 반환
     *
     * @return 삽입된 레코드의 Rid
     */
    fun insertRow(record: ByteArray): Rid {
        for (pageId in pageIds) {
            val buffer = bufferPool.getPage(pageId)
            val page = SlottedPage(buffer)

            try {
                val slotId = page.insert(record)
                bufferPool.markDirty(pageId)

                return Rid(pageId, slotId)
            } catch (_: IllegalStateException) {
                // not enough space, try next page
            }
        }

        val newPageId = allocateNewPage()
        val buffer = bufferPool.getPage(newPageId)
        val page = SlottedPage(buffer)

        val slotId = page.insert(record)
        bufferPool.markDirty(newPageId)

        return Rid(newPageId, slotId)
    }

    fun scanAll(): List<ByteArray> {
        val result = mutableListOf<ByteArray>()

        for (pageId in pageIds) {
            val buffer = bufferPool.getPage(pageId)
            val page = SlottedPage(buffer)

            val slotCount = page.slotCount()
            for (slotId in 0 until slotCount) {
                try {
                    result.add(page.read(slotId))
                } catch (_: IllegalStateException) {
                    // deleted record, skip
                }
            }
        }

        return result
    }
}
