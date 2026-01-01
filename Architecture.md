## 🏗️ 아키텍처

KooscoDB는 계층화된 아키텍처로 설계되어 각 계층이 명확한 책임을 가집니다.

```
┌────────────────────────────────────────────────────────────────┐
│                         Interface Layer                        │
│                                                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                      │
│  │   REPL   │  │    CLI   │  │    API   │                      │
│  └─────┬────┘  └─────┬────┘  └─────┬────┘                      │
│        │             │             │                           │
│        └─────────────┼─────────────┘                           │
└──────────────────────┼─────────────────────────────────────────┘
                       │
                       ▼
┌────────────────────────────────────────────────────────────────┐
│                        DB Engine Layer                         │
│                                                                │
│  ┌──────────────────────────────────────────────────────┐      │
│  │  DbEngine                                            │      │
│  │  - Session Management                                │      │
│  │  - Connection Pool                                   │      │
│  │  - Lifecycle Management                              │      │
│  └──────────────────────────────────────────────────────┘      │
└──────────────────────┼─────────────────────────────────────────┘
                       │
                       ▼
┌────────────────────────────────────────────────────────────────┐
│                   Query Processing Layer                       │
│                                                                │
│  ┌─────────┐    ┌─────────┐    ┌──────────┐                    │
│  │  Parser │ -> │ Planner │ -> │Optimizer │                    │
│  └─────────┘    └─────────┘    └─────┬────┘                    │
│   (SQL → AST)   (Logical Plan)       │                         │
│                                       ▼                        │
│                              ┌─────────────┐                   │
│                              │  Executor   │                   │
│                              └──────┬──────┘                   │
│                                     │                          │
└─────────────────────────────────────┼──────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────┐
│                   Execution / Operator Layer                   │
│                                                                │
│  ┌─────────┐  ┌──────────┐  ┌────────┐  ┌─────────┐            │
│  │ SeqScan │  │IndexScan │  │ Filter │  │ Project │            │
│  └────┬────┘  └────┬─────┘  └───┬────┘  └────┬────┘            │
│       │            │            │            │                 │
│       │  ┌──────────┐  ┌─────────────┐  ┌──────────┐           │
│       │  │NestedJoin│  │  HashJoin   │  │ MergeJoin│           │
│       │  └──────────┘  └─────────────┘  └──────────┘           │
│       │                                                        │
│       └───────────────────┬────────────────────────────────────┘
│                           │
└───────────────────────────┼───────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│                    Storage Engine Layer                        │
│                                                                │
│  ┌──────────────┐          ┌──────────────┐                    │
│  │ Table Access │          │Index Access  │                    │
│  │              │          │              │                    │
│  │ - HeapTable  │          │ - B+Tree     │                    │
│  │ - SlottedPage│          │ - HashIndex  │                    │
│  └──────┬───────┘          └──────┬───────┘                    │
│         │                         │                            │
│         └────────────┬────────────┘                            │
│                      ▼                                         │
│            ┌──────────────────┐                                │
│            │   BufferPool     │  ← Page Cache                  │
│            │                  │                                │
│            │ - Page Table     │                                │
│            │ - Replacement    │                                │
│            │ - Pin/Unpin      │                                │
│            └────────┬─────────┘                                │
│                     ▼                                          │
│            ┌──────────────────┐                                │
│            │  DiskManager     │  ← Physical I/O                │
│            │                  │                                │
│            │ - Page I/O       │                                │
│            │ - File Manager   │                                │
│            └──────────────────┘                                │
│                                                                │
│  ┌──────────────────────────────────────────────┐              │
│  │  Catalog / Data Dictionary                   │              │
│  │  - Schema Metadata                           │              │
│  │  - Statistics                                │              │
│  └──────────────────────────────────────────────┘              │
└────────────────────────────────────────────────────────────────┘
                            ▲
                            │
                  (Read/Write Coordination)
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│              Transaction & Concurrency Control Layer           │
│                                                                │
│  ┌──────────────────────┐                                      │
│  │ Transaction Manager  │                                      │
│  │                      │                                      │
│  │ - Begin/Commit/Abort │                                      │
│  │ - Isolation Levels   │                                      │
│  │ - Transaction State  │                                      │
│  └──────────┬───────────┘                                      │
│             ▼                                                  │
│  ┌──────────────────────┐     ┌──────────────────────┐         │
│  │   Lock Manager       │     │       MVCC           │         │
│  │                      │     │                      │         │
│  │ - S/X/IS/IX Locks    │     │ - Version Chain      │         │
│  │ - Deadlock Detection │     │ - Snapshot Isolation │         │
│  │ - Wait-for Graph     │     │ - Garbage Collection │         │
│  └──────────────────────┘     └──────────────────────┘         │
└────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│                 Logging & Recovery Layer                       │
│                                                                │
│  ┌──────────────────────┐                                      │
│  │    WAL Manager       │  ← Write-Ahead Logging               │
│  │                      │                                      │
│  │ - Redo Logging       │                                      │
│  │ - Undo Logging       │                                      │
│  │ - LSN Management     │                                      │
│  │ - Log Buffer         │                                      │
│  └──────────┬───────────┘                                      │
│             ▼                                                  │
│  ┌──────────────────────┐                                      │
│  │  Recovery Manager    │  ← Crash Recovery                    │
│  │                      │                                      │
│  │ - ARIES Algorithm    │                                      │
│  │ - Analysis Phase     │                                      │
│  │ - Redo Phase         │                                      │
│  │ - Undo Phase         │                                      │
│  └──────────────────────┘                                      │
│                                                                │
│  ┌──────────────────────┐                                      │
│  │ Checkpoint Manager   │  ← Periodic Checkpointing            │
│  │                      │                                      │
│  │ - Fuzzy Checkpoint   │                                      │
│  │ - Dirty Page Table   │                                      │
│  └──────────────────────┘                                      │
└────────────────────────────────────────────────────────────────┘
```

## 📊 데이터 흐름

### SELECT 쿼리 실행 흐름

```
1. Interface Layer
   REPL receives: "SELECT * FROM users WHERE age > 18"
         │
         ▼
2. DB Engine
   DbEngine creates session and delegates to query processor
         │
         ▼
3. Query Processing
   Parser → AST
   Planner → Logical Plan (Filter → SeqScan)
   Optimizer → Physical Plan (선택: IndexScan or SeqScan)
         │
         ▼
4. Execution
   Executor instantiates operators:
   - Filter(age > 18)
     └─ IndexScan(users, age_idx) or SeqScan(users)
         │
         ▼
5. Storage Access
   Operator requests pages via BufferPool
   - BufferPool checks cache (hit/miss)
   - If miss: DiskManager reads page from disk
   - Returns page data to operator
         │
         ▼
6. Transaction Control
   - Acquire read locks (if needed)
   - Check visibility (MVCC)
   - Log read operation (if needed)
         │
         ▼
7. Result Assembly
   Filter evaluates predicate
   Project selects columns
   Results accumulated and returned
         │
         ▼
8. Response
   REPL formats and displays results
```

### INSERT 쿼리 실행 흐름

```
1. Interface Layer
   REPL receives: "INSERT INTO users VALUES (1, 'Alice', 25)"
         │
         ▼
2. Transaction Start
   Begin transaction (if not in autocommit)
         │
         ▼
3. Query Processing
   Parser → AST
   Planner → Insert Plan
   Optimizer → Validate constraints
         │
         ▼
4. Lock Acquisition
   Acquire write lock on table/row
         │
         ▼
5. Storage Engine
   - HeapTable.insertRow()
   - BufferPool allocates/fetches page
   - SlottedPage.insert() writes record
   - Mark page as dirty
         │
         ▼
6. Index Maintenance
   Update all indexes (B+Tree, Hash)
   - Find leaf page
   - Insert key-value pair
   - Handle splits if necessary
         │
         ▼
7. WAL Logging
   - Generate LSN
   - Write redo log record
   - Write undo log record (for rollback)
   - Flush log to disk (write-ahead rule)
         │
         ▼
8. Commit
   - Release locks
   - Flush dirty pages (or defer)
   - Mark transaction as committed
         │
         ▼
9. Response
   REPL displays "OK"
```

## 🔑 핵심 컴포넌트

### Storage Engine
- **SlottedPage**: 가변 길이 레코드를 효율적으로 저장하는 페이지 레이아웃
- **BufferPool**: 디스크 페이지를 메모리에 캐싱하여 I/O 최소화
- **B+Tree**: 범위 쿼리와 정렬을 지원하는 인덱스 구조
- **DiskManager**: 페이지 단위 I/O와 파일 관리

### Transaction Management
- **Lock Manager**: 2PL(Two-Phase Locking) 기반 동시성 제어
- **MVCC**: 다중 버전 동시성 제어로 읽기-쓰기 충돌 최소화
- **Isolation Levels**: Read Committed, Repeatable Read, Serializable

### Recovery
- **WAL (Write-Ahead Logging)**: 모든 변경사항을 로그에 먼저 기록
- **ARIES Algorithm**: 분석(Analysis), 재실행(Redo), 취소(Undo) 3단계 복구
- **Checkpoint**: 복구 시간 단축을 위한 주기적 체크포인트

### Query Processing
- **Parser**: SQL을 추상 구문 트리(AST)로 변환
- **Optimizer**: 비용 기반 또는 규칙 기반 최적화로 실행 계획 선택
- **Executor**: 이터레이터 모델(Volcano Model)로 쿼리 실행
