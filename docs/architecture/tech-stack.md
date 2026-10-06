# 기술 스택

> 아키텍처 개요는 [README](README.md)를 본다. 정확한 버전은 `gradle/libs.versions.toml`이 원본이라 여기에는 적지 않는다.

## 언어 / 런타임

| 항목 | 선택 |
|---|---|
| 언어 | Kotlin |
| JVM | 21 (Gradle toolchain) |
| 빌드 | Gradle Kotlin DSL + 버전 카탈로그(`gradle/libs.versions.toml`) |

## 프레임워크

| 항목 | 선택 | 비고 |
|---|---|---|
| 애플리케이션 | Spring Boot 4 | |
| 웹 스택 | Spring WebFlux (리액티브, non-blocking) | Servlet/MVC 아님 |
| 동시성 모델 | Kotlin Coroutines + Project Reactor | 컨트롤러·서비스는 `suspend` 함수로 작성 |
| 인증 | `dozy-auth`의 `auth-spring-boot-starter` (Spring Security 리액티브 기반) | 토큰 검증·권한 변환·401·403 응답을 스타터가 제공한다. Store는 역할 인가와 매니저 지정 확인만 작성한다. 서비스 간 호출에는 스타터의 system token 클라이언트(`dozySystemWebClient`)를 쓴다. 라이브러리는 GitHub Packages에서 받으므로 빌드에 인증이 필요하다 |
| 모니터링 | Spring Boot Actuator | `health`, `info`만 웹에 노출 |

### 인증 설정

| 설정 | 값 | 이유 |
|---|---|---|
| `dozy.auth.audience` | `store` | |
| `dozy.auth.accepted-realms` | `[internal, partner]` | 본사 직원·서비스 계정과 점주를 받는다 |
| `dozy.auth.public-paths` | `/actuator/health/**`, `/api/v1/public/**` | 프로브와 손님 API는 토큰 없이 허용한다 |
| `dozy.auth.client.enabled` | `true` | Catalog를 부른다. Store의 서비스 계정 id·secret을 배포 설정의 비밀값으로 넣는다. 없으면 앱이 뜨지 않는다 |

## 영속성

| 항목 | 선택 | 이유 |
|---|---|---|
| DB | PostgreSQL | 부분 UNIQUE 인덱스, CHECK, `FOR UPDATE`/`FOR SHARE`, 조건부 upsert, 권고 잠금을 설계에 쓴다([ERD](../erd.md)) |
| ORM | JetBrains Exposed (R2DBC) | DSL로 동시성 처리(조건부 upsert, 잠금)를 명확하게 쓴다. Spring Data R2DBC 리포지토리 추상화는 쓰지 않는다([ADR-0005](../adr/0005-module-first-hexagonal-architecture.md)) |
| 전송 계층 | `spring-boot-starter-r2dbc` | Boot가 `spring.r2dbc.*`로 `ConnectionFactory`를 자동 구성하는 용도로만 쓴다. Exposed의 `R2dbcDatabase`가 이를 감싼다 |
| 커넥션 풀 | `r2dbc-pool` | Boot 4의 R2DBC 스타터에 포함되지 않아 따로 둔다 |
| 드라이버 | `org.postgresql:r2dbc-postgresql` 1.1.2 이상 | 1.1.1은 결과의 마지막 행이 빠질 수 있는 버그가 있다 |
| 마이그레이션 | Flyway (`spring-boot-flyway` 모듈, JDBC) | 순수 SQL로 PostgreSQL 기능을 그대로 쓴다. R2DBC를 지원하지 않아 앱 시작 시 JDBC로 한 번 실행한다 |
| Exposed 추가 모듈 | `exposed-java-time`, `exposed-migration-r2dbc`(테스트) | `TIMESTAMPTZ`·`DATE` 매핑, Table 정의와 스키마 불일치 검사 |
| 시간 | `java.time` (`Instant`, `LocalDate`, `Clock`) | JDK 표준이라 Jackson·Spring·Exposed 지원이 가장 넓다 |
| 로컬 DB | Docker Compose (`compose.yaml`) | `spring-boot-docker-compose`가 `bootRun` 시 자동 기동·연결 |

## Store에서 쓰는 그 밖의 기술

| 항목 | 선택 (제안) | 이유 |
|---|---|---|
| 메시지 소비 | Kafka (미정) | Catalog 상품 이벤트를 받는다. 2단계에서 재고 이벤트가 더해질 수 있다. 클라이언트와 코루틴 연동 방식은 메시징 기술과 함께 정한다 |
| Catalog 호출 | 스타터의 `dozySystemWebClient` (Spring `WebClient`) | system token을 자동으로 붙이고 캐시한다 |
| 캐시 | Caffeine (인스턴스별 인메모리) | Catalog의 카테고리 목록과 손님 메뉴용 표시 정보를 짧게 둔다. 인프라를 늘리지 않는다 |
| 요청 제한 | Bucket4j (인스턴스별 인메모리) | 공개 경로의 IP 단위 제한. 인스턴스가 늘면 한도가 대수만큼 늘어난다 |

## 직렬화

- **Jackson 3** (`tools.jackson` groupId) 하나로 통일한다. Spring Boot 4의 기본값이라 뺄 수 없고, 두 라이브러리를 함께 쓰면 타입마다 애노테이션을 두 벌 관리해야 하기 때문이다.

## 테스트

| 항목 | 선택 | 이유 |
|---|---|---|
| 프레임워크 | JUnit 5 + `kotlin.test` | 도메인 객체가 외부 협력자 없는 순수 객체라 Kotest·MockK 없이 충분하다 |
| 리액티브 | `spring-boot-starter-webflux-test`, `kotlinx-coroutines-test` | |
| 인증 | `dozy-auth`의 `auth-test` | `@WithDozyPrincipal`로 인증된 요청자를 만들고, `DozyTestTokens`로 실제 검증 체인을 거치는 토큰을 만든다 |
| 영속성 통합 | Testcontainers (`@ServiceConnection`) | 로컬의 docker-compose 자동 연결과 같은 방식으로 R2DBC·JDBC 연결을 자동 구성한다 |
| 아키텍처 | Konsist | 모듈·계층 의존 규칙과 이름 규칙을 검사한다 |

## 개발 편의

- `spring-boot-devtools`: 핫 리로드
- `kapt` + `spring-boot-configuration-processor`: `@ConfigurationProperties` 메타데이터
- ktlint (`org.jlleitschuh.gradle.ktlint`): 코드 스타일, pre-commit 훅
