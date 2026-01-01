## 📖 학습 자료

이 프로젝트는 다음 개념들을 실습하며 학습합니다:

1. **저장소 계층**
    - 페이지 기반 저장소 구조
    - Slotted Page 레이아웃
    - 버퍼 풀 관리 (LRU, Clock 알고리즘)

2. **인덱스 구조**
    - B+Tree 구현 (삽입, 삭제, 분할, 병합)
    - Hash Index
    - 복합 인덱스

3. **쿼리 처리**
    - SQL 파싱 및 AST 생성
    - 논리적/물리적 실행 계획
    - 조인 알고리즘 (Nested Loop, Hash Join, Merge Join)
    - 쿼리 최적화 (비용 모델, 통계 기반)

4. **트랜잭션 관리**
    - ACID 속성 보장
    - 격리 수준 구현
    - 2PL 락킹 프로토콜
    - 데드락 감지 및 해결

5. **동시성 제어**
    - MVCC (Multi-Version Concurrency Control)
    - 락 에스컬레이션
    - 래치(Latch)를 통한 내부 동기화

6. **로깅 및 복구**
    - WAL 프로토콜
    - ARIES 복구 알고리즘
    - Fuzzy Checkpoint
    - Undo/Redo 로깅

## 🎓 참고 문헌

- **Database System Concepts** (Silberschatz, Korth, Sudarshan)
- **Database Management Systems** (Ramakrishnan, Gehrke)
- **Transaction Processing** (Jim Gray, Andreas Reuter)
- **CMU 15-445/645 Database Systems** (Andy Pavlo)
