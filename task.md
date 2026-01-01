# 목표:

- RDB 직접 구현
- 트랜잭션 격리 수준, 자료 구조, 쿼리 분석, 옵티마이저 등을 직접 구현하며 체득

# 기술:

- 언어: kotlin
- 외부 의존성 최소 사용

# 요구사항:

- 인덱스와 유니크 키, 외래 키 사용 가능
- CLI를 통한 접속 가능

# 지금 만들 DB의 성격

단일 프로세스

로컬 파일 기반

SQL은 최소 (CREATE / INSERT / SELECT)

트랜잭션은 나중 (처음엔 single-thread)

“항상 실행 가능한 상태 유지”

# 🗺️ RDB 직접 구현 로드맵 (A → Z)

> 기준

- Language: **Kotlin**
- Goal: **학습 + 실사용 가능한 미니 RDB**
- Strategy: **항상 “동작하는 DB”를 유지하며 점진 확장**

---

# 🗺️ Mini RDB 직접 구현 체크리스트 (A → Z)

> Language: Kotlin  
> Goal: 학습 + 실사용 가능한 미니 RDB  
> Rule: 항상 "동작하는 DB" 상태 유지

---

## A. 프로젝트 골격

### A-1 ☑ 최소 구현

- [x] 패키지 구조 확정
    - buffer / catalog / cli / common / engine / index / sql / storage / tx
- [x] 공통 타입 정의
    - PageId, FrameId, TxId, Lsn, Rid
- [x] 공통 예외
    - DbException(code, message)
- [x] Gradle 기반 실행 가능 프로젝트
- [x] CLI REPL 기본 루프
- [x] Factory 패턴 도입
    - DbEngineFactory로 초기화 로직 분리
    - main → Factory → DbEngine → Repl 구조 확립

### A-2 🔧 리팩토링 / 성능

- [ ] 로깅 레벨 토글
- [ ] 간단한 마이크로 벤치마크 스크립트

---

## B. 파일 / 페이지 I/O

### B-1 ☑ 최소 구현

- [x] DB 파일 생성 / 오픈 / 클로즈
- [x] 고정 크기 Page (4KB)
- [x] readPage(pageId)
- [x] writePage(pageId)
- [x] allocatePage() (파일 확장)
- [x] FileDiskManager 구현
- [x] DiskManager 인터페이스 분리
- [ ] null path / parent 처리 안정화

### B-2 🔧 리팩토링 / 성능

- [ ] ByteBuffer 재사용
- [ ] fsync 정책 옵션화

---

## C. 페이지 레이아웃 & 레코드

### C-1 ☑ 최소 구현

- [x] Page Header
    - pageType
    - freeSpaceOffset
    - slotCount
- [x] Slotted Page (가변 길이 레코드)
- [x] insert / read
- [x] logical delete
- [ ] 최소 1회 compaction

### C-2 🔧 리팩토링 / 성능

- [ ] slot 재사용
- [ ] fragmentation 최소화 전략
- [ ] record header 최적화 (null bitmap, length)

---

## D. 카탈로그 (메타데이터)

### D-1 ☑ 최소 구현

- [x] TableMeta
    - tableName
    - filePath (String)
    - columns: List<ColumnMeta>
- [x] ColumnMeta
    - name
    - type (INT, STRING)
    - nullable
- [x] Catalog 인터페이스
- [x] FileCatalog (바이너리 포맷, append-only)
- [x] CREATE TABLE
- [x] SHOW TABLES
- [x] DESCRIBE TABLE
- [x] 재시작 시 catalog.meta 로드 검증
- [ ] system catalog 파일
- [x] 테이블 생성 / 로드
- [ ] Catalog 버그 수정
    - EOF 처리
    - 문자열 길이 검증
    - parent null 안정화

### D-2 🔧 리팩토링 / 성능

- [ ] catalog를 system table로 승격
- [ ] schema 캐시

---

## E. 힙 테이블 (Heap Storage)

### E-1 ☑ 최소 구현

- [x] HeapTable
- [x] pageId 리스트 관리
- [x] `insertRow() -> Rid`
- [x] `scanAll()`
- [x] DiskManager 직접 호출 제거
- [x] BufferPool 기반 페이지 접근

### E-2 🔧 리팩토링 / 성능

- [ ] free-space map
- [ ] page reuse
- [ ] sequential scan prefetch

---

## F. CLI / Session

### F-1 ☑ 최소 구현

- [x] Repl ↔ Executor ↔ HeapTable 연결
- [x] CREATE TABLE
- [x] INSERT
- [x] SELECT *
- [x] SHOW TABLES
- [x] DESCRIBE
- [x] REPL 종료 시 flushAll 연동
- [x] 출력 책임 CLI로 완전 이동
- [x] 테이블 출력 포매팅 개선
- [ ] session context
- [ ] autocommit

### F-2 🔧 리팩토링 / 성능

- [ ] pretty print
- [ ] script 실행

---

## G. Buffer Pool

### G-1 ☑ 최소 구현

- [x] 고정 크기 BufferPool
- [x] PageTable (pageId → frameId)
- [x] dirty page tracking
- [x] flushPage / flushAll
- [x] DiskManager 인터페이스 의존
- [x] eviction 없음 (의도적)

### G-2 🔧 리팩토링 / 성능

- [ ] pin / unpin
- [ ] eviction (LRU or Clock)
- [ ] hit / miss metrics
- [ ] background flush

---

## H. B+Tree Index (Read)

### H-1 ☑ 최소 구현

- [ ] B+Tree node layout
- [ ] `search(key)`
- [ ] leaf linked list

### H-2 🔧 리팩토링 / 성능

- [ ] range scan iterator
- [ ] prefix compression (optional)

---

## I. B+Tree Index (Write)

### I-1 ☑ 최소 구현

- [ ] insert
- [ ] leaf split
- [ ] internal split
- [ ] root split

### I-2 🔧 리팩토링 / 성능

- [ ] delete
- [ ] merge / redistribute
- [ ] fanout 튜닝

---

## J. 제약조건 (Constraints)

### J-1 ☑ 최소 구현

- [ ] Primary Key (unique index)
- [ ] NOT NULL
- [ ] UNIQUE

### J-2 🔧 리팩토링 / 성능

- [ ] CHECK constraint
- [ ] Foreign Key

---

## K. Query Executor

### K-1 ☑ 최소 구현

- [ ] SeqScan
- [ ] IndexScan
- [ ] Filter
- [ ] Project
- [ ] iterator (`open / next / close`)

### K-2 🔧 리팩토링 / 성능

- [ ] predicate pushdown
- [ ] column pruning

---

## L. Transaction Manager

### L-1 ☑ 최소 구현

- [ ] begin / commit / abort
- [ ] autocommit
- [ ] tx context

### L-2 🔧 리팩토링 / 성능

- [ ] tx 상태 진단

---

## M. Lock Manager (READ COMMITTED)

### M-1 ☑ 최소 구현

- [ ] row-level S / X lock
- [ ] lock table
- [ ] wait queue
- [ ] timeout 기반 deadlock 회피

### M-2 🔧 리팩토링 / 성능

- [ ] deadlock detection
- [ ] intention lock

---

## N. WAL (Logging)

### N-1 ☑ 최소 구현

- [ ] append-only WAL
- [ ] redo log record
- [ ] LSN
- [ ] write-ahead rule

### N-2 🔧 리팩토링 / 성능

- [ ] group commit
- [ ] log buffer

---

## O. Crash Recovery

### O-1 ☑ 최소 구현

- [ ] restart 시 redo replay

### O-2 🔧 리팩토링 / 성능

- [ ] checkpoint
- [ ] dirty page table

---

## P. Undo / Rollback

### P-1 ☑ 최소 구현

- [ ] abort 시 undo
- [ ] page-level undo

### P-2 🔧 리팩토링 / 성능

- [ ] ARIES-style undo
- [ ] savepoint

---

## Q. Foreign Key

### Q-1 ☑ 최소 구현

- [ ] parent 존재 검증
- [ ] delete restrict

### Q-2 🔧 리팩토링 / 성능

- [ ] cascade / set null

---

## R. Isolation Level 확장

### R-1 ☑ 최소 구현

- [ ] READ COMMITTED 안정화

### R-2 🔧 리팩토링 / 성능

- [ ] REPEATABLE READ
- [ ] SERIALIZABLE

---

## S. Optimizer (Rule-based)

### S-1 ☑ 최소 구현

- [ ] index 우선 선택 규칙
- [ ] filter pushdown

### S-2 🔧 리팩토링 / 성능

- [ ] cost model
- [ ] join order

---

## T. Statistics

### T-1 ☑ 최소 구현

- [ ] row count
- [ ] distinct count

### T-2 🔧 리팩토링 / 성능

- [ ] histogram
- [ ] analyze

---

## U. Join

### U-1 ☑ 최소 구현

- [ ] nested loop join

### U-2 🔧 리팩토링 / 성능

- [ ] hash join
- [ ] index nested loop join

---

## V. 운영 기능

### V-1 ☑ 최소 구현

- [ ] SHOW TABLES
- [ ] DESCRIBE
- [ ] EXPLAIN (형태만)

### V-2 🔧 리팩토링 / 성능

- [ ] metrics
- [ ] debug commands

---

## W. 안정성 / 테스트

### W-1 ☑ 최소 구현

- [ ] 랜덤 테스트
- [ ] 재시작 반복 테스트

### W-2 🔧 리팩토링 / 성능

- [ ] crash fuzzing
- [ ] 동시성 테스트

---

## X. 성능 튜닝 1차

### X-1 ☑ 최소 구현

- [ ] 병목 측정 포인트 정의

### X-2 🔧 리팩토링 / 성능

- [ ] buffer/page size 튜닝 가이드
- [ ] split 빈도 감소 전략

---

## Y. 성능 튜닝 2차 (고급)

### Y-1 ☑ 최소 구현

- [ ] (해당 없음)

### Y-2 🔧 리팩토링 / 성능

- [ ] latch contention 감소
- [ ] prefetch / read-ahead
- [ ] log flush 정책 개선

---

## Z. 마무리 / 문서화

### Z-1 ☑ 최소 구현

- [ ] 지원 기능 목록 명세
- [ ] 아키텍처 다이어그램
- [ ] 핵심 자료구조 설명

### Z-2 🔧 리팩토링 / 성능

- [ ] 벤치마크 결과 정리
- [ ] 설계 트레이드오프 문서화

---

## ✅ MVP 완주 기준

**B → C → E → G → I → L → M → N → O**

===

# 구조

📦 1. Storage & Physical Layer (가장 바닥)
구성요소 설명 직접 구현 포인트
Page 디스크 I/O의 최소 단위 (보통 4KB~16KB)    고정/가변 페이지 구조, header + body
File 테이블/인덱스가 저장되는 물리 파일 파일 분할, 확장 전략
Slotted Page 가변 길이 레코드 저장 방식 free space 관리
Record Format row 저장 형식 null bitmap, 가변 컬럼
Disk I/O 파일 read/write fsync, flush 타이밍
WAL (Redo Log)    장애 복구용 로그 LSN, write-ahead rule

🧠 2. Buffer Management
구성요소 설명 직접 구현 포인트
Buffer Pool 디스크 페이지 캐시 크기 고정, 페이지 교체
Page Table pageId → frame 매핑 hash map
Replacement Policy 페이지 교체 정책 LRU / Clock
Dirty Page 수정된 페이지 추적 flush 조건
Pin / Unpin 페이지 사용 중 보호 동시성 핵심

🔒 3. Transaction & Concurrency Control
구성요소 설명 직접 구현 포인트
Transaction 작업의 논리 단위 txId, state
Isolation Level 격리 수준 RC / RR / Serializable
Lock Manager 락 관리 S/X/Intent Lock
Lock Table 락 상태 저장 deadlock 탐지
MVCC 다중 버전 관리 undo log, version chain
Deadlock Detection 교착 상태 해결 wait-for graph

🧾 4. Logging & Recovery
구성요소 설명 직접 구현 포인트
WAL redo 로그 append-only
Undo Log 롤백용 로그 MVCC와 연계
Checkpoint 복구 단축 dirty page flush
Recovery crash 복구 redo → undo
LSN 로그 순서 번호 pageLSN 관리

🧱 5. Data Model & Schema
구성요소 설명 직접 구현 포인트
Table 테이블 메타정보 column schema
Column 컬럼 정의 타입, nullable
Schema Catalog 메타데이터 저장소 system table
Primary Key 기본 키 unique + index
Foreign Key 참조 무결성 cascade 처리
Index 탐색 가속 구조 B+Tree

🌳 6. Index & Access Methods
구성요소 설명 직접 구현 포인트
B+Tree 범위/정렬 인덱스 split / merge
Hash Index equal 검색 rehash
Index Page 인덱스 노드 internal/leaf
Secondary Index 보조 인덱스 rowId 저장
Unique Index 중복 방지 insert 시 검증

🧠 7. Query Processing
구성요소 설명 직접 구현 포인트
SQL Parser SQL → AST 직접 or 간단 문법
Logical Plan 논리 실행 계획 selection, join
Physical Plan 실제 연산 index scan vs seq scan
Executor 연산 실행기 iterator 모델
Predicate Pushdown 필터 최적화 early filter

⚙️ 8. Query Optimizer
구성요소 설명 직접 구현 포인트
Cost Model 실행 비용 계산 I/O 기반
Statistics 카디널리티 추정 histogram
Access Path 인덱스 선택 rule-based
Join Order 조인 순서 greedy

🛡️ 9. Constraints & Integrity
구성요소 설명 직접 구현 포인트
NOT NULL null 제약 insert 검사
UNIQUE 중복 방지 index 연계
CHECK 값 검증 조건식
FK Constraint 참조 무결성 참조 검사

🧑‍💻 10. Interface & Tooling
구성요소 설명 직접 구현 포인트
CLI DB 접속 인터페이스 REPL
Protocol 명령 전달 text 기반
Session 연결 단위 tx 바인딩
Error Handling 오류 체계 SQLSTATE
Config 설정 buffer size 등
