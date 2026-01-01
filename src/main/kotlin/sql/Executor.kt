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

    fun execute(command: Command) {
        when (command) {
            is CreateTableCommand -> {
                val columns = command.columns.map {
                    ColumnMeta(
                        name = it.name,
                        type = ColumnType.valueOf(it.type.uppercase()),
                        nullable = it.nullable
                    )
                }

                catalog.createTable(command.tableName, columns)
                println("OK")
            }

            is InsertCommand -> {
                val table = tableManager.open(command.tableName)
                table.insertRow(command.value.toByteArray())
                println("OK")
            }

            is SelectCommand -> {
                val table = tableManager.open(command.tableName)
                table.scanAll().map { String(it) }.forEach { println(it) }
            }

            ShowTablesCommand -> {
                val tables = catalog.listTables()
                if (tables.isEmpty()) println("No tables found")
                else tables.forEach {
                    println(it)
                }
            }

            is DescribeTableCommand -> {
                val meta = catalog.getTableMeta(command.tableName)

                // 각 컬럼의 최대 너비 계산
                val maxNameLen = maxOf("column_name".length, meta.columns.maxOfOrNull { it.name.length } ?: 0)
                val maxTypeLen = maxOf("type".length, meta.columns.maxOfOrNull { it.type.name.length } ?: 0)

                // 헤더 출력
                println("${"column_name".padEnd(maxNameLen)} | ${"type".padEnd(maxTypeLen)} | nullable")
                println("${"".padEnd(maxNameLen, '-')}-+-${"".padEnd(maxTypeLen, '-')}-+----------")

                // 데이터 출력
                meta.columns.forEach {
                    println("${it.name.padEnd(maxNameLen)} | ${it.type.name.padEnd(maxTypeLen)} | ${it.nullable}")
                }
            }
        }
    }
}
