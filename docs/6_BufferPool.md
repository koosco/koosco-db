# BufferPool에 대한 문서

## BufferPool이란?

`BufferPool`은 **페이지 캐싱 레이어**로, 디스크와 상위 레이어 사이에서 자주 접근하는 페이지를 메모리에 캐싱하여 디스크 I/O를 최소화합니다. 데이터베이스 성능의 핵심 컴포넌트입니다.

### DB에서의 역할

- **캐싱**: 자주 사용하는 페이지를 메모리에 유지
- **I/O 최적화**: 디스크 읽기/쓰기 횟수 감소
- **성능 향상**: 메모리 접근은 디스크보다 1000배 이상 빠름
- **일관성 보장**: Dirty tracking으로 수정된 페이지 추적
- **Flush 관리**: 적절한 시점에 변경 사항을 디스크에 기록

## 핵심 개념

### BufferPool 없는 경우 vs 있는 경우

```
Without BufferPool (❌)
┌─────────────────────────────────┐
│    Application                  │
└──────────┬──────────────────────┘
           │ 매번 디스크 I/O
           ▼
┌─────────────────────────────────┐
│    DiskManager                  │
│    (디스크 직접 접근)            │
└─────────────────────────────────┘

매 페이지 접근마다 디스크 I/O 발생
→ 느린 성능 (5-10ms per I/O)

With BufferPool (✅)
┌─────────────────────────────────┐
│    Application                  │
└──────────┬──────────────────────┘
           │ getPage(pageId)
           ▼
┌─────────────────────────────────┐
│    BufferPool                   │
│    (메모리 캐시)                 │
│    - Cache Hit: 즉시 반환       │
│    - Cache Miss: 디스크 읽기     │
└──────────┬──────────────────────┘
           │ Cache Miss 시에만
           ▼
┌─────────────────────────────────┐
│    DiskManager                  │
└─────────────────────────────────┘

캐시 히트 시 디스크 I/O 없음
→ 빠른 성능 (~100ns 메모리 접근)
```

### Cache Hit vs Cache Miss

```
Cache Hit (페이지가 이미 메모리에 있음)
┌─────────────────────────────────┐
│  getPage(pageId=5)              │
├─────────────────────────────────┤
│  pageTable: {5 → frameId: 2}    │
│  → frames[2] 즉시 반환          │
│  → 디스크 I/O 없음 (빠름!)      │
└─────────────────────────────────┘

Cache Miss (페이지가 메모리에 없음)
┌─────────────────────────────────┐
│  getPage(pageId=10)             │
├─────────────────────────────────┤
│  1. 빈 프레임 할당 (frameId: 3) │
│  2. 디스크에서 읽기              │
│  3. pageTable에 등록            │
│  4. frames[3] 반환              │
└─────────────────────────────────┘
```

### Frame과 Page Table

```
BufferPool 내부 구조
┌──────────────────────────────────────────────┐
│  frames (Array<Frame>)                       │
│  ┌────────┬────────┬────────┬────────┐      │
│  │Frame 0 │Frame 1 │Frame 2 │Frame 3 │      │
│  │PageId:1│PageId:5│PageId:7│ Empty  │      │
│  │dirty:✓ │dirty:✗ │dirty:✓ │        │      │
│  │4KB buf │4KB buf │4KB buf │4KB buf │      │
│  └────────┴────────┴────────┴────────┘      │
└──────────────────────────────────────────────┘
         ▲
         │ 매핑
         │
┌──────────────────────────────────────────────┐
│  pageTable (Map<PageId, FrameId>)            │
│  ┌──────────────────────────────────┐        │
│  │ PageId → FrameId                │        │
│  ├──────────────────────────────────┤        │
│  │   1    →    0                   │        │
│  │   5    →    1                   │        │
│  │   7    →    2                   │        │
│  └──────────────────────────────────┘        │
└──────────────────────────────────────────────┘
```

**Frame:**
- PageId: 어떤 페이지를 담고 있는지
- buffer: 실제 페이지 데이터 (4KB ByteBuffer)
- dirty: 수정 여부 플래그

**Page Table:**
- PageId → FrameId 매핑
- O(1) 조회 성능
- 캐시 히트/미스 판단

### Dirty Tracking

```
Dirty Page Lifecycle
┌─────────────────────────────────────┐
│  Step 1: getPage(pageId)            │
│  → buffer 반환, dirty = false       │
└─────────────────────────────────────┘
        ↓
┌─────────────────────────────────────┐
│  Step 2: buffer 수정                │
│  → 애플리케이션이 데이터 변경        │
└─────────────────────────────────────┘
        ↓
┌─────────────────────────────────────┐
│  Step 3: markDirty(pageId)          │
│  → dirty = true 설정                │
│  → "이 페이지는 디스크와 다름"       │
└─────────────────────────────────────┘
        ↓
┌─────────────────────────────────────┐
│  Step 4: flushPage(pageId)          │
│  → dirty 체크                       │
│  → dirty면 디스크에 기록             │
│  → dirty = false로 초기화           │
└─────────────────────────────────────┘
```

**Dirty Flag의 중요성:**
- 수정된 페이지만 디스크에 기록 (성능 최적화)
- 불필요한 디스크 쓰기 방지
- Crash Recovery 시 변경 사항 추적

## BufferPool 아키텍처

```
┌─────────────────────────────────────┐
│      Application Layer              │
│  (HeapTable, Index, Catalog)        │
└──────────────┬──────────────────────┘
               │ getPage(), markDirty()
               ▼
┌─────────────────────────────────────┐
│         BufferPool                  │
│  ┌───────────────────────────┐     │
│  │  Page Table (HashMap)     │     │
│  │  PageId → FrameId 매핑    │     │
│  └───────────────────────────┘     │
│  ┌───────────────────────────┐     │
│  │  Frames (Array)           │     │
│  │  - buffer: ByteBuffer     │     │
│  │  - pageId: PageId?        │     │
│  │  - dirty: Boolean         │     │
│  └───────────────────────────┘     │
└──────────────┬──────────────────────┘
               │ Cache Miss 시
               ▼
┌─────────────────────────────────────┐
│         DiskManager                 │
│  - readPage(pageId, buffer)         │
│  - writePage(pageId, buffer)        │
└─────────────────────────────────────┘
```

## 주요 메서드

### getPage(pageId: PageId): ByteBuffer

페이지를 버퍼 풀에서 조회하거나 디스크에서 로드

```kotlin
┌─────────────────────────────────────────┐
│  Step 1: 페이지 테이블 조회               │
├─────────────────────────────────────────┤
│  hit = pageTable[pageId]                │
│  if (hit != null) → Cache Hit!          │
└─────────────────────────────────────────┘
        ↓ Hit
┌─────────────────────────────────────────┐
│  Step 2a: Cache Hit 처리                │
├─────────────────────────────────────────┤
│  frames[hit].buffer.duplicate() 반환    │
│  → 디스크 I/O 없음 (빠름!)              │
└─────────────────────────────────────────┘

        ↓ Miss
┌─────────────────────────────────────────┐
│  Step 2b: Cache Miss 처리               │
├─────────────────────────────────────────┤
│  frameId = allocateFrameOrThrow()       │
│  → 빈 프레임 할당 (또는 예외)            │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 3: 프레임 바인딩                   │
├─────────────────────────────────────────┤
│  frame.bind(pageId)                     │
│  → pageId 설정, dirty = false           │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 4: 디스크에서 읽기                 │
├─────────────────────────────────────────┤
│  diskManager.readPage(pageId, buffer)   │
│  → 실제 디스크 I/O 발생                 │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 5: 페이지 테이블 등록               │
├─────────────────────────────────────────┤
│  pageTable[pageId] = frameId            │
│  → 다음 조회 시 Cache Hit               │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 6: 버퍼 반환                       │
├─────────────────────────────────────────┤
│  frame.buffer.duplicate()               │
│  → 복사본 반환 (position 독립적)         │
└─────────────────────────────────────────┘
```

### 조회 예제

```kotlin
val bufferPool = BufferPool(
    diskManager = diskManager,
    poolSize = 64,
    pageSize = 4096
)

// 첫 번째 조회: Cache Miss
val buffer1 = bufferPool.getPage(pageId = 5)
// → 디스크에서 읽기 (느림, ~5ms)
// → pageTable에 등록
// → frames[0]에 저장

// 두 번째 조회: Cache Hit!
val buffer2 = bufferPool.getPage(pageId = 5)
// → pageTable 조회
// → frames[0] 즉시 반환 (빠름, ~100ns)
// → 디스크 I/O 없음!
```

### 내부 동작 흐름

```kotlin
fun getPage(pageId: PageId): ByteBuffer {
    // Step 1: Cache Hit 체크
    val hit = pageTable[pageId]
    if (hit != null) {
        // Cache Hit: 즉시 반환
        return frames[hit].buffer.duplicate()
    }

    // Step 2: Cache Miss - 빈 프레임 할당
    val frameId = allocateFrameOrThrow()
    val frame = frames[frameId]

    // Step 3: 프레임 바인딩
    frame.bind(pageId)  // pageId 설정, dirty = false

    // Step 4: 디스크에서 읽기
    diskManager.readPage(pageId, frame.buffer)

    // Step 5: 페이지 테이블 등록
    pageTable[pageId] = frameId

    // Step 6: 버퍼 반환
    return frame.buffer.duplicate()
}
```

### duplicate() 반환의 의미

```kotlin
// duplicate() 반환
val buffer = bufferPool.getPage(pageId)

// 장점:
// 1. position/limit 독립적
buffer.position(100)  // 다른 사용자에게 영향 없음

// 2. underlying array 공유
buffer.put(0, 42.toByte())  // 원본 frame도 수정됨!

// 주의:
// - 수정 후 반드시 markDirty() 호출 필요
bufferPool.markDirty(pageId)
```

### markDirty(pageId: PageId)

페이지가 수정되었음을 BufferPool에 알림

```kotlin
┌─────────────────────────────────────────┐
│  Step 1: 페이지 테이블 조회               │
├─────────────────────────────────────────┤
│  frameId = pageTable[pageId]            │
│  없으면 예외 발생                        │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 2: Dirty 플래그 설정               │
├─────────────────────────────────────────┤
│  frames[frameId].dirty = true           │
│  → "이 페이지는 디스크에 기록 필요"      │
└─────────────────────────────────────────┘
```

### 사용 예제

```kotlin
val bufferPool = BufferPool(diskManager, 64, 4096)

// 페이지 조회
val buffer = bufferPool.getPage(pageId = 5)

// 데이터 수정
buffer.putInt(0, 12345)
buffer.putLong(4, 67890L)

// Dirty 표시 (필수!)
bufferPool.markDirty(pageId = 5)

// 나중에 디스크에 기록
bufferPool.flushPage(pageId = 5)
```

### flushPage(pageId: PageId)

특정 페이지를 디스크에 기록

```kotlin
┌─────────────────────────────────────────┐
│  Step 1: 페이지 테이블 조회               │
├─────────────────────────────────────────┤
│  frameId = pageTable[pageId]            │
│  없으면 종료 (아무 작업 안 함)            │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 2: Dirty 체크                     │
├─────────────────────────────────────────┤
│  if (!frame.dirty) → 종료               │
│  → 수정 안 된 페이지는 쓰기 불필요        │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 3: 디스크에 기록                   │
├─────────────────────────────────────────┤
│  diskManager.writePage(pageId, buffer)  │
│  → 실제 디스크 I/O 발생                 │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 4: Dirty 플래그 초기화             │
├─────────────────────────────────────────┤
│  frame.dirty = false                    │
│  → "디스크와 동기화됨"                   │
└─────────────────────────────────────────┘
```

### flushAll()

모든 Dirty 페이지를 디스크에 기록

```kotlin
fun flushAll() {
    for (frame in frames) {
        val pageId = frame.pageId ?: continue  // 빈 프레임 skip

        if (frame.dirty) {
            diskManager.writePage(pageId, frame.buffer)
            frame.dirty = false
        }
    }
}
```

**사용 시점:**
- 데이터베이스 종료 시
- 트랜잭션 커밋 시
- 주기적인 체크포인트
- 버퍼 풀 가득 참 (eviction 전)

### Flush 예제

```kotlin
val bufferPool = BufferPool(diskManager, 64, 4096)

// 여러 페이지 수정
for (pageId in 0 until 10) {
    val buffer = bufferPool.getPage(pageId)
    buffer.putInt(0, pageId * 100)
    bufferPool.markDirty(pageId)
}

// 방법 1: 특정 페이지만 flush
bufferPool.flushPage(pageId = 5)

// 방법 2: 모든 페이지 flush
bufferPool.flushAll()

// DB 종료 시 반드시 호출
bufferPool.flushAll()
diskManager.close()
```

### allocateFrameOrThrow(): Int (private)

빈 프레임을 할당하거나 예외 발생

```kotlin
private fun allocateFrameOrThrow(): Int {
    if (nextFreeFrame >= poolSize) {
        throw IllegalStateException(
            "BufferPool is full (poolSize=$poolSize). " +
            "Implement eviction later or increase pool size."
        )
    }
    return nextFreeFrame++
}
```

**현재 제한:**
- Eviction 미구현
- 버퍼 풀이 가득 차면 예외 발생
- `poolSize` 이상의 페이지 캐싱 불가

**향후 개선:**
- LRU/Clock eviction 구현
- Pin/Unpin 메커니즘
- 동적 크기 조정

## withPageForWrite 확장 함수

안전한 페이지 수정을 위한 헬퍼 함수

```kotlin
inline fun BufferPool.withPageForWrite(
    pageId: PageId,
    block: (ByteBuffer) -> Unit
) {
    val buffer = getPage(pageId)
    block(buffer)
    markDirty(pageId)  // 자동 dirty 표시
}
```

### 사용 예제

```kotlin
// Before: 수동 dirty 관리
val buffer = bufferPool.getPage(pageId)
buffer.putInt(0, 42)
bufferPool.markDirty(pageId)  // 까먹기 쉬움!

// After: 자동 dirty 관리
bufferPool.withPageForWrite(pageId) { buffer ->
    buffer.putInt(0, 42)
    // markDirty() 자동 호출
}
```

**장점:**
- markDirty() 호출 자동화
- 코드 간결성
- 실수 방지

## Frame 구조

```kotlin
data class Frame(
    var pageId: PageId?,     // null = 빈 프레임
    val buffer: ByteBuffer,  // 4KB 페이지 데이터
    var dirty: Boolean       // 수정 여부
) {
    fun bind(newPageId: PageId) {
        pageId = newPageId
        dirty = false
        buffer.clear()  // position = 0, limit = capacity
    }

    companion object {
        fun empty(pageSize: Int): Frame = Frame(
            pageId = null,
            buffer = ByteBuffer.allocate(pageSize),
            dirty = false
        )
    }
}
```

**Frame 상태 전환:**
```
Empty Frame
pageId = null
dirty = false
buffer = [0, 0, 0, ...]
        ↓ bind(pageId=5)
Bound Frame
pageId = 5
dirty = false
buffer = [0, 0, 0, ...]
        ↓ diskManager.readPage()
Loaded Frame
pageId = 5
dirty = false
buffer = [data from disk]
        ↓ 수정 + markDirty()
Dirty Frame
pageId = 5
dirty = true
buffer = [modified data]
        ↓ flushPage()
Clean Frame
pageId = 5
dirty = false
buffer = [data synced to disk]
```

## 사용 예제

### 기본 사용 패턴

```kotlin
// 1. BufferPool 생성
val diskManager = FileDiskManager(Path.of("data/koosco.db"))
val bufferPool = BufferPool(
    diskManager = diskManager,
    poolSize = 64,      // 64개 페이지 캐싱
    pageSize = 4096     // 4KB per page
)

// 2. 페이지 읽기
val pageId = 0
val buffer = bufferPool.getPage(pageId)

// 3. 데이터 읽기
val value = buffer.getInt(0)
println("Value: $value")

// 4. 데이터 쓰기
buffer.putInt(0, 12345)
bufferPool.markDirty(pageId)

// 5. 디스크에 기록
bufferPool.flushPage(pageId)

// 6. 종료 시 정리
bufferPool.flushAll()
diskManager.close()
```

### HeapTable과 통합

```kotlin
class HeapTable(
    private val diskManager: DiskManager,
    private val bufferPool: BufferPool
) {
    private val pageIds = mutableListOf<PageId>()

    fun insertRow(record: ByteArray): Rid {
        // BufferPool 사용으로 I/O 최적화
        for (pageId in pageIds) {
            val buffer = bufferPool.getPage(pageId)  // Cache hit 가능!
            val page = SlottedPage(buffer)

            try {
                val slotId = page.insert(record)
                bufferPool.markDirty(pageId)  // 수정 표시
                return Rid(pageId, slotId)
            } catch (_: IllegalStateException) {
                continue
            }
        }

        // 새 페이지 할당
        val newPageId = allocateNewPage()
        val buffer = bufferPool.getPage(newPageId)
        val page = SlottedPage(buffer)
        page.init()

        val slotId = page.insert(record)
        bufferPool.markDirty(newPageId)

        return Rid(newPageId, slotId)
    }

    fun scanAll(): List<ByteArray> {
        val result = mutableListOf<ByteArray>()

        for (pageId in pageIds) {
            val buffer = bufferPool.getPage(pageId)  // Cache hit!
            val page = SlottedPage(buffer)

            for (slotId in 0 until page.slotCount()) {
                try {
                    result.add(page.read(slotId))
                } catch (_: IllegalStateException) {
                    // Deleted record
                }
            }
        }

        return result
    }
}
```

### 캐시 효과 측정

```kotlin
fun measureCacheEffect() {
    val bufferPool = BufferPool(diskManager, 64, 4096)
    val pageId = 0

    // 첫 번째 접근: Cache Miss
    val start1 = System.nanoTime()
    bufferPool.getPage(pageId)
    val time1 = System.nanoTime() - start1
    println("First access (miss): ${time1 / 1_000_000.0}ms")

    // 두 번째 접근: Cache Hit
    val start2 = System.nanoTime()
    bufferPool.getPage(pageId)
    val time2 = System.nanoTime() - start2
    println("Second access (hit): ${time2 / 1_000_000.0}ms")

    println("Speedup: ${time1.toDouble() / time2}x")

    // 예상 결과:
    // First access (miss): 5.2ms    (디스크 I/O)
    // Second access (hit): 0.001ms  (메모리 접근)
    // Speedup: 5200x
}
```

### 대량 데이터 처리

```kotlin
fun bulkInsert(bufferPool: BufferPool, records: List<ByteArray>) {
    val pageId = 0
    val buffer = bufferPool.getPage(pageId)  // 한 번만 로드

    for (record in records) {
        // buffer 재사용 - 디스크 I/O 없음!
        buffer.position(nextOffset)
        buffer.put(record)
        nextOffset += record.size
    }

    bufferPool.markDirty(pageId)
    bufferPool.flushPage(pageId)  // 마지막에 한 번만 쓰기

    // 성능 개선:
    // - Before (no cache): 1000 reads + 1000 writes = 2000 I/O
    // - After (with cache): 1 read + 1 write = 2 I/O
    // → 1000배 I/O 감소!
}
```

### 디버깅

```kotlin
// 로드된 페이지 확인
val loadedPages = bufferPool.loadedPages()
println("Loaded pages: $loadedPages")
// 출력: Loaded pages: [0, 1, 5, 7, 10]

// 캐시 상태 확인
println("Cache size: ${loadedPages.size} / ${poolSize}")
println("Cache usage: ${loadedPages.size * 100.0 / poolSize}%")
```

## 성능 분석

### 시간 복잡도

| 연산 | 시간 복잡도 | 설명 |
|------|-------------|------|
| **getPage (hit)** | O(1) | HashMap 조회 + 메모리 접근 (~100ns) |
| **getPage (miss)** | O(1) + I/O | HashMap 조회 + 디스크 읽기 (~5ms) |
| **markDirty** | O(1) | HashMap 조회 + 플래그 설정 |
| **flushPage** | O(1) + I/O | HashMap 조회 + 디스크 쓰기 (~5ms) |
| **flushAll** | O(N) + I/O | N = 프레임 수, Dirty 페이지만 쓰기 |

### 공간 복잡도

```kotlin
// BufferPool 메모리 사용량
class BufferPool(
    poolSize: Int = 64,
    pageSize: Int = 4096
) {
    // frames: poolSize * (pageSize + overhead)
    // - pageSize: 4KB per frame
    // - overhead: ~50 bytes (Frame 객체, ByteBuffer 메타데이터)

    // pageTable: ~32 bytes per entry

    // 총 메모리: poolSize * (4096 + 50 + 32)
    //          ≈ poolSize * 4.1KB

    // 예: poolSize = 64
    // → 64 * 4.1KB ≈ 262KB

    // 예: poolSize = 1024
    // → 1024 * 4.1KB ≈ 4.2MB
}
```

### I/O 성능

```kotlin
// Cache Hit Rate에 따른 성능
fun ioPerformance(hitRate: Double) {
    val totalAccess = 1000
    val hits = (totalAccess * hitRate).toInt()
    val misses = totalAccess - hits

    // 시간 계산
    val hitTime = hits * 100         // 100ns per hit
    val missTime = misses * 5_000_000 // 5ms per miss

    val totalTime = hitTime + missTime
    println("Hit rate: ${hitRate * 100}%")
    println("Total time: ${totalTime / 1_000_000.0}ms")
}

// 결과:
// Hit rate: 0%   → Total time: 5000ms
// Hit rate: 50%  → Total time: 2500ms
// Hit rate: 80%  → Total time: 1000ms
// Hit rate: 95%  → Total time: 250ms
// Hit rate: 99%  → Total time: 50ms
```

### 벤치마크 예상

```kotlin
// 10,000 페이지 순차 접근
repeat(10_000) { i ->
    bufferPool.getPage(i % 64)  // 64개 페이지 순환
}

// Without BufferPool:
// - 10,000 reads from disk
// - Time: ~50 seconds (5ms per read)

// With BufferPool (64 frames):
// - First 64 reads: disk I/O (miss)
// - Next 9,936 reads: memory (hit)
// - Hit rate: 99.36%
// - Time: ~350ms (64 misses + 9,936 hits)
// → 142배 성능 향상!
```

## 설계 결정 사항

### 왜 Eviction이 없는가?

**현재 설계: No Eviction**
```kotlin
private fun allocateFrameOrThrow(): Int {
    if (nextFreeFrame >= poolSize) {
        throw IllegalStateException("BufferPool is full")
    }
    return nextFreeFrame++
}
```

**장점:**
- 구현 간단 (~120줄)
- 버그 가능성 낮음
- 예측 가능한 동작

**단점:**
- 버퍼 풀 크기 제한
- 큰 데이터셋 처리 불가

**사용 시나리오:**
- 소규모 DB (페이지 수 < poolSize)
- 충분한 메모리
- 프로토타입/학습용

**프로덕션 설계:**
```kotlin
// LRU Eviction
class BufferPoolWithLRU {
    private val lruList = LinkedList<PageId>()

    fun getPage(pageId: PageId): ByteBuffer {
        val hit = pageTable[pageId]
        if (hit != null) {
            // LRU 갱신: 최근 사용으로 이동
            lruList.remove(pageId)
            lruList.addFirst(pageId)
            return frames[hit].buffer.duplicate()
        }

        // Eviction 필요
        if (nextFreeFrame >= poolSize) {
            val victim = findVictim()  // LRU 사용
            evict(victim)
        }

        // 나머지 로직...
    }

    private fun findVictim(): PageId {
        // LRU: 가장 오래 사용 안 한 페이지
        return lruList.last()
    }
}
```

### 왜 Pin/Unpin이 없는가?

**현재 설계: No Pin/Unpin**

**장점:**
- 사용 간편
- API 단순

**단점:**
- 동시성 제어 불가
- Eviction 시 사용 중인 페이지 제거 가능

**프로덕션 설계:**
```kotlin
data class Frame(
    var pageId: PageId?,
    val buffer: ByteBuffer,
    var dirty: Boolean,
    var pinCount: Int = 0  // 추가
)

fun getPage(pageId: PageId): ByteBuffer {
    // ...
    frame.pinCount++  // Pin
    return frame.buffer.duplicate()
}

fun unpinPage(pageId: PageId) {
    val frameId = pageTable[pageId] ?: return
    frames[frameId].pinCount--
}

private fun findVictim(): PageId {
    // pinCount > 0인 페이지는 evict 불가
    return lruList.first {
        frames[pageTable[it]!!].pinCount == 0
    }
}
```

### 왜 duplicate()를 반환하는가?

**현재 설계:**
```kotlin
return frame.buffer.duplicate()
```

**대안 1: 원본 반환**
```kotlin
return frame.buffer  // ❌
```
- 문제: 여러 사용자가 position/limit 공유
- 동시성 문제 발생

**대안 2: 복사본 반환**
```kotlin
val copy = ByteBuffer.allocate(pageSize)
copy.put(frame.buffer)
return copy  // ❌
```
- 문제: 메모리 낭비, 복사 오버헤드
- 수정 사항이 원본에 반영 안 됨

**현재 방식 (duplicate):**
```kotlin
return frame.buffer.duplicate()  // ✅
```
- position/limit 독립적
- underlying array 공유
- 메모리 효율적
- 수정 사항 원본 반영

### 왜 HashMap을 Page Table로 사용하는가?

**HashMap vs Array:**
```kotlin
// HashMap (현재)
val pageTable: MutableMap<PageId, Int> = HashMap()

// Array (대안)
val pageTable: Array<Int?> = arrayOfNulls(MAX_PAGES)
```

**HashMap 장점:**
- PageId가 연속적이지 않아도 됨
- 메모리 효율적 (실제 사용하는 페이지만)
- O(1) 조회/삽입

**Array 장점:**
- 약간 더 빠름 (~10%)
- 메모리 지역성 좋음

**선택 이유:**
- 유연성 > 성능 (10% 차이는 미미)
- 실제 병목은 디스크 I/O (5ms vs 100ns)

## 향후 개선 방향

### 1. LRU Eviction 구현

```kotlin
class BufferPoolWithLRU(
    private val diskManager: DiskManager,
    private val poolSize: Int,
    private val pageSize: Int
) {
    private val frames: Array<Frame> = Array(poolSize) { Frame.empty(pageSize) }
    private val pageTable: MutableMap<PageId, Int> = HashMap()
    private val lruList: LinkedList<PageId> = LinkedList()

    fun getPage(pageId: PageId): ByteBuffer {
        val hit = pageTable[pageId]
        if (hit != null) {
            // LRU 갱신
            lruList.remove(pageId)
            lruList.addFirst(pageId)
            return frames[hit].buffer.duplicate()
        }

        // Eviction
        val frameId = if (pageTable.size < poolSize) {
            pageTable.size
        } else {
            evictLRU()
        }

        val frame = frames[frameId]
        frame.bind(pageId)
        diskManager.readPage(pageId, frame.buffer)

        pageTable[pageId] = frameId
        lruList.addFirst(pageId)

        return frame.buffer.duplicate()
    }

    private fun evictLRU(): Int {
        val victim = lruList.removeLast()  // LRU
        val frameId = pageTable.remove(victim)!!

        val frame = frames[frameId]
        if (frame.dirty) {
            diskManager.writePage(victim, frame.buffer)
        }

        return frameId
    }
}
```

### 2. Clock Algorithm (Second-Chance)

```kotlin
class BufferPoolWithClock {
    private val frames: Array<Frame> = Array(poolSize) { Frame.empty(pageSize) }
    private val refBits: BooleanArray = BooleanArray(poolSize)
    private var clockHand: Int = 0

    private fun evictClock(): Int {
        while (true) {
            if (!refBits[clockHand]) {
                // 희생자 발견
                return clockHand
            }

            // Second chance: refBit 초기화
            refBits[clockHand] = false
            clockHand = (clockHand + 1) % poolSize
        }
    }

    fun getPage(pageId: PageId): ByteBuffer {
        val hit = pageTable[pageId]
        if (hit != null) {
            refBits[hit] = true  // 참조 비트 설정
            return frames[hit].buffer.duplicate()
        }

        // Eviction + 로드
        val frameId = evictClock()
        // ...
    }
}
```

### 3. Pin/Unpin 메커니즘

```kotlin
data class Frame(
    var pageId: PageId?,
    val buffer: ByteBuffer,
    var dirty: Boolean,
    var pinCount: Int = 0
)

fun pinPage(pageId: PageId): ByteBuffer {
    val buffer = getPage(pageId)
    val frameId = pageTable[pageId]!!
    frames[frameId].pinCount++
    return buffer
}

fun unpinPage(pageId: PageId, dirty: Boolean = false) {
    val frameId = pageTable[pageId] ?: return
    frames[frameId].pinCount--

    if (dirty) {
        frames[frameId].dirty = true
    }
}

private fun evict(frameId: Int) {
    val frame = frames[frameId]

    // Pin된 페이지는 evict 불가
    require(frame.pinCount == 0) {
        "Cannot evict pinned page: ${frame.pageId}"
    }

    // ...
}
```

### 4. 비동기 Flush (Background Writer)

```kotlin
class BufferPoolWithAsyncFlush {
    private val flushQueue: BlockingQueue<PageId> = LinkedBlockingQueue()
    private val flusher: Thread

    init {
        flusher = Thread {
            while (!Thread.interrupted()) {
                val pageId = flushQueue.take()
                flushPageInternal(pageId)
            }
        }
        flusher.start()
    }

    fun markDirty(pageId: PageId) {
        val frameId = pageTable[pageId]!!
        frames[frameId].dirty = true

        // 비동기 flush 예약
        flushQueue.offer(pageId)
    }

    private fun flushPageInternal(pageId: PageId) {
        val frameId = pageTable[pageId] ?: return
        val frame = frames[frameId]

        if (frame.dirty) {
            diskManager.writePage(pageId, frame.buffer)
            frame.dirty = false
        }
    }
}
```

### 5. 통계 수집

```kotlin
class BufferPoolWithStats {
    private var hitCount: Long = 0
    private var missCount: Long = 0

    fun getPage(pageId: PageId): ByteBuffer {
        val hit = pageTable[pageId]
        if (hit != null) {
            hitCount++
            return frames[hit].buffer.duplicate()
        }

        missCount++
        // ...
    }

    fun getStats(): BufferPoolStats {
        val total = hitCount + missCount
        return BufferPoolStats(
            hitCount = hitCount,
            missCount = missCount,
            hitRate = if (total > 0) hitCount.toDouble() / total else 0.0,
            evictions = evictionCount,
            dirtyPages = frames.count { it.dirty }
        )
    }
}

data class BufferPoolStats(
    val hitCount: Long,
    val missCount: Long,
    val hitRate: Double,
    val evictions: Long,
    val dirtyPages: Int
)
```

### 6. Prefetching (선행 로드)

```kotlin
fun prefetch(pageIds: List<PageId>) {
    // 여러 페이지 미리 로드
    for (pageId in pageIds) {
        if (pageTable[pageId] == null) {
            getPage(pageId)  // Cache에 로드
        }
    }
}

// 사용 예: 순차 스캔 최적화
fun scanAllWithPrefetch(heapTable: HeapTable) {
    val pageIds = heapTable.getPageIds()

    // 앞으로 읽을 페이지 미리 로드
    bufferPool.prefetch(pageIds)

    for (pageId in pageIds) {
        val buffer = bufferPool.getPage(pageId)  // Hit!
        // ...
    }
}
```

## 베스트 프랙티스

### ✅ DO

```kotlin
// 1. 종료 시 반드시 flush
fun closeDatabase() {
    bufferPool.flushAll()
    diskManager.close()
}

// 2. markDirty() 호출 필수
val buffer = bufferPool.getPage(pageId)
buffer.putInt(0, 42)
bufferPool.markDirty(pageId)  // 필수!

// 3. withPageForWrite 사용
bufferPool.withPageForWrite(pageId) { buffer ->
    buffer.putInt(0, 42)
    // markDirty() 자동
}

// 4. 페이지 재사용
val buffer = bufferPool.getPage(pageId)
for (i in 0 until 100) {
    // buffer 재사용 - Cache Hit!
    buffer.putInt(i * 4, i)
}
bufferPool.markDirty(pageId)

// 5. 주기적인 flush
fun checkpoint() {
    bufferPool.flushAll()
    // 변경 사항 안전하게 디스크에 기록
}
```

### ❌ DON'T

```kotlin
// 1. markDirty() 누락
val buffer = bufferPool.getPage(pageId)
buffer.putInt(0, 42)
// ❌ markDirty() 호출 안 함
// → 변경 사항이 디스크에 기록 안 됨!

// 2. flush 없이 종료
bufferPool.getPage(pageId)
diskManager.close()  // ❌ flushAll() 호출 안 함
// → Dirty 페이지 유실!

// 3. 반복적인 getPage()
for (i in 0 until 100) {
    val buffer = bufferPool.getPage(pageId)  // ❌ 매번 호출
    buffer.putInt(i * 4, i)
}
// → 불필요한 오버헤드

// 4. 버퍼 풀 크기 부족
val bufferPool = BufferPool(diskManager, poolSize = 10)
for (i in 0 until 100) {
    bufferPool.getPage(i)  // ❌ 11번째부터 예외 발생
}
```

## 관련 컴포넌트

- **DiskManager**: BufferPool이 의존, Cache Miss 시 페이지 로드
- **HeapTable**: BufferPool 사용으로 I/O 성능 향상
- **SlottedPage**: BufferPool에서 받은 ByteBuffer로 페이지 관리
- **FileCatalog**: 향후 BufferPool 통합 가능
- **Transaction Manager** (향후): BufferPool과 연동하여 ACID 보장
- **Lock Manager** (향후): Pin/Unpin과 함께 동시성 제어