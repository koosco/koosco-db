package com.koosco.storage

import com.koosco.common.PageId
import java.nio.ByteBuffer

/**
 * fileName       : DiskManager
 * author         : koo
 * date           : 2026. 1. 1. 오후 7:58
 * description    :
 */
interface DiskManager {

    fun readPage(pageId: PageId, destination: ByteBuffer)

    fun writePage(pageId: PageId, source: ByteBuffer)

    fun allocatePage(): PageId
}
