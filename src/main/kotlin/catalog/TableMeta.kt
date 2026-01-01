package com.koosco.catalog

/**
 * fileName       : TableMeta
 * author         : koo
 * date           : 2025. 12. 31. 오전 6:38
 * description    :
 */
data class TableMeta(

    val tableName: String,

    val filePath: String,

    val columns: List<ColumnMeta>
)
