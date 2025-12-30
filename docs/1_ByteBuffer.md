# ByteBuffer에 대한 문서

## ByteBuffer란?

`ByteBuffer`는 바이트 배열을 효율적으로 읽고 쓰기 위한 버퍼로, 단순 ByteArray + offset 관리의 안전하고 표준화된 버전

### DB에서의 역할

- 디스크 페이지(4KB)를 메모리에 적재
- page header, slot directory, record body를 정해진 offset에 기록
- File I/O와 Memory I/O 간의 중개자 역할

## ByteBuffer의 핵심 상태

ByteBuffer는 세 가지 상태를 가집니다.

- capacity : 전체 크기 (고정)
- limit : 읽기/쓰기 가능한 최대 위치
- position : 현재 읽기/쓰기 위치

## 생성 방법

```kotlin
val buffer = ByteBuffer.allocate(4096) // 4KB 버퍼 생성
```

- JVM Heap
- 배열 기반
- 쉬운 디버깅
- 로컬 DB 구현에 적합

### c.c. Direct ByteBuffer

```kotlin
val buffer = ByteBuffer.allocateDirect(4096) // 4KB Direct 버퍼 생성
```

- native memory
- OS I/O 최적화
- GC 부담 감소
- 대용량 데이터 처리에 적합
- 복잡한 디버깅

## 쓰기/읽기 기본 흐름

```kotlin
buffer.putInt(123)
buffer.putShort(10)
buffer.put(byteArrayOf(1, 2, 3))
```

- position은 자동 증가
- limit과 capacity는 변경되지 않음

### flip : 쓰기에서 읽기로 전환

```kotlin
buffer.flip()

// 읽기 작업
val a = buffer.getInt()
val b = buffer.getShort()
```

## 절대 좌표 기반 읽기/쓰기

DB에서는 position 기반 접근 대신 offset 기반 접근을 사용합니다.
Page layout이 고정 offset을 가지며, position이 오염되면 전체 페이지가 깨질 위험이 있기 때문에 멀티 필드 접근이 안전한 offset 기반 접근을 선호합니다.

```kotlin
buffer.putInt(0, pageType)
buffer.putShort(4, slotCount)
buffer.putInt(6, freeSpaceOffset)

// 읽기
val pageType = buffer.getInt(0)
val slotCount = buffer.getShort(4)
val freeSpaceOffset = buffer.getInt(6)
```

## Slotted Page 사용 예

```kotlin
class SlottedPage(private val buffer: ByteBuffer) {

    fun slotCount(): Int = buf.getShort(SLOT_COUNT_OFFSET).toInt()

    fun read(slotId: Int): ByteArray {
        val offset = SlotOffset(slotId)
        val length = slotLength(slotId)
        val data = ByteArray(length)

        buffer.position(offset)
        buffer.get(data)

        return data
    }
}
```

## ByteBuffer API

### 주요 쓰기 메서드 (Relative)

```kotlin
buffer.put(byte: Byte)           // 1바이트 쓰기
buffer.putShort(value: Short)    // 2바이트 쓰기
buffer.putInt(value: Int)        // 4바이트 쓰기
buffer.putLong(value: Long)      // 8바이트 쓰기
buffer.put(src: ByteArray)       // 바이트 배열 쓰기
```

### 주요 읽기 메서드 (Relative)

```kotlin
val byte = buffer.get()          // 1바이트 읽기
val short = buffer.getShort()    // 2바이트 읽기
val int = buffer.getInt()        // 4바이트 읽기
val long = buffer.getLong()      // 8바이트 읽기
buffer.get(dst: ByteArray)       // 바이트 배열로 읽기
```

### 절대 위치 기반 메서드 (Absolute)

```kotlin
buffer.put(index: Int, byte: Byte)
buffer.putInt(index: Int, value: Int)
buffer.getInt(index: Int): Int
// position을 변경하지 않음
```

### 상태 전환 메서드

```kotlin
buffer.flip()      // limit = position, position = 0 (쓰기→읽기)
buffer.rewind()    // position = 0 (처음부터 다시 읽기)
buffer.clear()     // position = 0, limit = capacity (재사용 준비)
buffer.compact()   // 남은 데이터를 앞으로 이동 후 쓰기 모드
```

#### flip() 상세

```
쓰기 모드: [data data data | position | ... limit=capacity]
flip() 후: [position | data data data | limit | ...]
```

읽기 준비 완료 상태로 전환

#### compact() 상세

```
읽기 중: [읽음 읽음 | position | 안읽음 안읽음 | limit]
compact(): [안읽음 안읽음 | position | ... limit=capacity]
```

일부만 읽고 다시 쓰기 모드로 전환할 때 사용

### 유틸리티 메서드

```kotlin
buffer.remaining(): Int      // limit - position (읽거나 쓸 수 있는 바이트 수)
buffer.hasRemaining(): Boolean
buffer.array(): ByteArray    // 백킹 배열 반환 (allocate()로 생성 시만 가능)
buffer.slice()               // 현재 position부터 새로운 독립 뷰 생성
buffer.duplicate()           // 같은 데이터 공유하는 새 버퍼 생성
```

## Endian

**Endian**은 멀티바이트 데이터(Short, Int, Long)를 메모리에 저장하는 바이트 순서를 의미합니다.

### Big Endian vs Little Endian

예: 정수 `0x12345678`을 4바이트로 저장할 때

**Big Endian** (네트워크 바이트 순서)

```
주소: [0x00] [0x01] [0x02] [0x03]
값:   [0x12] [0x34] [0x56] [0x78]
```

- 높은 바이트가 낮은 주소에 저장
- 네트워크 프로토콜 표준
- Java ByteBuffer 기본값

**Little Endian** (Intel x86/x64)

```
주소: [0x00] [0x01] [0x02] [0x03]
값:   [0x78] [0x56] [0x34] [0x12]
```

- 낮은 바이트가 낮은 주소에 저장 - 대부분의 CPU 아키텍처
- 메모리 직접 접근 시 효율적

### ByteBuffer에서 Endian 설정

```kotlin
import java.nio.ByteOrder

// Big Endian (기본값)
val buffer = ByteBuffer.allocate(1024)
buffer.order(ByteOrder.BIG_ENDIAN)

// Little Endian
buffer.order(ByteOrder.LITTLE_ENDIAN)

// 시스템 네이티브 Endian
buffer.order(ByteOrder.nativeOrder())
```

### DB에서 Endian 선택

**권장: Big Endian**

- 플랫폼 독립적인 데이터 포맷
- 파일 이식성 보장
- 다른 시스템과의 데이터 교환 용이

```kotlin
class DiskManager {
    private fun createPage(): ByteBuffer {
        val buffer = ByteBuffer.allocate(PAGE_SIZE)
        buffer.order(ByteOrder.BIG_ENDIAN)  // 명시적 설정
        return buffer
    }
}
```

**Little Endian을 사용하는 경우:**

- 메모리 매핑 파일 사용 시 CPU 네이티브 순서와 일치
- Direct ByteBuffer로 OS 커널과 직접 통신
- 극한의 성능 최적화가 필요한 경우

### 주의사항

```kotlin
// ❌ 잘못된 예: Endian 불일치
val writeBuffer = ByteBuffer.allocate(4)
writeBuffer.order(ByteOrder.BIG_ENDIAN)
writeBuffer.putInt(0x12345678)

val readBuffer = ByteBuffer.wrap(writeBuffer.array())
readBuffer.order(ByteOrder.LITTLE_ENDIAN)  // 다른 Endian!
val value = readBuffer.getInt()  // 0x78563412 (잘못된 값)

// ✅ 올바른 예: 동일한 Endian
val readBuffer = ByteBuffer.wrap(writeBuffer.array())
readBuffer.order(ByteOrder.BIG_ENDIAN)  // 쓸 때와 동일
val value = readBuffer.getInt()  // 0x12345678 (올바른 값)
```

## ByteArray vs. ByteBuffer

| 특성             | ByteArray                      | ByteBuffer                                        |
|----------------|--------------------------------|---------------------------------------------------|
| **타입**         | Primitive 배열                   | Wrapper 객체                                        |
| **위치 추적**      | 없음 (수동 관리)                     | position, limit, capacity                         |
| **타입 변환**      | 수동 (비트 연산 필요)                  | `getInt()`, `putLong()` 등 내장                      |
| **Endian 처리**  | 수동 구현 필요                       | `order()` 메서드로 설정                                 |
| **성능**         | 직접 접근 빠름                       | 약간의 오버헤드                                          |
| **안전성**        | ArrayIndexOutOfBoundsException | BufferOverflowException, BufferUnderflowException |
| **File I/O**   | 수동 변환 필요                       | FileChannel과 직접 호환                                |
| **메모리 위치**     | 항상 Heap                        | Heap 또는 Direct (Off-Heap)                         |
| **Slice/View** | 불가능                            | `slice()`, `duplicate()` 지원                       |
| **사용 예**       | 간단한 바이트 저장                     | DB 페이지, 네트워크 프로토콜, 파일 I/O                         |

### 선택 가이드

**ByteArray를 사용하는 경우:**

- 단순 바이트 데이터 저장
- 크기가 작고 복잡한 조작이 없는 경우
- GC 친화적인 단순 구조 선호

**ByteBuffer를 사용하는 경우:**

- 멀티바이트 타입 읽기/쓰기가 빈번한 경우
- File I/O와 직접 통신 (FileChannel)
- 플랫폼 독립적인 바이너리 포맷 필요
- DB 페이지 관리처럼 구조화된 데이터 접근

## 베스트 프랙티스와 주의사항

### ✅ DO

```kotlin
// 1. Endian을 명시적으로 설정
val buffer = ByteBuffer.allocate(PAGE_SIZE)
buffer.order(ByteOrder.BIG_ENDIAN)

// 2. DB에서는 절대 위치 기반 접근 선호
buffer.putInt(OFFSET_PAGE_TYPE, pageType)
buffer.getInt(OFFSET_PAGE_TYPE)  // position 오염 방지

// 3. 읽기 전 flip() 호출
buffer.putInt(100)
buffer.flip()  // 읽기 모드로 전환
val value = buffer.getInt()

// 4. 재사용 시 clear() 호출
buffer.clear()  // position=0, limit=capacity
```

### ❌ DON'T

```kotlin
// 1. flip() 없이 읽기
buffer.putInt(100)
val value = buffer.getInt()  // ❌ BufferUnderflowException

// 2. position 기반 접근과 절대 접근 혼용
buffer.putInt(100)           // position 증가
buffer.putInt(4, 200)        // position 불변
buffer.flip()
val a = buffer.getInt()      // 100이 아닐 수 있음!

// 3. Direct 버퍼의 array() 호출
val direct = ByteBuffer.allocateDirect(1024)
val arr = direct.array()  // ❌ UnsupportedOperationException

// 4. Endian 불일치
writeBuffer.order(ByteOrder.BIG_ENDIAN)
readBuffer.order(ByteOrder.LITTLE_ENDIAN)  // ❌ 데이터 손상
```

### 성능 팁

1. **버퍼 재사용**: `clear()`로 재활용, 반복 할당 방지
2. **Direct 버퍼**: I/O 집약적인 경우 `allocateDirect()` 고려
3. **Bulk 연산**: `put(ByteArray)`가 개별 `put(byte)` 반복보다 빠름
4. **Slice 활용**: 큰 버퍼의 일부만 전달할 때 복사 대신 `slice()` 사용

### 디버깅 팁

```kotlin
// 버퍼 상태 출력
fun ByteBuffer.debug(): String {
    return "pos=$position, limit=$limit, cap=$capacity, remaining=$remaining"
}

// 내용 확인 (position 보존)
fun ByteBuffer.peek(offset: Int, length: Int): ByteArray {
    val pos = position()
    val data = ByteArray(length)
    position(offset)
    get(data)
    position(pos)  // 원상복구
    return data
}
```

