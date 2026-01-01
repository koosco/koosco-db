package com.koosco

import com.koosco.catalog.FileCatalog
import com.koosco.cil.Repl
import com.koosco.sql.Executor
import com.koosco.storage.TableManager
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
    val catalogFile = baseDir.resolve("catalog.meta")

    val catalog = FileCatalog(
        baseDir = baseDir,
        catalogFile = catalogFile
    )

    val tableManager = TableManager(catalog)
    val executor = Executor(catalog, tableManager)

    Repl(executor).run()
}
