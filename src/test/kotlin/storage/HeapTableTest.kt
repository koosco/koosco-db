package storage

import com.koosco.storage.DiskManager
import com.koosco.storage.HeapTable
import org.junit.jupiter.api.Test
import java.nio.file.Paths

/**
 * fileName       : HeapTableTest
 * author         : koo
 * date           : 2025. 12. 31. 오전 5:42
 * description    :
 */
class HeapTableTest {

    @Test
    fun givenVariable_whenInsert_thenCanRetrieve() {
        val diskManager = DiskManager(Paths.get("data/heap.db"))
        val heap = HeapTable(diskManager)

        val r1 = heap.insertRow("hello".toByteArray())
        val r2 = heap.insertRow("world".toByteArray())
        val r3 = heap.insertRow("koosco-db".toByteArray())

        println(r1)
        println(r2)
        println(r3)

        heap.scanAll().forEach {
            println(String(it))
        }
    }
}
