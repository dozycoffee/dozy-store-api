# 영속성

> 아키텍처 개요는 [README](README.md)를 본다. 테이블과 동시성 처리는 [ERD](../erd.md)와 마이그레이션 파일이 원본이다.

스키마를 어떻게 바꾸고, Exposed로 어떻게 매핑하며, 트랜잭션을 어떻게 여는지 다룬다.

## 역할 분담

| 도구 | 역할 | 하지 않는 것 |
|---|---|---|
| Flyway (JDBC) | 스키마 버전 관리와 적용. 앱이 시작될 때 한 번 실행 | 요청 처리 |
| Exposed (R2DBC) | 조회와 변경(DSL) | 테이블 생성(`SchemaUtils.create` 금지) |
| `exposed-migration-r2dbc` | Exposed `Table` 정의와 실제 스키마의 불일치 검사(통합 테스트) | 스키마 적용 |

- Flyway는 `spring-boot-flyway` 모듈만 쓴다. `spring-boot-starter-jdbc`를 추가하거나 요청 경로에서 JDBC를 쓰지 않는다.
- 접속 정보는 설정 파일에 두지 않는다. 로컬은 docker-compose, 테스트는 Testcontainers `@ServiceConnection`이 R2DBC와 JDBC 연결 정보를 함께 만든다. 운영은 환경 변수로 `spring.r2dbc.*`와 `spring.flyway.*`를 함께 지정한다.

## 마이그레이션 작성 규칙

- 위치와 이름: `src/main/resources/db/migration/V{번호}__{설명}.sql`
- **적용된 마이그레이션은 고치지 않는다.** 바꿀 일이 생기면 새 버전으로 추가한다.
- 스키마를 바꾸는 PR에서 [ERD](../erd.md)와 Exposed `Table` 정의를 함께 고친다. 불일치는 스키마 검사 테스트가 잡는다.
- 기본 규칙: ID는 identity, 상태값은 `VARCHAR` + `CHECK`, 시각은 `TIMESTAMPTZ`, 감사 컬럼 기본값은 `now()`. `version`은 낙관적 잠금을 쓰는 `stores`에만 둔다.
- 상태값을 추가하거나 바꿀 때는 `CHECK` 제약을 교체한다.
- **(2단계)** 테이블·제약은 재고 연동 때 새 마이그레이션으로 추가한다.
- 큰 테이블의 인덱스는 `CREATE INDEX CONCURRENTLY`로 만든다(트랜잭션 밖에서 실행). 스키마 변경과 대량 데이터 이전은 나눈다.

## Exposed 매핑

- 헥사고날 구조라 Exposed `Table`과 Repository 구현(`ExposedXxxRepository`)은 각 모듈의 `adapter/outbound/persistence`에, Repository 인터페이스는 `application/port/outbound`에 둔다([ADR-0005](../adr/0005-module-first-hexagonal-architecture.md)). 도메인 모델과 테이블 매핑은 분리한다.
- 새 `Table`을 만들면 테스트 소스의 Table 목록에 등록한다. 그래야 불일치 검사 대상이 된다.
- `Table` 정의는 마이그레이션과 같은 이름·옵션으로 쓴다.
  - 감사 컬럼은 기본값이 마이그레이션과 같은 `now()`로 표현되도록 공통 헬퍼(`auditTimestamp`)로 만든다.
  - 이름을 붙이지 않은 제약(FK, UNIQUE)은 PostgreSQL이 만든 이름(`<테이블>_<컬럼>_fkey`, `<테이블>_<컬럼>_key`)을 그대로 적는다. FK의 삭제 옵션도 마이그레이션과 맞춘다.
  - 부분 인덱스(`WHERE …`)는 `filterCondition`으로 같은 조건을 적는다. 불일치 검사는 `WHERE` 조건과 `CHECK` 식을 비교하지 않으므로, 부분 인덱스의 동작과 `CHECK`는 Repository 통합 테스트에서 DB에 직접 확인한다.
- 시각 컬럼은 `timestampWithTimeZone`으로 읽고 매퍼에서 `Instant`로 바꾼다. 상태값 컬럼은 도메인 enum으로 읽는다.
- 감사 컬럼 `updated_at`은 UPDATE 문에서 DB 시계로 채운다.
- 목록 조회의 이름 검색은 대소문자 무시 부분 일치(`%`·`_`는 글자로 찾음)로, 이름 순 정렬은 `COLLATE "ko-x-icu"`로 쓴다. DB 기본 정렬 규칙은 한글을 가나다순으로 정렬하지 않기 때문이다.

## Repository 구현 규칙

- **Repository는 자기 애그리거트 테이블만 다룬다.** 다른 애그리거트의 데이터가 필요하면 application이 그 Repository나 다른 모듈의 UseCase를 부른다.
- Repository는 트랜잭션을 열지 않는다. application의 `TransactionRunner.inTransaction` 안에서 호출된다.
- 잠금 조회는 `findByIdForUpdate`처럼 `…ForUpdate`(`SELECT … FOR UPDATE`), 공유 잠금은 `…ForShare`(`SELECT … FOR SHARE`) 이름으로 쓴다. 여러 행을 잠글 때는 교착을 피하도록 항상 id 순서로 잠근다.
- 하위 컬렉션(복제본의 대상 매장 등)은 저장할 때 지우고 다시 넣는다.
- 낙관적 잠금 대상(`stores`)은 `UPDATE … WHERE id = ? AND version = ?`로 저장하고, 바뀐 행이 0개면 `VersionConflictException`을 던진다. 성공하면 도메인 객체의 `version`을 1 올린다.
- 낙관적 잠금을 쓰지 않는 애그리거트는 행 전체를 덮어쓰지 않는다. 서로 다른 필드를 바꾸는 요청이 겹칠 수 있으면 필드별 저장 메서드로 바꾼 필드만 UPDATE한다.
- 행이 없으면 만드는 Lazy 생성은 `INSERT … ON CONFLICT … DO UPDATE … RETURNING` 한 문장으로 한다. 동시 요청에도 행이 하나만 생기고, 다시 조회하지 않아도 행을 돌려받는다.
- UNIQUE·PK 위반(23505)은 그 규칙의 도메인 예외(예: `StoreNameDuplicatedException`)로 바꾼다.
- Repository는 도메인 이벤트를 발행하지 않는다. application이 저장한 뒤 꺼내 처리한다.
- 여러 애그리거트를 불러올 때 하위 컬렉션은 애그리거트마다 조회하지 않고 테이블마다 한 번(`… IN (…)`)씩 조회해 묶는다.
- 통합 테스트로 확인할 것: 저장 후 조회(왕복), DB 제약(UNIQUE, FK, CHECK), 해당하는 동시성 규칙.

## Store에서 쓰는 잠금

### 권고 잠금 네임스페이스

잠글 행이 정해져 있지 않은 범위를 직렬화할 때는 트랜잭션 단위 권고 잠금(`pg_advisory_xact_lock(용도, 대상 ID)`)을 쓴다. 용도 번호는 `common.exposed.AdvisoryLockNamespace`에 모아 겹치지 않게 하고, 한 번 정한 번호는 바꾸거나 다시 쓰지 않는다(지금 1은 비어 있다). 잠금은 트랜잭션이 끝날 때 풀린다.

| 번호 | 용도 | 둘째 키 |
|---|---|---|
| 2 | (2단계, 예약) 재고 이벤트 보관과 상품 복제의 SKU 매칭 | `hashtext(sku)` |

### 상품 복제본 갱신

- 이벤트 하나를 한 트랜잭션에서 처리한다. 복제본 교체, 판매 범위 정리, 삭제 정리(2단계에서는 보관 재고 이벤트 반영도)가 함께 커밋되거나 함께 롤백된다.
- 상품 행 단위로 직렬화한다. 행이 있으면 `SELECT … FOR UPDATE`, 없으면 `INSERT … ON CONFLICT (product_id) DO NOTHING` 후 다시 잠근다.
- 판매 범위의 대상 매장(`catalog_product_target_stores`)은 교체할 때 지우고 다시 넣는다.
- 매니저 설정 변경은 복제본 행을 `SELECT … FOR SHARE`로 잠근 채 판매 범위를 확인한다(`catalogproduct`의 `CheckProductSellableUseCase`). 잠금 순서는 항상 **복제본 → 매장 설정**이다(교착 방지).

### 매장

- `stores`는 낙관적 잠금(`version`)을 쓴다. 상태 전환은 `findByIdForUpdate`로 잠그고 저장할 때 `version`도 올린다.
- 매장 코드는 `store_code_seq`에서 받아 애플리케이션이 `ST-%08d` 형식으로 만든다.

### 조회 성능

- 손님 메뉴와 매장 상품 목록은 "이 매장이 판매 범위에 든 `ACTIVE` 상품"을 자주 고른다. `catalog_products`의 `ALL` 상품과 `catalog_product_target_stores(store_id)`로 대상 매장 상품을 합치는 쿼리를 한 번에 쓴다.
- 노출 현황 매장 수는 상품 ID 목록(최대 100개)마다 매장을 돌며 판단하므로 `storeproduct.adapter.outbound.query`에서 집계 쿼리로 쓴다. 이 쿼리는 노출 판단(요구사항 4장)을 SQL로 한 번 더 표현하므로, `ProductVisibilityPolicy`와 결과가 같은지 테스트로 맞춘다.

## 트랜잭션

- application 서비스는 `TransactionRunner.inTransaction { … }`으로 유스케이스의 트랜잭션 경계를 연다. 블록이 예외로 끝나면 블록 안의 변경이 모두 롤백된다. 안쪽에서 다시 호출하면 바깥 트랜잭션을 이어 쓴다.
- Spring `@Transactional`과 섞지 않는다.
- **Exposed의 자동 재시도는 끈다**(`defaultMaxAttempts = 1`). Exposed는 기본으로 DB 예외가 나면 트랜잭션 블록 전체를 다시 실행하는데, 이는 동시성 버그를 가리고 블록 안의 외부 호출을 중복시킨다.
- **커넥션 풀**: `r2dbc-pool`이 클래스패스에 있으면 Spring Boot가 `ConnectionFactory`를 풀로 감싼다. 크기는 `spring.r2dbc.pool.*`로 정한다.
- **Exposed 연결 메타데이터는 시작할 때 읽는다.** Exposed는 식별자 규칙·DB 버전 같은 메타데이터를 처음 쓸 때 블로킹으로 DB에 묻는다. 첫 사용이 Netty 이벤트 루프 스레드에서 일어나면 그 스레드가 멈춘다. `R2dbcDatabase`를 만들 때 이벤트 루프 밖에서 미리 읽어 둔다.
- 여러 워커가 나눠 처리하는 조회가 생기면 `FOR UPDATE SKIP LOCKED`로 다른 트랜잭션이 잠근 행을 건너뛴다.

## 시간

- 코드는 시스템 기본 시간대를 쓰지 않는다. 현재 시각은 `Clock` 빈(UTC)으로 구하고, 업무 날짜(개점일, 폐점일)는 업무 시간대(설정값, 기본 `Asia/Seoul`)로 해석한다.
- SQL에서 날짜를 계산하지 않는다(`CURRENT_DATE`, `now()::date` 금지). 필요한 날짜는 앱이 계산해 파라미터로 넘긴다.
- 서버, JVM, DB는 UTC로 둔다.
