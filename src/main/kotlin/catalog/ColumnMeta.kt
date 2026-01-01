package com.koosco.catalog

/**
 * fileName       : ColumnMeta
 * author         : koo
 * date           : 2026. 1. 1. 오후 7:12
 * description    :
 */
enum class ColumnType {
    INT,
    STRING,
}

data class ColumnMeta(
    val name: String,
    val type: ColumnType,
    val nullable: Boolean,
)
