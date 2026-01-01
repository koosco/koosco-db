package com.koosco.storage

import com.koosco.catalog.Catalog
import java.nio.file.Paths

/**
 * fileName       : TableManager
 * author         : koo
 * date           : 2025. 12. 31. 오전 6:48
 * description    :
 */
class TableManager(
    private val catalog: Catalog
) {

    private val opened = mutableMapOf<String, HeapTable>()

    fun open(tableName: String): HeapTable {
        return opened.getOrPut(tableName) {
            val meta = catalog.getTableMeta(tableName)
            HeapTable(DiskManager(Paths.get(meta.filePath)))
        }
    }
}
