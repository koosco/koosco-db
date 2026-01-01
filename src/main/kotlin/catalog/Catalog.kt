package com.koosco.catalog

/**
 * fileName       : Catalog
 * author         : koo
 * date           : 2025. 12. 31. 오전 6:38
 * description    :
 */
interface Catalog {

    fun createTable(tableName: String, columns: List<ColumnMeta>)

    fun getTableMeta(tableName: String): TableMeta

    fun listTables(): List<String>
}
