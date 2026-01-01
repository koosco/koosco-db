package com.koosco.cil

import com.koosco.db.engine.DbEngine
import com.koosco.sql.DescribeResult
import com.koosco.sql.OkResult
import com.koosco.sql.RowsResult
import com.koosco.sql.TablesResult

/**
 * fileName       : Repl
 * author         : koo
 * date           : 2025. 12. 30. 오후 5:55
 * description    :
 */
class Repl(
    private val dbEngine: DbEngine
) {

    fun run() {
        while (true) {
            print("KooscoDB> ")
            val line = readLine() ?: break
            val trimmed = line.trim()

            if ("exit".equals(trimmed, ignoreCase = true)) {
                println("bye")
                break
            }

            if (trimmed.isEmpty()) continue

            try {
                val result = dbEngine.execute(trimmed)

                when (result) {
                    is OkResult -> println("OK")
                    is TablesResult -> result.tables.forEach { println(it) }
                    is RowsResult -> result.rows.forEach { println(it.joinToString()) }
                    is DescribeResult -> printDescribe(result)
                }
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
        }

        dbEngine.close()
    }

    private fun printDescribe(result: DescribeResult) {
        // 각 컬럼의 최대 너비 계산
        val columns = result.columns
        val maxNameLen =
            maxOf("column_name".length, columns.maxOfOrNull { it.name.length } ?: 0)
        val maxTypeLen = maxOf("type".length, columns.maxOfOrNull { it.type.name.length } ?: 0)

        // 헤더 출력
        println("${"column_name".padEnd(maxNameLen)} | ${"type".padEnd(maxTypeLen)} | nullable")
        println("${"".padEnd(maxNameLen, '-')}-+-${"".padEnd(maxTypeLen, '-')}-+----------")

        // 데이터 출력
        columns.forEach {
            println("${it.name.padEnd(maxNameLen)} | ${it.type.name.padEnd(maxTypeLen)} | ${it.nullable}")
        }
    }
}
