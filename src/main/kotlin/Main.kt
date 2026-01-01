package com.koosco

import com.koosco.cil.Repl
import com.koosco.db.engine.DbEngineFactory
import java.nio.file.Path

/**
 * fileName       : ${NAME}
 * author         : koo
 * date           : 2025. 12. 30. 오후 5:43
 * description    :
 */
fun main() {
    println("KooscoDB starting...")

    val baseDir = Path.of("data")
    val dbEngine = DbEngineFactory.create(baseDir)

    Repl(dbEngine).run()
}
