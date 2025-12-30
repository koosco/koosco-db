# HeapTable에 대한 문서

## HeapTable이란?

`HeapTable`은 **정렬되지 않은 레코드 집합**을 여러 페이지에 걸쳐 저장하고 관리하는 테이블 구현입니다. 가장 단순한 형태의 테이블 스토리지 엔진으로, 삽입 순서대로 데이터를 저장합니다.

### DB에서의 역할

- **테이블 추상화**: 여러 페이지를 하나의 논리적 테이블로 관리
- **레코드 삽입**: 적절한 페이지를 찾아 레코드 삽입
- **테이블 스캔**: 모든 페이지를 순회하며 레코드 읽기
- **공간 관리**: 페이지가 꽉 차면 자동으로 새 페이지 할당

## 핵심 개념

### Heap 저장 방식

"Heap"은 **정렬되지 않은 무더기**를 의미합니다.

```
Heap Table (정렬 없음)
┌────────────────────────────────┐
│ Page 0 │ Page 1 │ Page 2 │ ... │
├────────┼────────┼────────┼─────┤
│ R5, R1 │ R3, R7 │ R2, R4 │ ... │
└────────┴────────┴────────┴─────┘
삽입 순서대로 저장, 순서 보장 없음
```

**vs. Sorted Table (B-Tree 등)**
```
Sorted Table (키로 정렬)
┌────────────────────────────────┐
│ Page 0 │ Page 1 │ Page 2 │ ... │
├────────┼────────┼────────┼─────┤
│ R1, R2 │ R3, R4 │ R5, R7 │ ... │
└────────┴────────┴────────┴─────┘
키 순서대로 정렬, 빠른 검색
```

### RID (Record Identifier)

각 레코드는 고유한 RID로 식별됩니다.

```kotlin
data class Rid(
    val pageId: PageId,  // 어느 페이지에 있는지
    val slotId: Int      // 페이지 내 몇 번째 슬롯인지
)

// 예: Rid(pageId=3, slotId=5)
// → 3번 페이지의 5번 슬롯에 있는 레코드
```

**RID의 중요성:**
- 레코드의 물리적 위치를 직접 참조
- O(1) 시간에 레코드 접근 가능
- 인덱스가 레코드를 가리킬 때 사용

## HeapTable 아키텍처

```
┌─────────────────────────────────────┐
│         Application Layer           │
│  (SQL 쿼리, ORM)                    │
└──────────────┬──────────────────────┘
               │ insertRow(), scanAll()
               ▼
┌─────────────────────────────────────┐
│         HeapTable                   │
│  - 여러 페이지 관리                  │
│  - 적절한 페이지 찾기                │
│  - 새 페이지 할당                    │
└──────────────┬──────────────────────┘
               │ PageId, Rid
               ▼
┌─────────────────────────────────────┐
│         DiskManager                 │
│  - 페이지 I/O                       │
│  - PageId ↔ Offset 변환             │
└──────────────┬──────────────────────┘
               │ ByteBuffer
               ▼
┌─────────────────────────────────────┐
│         SlottedPage                 │
│  - 페이지 내부 레코드 관리           │
│  - 삽입, 읽기, 삭제                  │
└─────────────────────────────────────┘
```

## 주요 메서드

### insertRow(record: ByteArray): Rid

레코드를 테이블에 삽입하고 RID 반환

```kotlin
┌─────────────────────────────────────────┐
│  Step 1: 기존 페이지에서 공간 찾기         │
├─────────────────────────────────────────┤
│  for (pageId in pageIds) {              │
│      페이지 읽기                         │
│      try { 삽입 시도 }                   │
│      성공 시 Rid 반환                    │
│      실패 시 다음 페이지 시도             │
│  }                                      │
└─────────────────────────────────────────┘
        ↓ (모든 페이지 꽉 참)
┌─────────────────────────────────────────┐
│  Step 2: 새 페이지 할당                  │
├─────────────────────────────────────────┤
│  newPageId = allocateNewPage()          │
│  페이지 읽기, 초기화, 삽입               │
│  Rid(newPageId, slotId) 반환            │
└─────────────────────────────────────────┘
```

### 삽입 예제

```kotlin
val heapTable = HeapTable(diskManager)

// 첫 번째 레코드 삽입
val record1 = "Alice".toByteArray()
val rid1 = heapTable.insertRow(record1)
// → Rid(pageId=0, slotId=0)
// 페이지가 없으므로 새 페이지 할당

// 두 번째 레코드 삽입
val record2 = "Bob".toByteArray()
val rid2 = heapTable.insertRow(record2)
// → Rid(pageId=0, slotId=1)
// 같은 페이지에 공간 있으므로 삽입

// 큰 레코드 여러 개 삽입 (페이지 꽉 참)
val largeRecord = ByteArray(2000)
repeat(3) {
    heapTable.insertRow(largeRecord)
}
// → 두 번째 페이지 자동 할당
```

### 내부 동작 흐름

```kotlin
fun insertRow(record: ByteArray): Rid {
    // Phase 1: 기존 페이지 순회
    for (pageId in pageIds) {  // [0, 1, 2, ...]
        val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
        diskManager.readPage(pageId, buffer)  // 디스크 → 메모리

        val page = SlottedPage(buffer)
        try {
            val slotId = page.insert(record)  // 삽입 시도
            diskManager.writePage(pageId, buffer)  // 메모리 → 디스크

            return Rid(pageId, slotId)  // 성공!
        } catch (_: IllegalStateException) {
            // "Not enough space" → 다음 페이지 시도
            continue
        }
    }

    // Phase 2: 모든 페이지 꽉 참 → 새 페이지 할당
    val newPageId = allocateNewPage()  // DiskManager 호출
    val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
    diskManager.readPage(newPageId, buffer)

    val page = SlottedPage(buffer)
    val slotId = page.insert(record)
    diskManager.writePage(newPageId, buffer)

    return Rid(newPageId, slotId)
}
```

### scanAll(): List<ByteArray>

테이블의 모든 레코드를 순회하여 반환 (Full Table Scan)

```kotlin
┌─────────────────────────────────────────┐
│  Step 1: 모든 페이지 순회                 │
├─────────────────────────────────────────┤
│  for (pageId in pageIds) {              │
│      페이지 읽기                         │
│      페이지 내 모든 슬롯 순회             │
│  }                                      │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 2: 각 슬롯의 레코드 읽기            │
├─────────────────────────────────────────┤
│  for (slotId in 0 until slotCount) {    │
│      try { 레코드 읽기 }                 │
│      삭제된 레코드는 skip                │
│  }                                      │
└─────────────────────────────────────────┘
```

### 스캔 예제

```kotlin
val heapTable = HeapTable(diskManager)

// 데이터 삽입
heapTable.insertRow("Alice".toByteArray())
heapTable.insertRow("Bob".toByteArray())
heapTable.insertRow("Charlie".toByteArray())

// 전체 스캔
val allRecords = heapTable.scanAll()
for (record in allRecords) {
    println(String(record))
}
// 출력:
// Alice
// Bob
// Charlie
```

### 삭제된 레코드 처리

```kotlin
fun scanAll(): List<ByteArray> {
    val result = mutableListOf<ByteArray>()

    for (pageId in pageIds) {
        val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
        diskManager.readPage(pageId, buffer)

        val page = SlottedPage(buffer)
        val slotCount = page.slotCount()

        for (slotId in 0 until slotCount) {
            try {
                result.add(page.read(slotId))  // 성공
            } catch (_: IllegalStateException) {
                // "Deleted record" → skip
                // 결과에 포함하지 않음
            }
        }
    }

    return result
}
```

### allocateNewPage(): PageId (private)

새로운 빈 페이지를 할당하고 초기화

```kotlin
private fun allocateNewPage(): PageId {
    // 1. DiskManager에 새 페이지 요청
    val pageId = diskManager.allocatePage()

    // 2. 페이지 읽기 (0으로 초기화된 상태)
    val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
    diskManager.readPage(pageId, buffer)

    // 3. SlottedPage로 초기화
    val page = SlottedPage(buffer)
    page.init()  // Header 설정

    // 4. 초기화된 페이지를 디스크에 기록
    diskManager.writePage(pageId, buffer)

    // 5. pageIds 목록에 추가
    pageIds.add(pageId)

    return pageId
}
```

## 페이지 관리 전략

### 페이지 목록 관리

```kotlin
private val pageIds = mutableListOf<PageId>()
```

**현재 구현:**
- 메모리에 페이지 ID 목록 유지
- 순차적으로 페이지 검색

**향후 개선 방향:**
```kotlin
// Catalog 테이블에 메타데이터 저장
class Catalog {
    fun getTablePages(tableId: Int): List<PageId>
    fun addTablePage(tableId: Int, pageId: PageId)
}

// HeapTable이 Catalog 사용
class HeapTable(
    private val diskManager: DiskManager,
    private val catalog: Catalog,
    private val tableId: Int
) {
    private fun loadPageIds(): List<PageId> {
        return catalog.getTablePages(tableId)
    }
}
```

### 삽입 전략: First-Fit

```kotlin
// First-Fit: 첫 번째로 공간이 있는 페이지에 삽입
for (pageId in pageIds) {
    try {
        return page.insert(record)  // 성공 시 즉시 반환
    } catch (_: IllegalStateException) {
        continue  // 다음 페이지 시도
    }
}
```

**First-Fit의 특징:**
- 구현 간단
- 빠른 삽입 (평균 케이스)
- 페이지 단편화 가능성

**대안 전략:**
```kotlin
// Best-Fit: 가장 적합한 크기의 페이지에 삽입
fun findBestFitPage(recordSize: Int): PageId? {
    var bestPageId: PageId? = null
    var minWaste = Int.MAX_VALUE

    for (pageId in pageIds) {
        val freeSpace = getPageFreeSpace(pageId)
        if (freeSpace >= recordSize) {
            val waste = freeSpace - recordSize
            if (waste < minWaste) {
                minWaste = waste
                bestPageId = pageId
            }
        }
    }

    return bestPageId
}

// Worst-Fit: 가장 여유 공간이 많은 페이지에 삽입
// → 단편화 최소화, 하지만 검색 오버헤드
```

## 사용 예제

### 기본 사용 패턴

```kotlin
// 1. HeapTable 생성
val diskManager = DiskManager(Path.of("data/mydb.db"))
val heapTable = HeapTable(diskManager)

// 2. 레코드 삽입
val records = listOf(
    "Alice".toByteArray(),
    "Bob".toByteArray(),
    "Charlie".toByteArray()
)

val rids = mutableListOf<Rid>()
for (record in records) {
    val rid = heapTable.insertRow(record)
    rids.add(rid)
    println("Inserted: $rid")
}

// 3. 전체 테이블 스캔
val allRecords = heapTable.scanAll()
println("Total records: ${allRecords.size}")
for (record in allRecords) {
    println("Record: ${String(record)}")
}

// 4. 종료
diskManager.close()
```

### 대량 데이터 삽입

```kotlin
// 벤치마크: 10,000개 레코드 삽입
val startTime = System.currentTimeMillis()

val recordTemplate = "User %05d"
repeat(10_000) { i ->
    val record = recordTemplate.format(i).toByteArray()
    heapTable.insertRow(record)

    if ((i + 1) % 1000 == 0) {
        println("Inserted ${i + 1} records...")
    }
}

val elapsed = System.currentTimeMillis() - startTime
println("Time: ${elapsed}ms")
println("Throughput: ${10_000 / (elapsed / 1000.0)} records/sec")

// 예상 결과:
// - 페이지 수: ~30 (레코드 크기와 페이지 활용률에 따라 다름)
// - 성능: ~5000-10000 records/sec (SSD 기준)
```

### 특정 RID로 레코드 읽기

```kotlin
// RID를 알고 있을 때 직접 접근 (향후 구현)
fun getRecord(rid: Rid): ByteArray {
    val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
    diskManager.readPage(rid.pageId, buffer)

    val page = SlottedPage(buffer)
    return page.read(rid.slotId)
}

// 사용 예
val rid = heapTable.insertRow("Alice".toByteArray())
val record = getRecord(rid)
println(String(record))  // "Alice"
```

## 성능 분석

### 시간 복잡도

| 연산 | 시간 복잡도 | 설명 |
|------|-------------|------|
| **insertRow** | O(N) | N = 페이지 수, 최악의 경우 모든 페이지 순회 |
| **scanAll** | O(N * M) | N = 페이지 수, M = 페이지당 평균 레코드 수 |
| **getRecord(rid)** | O(1) | RID로 직접 접근 (향후 구현) |

**삽입 성능 개선:**
```kotlin
// 현재: O(N) - 순차 검색
for (pageId in pageIds) { ... }

// 개선: O(1) - 마지막 페이지 추적
private var lastPageId: PageId? = null
private var lastPageFreeSpace: Int = 0

fun insertRow(record: ByteArray): Rid {
    // 마지막 페이지에 먼저 시도
    if (lastPageId != null && lastPageFreeSpace >= record.size + 4) {
        try {
            return insertToPage(lastPageId!!, record)
        } catch (_: IllegalStateException) {
            lastPageFreeSpace = 0  // 갱신
        }
    }

    // 기존 로직
    ...
}
```

### 공간 활용률

```kotlin
// 페이지 활용률 계산
fun calculateUtilization(): Double {
    var totalSpace = 0L
    var usedSpace = 0L

    for (pageId in pageIds) {
        totalSpace += Page.PAGE_SIZE

        val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
        diskManager.readPage(pageId, buffer)

        val page = SlottedPage(buffer)
        usedSpace += (Page.PAGE_SIZE - page.remainingSpace())
    }

    return usedSpace.toDouble() / totalSpace
}

// 일반적인 활용률: 60-80%
// - 헤더/슬롯 오버헤드: ~5%
// - 페이지 내부 단편화: ~15-35%
```

### I/O 성능

```kotlin
// 삽입 시 I/O 횟수
fun insertRow(record: ByteArray): Rid {
    // 최악의 경우: N개 페이지 모두 읽기 + 새 페이지 할당
    // - Read: N + 1 (모든 페이지 + 새 페이지)
    // - Write: 1 (마지막 페이지만)
    // Total: N + 2 I/O

    // 평균 케이스: 첫 번째 페이지에 공간 있음
    // - Read: 1
    // - Write: 1
    // Total: 2 I/O
}

// 스캔 시 I/O 횟수
fun scanAll(): List<ByteArray> {
    // N개 페이지 모두 읽기
    // - Read: N
    // - Write: 0
    // Total: N I/O
}
```

## 설계 결정 사항

### 왜 Heap 구조를 사용하는가?

**장점:**
- 구현 간단 (100줄 이내)
- 삽입 빠름 (평균 O(1) I/O)
- 공간 낭비 적음 (정렬 오버헤드 없음)

**단점:**
- 검색 느림 (Full Table Scan 필요)
- 특정 레코드 찾기 O(N * M)

**사용 사례:**
- 소규모 테이블 (<1000 레코드)
- 항상 전체 스캔하는 테이블
- 인덱스가 검색을 담당하는 경우

**대안: B-Tree Table**
```kotlin
// B-Tree 기반 테이블
// 장점: 정렬된 순서, 빠른 검색 O(log N)
// 단점: 삽입 느림, 구현 복잡

class BTreeTable {
    fun insert(key: Int, record: ByteArray)
    fun search(key: Int): ByteArray  // O(log N)
    fun rangeQuery(start: Int, end: Int): List<ByteArray>
}
```

### 왜 pageIds를 메모리에 유지하는가?

**현재 설계:**
```kotlin
private val pageIds = mutableListOf<PageId>()
```

**장점:**
- 빠른 페이지 순회
- 구현 간단

**단점:**
- 메모리 사용 (페이지당 8 bytes)
- 재시작 시 복구 불가 (영속성 없음)

**프로덕션 설계:**
```kotlin
// Catalog 테이블에 저장
class Catalog {
    // 시스템 테이블: table_id → [page_ids]
    fun getTablePages(tableId: Int): List<PageId> {
        // Catalog 페이지에서 읽기
    }
}

// HeapTable은 Catalog 사용
class HeapTable(
    private val catalog: Catalog,
    private val tableId: Int
) {
    private val pageIds: List<PageId> by lazy {
        catalog.getTablePages(tableId)
    }
}
```

### 왜 삭제 연산이 없는가?

**현재 제한:**
- `insertRow()`: ✅ 지원
- `scanAll()`: ✅ 지원
- `deleteRow(rid)`: ❌ 미구현
- `updateRow(rid, newRecord)`: ❌ 미구현

**향후 구현:**
```kotlin
fun deleteRow(rid: Rid) {
    val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
    diskManager.readPage(rid.pageId, buffer)

    val page = SlottedPage(buffer)
    page.delete(rid.slotId)  // 논리적 삭제

    diskManager.writePage(rid.pageId, buffer)
}

fun updateRow(rid: Rid, newRecord: ByteArray) {
    // 방법 1: 삭제 후 재삽입
    deleteRow(rid)
    insertRow(newRecord)  // 새 RID 할당

    // 방법 2: In-place 업데이트 (크기가 같을 때만 가능)
    if (newRecord.size == oldRecord.size) {
        // 같은 슬롯에 덮어쓰기
    }
}
```

## 향후 개선 방향

### 1. 페이지 캐싱

```kotlin
// 자주 접근하는 페이지는 메모리에 캐싱
private val pageCache = LRUCache<PageId, SlottedPage>(capacity = 100)

fun insertRow(record: ByteArray): Rid {
    for (pageId in pageIds) {
        val page = pageCache.getOrPut(pageId) {
            val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
            diskManager.readPage(pageId, buffer)
            SlottedPage(buffer)
        }

        try {
            val slotId = page.insert(record)
            // 나중에 flush
            return Rid(pageId, slotId)
        } catch (_: IllegalStateException) {
            continue
        }
    }
}
```

### 2. Free Space Map

```kotlin
// 각 페이지의 여유 공간 추적
class FreeSpaceMap {
    private val freeSpaces = mutableMapOf<PageId, Int>()

    fun update(pageId: PageId, freeSpace: Int) {
        freeSpaces[pageId] = freeSpace
    }

    fun findPageWithSpace(requiredSpace: Int): PageId? {
        return freeSpaces
            .filter { it.value >= requiredSpace }
            .minByOrNull { it.value }
            ?.key
    }
}

// HeapTable에서 사용
class HeapTable(
    private val diskManager: DiskManager,
    private val freeSpaceMap: FreeSpaceMap
) {
    fun insertRow(record: ByteArray): Rid {
        val pageId = freeSpaceMap.findPageWithSpace(record.size + 4)
        if (pageId != null) {
            // 직접 해당 페이지에 삽입 (O(1))
        }
        // ...
    }
}
```

### 3. 병렬 스캔

```kotlin
// 여러 페이지를 병렬로 스캔
fun scanAllParallel(): List<ByteArray> {
    return pageIds.parallelStream()
        .flatMap { pageId ->
            val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
            diskManager.readPage(pageId, buffer)

            val page = SlottedPage(buffer)
            (0 until page.slotCount())
                .mapNotNull { slotId ->
                    try {
                        page.read(slotId)
                    } catch (e: IllegalStateException) {
                        null  // 삭제된 레코드 제외
                    }
                }
                .stream()
        }
        .collect(Collectors.toList())
}
```

### 4. Vacuum (공간 회수)

```kotlin
// 삭제된 레코드가 많은 페이지 압축
fun vacuum() {
    for (pageId in pageIds) {
        val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
        diskManager.readPage(pageId, buffer)

        val page = SlottedPage(buffer)

        // 단편화율 확인
        if (page.fragmentationRatio() > 0.3) {
            page.compact()  // 압축
            diskManager.writePage(pageId, buffer)
        }
    }
}
```

## 베스트 프랙티스

### ✅ DO

```kotlin
// 1. 리소스 해제 보장
val diskManager = DiskManager(dbPath)
val heapTable = HeapTable(diskManager)
try {
    // ... 작업 ...
} finally {
    diskManager.close()
}

// 2. 대량 삽입 시 배치 처리
fun bulkInsert(records: List<ByteArray>) {
    val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)  // 재사용

    for (record in records) {
        heapTable.insertRow(record)

        if (records.indexOf(record) % 1000 == 0) {
            // 주기적으로 진행 상황 체크
            println("Inserted ${records.indexOf(record)} records")
        }
    }
}

// 3. 스캔 시 예외 처리
val records = heapTable.scanAll()
for (record in records) {
    try {
        // 레코드 처리
        processRecord(record)
    } catch (e: Exception) {
        // 개별 레코드 오류가 전체 스캔을 멈추지 않도록
        log.warn("Failed to process record", e)
    }
}
```

### ❌ DON'T

```kotlin
// 1. 중복 페이지 읽기
for (pageId in pageIds) {
    val buffer1 = ByteBuffer.allocate(Page.PAGE_SIZE)
    diskManager.readPage(pageId, buffer1)  // ❌ 반복 I/O

    val buffer2 = ByteBuffer.allocate(Page.PAGE_SIZE)
    diskManager.readPage(pageId, buffer2)  // ❌ 중복 읽기
}

// 2. scanAll() 반복 호출
repeat(10) {
    val records = heapTable.scanAll()  // ❌ 매번 전체 스캔!
    // O(N) → O(10N)
}

// 3. RID 검증 없이 사용
val rid = Rid(pageId = 999, slotId = 999)  // ❌ 존재하지 않는 RID
getRecord(rid)  // 예외 발생
```

## 관련 컴포넌트

- **DiskManager**: 페이지 I/O 담당, HeapTable이 의존
- **SlottedPage**: 페이지 내부 레코드 관리, HeapTable이 사용
- **Catalog** (향후): 테이블 메타데이터 관리, 페이지 목록 영속화
- **Index** (향후): HeapTable의 빠른 검색을 위한 인덱스
- **BufferPool** (향후): HeapTable의 페이지 캐싱 제공
