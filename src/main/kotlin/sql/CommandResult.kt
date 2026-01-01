package com.koosco.sql

import com.koosco.catalog.ColumnMeta

/**
 * fileName       : CommandResult
 * author         : koo
 * date           : 2026. 1. 1. 오후 8:50
 * description    :
 */
sealed interface CommandResult

object OkResult : CommandResult

data class RowsResult(
    val rows: List<List<String>>
) : CommandResult

data class TablesResult(
    val tables: List<String>
) : CommandResult

data class DescribeResult(
    val columns: List<ColumnMeta>
) : CommandResult
