package com.koosco.sql

import com.koosco.storage.HeapTable

/**
 * fileName       : Executor
 * author         : koo
 * date           : 2025. 12. 31. 오전 6:18
 * description    :
 */
class Executor(
    private val heapTable: HeapTable
) {

    fun execute(command: Command) {
        when (command) {
            is InsertCommand -> {
                heapTable.insertRow(command.value.toByteArray())
            }

            SelectCommand -> {
                heapTable.scanAll()
                    .map { String(it) }
                    .forEach { println(it) }
            }
        }
    }
}
