# Catalog에 대한 문서

## Catalog란?

`Catalog`는 **데이터베이스의 메타데이터를 관리하는 시스템**입니다. 데이터베이스에 존재하는 모든 테이블의 정보(이름, 위치, 스키마 등)를 저장하고 조회할 수 있게 해주는 핵심 컴포넌트입니다.

### DB에서의 역할

- **메타데이터 관리**: 테이블 이름, 파일 경로 등 테이블에 대한 정보 저장
- **테이블 생성**: 새로운 테이블 생성 및 메타데이터 등록
- **테이블 조회**: 테이블 메타데이터 검색 및 반환
- **테이블 목록**: 데이터베이스 내 모든 테이블 목록 제공
- **영속성 보장**: 메타데이터를 파일에 저장하여 재시작 후에도 유지

## 핵심 개념

### Catalog의 필요성

데이터베이스는 여러 개의 테이블을 관리합니다. 각 테이블은 고유한 이름과 저장 위치를 가지며, Catalog는 이러한 정보를 중앙에서 관리합니다.

```
Without Catalog (❌)
┌─────────────────────────────────┐
│ "users 테이블이 어디 있지?"      │
│ → 모든 파일을 순회하며 검색      │
│ → O(N) 시간 소요                │
└─────────────────────────────────┘

With Catalog (✅)
┌─────────────────────────────────┐
│ "users 테이블이 어디 있지?"      │
│ → Catalog에서 O(1) 조회         │
│ → users.tbl 경로 즉시 반환      │
└─────────────────────────────────┘
```

### 시스템 카탈로그 vs 사용자 테이블

```
Database Structure
┌──────────────────────────────────┐
│  System Catalog (메타데이터)     │
├──────────────────────────────────┤
│  - catalog.dat                   │
│  - 테이블 이름 → 파일 경로 매핑   │
│  - 메모리에 로드된 맵 (빠른 조회) │
└──────────┬───────────────────────┘
           │
           ▼
┌──────────────────────────────────┐
│  User Tables (실제 데이터)        │
├──────────────────────────────────┤
│  - users.tbl                     │
│  - products.tbl                  │
│  - orders.tbl                    │
└──────────────────────────────────┘
```

**시스템 카탈로그:**
- 데이터베이스의 "전화번호부"
- 모든 테이블의 위치 정보 저장
- 데이터베이스 시작 시 메모리에 로드
- 빠른 조회를 위한 인덱스 역할

**사용자 테이블:**
- 실제 비즈니스 데이터 저장
- Catalog를 통해 접근
- 독립적인 파일로 관리

### Catalog 파일 포맷

```
catalog.dat 파일 구조
┌─────────────────────────────────────┐
│ [Entry 1: users 테이블]             │
├─────────────────────────────────────┤
│  4 bytes: name length (5)           │
│  5 bytes: "users"                   │
│  4 bytes: path length (29)          │
│ 29 bytes: "/data/tables/users.tbl"  │
├─────────────────────────────────────┤
│ [Entry 2: products 테이블]          │
├─────────────────────────────────────┤
│  4 bytes: name length (8)           │
│  8 bytes: "products"                │
│  4 bytes: path length (32)          │
│ 32 bytes: "/data/tables/products.tbl"│
└─────────────────────────────────────┘

각 엔트리 포맷:
- Int (4 bytes): 테이블 이름 길이
- String: UTF-8 인코딩된 테이블 이름
- Int (4 bytes): 파일 경로 길이
- String: UTF-8 인코딩된 파일 경로
```

## Catalog 아키텍처

```
┌─────────────────────────────────────┐
│      Application Layer              │
│  (SQL Executor, REPL)               │
└──────────────┬──────────────────────┘
               │ createTable(), getTableMeta()
               ▼
┌─────────────────────────────────────┐
│         Catalog Interface           │
│  - createTable(name)                │
│  - getTableMeta(name)               │
│  - listTables()                     │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│         FileCatalog                 │
│  - Memory: LinkedHashMap<String,    │
│            TableMeta>               │
│  - Disk: catalog.dat 파일           │
│  - 생성 시 자동 로드                 │
│  - 추가 시 자동 영속화               │
└──────────────┬──────────────────────┘
               │ TableMeta
               ▼
┌─────────────────────────────────────┐
│         TableMeta                   │
│  - tableName: String                │
│  - filePath: String                 │
└─────────────────────────────────────┘
```

## 주요 컴포넌트

### Catalog (Interface)

Catalog의 핵심 기능을 정의하는 인터페이스

```kotlin
interface Catalog {
    fun createTable(tableName: String)
    fun getTableMeta(tableName: String): TableMeta
    fun listTables(): List<String>
}
```

**설계 목적:**
- 다양한 Catalog 구현 가능 (FileCatalog, MemoryCatalog, DBCatalog 등)
- 테스트 용이성 (Mock 구현 가능)
- 확장성 (향후 기능 추가 시 구현체만 변경)

### FileCatalog (Implementation)

파일 기반 Catalog 구현체

```kotlin
class FileCatalog(
    private val baseDir: Path,      // 테이블 파일 저장 디렉토리
    private val catalogFile: Path,  // 카탈로그 메타데이터 파일
) : Catalog {
    private val metas = linkedMapOf<String, TableMeta>()

    init {
        Files.createDirectories(baseDir)
        loadIfExists()  // 기존 카탈로그 로드
    }
}
```

**핵심 특징:**
- **Dual Storage**: 메모리(LinkedHashMap) + 디스크(catalog.dat)
- **Lazy Loading**: 초기화 시 한 번만 파일 읽기
- **Append-Only**: 새 테이블 추가 시 파일 끝에 추가
- **Crash Recovery**: 파일이 손상되지 않는 한 복구 가능

### TableMeta (Data Class)

테이블 메타데이터를 표현하는 데이터 클래스

```kotlin
data class TableMeta(
    val tableName: String,  // 테이블 이름 (예: "users")
    val filePath: String    // 테이블 파일 경로 (예: "/data/tables/users.tbl")
)
```

**향후 확장 가능성:**
```kotlin
// 추가 가능한 필드들
data class TableMeta(
    val tableName: String,
    val filePath: String,
    val schema: Schema,           // 컬럼 정보
    val rowCount: Long,            // 레코드 수
    val createdAt: Instant,        // 생성 시간
    val modifiedAt: Instant,       // 수정 시간
    val pageIds: List<PageId>,     // 페이지 목록
)
```

## 주요 메서드

### createTable(tableName: String)

새로운 테이블을 생성하고 메타데이터를 등록

```kotlin
┌─────────────────────────────────────────┐
│  Step 1: 입력 검증                       │
├─────────────────────────────────────────┤
│  - 테이블 이름이 비어있지 않은지 확인     │
│  - 테이블 이름이 이미 존재하는지 확인     │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 2: 테이블 파일 생성                │
├─────────────────────────────────────────┤
│  - tablePath = baseDir/tableName.tbl    │
│  - 파일이 없으면 빈 파일 생성             │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 3: 메타데이터 영속화               │
├─────────────────────────────────────────┤
│  - TableMeta 객체 생성                   │
│  - catalog.dat 파일에 append             │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 4: 메모리에 등록                   │
├─────────────────────────────────────────┤
│  - metas[tableName] = meta              │
└─────────────────────────────────────────┘
```

### 생성 예제

```kotlin
val catalog = FileCatalog(
    baseDir = Path.of("/data/tables"),
    catalogFile = Path.of("/data/catalog.dat")
)

// 테이블 생성
catalog.createTable("users")
catalog.createTable("products")
catalog.createTable("orders")

// 결과:
// - /data/tables/users.tbl (빈 파일)
// - /data/tables/products.tbl (빈 파일)
// - /data/tables/orders.tbl (빈 파일)
// - /data/catalog.dat (메타데이터)
```

### 내부 동작 흐름

```kotlin
override fun createTable(tableName: String) {
    // Step 1: 입력 검증
    require(tableName.isNotBlank()) { "tableName is blank" }
    if (metas.containsKey(tableName)) {
        error("Table already exists: $tableName")
    }

    // Step 2: 테이블 파일 생성
    val tablePath = baseDir.resolve("$tableName.tbl").toString()
    val meta = TableMeta(tableName, tablePath)

    val path = Path.of(tablePath)
    if (!Files.exists(path)) {
        Files.createFile(path)  // 빈 파일 생성
    }

    // Step 3: 메타데이터 영속화
    appendMeta(meta)  // catalog.dat에 추가

    // Step 4: 메모리에 등록
    metas[tableName] = meta
}
```

### getTableMeta(tableName: String): TableMeta

테이블의 메타데이터를 조회

```kotlin
┌─────────────────────────────────────────┐
│  Step 1: 메모리 맵에서 조회 (O(1))        │
├─────────────────────────────────────────┤
│  metas[tableName]                       │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Step 2: 존재하지 않으면 예외 발생        │
├─────────────────────────────────────────┤
│  error("Table not found: $tableName")   │
└─────────────────────────────────────────┘
```

### 조회 예제

```kotlin
val catalog = FileCatalog(baseDir, catalogFile)
catalog.createTable("users")

// 메타데이터 조회
val meta = catalog.getTableMeta("users")
println("Table: ${meta.tableName}")
println("Path: ${meta.filePath}")
// 출력:
// Table: users
// Path: /data/tables/users.tbl

// 존재하지 않는 테이블 조회
try {
    catalog.getTableMeta("nonexistent")
} catch (e: IllegalStateException) {
    println(e.message)  // "Table not found: nonexistent"
}
```

### listTables(): List<String>

모든 테이블 이름 목록을 반환

```kotlin
override fun listTables(): List<String> = metas.keys.toList()
```

**특징:**
- **O(N) 복잡도**: N = 테이블 개수
- **삽입 순서 보장**: LinkedHashMap 사용
- **불변 리스트 반환**: 원본 맵 보호

### 목록 조회 예제

```kotlin
val catalog = FileCatalog(baseDir, catalogFile)

catalog.createTable("users")
catalog.createTable("products")
catalog.createTable("orders")

// 테이블 목록 조회
val tables = catalog.listTables()
println("Total tables: ${tables.size}")
for (table in tables) {
    println("- $table")
}
// 출력:
// Total tables: 3
// - users
// - products
// - orders
```

## 영속성 메커니즘

### loadIfExists() - 초기화 시 로드

데이터베이스 시작 시 catalog.dat 파일에서 메타데이터 로드

```kotlin
private fun loadIfExists() {
    if (!Files.exists(catalogFile)) return  // 파일 없으면 skip

    DataInputStream(
        BufferedInputStream(Files.newInputStream(catalogFile))
    ).use { input ->
        while (true) {
            // EOF 도달 시 null 반환하여 종료
            val name = runCatching {
                readString(input)
            }.getOrNull() ?: break

            val path = readString(input)

            metas[name] = TableMeta(name, path)
        }
    }
}
```

**로딩 프로세스:**
```
catalog.dat 파일 읽기
        ↓
┌─────────────────────────────────────┐
│  Entry 1 읽기                       │
│  name = "users"                     │
│  path = "/data/tables/users.tbl"    │
│  → metas["users"] = TableMeta(...)  │
└─────────────────────────────────────┘
        ↓
┌─────────────────────────────────────┐
│  Entry 2 읽기                       │
│  name = "products"                  │
│  path = "/data/tables/products.tbl" │
│  → metas["products"] = TableMeta(...│
└─────────────────────────────────────┘
        ↓
┌─────────────────────────────────────┐
│  EOF 도달 → 로딩 완료                │
└─────────────────────────────────────┘
```

### appendMeta() - 메타데이터 추가

새 테이블 생성 시 catalog.dat 파일에 메타데이터 추가

```kotlin
private fun appendMeta(meta: TableMeta) {
    Files.createDirectories(catalogFile.parent)

    DataOutputStream(
        BufferedOutputStream(
            Files.newOutputStream(
                catalogFile,
                StandardOpenOption.CREATE,   // 없으면 생성
                StandardOpenOption.APPEND    // 끝에 추가
            )
        )
    ).use { out ->
        writeString(out, meta.tableName)
        writeString(out, meta.filePath)
    }
}
```

**Append-Only 설계:**
```
catalog.dat 초기 상태
┌─────────────────────┐
│ [users entry]       │
│ [products entry]    │
└─────────────────────┘

createTable("orders") 호출
        ↓
┌─────────────────────┐
│ [users entry]       │
│ [products entry]    │
│ [orders entry]      │  ← APPEND
└─────────────────────┘
```

**장점:**
- 원자성: 부분 쓰기 발생 시에도 기존 데이터 보존
- 단순성: 복잡한 업데이트 로직 불필요
- 성능: O(1) 추가 연산

**단점:**
- 삭제 연산 미지원 (현재 구현)
- 파일 크기 증가 (압축 필요)

### 직렬화 메커니즘

```kotlin
// 문자열 쓰기
private fun writeString(out: DataOutputStream, s: String) {
    val bytes = s.toByteArray(Charsets.UTF_8)

    out.writeInt(bytes.size)  // 4 bytes: 길이
    out.write(bytes)          // N bytes: 데이터
}

// 문자열 읽기
private fun readString(input: DataInputStream): String {
    val len = input.readInt()  // 4 bytes 읽기

    // 손상 방지: 비정상적인 길이 검증
    require(len >= 0 && len <= 1_000_000) {
        "Corrupted catalog: invalid string length: $len"
    }

    val bytes = ByteArray(len)
    input.readFully(bytes)  // 정확히 len 바이트 읽기

    return String(bytes, Charsets.UTF_8)
}
```

**바이너리 포맷 선택 이유:**
```
Text Format (JSON/CSV):
{
  "tableName": "users",
  "filePath": "/data/tables/users.tbl"
}
→ 파싱 오버헤드, 크기 큼, 손상 가능성

Binary Format:
[4 bytes: len][N bytes: data][4 bytes: len][N bytes: data]
→ 빠른 읽기, 작은 크기, 명확한 구조
```

## 사용 예제

### 기본 사용 패턴

```kotlin
// 1. FileCatalog 생성
val baseDir = Path.of("data/tables")
val catalogFile = Path.of("data/catalog.dat")
val catalog = FileCatalog(baseDir, catalogFile)

// 2. 테이블 생성
catalog.createTable("users")
catalog.createTable("products")
catalog.createTable("orders")

// 3. 테이블 목록 조회
val tables = catalog.listTables()
println("Tables: $tables")
// 출력: Tables: [users, products, orders]

// 4. 특정 테이블 메타데이터 조회
val usersMeta = catalog.getTableMeta("users")
println("Users table path: ${usersMeta.filePath}")
// 출력: Users table path: data/tables/users.tbl
```

### SQL Executor와 통합

```kotlin
class SqlExecutor(
    private val catalog: Catalog,
    private val diskManager: DiskManager
) {
    fun execute(sql: String): String {
        return when {
            sql.startsWith("CREATE TABLE") -> {
                val tableName = parseTableName(sql)
                catalog.createTable(tableName)
                "Table created: $tableName"
            }

            sql.startsWith("SHOW TABLES") -> {
                val tables = catalog.listTables()
                tables.joinToString("\n")
            }

            sql.startsWith("SELECT") -> {
                val tableName = parseFromClause(sql)
                val meta = catalog.getTableMeta(tableName)

                // HeapTable로 데이터 읽기
                val heapTable = HeapTable(diskManager, meta.filePath)
                val records = heapTable.scanAll()

                records.joinToString("\n") { String(it) }
            }

            else -> "Unknown command"
        }
    }
}
```

### 재시작 후 복구

```kotlin
// Session 1: 테이블 생성
fun session1() {
    val catalog = FileCatalog(
        baseDir = Path.of("data/tables"),
        catalogFile = Path.of("data/catalog.dat")
    )

    catalog.createTable("users")
    catalog.createTable("products")

    println("Created tables: ${catalog.listTables()}")
    // 출력: Created tables: [users, products]
}

// Session 2: 재시작 후 로드
fun session2() {
    val catalog = FileCatalog(
        baseDir = Path.of("data/tables"),
        catalogFile = Path.of("data/catalog.dat")
    )
    // init 블록에서 loadIfExists() 자동 호출

    println("Loaded tables: ${catalog.listTables()}")
    // 출력: Loaded tables: [users, products]

    // 기존 테이블 메타데이터 사용 가능
    val usersMeta = catalog.getTableMeta("users")
    println("Users path: ${usersMeta.filePath}")
}
```

### 에러 처리

```kotlin
val catalog = FileCatalog(baseDir, catalogFile)

// 1. 중복 테이블 생성 시도
catalog.createTable("users")
try {
    catalog.createTable("users")  // 예외 발생
} catch (e: IllegalStateException) {
    println(e.message)  // "Table already exists: users"
}

// 2. 존재하지 않는 테이블 조회
try {
    catalog.getTableMeta("nonexistent")
} catch (e: IllegalStateException) {
    println(e.message)  // "Table not found: nonexistent"
}

// 3. 빈 테이블 이름
try {
    catalog.createTable("")
} catch (e: IllegalArgumentException) {
    println(e.message)  // "tableName is blank"
}

// 4. 안전한 사용 패턴
fun safeGetTable(catalog: Catalog, name: String): TableMeta? {
    return try {
        catalog.getTableMeta(name)
    } catch (e: IllegalStateException) {
        null  // 테이블 없으면 null 반환
    }
}
```

## 성능 분석

### 시간 복잡도

| 연산 | 시간 복잡도 | 설명 |
|------|-------------|------|
| **createTable** | O(1) | HashMap 삽입 + Append-only 파일 쓰기 |
| **getTableMeta** | O(1) | HashMap 조회 |
| **listTables** | O(N) | N = 테이블 개수, HashMap keys 변환 |
| **초기화 (loadIfExists)** | O(N) | N = 테이블 개수, 파일 순차 읽기 |

### 공간 복잡도

```kotlin
// 메모리 사용량
class FileCatalog {
    private val metas: LinkedHashMap<String, TableMeta>
    // 테이블당 메모리: ~200 bytes
    // - String (tableName): ~50 bytes
    // - String (filePath): ~100 bytes
    // - HashMap overhead: ~50 bytes
}

// 예: 1000개 테이블
// - 메모리: 1000 * 200 bytes ≈ 200KB
// - 디스크 (catalog.dat): ~150KB
```

### I/O 성능

```kotlin
// 테이블 생성 시 I/O
fun createTable(tableName: String) {
    // 1. 테이블 파일 생성: 1 write (빈 파일)
    Files.createFile(tablePath)

    // 2. 카탈로그 파일 append: 1 write (~50 bytes)
    appendMeta(meta)

    // Total: 2 writes (매우 빠름)
}

// 초기화 시 I/O
fun loadIfExists() {
    // N개 테이블의 메타데이터 읽기: 1 sequential read
    // - SSD: ~1ms (100MB/s 기준, 파일 크기 << 1MB)
    // - HDD: ~5ms (seek time 포함)
}
```

### 벤치마크 예상

```kotlin
// 1000개 테이블 생성
repeat(1000) {
    catalog.createTable("table_$it")
}
// 예상 시간:
// - 메모리 작업: ~1ms
// - 파일 I/O: ~50ms (SSD), ~200ms (HDD)
// Total: ~50-200ms

// 초기화 (1000개 테이블 로드)
val catalog = FileCatalog(baseDir, catalogFile)
// 예상 시간:
// - 파일 읽기: ~1ms (SSD), ~5ms (HDD)
// - 메모리 로드: ~1ms
// Total: ~2-6ms
```

## 설계 결정 사항

### 왜 LinkedHashMap을 사용하는가?

```kotlin
private val metas = linkedMapOf<String, TableMeta>()
```

**HashMap vs LinkedHashMap:**
```
HashMap:
- 순서 보장 없음
- 조금 더 빠름 (~5%)

LinkedHashMap:
- 삽입 순서 보장 ✅
- listTables() 결과가 일관적 ✅
- 디버깅 용이 ✅
```

**사용 이유:**
- 테이블 목록을 생성 순서대로 보여주기 위함
- 성능 차이 미미 (O(1) → O(1))
- 사용자 경험 향상

### 왜 Append-Only 방식인가?

**현재 구현:**
```kotlin
fun appendMeta(meta: TableMeta) {
    Files.newOutputStream(
        catalogFile,
        StandardOpenOption.APPEND  // 끝에 추가만
    )
}
```

**장점:**
1. **원자성**: 부분 쓰기 발생 시에도 기존 데이터 보존
2. **단순성**: 파일 중간 수정 불필요
3. **성능**: O(1) 쓰기 연산

**단점:**
1. 삭제 연산 미지원
2. 파일 크기 계속 증가
3. 단편화 가능성

**향후 개선:**
```kotlin
// Compaction: 주기적으로 파일 재작성
fun compact() {
    val tempFile = catalogFile.resolveSibling("catalog.dat.tmp")

    DataOutputStream(Files.newOutputStream(tempFile)).use { out ->
        for ((_, meta) in metas) {
            writeString(out, meta.tableName)
            writeString(out, meta.filePath)
        }
    }

    Files.move(tempFile, catalogFile, REPLACE_EXISTING)
}
```

### 왜 초기화 시 전체 로드하는가?

**현재 방식: Eager Loading**
```kotlin
init {
    loadIfExists()  // 모든 메타데이터 로드
}
```

**장점:**
- 이후 조회 O(1)
- 구현 간단
- 메모리 사용량 적음 (테이블당 ~200 bytes)

**대안: Lazy Loading**
```kotlin
// 필요할 때만 로드
fun getTableMeta(tableName: String): TableMeta {
    return metas[tableName] ?: loadFromDisk(tableName)
}
```

**선택 이유:**
- 일반적으로 테이블 개수가 적음 (<1000개)
- Eager loading으로도 메모리 부담 없음
- 코드 단순성 유지

### 왜 별도 catalog.dat 파일을 사용하는가?

**대안 1: 테이블 파일 내부에 메타데이터 저장**
```kotlin
// users.tbl 파일 내부
[Header: TableMeta]
[Data: Records...]

// 단점: 모든 테이블 파일 열어야 목록 조회 가능
```

**대안 2: 디렉토리 스캔**
```kotlin
fun listTables(): List<String> {
    return Files.list(baseDir)
        .filter { it.toString().endsWith(".tbl") }
        .map { it.fileName.toString().removeSuffix(".tbl") }
        .toList()
}

// 단점: 매번 파일 시스템 I/O, 메타데이터 부족
```

**현재 방식: 중앙 집중식 catalog.dat**
```
장점:
✅ 한 번 읽기로 모든 메타데이터 로드
✅ 빠른 조회 (메모리 기반)
✅ 확장 가능 (스키마, 인덱스 등 추가)

단점:
❌ 단일 실패 지점 (파일 손상 시 복구 어려움)
❌ 동시성 문제 (여러 프로세스 접근 시)
```

## 향후 개선 방향

### 1. 테이블 삭제 지원

```kotlin
interface Catalog {
    fun dropTable(tableName: String)
}

class FileCatalog : Catalog {
    override fun dropTable(tableName: String) {
        // 1. 메모리에서 제거
        val meta = metas.remove(tableName)
            ?: error("Table not found: $tableName")

        // 2. 테이블 파일 삭제
        Files.deleteIfExists(Path.of(meta.filePath))

        // 3. 카탈로그 파일 재작성 (compaction)
        rewriteCatalogFile()
    }

    private fun rewriteCatalogFile() {
        val tempFile = catalogFile.resolveSibling("catalog.dat.tmp")

        DataOutputStream(Files.newOutputStream(tempFile)).use { out ->
            for ((_, meta) in metas) {
                writeString(out, meta.tableName)
                writeString(out, meta.filePath)
            }
        }

        Files.move(tempFile, catalogFile, REPLACE_EXISTING)
    }
}
```

### 2. 스키마 정보 저장

```kotlin
data class Schema(
    val columns: List<Column>
)

data class Column(
    val name: String,
    val type: DataType,
    val nullable: Boolean,
    val defaultValue: Any?
)

enum class DataType {
    INT, VARCHAR, BOOLEAN, DATE
}

data class TableMeta(
    val tableName: String,
    val filePath: String,
    val schema: Schema  // 추가
)

// 사용 예
val schema = Schema(
    columns = listOf(
        Column("id", DataType.INT, false, null),
        Column("name", DataType.VARCHAR, false, ""),
        Column("age", DataType.INT, true, null)
    )
)

catalog.createTable("users", schema)
```

### 3. 트랜잭션 지원

```kotlin
class TransactionalCatalog(
    private val catalog: FileCatalog
) : Catalog {
    private val writeAheadLog = WAL(Path.of("data/catalog.wal"))

    fun beginTransaction(): Transaction {
        return Transaction(this)
    }

    class Transaction(private val catalog: TransactionalCatalog) {
        private val changes = mutableListOf<Change>()

        fun createTable(name: String) {
            changes.add(Change.Create(name))
        }

        fun commit() {
            // 1. WAL에 기록
            for (change in changes) {
                catalog.writeAheadLog.append(change)
            }

            // 2. 실제 적용
            for (change in changes) {
                when (change) {
                    is Change.Create -> catalog.catalog.createTable(change.name)
                    is Change.Drop -> catalog.catalog.dropTable(change.name)
                }
            }

            // 3. WAL 정리
            catalog.writeAheadLog.clear()
        }

        fun rollback() {
            changes.clear()
        }
    }
}
```

### 4. 인덱스 메타데이터 관리

```kotlin
data class IndexMeta(
    val indexName: String,
    val tableName: String,
    val columnNames: List<String>,
    val indexType: IndexType,  // B-Tree, Hash, etc.
    val filePath: String
)

enum class IndexType {
    BTREE, HASH, BITMAP
}

interface Catalog {
    // 기존 메서드들...

    fun createIndex(indexName: String, tableName: String, columns: List<String>)
    fun getIndexMeta(indexName: String): IndexMeta
    fun listIndexes(tableName: String): List<String>
}
```

### 5. 통계 정보 수집

```kotlin
data class TableStats(
    val rowCount: Long,
    val totalSize: Long,
    val avgRowSize: Int,
    val pageCount: Int,
    val lastAnalyzed: Instant
)

data class TableMeta(
    val tableName: String,
    val filePath: String,
    val schema: Schema,
    val stats: TableStats?  // nullable
)

interface Catalog {
    fun updateStats(tableName: String, stats: TableStats)
    fun getStats(tableName: String): TableStats?
}

// 쿼리 옵티마이저에서 사용
class QueryOptimizer(private val catalog: Catalog) {
    fun optimize(query: Query): ExecutionPlan {
        val stats = catalog.getStats(query.tableName)

        // 통계 정보 기반 최적화
        if (stats != null && stats.rowCount < 1000) {
            return FullTableScan(query)
        } else {
            return IndexScan(query)
        }
    }
}
```

### 6. 손상 복구 메커니즘

```kotlin
class FileCatalog {
    fun verify(): Boolean {
        try {
            val tempMetas = linkedMapOf<String, TableMeta>()

            DataInputStream(
                BufferedInputStream(Files.newInputStream(catalogFile))
            ).use { input ->
                while (true) {
                    val name = runCatching {
                        readString(input)
                    }.getOrNull() ?: break

                    val path = readString(input)

                    // 테이블 파일 존재 확인
                    if (!Files.exists(Path.of(path))) {
                        return false
                    }

                    tempMetas[name] = TableMeta(name, path)
                }
            }

            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun repair() {
        // 손상된 catalog.dat 복구
        val backup = catalogFile.resolveSibling("catalog.dat.backup")

        if (Files.exists(backup)) {
            Files.copy(backup, catalogFile, REPLACE_EXISTING)
        } else {
            // 디렉토리 스캔으로 재구성
            rebuildFromFiles()
        }
    }

    private fun rebuildFromFiles() {
        metas.clear()

        Files.list(baseDir)
            .filter { it.toString().endsWith(".tbl") }
            .forEach { file ->
                val tableName = file.fileName.toString().removeSuffix(".tbl")
                val meta = TableMeta(tableName, file.toString())
                metas[tableName] = meta
            }

        // 카탈로그 파일 재작성
        rewriteCatalogFile()
    }
}
```

## 베스트 프랙티스

### ✅ DO

```kotlin
// 1. Catalog 인스턴스 재사용
val catalog = FileCatalog(baseDir, catalogFile)
// 여러 번 생성하지 말 것 (불필요한 파일 I/O)

// 2. 테이블 존재 확인 후 생성
fun createTableIfNotExists(catalog: Catalog, name: String) {
    val tables = catalog.listTables()
    if (name !in tables) {
        catalog.createTable(name)
    }
}

// 3. 안전한 메타데이터 조회
fun safeGetMeta(catalog: Catalog, name: String): TableMeta? {
    return runCatching {
        catalog.getTableMeta(name)
    }.getOrNull()
}

// 4. 리소스 관리
class Database(baseDir: Path) : AutoCloseable {
    private val catalog = FileCatalog(
        baseDir = baseDir.resolve("tables"),
        catalogFile = baseDir.resolve("catalog.dat")
    )

    override fun close() {
        // 필요 시 정리 작업
    }
}
```

### ❌ DON'T

```kotlin
// 1. 중복 Catalog 인스턴스 생성
repeat(100) {
    val catalog = FileCatalog(baseDir, catalogFile)  // ❌ 매번 파일 읽기!
}

// 2. 존재 확인 없이 테이블 생성
catalog.createTable("users")
catalog.createTable("users")  // ❌ 예외 발생!

// 3. 예외 무시
try {
    catalog.getTableMeta("nonexistent")
} catch (e: Exception) {
    // ❌ 아무 처리도 안 함
}

// 4. 긴 테이블 이름
catalog.createTable("a".repeat(1_000_000))  // ❌ 메모리 낭비, 느린 I/O
```

## 관련 컴포넌트

- **HeapTable**: Catalog에서 조회한 filePath로 테이블 데이터 접근
- **DiskManager**: 테이블 파일의 실제 I/O 담당
- **SqlExecutor**: Catalog를 통해 테이블 메타데이터 관리
- **Schema** (향후): Catalog가 테이블 스키마 정보 저장
- **Index** (향후): Catalog가 인덱스 메타데이터 관리
- **BufferPool** (향후): Catalog 페이지 캐싱