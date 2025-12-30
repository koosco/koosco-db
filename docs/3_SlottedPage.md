# SlottedPage에 대한 문서

## SlottedPage란?

`SlottedPage`는 하나의 4KB 페이지 내부에 **가변 길이 레코드**를 효율적으로 저장하기 위한 페이지 레이아웃 구현입니다.

### DB에서의 역할

- **가변 길이 레코드 저장**: 문자열, JSON 등 크기가 다른 데이터 저장
- **공간 효율성**: 페이지 내부 단편화 최소화
- **빠른 레코드 접근**: SlotId로 O(1) 접근
- **논리적 삭제 지원**: 레코드 위치 변경 없이 삭제 마킹

## 페이지 레이아웃 구조

```
┌─────────────────────────────────────────────────┐ 0
│            Header (6 bytes)                     │
│  - pageType(2) + freeSpaceOffset(2) + slotCount(2) │
├─────────────────────────────────────────────────┤ 6
│            Slot Directory (grows ↓)             │
│  Slot 0: [offset(2) | length(2)]                │
│  Slot 1: [offset(2) | length(2)]                │
│  Slot 2: [offset(2) | length(2)]                │
│  ...                                            │
├─────────────────────────────────────────────────┤
│            Free Space                           │
│            (shrinks as data added)              │
├─────────────────────────────────────────────────┤
│            Record Data (grows ↑)                │
│  ... Record 2 ...                               │
│  ... Record 1 ...                               │
│  ... Record 0 ...                               │
└─────────────────────────────────────────────────┘ 4096
```

### 핵심 설계 원칙

**1. 양방향 성장 구조**
- Slot Directory: 위에서 아래로 성장 (↓)
- Record Data: 아래에서 위로 성장 (↑)
- Free Space: 중간에서 압축

**2. 간접 참조 (Indirection)**
- 레코드를 직접 슬롯 ID로 참조하지 않고, Slot Directory를 통해 접근
- 레코드 위치가 변경되어도 슬롯 ID는 유지 (향후 compaction 지원)

**3. 절대 위치 기반 접근**
- ByteBuffer의 position 대신 고정 offset 사용
- 멀티 필드 접근 시 position 오염 방지

## 헤더 구조

```kotlin
┌─────────────────────────────────────────┐
│  Offset  │  Size  │  Field             │
├──────────┼────────┼────────────────────┤
│    0     │   2    │  pageType          │
│    2     │   2    │  freeSpaceOffset   │
│    4     │   2    │  slotCount         │
└─────────────────────────────────────────┘
Total: 6 bytes
```

### 필드 설명

**pageType** (2 bytes)
- 페이지 종류 식별 (현재는 1로 고정)
- 향후 확장: heap page(1), index page(2), catalog page(3) 등

**freeSpaceOffset** (2 bytes)
- 다음 레코드를 삽입할 위치 (페이지 끝에서부터 역순)
- 초기값: 4096 (페이지 맨 끝)
- 레코드 삽입마다 감소

**slotCount** (2 bytes)
- 현재 페이지에 존재하는 슬롯 개수
- 삭제된 레코드도 포함 (슬롯은 재사용 안 함)

## Slot Directory

각 슬롯은 4바이트로 구성:

```kotlin
┌───────────────────────────┐
│  Offset (2)  │  Length (2) │
└───────────────────────────┘
```

**Slot 계산 공식:**
```kotlin
val slotPosition = HEADER_SIZE + slotId * SLOT_SIZE
//                     6       +  slotId *    4
```

**예시:**
- Slot 0: offset 6 (헤더 바로 다음)
- Slot 1: offset 10
- Slot 2: offset 14
- Slot N: offset 6 + N * 4

## 레코드 삽입 알고리즘

### insert(record: ByteArray): Int

```kotlin
┌─────────────────────────────────────────┐
│  Step 1: 공간 확인                       │
├─────────────────────────────────────────┤
│  필요 공간 = record.size + SLOT_SIZE(4) │
│  사용 가능 = freeSpaceOffset - slotDirEnd │
│  → 공간 부족 시 예외 발생                 │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 2: 레코드 데이터 쓰기               │
├─────────────────────────────────────────┤
│  recordOffset = freeSpace - record.size │
│  buffer.position(recordOffset)          │
│  buffer.put(record)                     │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 3: 슬롯 디렉토리 업데이트           │
├─────────────────────────────────────────┤
│  slotPos = HEADER_SIZE + slotCount * 4  │
│  buffer.putShort(slotPos, recordOffset) │
│  buffer.putShort(slotPos+2, record.size)│
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 4: 헤더 업데이트                   │
├─────────────────────────────────────────┤
│  setFreeSpaceOffset(recordOffset)       │
│  setSlotCount(slotCount + 1)            │
│  return slotCount (새 슬롯 ID)           │
└─────────────────────────────────────────┘
```

### 삽입 예제

```kotlin
val page = SlottedPage(buffer)
page.init()

// 첫 번째 레코드 삽입
val record1 = "Hello".toByteArray()  // 5 bytes
val slotId1 = page.insert(record1)   // slotId = 0

// 페이지 상태:
// Header: pageType=1, freeSpace=4091, slotCount=1
// Slot 0: offset=4091, length=5
// Record at 4091: "Hello"

// 두 번째 레코드 삽입
val record2 = "World!".toByteArray()  // 6 bytes
val slotId2 = page.insert(record2)    // slotId = 1

// 페이지 상태:
// Header: pageType=1, freeSpace=4085, slotCount=2
// Slot 0: offset=4091, length=5
// Slot 1: offset=4085, length=6
// Record at 4091: "Hello"
// Record at 4085: "World!"
```

### 공간 부족 처리

```kotlin
try {
    val slotId = page.insert(largeRecord)
} catch (e: IllegalStateException) {
    // "Not enough space" 예외 발생
    // 상위 계층(HeapTable)이 새 페이지 할당 필요
}
```

## 레코드 읽기 알고리즘

### read(slotId: Int): ByteArray

```kotlin
┌─────────────────────────────────────────┐
│  Step 1: 슬롯 ID 유효성 검증              │
├─────────────────────────────────────────┤
│  require(slotId in 0 until slotCount)   │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 2: 슬롯에서 offset, length 읽기    │
├─────────────────────────────────────────┤
│  slotPos = HEADER_SIZE + slotId * 4     │
│  offset = buffer.getShort(slotPos)      │
│  length = buffer.getShort(slotPos + 2)  │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 3: 삭제 여부 확인                  │
├─────────────────────────────────────────┤
│  if (length == 0)                       │
│      throw "Deleted record"             │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 4: 레코드 데이터 읽기               │
├─────────────────────────────────────────┤
│  data = ByteArray(length)               │
│  buffer.position(offset)                │
│  buffer.get(data)                       │
│  return data                            │
└─────────────────────────────────────────┘
```

### 읽기 예제

```kotlin
val page = SlottedPage(buffer)

// 슬롯 0의 레코드 읽기
val record = page.read(slotId = 0)
println(String(record))  // "Hello"

// 삭제된 레코드 읽기 시도
try {
    page.read(deletedSlotId)
} catch (e: IllegalStateException) {
    // "Deleted record" 예외 발생
}
```

## 레코드 삭제 알고리즘

### delete(slotId: Int)

```kotlin
// 논리적 삭제: length를 0으로 설정
val slotPos = HEADER_SIZE + slotId * SLOT_SIZE
buffer.putShort(slotPos + 2, 0)  // length = 0

// ⚠️ 주의: 실제 레코드 데이터는 그대로 남음
// ⚠️ 공간은 회수되지 않음 (향후 compaction 필요)
```

### 삭제의 한계와 향후 개선

**현재 구현:**
- 논리적 삭제만 지원 (length = 0 마킹)
- 삭제된 공간은 재사용 불가
- 페이지 단편화 발생 가능

**향후 개선 방향:**
```kotlin
// Compaction: 삭제된 레코드 공간 회수
fun compact() {
    // 1. 살아있는 레코드들을 페이지 끝으로 재배치
    // 2. Slot Directory 업데이트
    // 3. Free Space 통합
}

// Vacuum: 여러 페이지의 레코드 재배치
fun vacuum() {
    // 단편화된 페이지들을 압축하여 빈 페이지 회수
}
```

## 페이지 초기화

### init()

```kotlin
fun init() {
    buffer.clear()  // position = 0, limit = capacity

    setPageType(1)                    // 고정값
    setSlotCount(0)                   // 슬롯 없음
    setFreeSpaceOffset(Page.PAGE_SIZE) // 4096 (맨 끝)

    buffer.clear()  // 다시 position 0으로
}
```

**초기 상태:**
```
┌─────────────────────────────────────────┐ 0
│  Header: type=1, free=4096, slots=0     │
├─────────────────────────────────────────┤ 6
│                                         │
│         Empty Free Space                │
│         (4090 bytes available)          │
│                                         │
└─────────────────────────────────────────┘ 4096
```

## 사용 예제

### 기본 사용 패턴

```kotlin
// 1. 페이지 생성 및 초기화
val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
val page = SlottedPage(buffer)
page.init()

// 2. 레코드 삽입
val records = listOf(
    "Alice".toByteArray(),
    "Bob".toByteArray(),
    "Charlie".toByteArray()
)

val slotIds = mutableListOf<Int>()
for (record in records) {
    try {
        val slotId = page.insert(record)
        slotIds.add(slotId)
    } catch (e: IllegalStateException) {
        println("페이지 공간 부족!")
        break
    }
}

// 3. 레코드 읽기
for (slotId in slotIds) {
    val data = page.read(slotId)
    println("Slot $slotId: ${String(data)}")
}

// 4. 레코드 삭제
page.delete(slotIds[1])  // Bob 삭제

// 5. 전체 스캔
for (i in 0 until page.slotCount()) {
    try {
        val data = page.read(i)
        println("Active record: ${String(data)}")
    } catch (e: IllegalStateException) {
        println("Deleted slot: $i")
    }
}
```

### DiskManager와 함께 사용

```kotlin
val diskManager = DiskManager(Path.of("data/mydb.db"))
val pageId = diskManager.allocatePage()

// 페이지 읽기
val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
diskManager.readPage(pageId, buffer)

// 페이지 초기화
val page = SlottedPage(buffer)
page.init()

// 데이터 삽입
val slotId = page.insert("Hello World".toByteArray())

// 페이지 쓰기 (디스크에 영구 저장)
diskManager.writePage(pageId, buffer)
```

## 공간 계산

### 최대 저장 가능 레코드 수

```kotlin
// 페이지 크기: 4096 bytes
// 헤더: 6 bytes
// 사용 가능 공간: 4090 bytes

// 레코드 크기가 R bytes일 때:
// 슬롯 크기: 4 bytes
// 필요 공간: R + 4 bytes

// 최대 레코드 수 = 4090 / (R + 4)

// 예시:
// - 10 byte 레코드: 4090 / 14 = 292개
// - 50 byte 레코드: 4090 / 54 = 75개
// - 100 byte 레코드: 4090 / 104 = 39개
```

### 단편화 계산

```kotlin
fun fragmentationRatio(): Double {
    val totalSpace = Page.PAGE_SIZE - HEADER_SIZE
    val usedSpace = (Page.PAGE_SIZE - getFreeSpaceOffset()) + (slotCount() * SLOT_SIZE)
    val wastedSpace = totalSpace - usedSpace - remainingSpace()

    return wastedSpace.toDouble() / totalSpace
}

fun remainingSpace(): Int {
    val slotDirEnd = HEADER_SIZE + slotCount() * SLOT_SIZE
    return getFreeSpaceOffset() - slotDirEnd
}
```

## 설계 결정 사항

### 왜 Slotted Page 구조를 사용하는가?

**대안 1: 고정 길이 레코드**
```
❌ 단점:
- 가변 길이 데이터 비효율 (문자열, JSON 등)
- 공간 낭비 심각 (최대 길이만큼 할당)

✅ Slotted Page 장점:
- 가변 길이 레코드 효율적 저장
- 공간 활용률 높음
```

**대안 2: Linked List**
```
❌ 단점:
- 레코드 접근 O(N)
- 포인터 체이싱으로 캐시 미스 증가

✅ Slotted Page 장점:
- 슬롯 ID로 O(1) 접근
- 메모리 지역성 좋음
```

### 왜 양방향 성장 구조인가?

```
┌─────────────────────────────────────────┐
│  Slot Directory (↓)                     │
│                                         │
│         Free Space (shrink)             │
│                                         │
│  Record Data (↑)                        │
└─────────────────────────────────────────┘
```

**장점:**
- Free Space를 한곳에 집중 (단편화 최소화)
- 공간 확인 간단: `freeSpace - slotDirEnd`
- 메모리 압축(compaction) 용이

**대안 (단방향 성장):**
```
❌ 문제:
- Free Space가 여러 곳에 분산
- 단편화 심각
- 압축 알고리즘 복잡
```

### 왜 절대 위치 기반 접근인가?

```kotlin
// ✅ 절대 위치 (현재)
buffer.getShort(OFFSET_SLOT_COUNT)
buffer.putInt(6, freeSpaceOffset)

// ❌ 상대 위치 (대안)
buffer.position(4)
val slotCount = buffer.getShort()
buffer.flip()  // position 오염!
```

**절대 위치 장점:**
- position 오염 걱정 없음
- 멀티 필드 접근 안전
- 코드 가독성 향상

## 성능 고려사항

### 삽입 성능

```kotlin
// O(1) 시간 복잡도
// - 헤더 읽기: O(1)
// - 레코드 쓰기: O(1)
// - 슬롯 쓰기: O(1)
// - 헤더 업데이트: O(1)

fun insert(record: ByteArray): Int {
    // ... 모두 상수 시간 연산 ...
}
```

### 읽기 성능

```kotlin
// O(1) 시간 복잡도
// - 슬롯 위치 계산: O(1)
// - offset, length 읽기: O(1)
// - 레코드 데이터 복사: O(length)

fun read(slotId: Int): ByteArray {
    // ... 모두 상수 시간 연산 ...
}
```

### 공간 효율성

```kotlin
// 오버헤드: 헤더(6) + 슬롯(4 * N)
// N개 레코드, 평균 크기 R일 때:
// 오버헤드 비율 = (6 + 4*N) / (6 + 4*N + R*N)
//              = (6 + 4*N) / (6 + (R+4)*N)

// 예시 (R=100):
// 10개 레코드: (6 + 40) / (6 + 1040) = 4.4%
// 100개 레코드: (6 + 400) / (6 + 10400) = 3.9%
```

## 에러 처리

### 주요 에러 시나리오

**1. 공간 부족**
```kotlin
try {
    page.insert(record)
} catch (e: IllegalStateException) {
    // "Not enough space"
    // → 상위 계층이 새 페이지 할당
}
```

**2. 잘못된 슬롯 ID**
```kotlin
try {
    page.read(slotId = 999)
} catch (e: IllegalArgumentException) {
    // "slotId out of range"
}
```

**3. 삭제된 레코드 접근**
```kotlin
try {
    page.read(deletedSlotId)
} catch (e: IllegalStateException) {
    // "Deleted record"
    // → 레코드 스캔 시 건너뛰기
}
```

## 베스트 프랙티스

### ✅ DO

```kotlin
// 1. 페이지 초기화 항상 호출
val page = SlottedPage(buffer)
page.init()  // 필수!

// 2. 삽입 전 공간 확인 (선택적)
if (page.remainingSpace() >= record.size + 4) {
    page.insert(record)
}

// 3. 삭제된 레코드 스캔 시 예외 처리
for (i in 0 until page.slotCount()) {
    try {
        val record = page.read(i)
        // ... 처리 ...
    } catch (e: IllegalStateException) {
        // 삭제된 레코드, 건너뛰기
    }
}
```

### ❌ DON'T

```kotlin
// 1. 초기화 없이 사용
val page = SlottedPage(buffer)
page.insert(record)  // ❌ 초기화 안 됨!

// 2. position 기반 접근과 혼용
buffer.putInt(100)  // position 이동
page.insert(record)  // ❌ position 오염!

// 3. 슬롯 ID 검증 없이 사용
val slotId = userInput  // ❌ 검증 없음
page.read(slotId)
```

## 디버깅 팁

### 페이지 덤프 유틸리티

```kotlin
fun SlottedPage.dump() {
    println("=== SlottedPage Dump ===")
    println("Slot Count: ${slotCount()}")
    println("Free Space Offset: ${getFreeSpaceOffset()}")
    println("Remaining Space: ${remainingSpace()}")
    println()

    println("=== Slots ===")
    for (i in 0 until slotCount()) {
        val slotPos = HEADER_SIZE + i * SLOT_SIZE
        val offset = buffer.getShort(slotPos).toInt()
        val length = buffer.getShort(slotPos + 2).toInt()

        if (length == 0) {
            println("Slot $i: DELETED")
        } else {
            println("Slot $i: offset=$offset, length=$length")
        }
    }

    println()
    println("=== Records ===")
    for (i in 0 until slotCount()) {
        try {
            val data = read(i)
            println("Record $i: ${String(data)}")
        } catch (e: IllegalStateException) {
            println("Record $i: DELETED")
        }
    }
}
```

### 단편화 분석

```kotlin
fun SlottedPage.analyzeFragmentation() {
    val totalSpace = Page.PAGE_SIZE - HEADER_SIZE
    val slotDirSize = slotCount() * SLOT_SIZE
    val recordDataSize = Page.PAGE_SIZE - getFreeSpaceOffset()
    val freeSpace = remainingSpace()
    val overhead = HEADER_SIZE + slotDirSize

    println("=== Space Analysis ===")
    println("Total: $totalSpace bytes")
    println("Header: ${HEADER_SIZE} bytes")
    println("Slot Directory: $slotDirSize bytes")
    println("Record Data: $recordDataSize bytes")
    println("Free Space: $freeSpace bytes")
    println("Overhead: $overhead bytes (${overhead * 100 / totalSpace}%)")

    val fragmentation = fragmentationRatio()
    println("Fragmentation: ${(fragmentation * 100).toInt()}%")

    if (fragmentation > 0.2) {
        println("⚠️ High fragmentation detected! Consider compaction.")
    }
}
```

## 향후 개선 방향

### 1. Compaction (공간 회수)

```kotlin
fun compact() {
    // 살아있는 레코드들을 재배치
    val liveRecords = mutableListOf<Pair<Int, ByteArray>>()

    for (i in 0 until slotCount()) {
        try {
            liveRecords.add(i to read(i))
        } catch (e: IllegalStateException) {
            // 삭제된 레코드 제외
        }
    }

    // 페이지 재초기화
    init()

    // 레코드 재삽입
    for ((oldSlotId, record) in liveRecords) {
        insert(record)
    }
}
```

### 2. 슬롯 재사용

```kotlin
// 현재: 슬롯은 절대 재사용 안 됨
// 향후: 삭제된 슬롯 추적 및 재사용

private val freeSlots = mutableListOf<Int>()

fun delete(slotId: Int) {
    // 기존 로직
    freeSlots.add(slotId)
}

fun insert(record: ByteArray): Int {
    if (freeSlots.isNotEmpty()) {
        val slotId = freeSlots.removeAt(0)
        // 슬롯 재사용
        return slotId
    }
    // 새 슬롯 할당
}
```

### 3. 가변 길이 슬롯

```kotlin
// 현재: 모든 슬롯 4 bytes 고정
// 향후: 레코드 크기에 따라 슬롯 크기 조절

// Small record (<256 bytes): 2 byte offset, 1 byte length
// Large record: 2 byte offset, 2 byte length
```

## 관련 컴포넌트

- **DiskManager**: 페이지를 디스크에서 읽어와 SlottedPage로 전달
- **HeapTable**: SlottedPage를 사용하여 테이블 데이터 관리
- **BufferPool** (향후): 자주 사용되는 SlottedPage를 메모리에 캐싱
- **Catalog** (향후): 페이지 메타데이터 관리
