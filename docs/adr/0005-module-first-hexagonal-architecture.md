# ADR-0005: 모듈 우선 헥사고날 구조로 만들고, 다른 모듈은 UseCase로만 부른다

- **상태**: 채택
- **날짜**: 2026-10-06
- **관련**: [아키텍처](../architecture/README.md), [패키지 구조](../architecture/package-structure.md), [영속성](../architecture/persistence.md)

## 맥락

Store를 헥사고날 구조로 만들기로 하면서 정할 것이 넷이었다.

- 최상위를 모듈로 나눌지 계층으로 나눌지
- 방향 패키지 이름
- 영속성 기술
- 다른 모듈의 기능을 어떻게 쓸지

고려한 사정은 다음과 같다.

- 모듈 사이의 의존 방향이 분명하다. 매장 관리와 상품 복제본은 서로 독립이고, 매장 상품이 둘을 쓰고, 손님 메뉴가 매장 상품을 쓴다.
- 같은 조직의 다른 서비스에 구조가 여럿 있다. 창고·재고 서비스는 모듈 우선 헥사고날이고, 인증 서비스는 계층 우선 헥사고날에 `inbound`/`outbound` 패키지와 Exposed를 쓴다. 상품 서비스는 모듈 우선 레이어드에 Exposed를 쓴다.
- 같은 팀이 Exposed와 그 규칙(스키마 불일치 검사, 트랜잭션, 메타데이터 처리, 조건부 upsert, 권고 잠금)을 이미 갖추었다.
- ktlint를 쓴다. `in`은 Kotlin 예약어라 패키지 이름에 백틱이 필요하고 ktlint의 `package-name` 규칙에 걸린다.

## 결정

- **최상위는 도메인 모듈이고, 모듈 안을 헥사고날로 나눈다.** 모듈은 `management`, `catalogproduct`, `storeproduct`, `menu`다. 모듈 밖에는 `core`(도메인 공통 타입)와 `common`(기술 공통)만 둔다.
- **모듈 안의 구조**는 `domain` / `application/port/inbound` / `application/port/outbound` / `application/service` / `adapter/inbound` / `adapter/outbound`다. 방향 패키지는 `inbound`/`outbound`로 쓴다.
- **Repository 인터페이스는 `application/port/outbound`에 둔다.** 애플리케이션이 바깥에 요구하는 기능이므로 outbound port다. domain은 순수 Kotlin 모델·규칙만 둔다.
- **영속성은 Exposed(R2DBC)다.** Exposed `Table`과 Repository 구현은 `adapter/outbound/persistence`에 두고, 도메인 모델과 테이블 매핑을 분리한다.
- **다른 모듈은 그 모듈의 `application/port/inbound`(UseCase, Command, Result)로만 쓴다.** 다른 모듈의 Repository, 도메인 모델, adapter는 쓰지 않는다. 호출은 같은 트랜잭션 안에서 실행된다(`TransactionRunner`가 바깥 트랜잭션을 이어 씀). 예외는 다음 셋이다.
  - **`core`의 공통 타입**(`StoreId`, `ProductId`, `Sku`, `DomainException` 등)은 어디서나 쓴다.
  - **모듈이 내보내는 도메인 이벤트**(`catalogproduct.domain.event`)는 다른 모듈의 핸들러가 구독할 수 있다. `catalogproduct`가 `storeproduct`를 모른 채 판매 범위 정리를 일으키기 위해서다.
  - **노출 현황 집계 쿼리**는 `storeproduct`의 outbound 조회 어댑터가 다른 모듈의 테이블을 읽기 전용으로 직접 조회할 수 있다. 상품 수 × 매장 수의 판단을 UseCase 호출로 하면 느리기 때문이다. 쓰기는 하지 않는다.
- **잠금이 필요한 확인도 UseCase로 한다.** 예: 매니저 설정 변경은 `catalogproduct`의 "판매 가능 여부 확인(잠금)" UseCase를 부르고, 그 UseCase가 상품 복제본 행을 `FOR SHARE`로 잠근다. 같은 트랜잭션이라 잠금이 유지된다.
- **규칙은 Konsist로 검사한다.** domain의 Spring·Exposed import 금지, 계층 의존 방향, 다른 모듈은 `application.port.inbound`와 허용된 이벤트만 참조, 모듈 간 의존 방향, 이름 규칙.

## 검토한 대안

- **계층 우선 헥사고날**: 여러 모듈이 함께 쓰는 포트를 한곳에 둘 수 있다. 하지만 모듈 경계가 패키지에 드러나지 않는다.
- **모듈 우선 레이어드**: 포트를 외부 시스템에만 두어 구조가 단순하다. 하지만 헥사고날로 만들기로 한 방향과 맞지 않고, 모듈의 바깥 계약이 드러나지 않는다.
- **`in`/`out` 패키지**: 다른 헥사고날 서비스 일부와 이름이 같아진다. 하지만 모든 import에 백틱이 붙고 ktlint 규칙을 꺼야 한다.
- **Spring Data R2DBC**: Spring 기본 추상화를 쓴다. 하지만 팀이 쌓은 Exposed 규칙을 다시 만들어야 하고, Store의 동시성 처리(조건부 upsert, `FOR SHARE`, 권고 잠금)는 DSL로 쓰는 편이 명확하다.
- **다른 모듈의 도메인 모델을 읽기만 함**: 조회 UseCase가 필요 없어 코드가 짧다. 하지만 다른 모듈의 Repository와 도메인 모델에 기대게 되어 모듈의 바깥 계약이 흐려진다.

## 결과

- **얻는 것**
  - 모듈마다 바깥 계약이 UseCase로 고정되어, 모듈 안을 바꿔도 다른 모듈이 깨지지 않는다.
  - 팀의 Exposed 규칙과 테스트 기반을 그대로 쓴다.
  - 규칙을 Konsist로 기계적으로 검사한다.
- **감수하는 것**
  - 다른 모듈이 쓰는 조회 UseCase와 Result 타입이 늘어난다.
  - 도메인 모델과 테이블 사이의 매핑 코드가 필요하다.
  - 노출 현황 집계 쿼리는 노출 판단 규칙(요구사항 4장)을 SQL로 한 번 더 표현한다. 정책 클래스와 결과가 같은지 테스트로 맞춘다.
