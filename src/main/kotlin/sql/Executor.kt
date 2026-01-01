package com.koosco.sql

import com.koosco.catalog.Catalog
import com.koosco.catalog.ColumnMeta
import com.koosco.catalog.ColumnType
import com.koosco.storage.TableManager

/**
 * fileName       : Executor
 * author         : koo
 * date           : 2025. 12. 31. 오전 6:18
 * description    :
 */
class Executor(
    private val catalog: Catalog,
    private val tableManager: TableManager,
) {

    fun execute(command: Command): CommandResult = when (command) {
        is CreateTableCommand -> {
            val columns = command.columns.map {
                ColumnMeta(
                    name = it.name,
                    type = ColumnType.valueOf(it.type.uppercase()),
                    nullable = it.nullable
                )
            }

            catalog.createTable(command.tableName, columns)
            OkResult
        }

        is InsertCommand -> {
            val table = tableManager.open(command.tableName)
            table.insertRow(command.value.toByteArray())
            OkResult
        }

        is SelectCommand -> {
            val table = tableManager.open(command.tableName)
            val rows = table.scanAll()
                .map { listOf(String(it)) }

            RowsResult(rows)
        }

        ShowTablesCommand -> {
            TablesResult(catalog.listTables())
        }

        is DescribeTableCommand -> {
            val meta = catalog.getTableMeta(command.tableName)

            DescribeResult(meta.columns)
        }
    }
}
