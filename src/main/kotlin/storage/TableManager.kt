package com.koosco.storage

import com.koosco.buffer.BufferPool
import com.koosco.catalog.Catalog

/**
 * fileName       : TableManager
 * author         : koo
 * date           : 2025. 12. 31. 오전 6:48
 * description    :
 */
class TableManager(
    private val catalog: Catalog,
    private val diskManager: DiskManager,
    private val bufferPool: BufferPool
) {

    private val opened = mutableMapOf<String, HeapTable>()

    fun open(tableName: String): HeapTable {
        return opened.getOrPut(tableName) {
            val meta = catalog.getTableMeta(tableName)
            HeapTable(
                diskManager = diskManager,
                bufferPool = bufferPool,
            )
        }
    }
}
