package com.koosco

import com.koosco.cil.Repl
import com.koosco.sql.Executor
import com.koosco.storage.DiskManager
import com.koosco.storage.HeapTable
import java.nio.file.Paths

/**
 * fileName       : ${NAME}
 * author         : koo
 * date           : 2025. 12. 30. 오후 5:43
 * description    :
 */
fun main() {
    println("KooscoDB starting...")

    val diskManager = DiskManager(Paths.get("data/heap.db"))
    val heapTable = HeapTable(diskManager)
    val executor = Executor(heapTable)

    Repl(executor).run()
}
