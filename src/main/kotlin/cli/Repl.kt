package com.koosco.cil

import com.koosco.sql.CommandParser
import com.koosco.sql.Executor

/**
 * fileName       : Repl
 * author         : koo
 * date           : 2025. 12. 30. 오후 5:55
 * description    :
 */
class Repl(
    private val executor: Executor,
) {

    private val parser = CommandParser()

    fun run() {
        while (true) {
            print("KooscoDB> ")
            val line = readLine() ?: break
            val trimmed = line.trim()

            if (trimmed.lowercase() == "exit") {
                println("bye")
                break
            }

            if (trimmed.isEmpty()) continue

            try {
                parser.parse(trimmed).run {
                    executor.execute(this)
                }
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
        }
    }
}
