# DiskManager에 대한 문서

## DiskManager란?

`DiskManager`는 디스크 파일과 메모리 간의 페이지 입출력을 담당하는 저수준 I/O 관리자입니다.

### DB에서의 역할

- **PageId ↔ Disk Offset 변환**: 논리적 페이지 번호를 물리적 파일 오프셋으로 변환
- **추상화 계층**: 상위 계층(HeapTable, Index 등)에 디스크 오프셋을 숨기고 PageId만 노출
- **File I/O 처리**: FileChannel을 사용한 효율적인 파일 읽기/쓰기

## 핵심 개념

### PageId와 Disk Offset

```kotlin
// PageId: 논리적 페이지 번호 (0, 1, 2, ...)
// Disk Offset: 파일 내 물리적 바이트 위치 (0, 4096, 8192, ...)

val pageId: PageId = 5
val offset = pageId * Page.PAGE_SIZE  // 5 * 4096 = 20480 bytes
```

**PageId를 사용하는 이유:**
- 페이지 크기 변경 시 상위 계층 코드 수정 불필요
- 논리적 추상화로 코드 가독성 향상
- 오프셋 계산 실수 방지

### 페이지 단위 I/O

DB는 항상 **페이지 단위(4KB)**로 I/O를 수행합니다.

```
파일 구조:
[Page 0: 4KB][Page 1: 4KB][Page 2: 4KB][Page 3: 4KB]...
  offset=0    offset=4096  offset=8192  offset=12288
```

**장점:**
- 디스크 블록 크기와 정렬되어 효율적
- 단편화 방지
- 버퍼 풀 관리 단순화

## DiskManager 아키텍처

```
┌─────────────────────────────────────┐
│         HeapTable / Index           │
│  (PageId만 사용, offset 몰라도 됨)     │
└──────────────┬──────────────────────┘
               │ PageId
               ▼
┌─────────────────────────────────────┐
│         DiskManager                 │
│  PageId → Offset 변환               │
│  FileChannel I/O 처리               │
└──────────────┬──────────────────────┘
               │ FileChannel
               ▼
┌─────────────────────────────────────┐
│         Physical Disk File          │
│  data.db (4KB aligned pages)        │
└─────────────────────────────────────┘
```

## 주요 메서드

### readPage(pageId, buffer)

페이지를 디스크에서 메모리로 읽어옴

```kotlin
val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
diskManager.readPage(pageId = 5, buffer)

// 내부 동작:
// 1. buffer.clear() - 읽기 준비
// 2. offset = pageId * 4096 = 20480
// 3. FileChannel.read(buffer, offset)
// 4. buffer.flip() - 읽은 데이터 사용 준비
```

**사전 조건:**
- `buffer.capacity() == Page.PAGE_SIZE` (4096 bytes)

**사후 조건:**
- buffer는 읽기 모드 (`flip()` 호출 완료)
- buffer.position() == 0, buffer.limit() == 읽은 바이트 수

### writePage(pageId, buffer)

메모리의 페이지를 디스크에 기록

```kotlin
val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
// ... buffer에 데이터 작성 ...
diskManager.writePage(pageId = 5, buffer)

// 내부 동작:
// 1. buffer.rewind() - position을 0으로 (처음부터 쓰기)
// 2. offset = pageId * 4096
// 3. FileChannel.write(buffer, offset)
```

**사전 조건:**
- `buffer.capacity() == Page.PAGE_SIZE`
- buffer에 유효한 페이지 데이터 존재

**사후 조건:**
- 디스크 파일에 4KB 데이터 기록 완료

### allocatePage()

새로운 빈 페이지를 파일 끝에 할당

```kotlin
val newPageId = diskManager.allocatePage()
// 예: 파일에 3개 페이지 존재 → newPageId = 3 반환

// 내부 동작:
// 1. newPageId = fileSize / 4096
// 2. 0으로 채운 4KB 페이지를 파일 끝에 추가
// 3. newPageId 반환
```

**사용 시나리오:**
- HeapTable이 기존 페이지가 모두 꽉 찼을 때
- 새로운 인덱스 페이지 생성 시
- 데이터베이스 초기화 시

**주의사항:**
- 할당된 페이지는 0으로 초기화됨
- 실제 사용 전에 SlottedPage.init() 호출 필요

## 사용 예제

### 기본 사용 패턴

```kotlin
// 1. DiskManager 초기화
val diskManager = DiskManager(Path.of("data/mydb.db"))

// 2. 새 페이지 할당
val pageId = diskManager.allocatePage()

// 3. 페이지 읽기
val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
diskManager.readPage(pageId, buffer)

// 4. 페이지 수정 (SlottedPage 사용)
val page = SlottedPage(buffer)
page.init()
page.insert("Hello World".toByteArray())

// 5. 페이지 쓰기
diskManager.writePage(pageId, buffer)

// 6. 종료 시 리소스 해제
diskManager.close()
```

### HeapTable과의 협력 패턴

```kotlin
class HeapTable(private val diskManager: DiskManager) {

    fun insertRow(record: ByteArray): Rid {
        // 1. 기존 페이지 시도
        for (pageId in pageIds) {
            val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
            diskManager.readPage(pageId, buffer)

            val page = SlottedPage(buffer)
            try {
                val slotId = page.insert(record)
                diskManager.writePage(pageId, buffer)
                return Rid(pageId, slotId)
            } catch (e: IllegalStateException) {
                // 공간 부족, 다음 페이지 시도
            }
        }

        // 2. 새 페이지 할당
        val newPageId = diskManager.allocatePage()
        val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
        diskManager.readPage(newPageId, buffer)

        val page = SlottedPage(buffer)
        page.init()
        val slotId = page.insert(record)
        diskManager.writePage(newPageId, buffer)

        return Rid(newPageId, slotId)
    }
}
```

## 설계 결정 사항

### 왜 FileChannel을 사용하는가?

**FileChannel의 장점:**
- **위치 기반 I/O**: `read(buffer, offset)`로 seek 없이 임의 위치 접근
- **ByteBuffer 직접 지원**: 변환 없이 효율적 I/O
- **Non-blocking I/O**: 비동기 처리 가능 (향후 확장)
- **파일 잠금**: 동시성 제어 지원

```kotlin
// FileChannel 사용 (현재)
channel.read(buffer, offset)  // seek 불필요, 빠름

// vs. InputStream 사용 (대안)
inputStream.skip(offset)      // 순차 이동, 느림
inputStream.read(bytes)
```

### 왜 페이지 크기를 4KB로 고정하는가?

**4KB 선택 이유:**
- 대부분의 OS 페이지 크기와 일치 (리눅스, macOS: 4KB, Windows: 4KB)
- SSD/HDD 블록 크기와 정렬되어 효율적
- 너무 작으면: I/O 횟수 증가, 오버헤드 증가
- 너무 크면: 메모리 낭비, 캐시 효율 저하

```kotlin
companion object {
    const val PAGE_SIZE = 4096  // 4KB
}
```

**다른 DB의 페이지 크기:**
- PostgreSQL: 8KB (기본값)
- MySQL InnoDB: 16KB
- SQLite: 4KB (기본값)
- Oracle: 8KB

### 왜 allocate()를 사용하는가? (Direct Buffer가 아닌)

```kotlin
// Heap 버퍼 사용 (현재)
val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)

// vs. Direct 버퍼 (대안)
val buffer = ByteBuffer.allocateDirect(Page.PAGE_SIZE)
```

**Heap 버퍼 선택 이유:**
- 로컬 DB 구현으로 극한의 성능 불필요
- 디버깅 용이 (힙 덤프, 메모리 프로파일링)
- GC가 자동 관리 (수동 해제 불필요)
- 코드 단순성 우선

**Direct 버퍼로 전환하는 경우:**
- 프로덕션 성능 최적화 필요 시
- I/O 집약적 워크로드
- GC 압박 감소 필요 시

## 성능 고려사항

### I/O 최소화 전략

**1. 버퍼 재사용**
```kotlin
// ❌ 나쁜 예: 매번 새 버퍼 할당
fun readMultiplePages(pageIds: List<PageId>) {
    for (pageId in pageIds) {
        val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)  // 매번 할당!
        diskManager.readPage(pageId, buffer)
    }
}

// ✅ 좋은 예: 버퍼 재사용
fun readMultiplePages(pageIds: List<PageId>) {
    val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)  // 한 번만 할당
    for (pageId in pageIds) {
        diskManager.readPage(pageId, buffer)  // readPage가 clear() 호출
        // ... 데이터 처리 ...
    }
}
```

**2. 배치 I/O**
```kotlin
// 여러 페이지를 순차적으로 읽을 때
fun readSequentialPages(startPageId: PageId, count: Int) {
    val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
    for (i in 0 until count) {
        diskManager.readPage(startPageId + i, buffer)
        // ... 처리 ...
    }
}
```

### 파일 크기 관리

```kotlin
// 현재 파일에 몇 개의 페이지가 있는지 확인
fun pageCount(): Long {
    return dbFile.size() / Page.PAGE_SIZE
}

// 파일 크기 미리 확장 (성능 향상)
fun preallocatePages(count: Int) {
    repeat(count) {
        allocatePage()
    }
}
```

## 에러 처리

### 주요 에러 시나리오

**1. 잘못된 버퍼 크기**
```kotlin
// ❌ 런타임 에러
val smallBuffer = ByteBuffer.allocate(1024)  // 4KB가 아님!
diskManager.readPage(pageId, smallBuffer)
// → IllegalArgumentException: buffer.capacity() != Page.PAGE_SIZE
```

**2. 존재하지 않는 페이지 읽기**
```kotlin
// 파일에 3개 페이지만 존재 (0, 1, 2)
diskManager.readPage(pageId = 10, buffer)
// → 부분 읽기 또는 EOF 발생, buffer에 0으로 채워질 수 있음
```

**3. 디스크 공간 부족**
```kotlin
try {
    val pageId = diskManager.allocatePage()
} catch (e: IOException) {
    // 디스크 공간 부족 또는 파일 시스템 오류
    log.error("Failed to allocate page", e)
}
```

## 향후 개선 방향

### 1. 버퍼 풀 통합
```kotlin
// 현재: DiskManager가 버퍼 관리 안 함
val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
diskManager.readPage(pageId, buffer)

// 향후: BufferPool과 통합
val page = bufferPool.fetchPage(pageId)  // DiskManager 내부 호출
```

### 2. 페이지 캐싱
```kotlin
// 자주 읽는 페이지는 메모리에 캐시
private val pageCache = LRUCache<PageId, ByteBuffer>(capacity = 100)

fun readPageCached(pageId: PageId): ByteBuffer {
    return pageCache.getOrPut(pageId) {
        val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
        readPage(pageId, buffer)
        buffer
    }
}
```

### 3. 비동기 I/O
```kotlin
// 향후: 코루틴 기반 비동기 I/O
suspend fun readPageAsync(pageId: PageId, buffer: ByteBuffer) {
    withContext(Dispatchers.IO) {
        val offset = pageId * Page.PAGE_SIZE
        dbFile.channel.read(buffer, offset)
    }
}
```

### 4. Write-Ahead Logging (WAL)
```kotlin
// 향후: WAL 통합으로 내구성 보장
fun writePage(pageId: PageId, buffer: ByteBuffer) {
    walManager.logPageWrite(pageId, buffer)  // WAL 먼저 기록
    diskManager.writePage(pageId, buffer)    // 실제 페이지 쓰기
}
```

## 베스트 프랙티스

### ✅ DO

```kotlin
// 1. 리소스 해제 보장
val diskManager = DiskManager(dbPath)
try {
    // ... DB 작업 ...
} finally {
    diskManager.close()
}

// 2. 버퍼 크기 검증
require(buffer.capacity() == Page.PAGE_SIZE) {
    "Buffer must be exactly ${Page.PAGE_SIZE} bytes"
}

// 3. 버퍼 재사용
val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
for (pageId in pageIds) {
    diskManager.readPage(pageId, buffer)
    // ... 처리 ...
}
```

### ❌ DON'T

```kotlin
// 1. 버퍼 크기 검증 생략
val buffer = ByteBuffer.allocate(1024)  // ❌ 잘못된 크기
diskManager.readPage(pageId, buffer)

// 2. close() 호출 누락
val diskManager = DiskManager(dbPath)
// ... 작업 후 close() 호출 안 함 ...  // ❌ 파일 핸들 누수

// 3. PageId 범위 검증 없이 사용
val pageId = userInput  // ❌ 검증 없음
diskManager.readPage(pageId, buffer)  // 존재하지 않는 페이지일 수 있음
```

## 디버깅 팁

### 페이지 덤프 유틸리티
```kotlin
fun dumpPage(pageId: PageId) {
    val buffer = ByteBuffer.allocate(Page.PAGE_SIZE)
    diskManager.readPage(pageId, buffer)

    println("=== Page $pageId ===")
    println("Capacity: ${buffer.capacity()}")
    println("Position: ${buffer.position()}")
    println("Limit: ${buffer.limit()}")

    // 처음 64바이트 hex 덤프
    buffer.position(0)
    val header = ByteArray(64)
    buffer.get(header)
    println("Header: ${header.joinToString(" ") { "%02x".format(it) }}")
}
```

### 파일 구조 검사
```kotlin
fun inspectDatabaseFile() {
    val fileSize = dbFile.size()
    val pageCount = fileSize / Page.PAGE_SIZE

    println("=== Database File Info ===")
    println("File size: $fileSize bytes")
    println("Page count: $pageCount")
    println("Page size: ${Page.PAGE_SIZE} bytes")

    if (fileSize % Page.PAGE_SIZE != 0L) {
        println("⚠️ Warning: File size not aligned to page boundary!")
    }
}
```

## 관련 컴포넌트

- **SlottedPage**: DiskManager가 읽은 ByteBuffer를 페이지 구조로 해석
- **HeapTable**: DiskManager를 사용하여 테이블 페이지 관리
- **BufferPool** (향후): DiskManager를 래핑하여 페이지 캐싱 제공
- **WAL Manager** (향후): DiskManager와 협력하여 내구성 보장
