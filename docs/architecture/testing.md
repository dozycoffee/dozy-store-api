# 테스트

> 아키텍처 개요는 [README](README.md)를 본다. 테스트 도구를 고른 이유는 [기술 스택](tech-stack.md#테스트)에 있다.

## 계층별 전략

| 계층 | 도구 | 검증 대상 |
|---|---|---|
| domain 단위 | JUnit 5 + `kotlin.test` | 애그리거트 불변식과 상태 전이. 저장소·Spring 없이 객체만 만든다 |
| application 정책 단위 | JUnit 5 + `kotlin.test` | 여러 애그리거트를 함께 보는 판단(`application/policy`). 정책은 I/O가 없으므로 객체만 만든다 |
| persistence 통합 | Testcontainers(PostgreSQL), `IntegrationTest` 상속 | 마이그레이션 적용, Exposed Table 정의와 스키마 일치, Repository 매핑, CHECK·부분 UNIQUE 등 DB 제약, 트랜잭션, 동시성 처리([ERD](../erd.md#동시성-처리)) |
| application 서비스(유스케이스) | Testcontainers(PostgreSQL), `ApplicationTest` 상속 | 조회·잠금·저장 오케스트레이션, 도메인 이벤트 처리, 다른 모듈 UseCase 호출, 외부 포트 호출, 거부 시 DB 상태 불변 |
| API | Testcontainers(PostgreSQL), `ApiTest` 상속, `WebTestClient`, `auth-test` | 엔드포인트별 성공 응답 형식, 웹 계층이 관여하는 오류(요청 오류 400, 도메인 오류의 상태 매핑, 428, 409의 `currentVersion`, 429, 503), 인가. 규칙 자체는 유스케이스 테스트가 덮는다 |
| 아키텍처 | Konsist | 모듈·계층 의존 규칙과 이름 규칙([패키지 구조](package-structure.md#아키텍처-테스트-konsist)) |

**유스케이스·API 테스트에 가짜 저장소를 쓰지 않는다.** 이 계층에서 틀리기 쉬운 것이 잠금 순서, 조건부 upsert, 트랜잭션 롤백인데 가짜 저장소는 이를 전부 통과시킨다. 실제 Repository와 DB로 검증한다.

### 외부 서비스는 포트를 가짜로 바꾼다

저장소는 가짜로 바꾸지 않지만, **외부 서비스 포트는 가짜로 바꾼다.** 실제 Catalog를 띄울 수 없고, Catalog의 응답을 테스트마다 정해야 하기 때문이다.

| 대상 | 유스케이스·API 테스트 | 어댑터 자체의 테스트 |
|---|---|---|
| `CatalogProductInfoPort`, `CatalogProductSnapshotPort` | 테스트 전용 구현(응답을 테스트가 정함)으로 바꾼다. 실패 응답(`CATALOG_UNAVAILABLE`)도 여기서 만든다 | `CatalogClient`는 MockWebServer 같은 가짜 HTTP 서버로 요청 형식, 응답 번역, 시간 초과, 캐시를 확인한다 |
| Catalog 이벤트 (2단계: 재고 이벤트) | 컨슈머를 거치지 않고 반영 UseCase를 직접 부른다(이벤트 내용을 테스트가 정함) | 컨슈머는 메시지 → Command 변환을 단위 테스트로, 브로커 연결은 메시징 기술이 정해지면 Testcontainers로 확인한다 |

### 시나리오를 테스트로 옮기는 방법

[시나리오](../scenarios.md) S1~S8이 테스트의 기준이다.

- **유스케이스 테스트**: 서비스 메서드 하나를 검증한다. 시나리오의 각 단계와 예외 흐름이 테스트 메서드가 되고, `@Nested`로 "S4 기본 흐름" / "S4 예외 흐름"처럼 묶는다.
- **시나리오 테스트**: 여러 모듈에 걸친 흐름(S1 매장 개점, S2 휴업·폐점, S5 Catalog 변경 반영, S8 손님 메뉴, 2단계에서 S6)을 한 테스트가 순서대로 밟는다. 테스트 소스의 `scenario/` 패키지에 둔다.
- 클래스 `@DisplayName`에 시나리오 번호를 남긴다. 시나리오 문서가 바뀔 때 고칠 테스트를 찾을 수 있어야 한다.

## 인가 테스트

요청자를 `auth-test`의 `@WithDozyPrincipal`로 만든다. 같은 엔드포인트를 요청자별로 확인한다.

| 요청자 | 만드는 법 | 확인할 것 |
|---|---|---|
| 점주 매니저 | `type = PARTNER`, `id = 지정한 계정` | 지정된 매장은 성공, 다른 매장은 404 |
| 직원 매니저 | `type = EMPLOYEE`, `roles = ["store:manager"]` | 위와 같음. 역할이 없으면 Store를 부를 수 없음 |
| 종류가 다른 같은 ID | 지정과 같은 `id`, 다른 `type` | 매니저가 아님(404) |
| 본사 매장 관리 | `roles = ["store:admin"]` | 모든 매장 읽기·쓰기 |
| 본사 조회 | `roles = ["store:viewer"]` | 조회 성공, 변경 403 |
| Catalog | `type = SYSTEM`, `roles = ["store:catalog_agent"]` | `/internal/**`만 |
| 로그인 없음 | 애노테이션 없음 | `/public/**`만. 요청 제한 429 |

토큰 검증 자체(다른 realm, 다른 audience, 만료, 믿지 않는 키)는 `DozyTestTokens`로 실제 토큰을 만들어 확인한다. Store는 realm 두 개(`internal`, `partner`)를 받으므로 둘 다 확인한다.

## 동시성 테스트

[ERD 동시성 처리](../erd.md#동시성-처리)의 경합은 통합 테스트로 확인한다.

- 같은 상품의 이벤트가 동시에 처리돼도 순번이 큰 상태가 남는다.
- 매니저 설정 변경과 판매 범위 정리가 겹쳐도 범위 밖 매장의 설정이 남지 않는다(`FOR SHARE` / `FOR UPDATE`).
- 같은 이름 매장 동시 등록, 같은 매니저 동시 지정이 하나만 성공한다.
- (2단계) 재고 이벤트 보관과 상품 복제가 겹쳐도 보관 이벤트가 반영된다(SKU 권고 잠금).

## 통합 테스트 기반

- 영속성 통합 테스트는 `support/IntegrationTest`를 상속한다(`@SpringBootTest`). 유스케이스 테스트는 그 하위인 `ApplicationTest`, API 테스트는 `ApiTest`를 상속한다.
- `ApplicationTest`는 결과 확인용 `tx { … }`를 준다. 테스트가 Repository를 직접 불러 상태를 확인할 때 쓴다.
- `IntegrationTest`에는 `integration` 태그가 붙는다. `./gradlew unitTest`는 이 태그를 빼고 돌려 Docker 없이 도메인·정책 테스트만 확인한다. `./gradlew test`는 전체를 돌린다.
- PostgreSQL 컨테이너는 JVM 전체에서 하나만 띄워 공유한다. 스키마는 실제 앱과 같이 Flyway가 만든다.
- **데이터 정리**: 테스트마다 `flyway_schema_history`를 뺀 모든 테이블을 `TRUNCATE … RESTART IDENTITY CASCADE`로 비운다. 테스트는 다른 테스트가 남긴 데이터에 기대지 않는다.
- 테스트 JVM 시간대는 UTC로 고정한다. 시간이 들어가는 테스트는 고정된 값이나 `Clock`을 쓴다.

## 작성 관례

- **이름**: 테스트 메서드 이름은 한국어 백틱 문장으로 쓴다. 규칙별로 `@Nested` 내부 클래스로 묶고 `@DisplayName`을 붙인다.
- **단언**: `kotlin.test`(`assertEquals`, `assertFailsWith`, `assertIs` 등)를 쓴다.
- **예외**: `assertFailsWith<XxxException>`으로 예외 타입을 구분한다. 분류가 중요하면 `errorCode`나 `errorCode.type`까지 검증한다.
- **거부 시 상태 불변**: 요청이 거부되는 테스트는 예외뿐 아니라 상태(기존 값, 등록된 이벤트 등)가 그대로인지도 검증한다.
- **버그 수정 테스트**: 수정 전 코드에서 실패하는 것을 확인한 뒤 수정한다.
- **반복 케이스**: 입력만 다르고 모양이 같은 케이스는 `@ParameterizedTest`로 묶는다.

## 픽스처

테스트 객체 생성 헬퍼는 테스트 소스의 `fixture/`에 애그리거트별 파일로 둔다. 이름 있는 인자와 기본값을 가진 최상위 함수로 만들고 빌더 클래스는 만들지 않는다.

1. 기본값은 **유효한 최소 상태**다. 예: `store()`의 기본 상태는 등록 직후인 `PREPARING`.
2. **테스트 결과에 영향을 주는 값은 기본값과 같더라도 테스트에 명시한다.**
3. 픽스처는 객체를 만들기만 하고, 검증 대상이 되는 행위(예: `open()`)는 하지 않는다.
4. ID와 값은 고정값을 쓴다. 랜덤 데이터는 쓰지 않는다.
5. 이름 있는 시나리오 픽스처는 여러 테스트에서 3회 이상 반복되는 상황에만 만든다.

유스케이스 테스트에서 검증 대상이 아닌 테이블(예: 매장 상품 테스트의 상품 복제본)은 `execute(sql)`로 직접 넣는다. 다른 유스케이스를 거쳐 준비하면 그 유스케이스가 깨질 때 관계없는 테스트가 함께 실패한다.
