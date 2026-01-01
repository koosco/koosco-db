package com.koosco.sql

import com.koosco.catalog.Catalog
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
                catalog.createTable(command.tableName)
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
        }
    }
}
