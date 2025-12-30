package com.koosco.cil

/**
 * fileName       : Repl
 * author         : koo
 * date           : 2025. 12. 30. 오후 5:55
 * description    :
 */
class Repl {

    fun run() {
        while (true) {
            print("KooscoDB> ")
            val line = readLine() ?: break

            if (line.lowercase() == "exit") {
                println("bye")
                break
            }

            println("echo: $line")
        }
    }
}
