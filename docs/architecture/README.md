# Store 아키텍처

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다.

어떤 기술로, 코드를 어떻게 구성하는지 다룬다. 주제가 서로 독립적이라 주제별 문서로 나눈다.

| 문서 | 다루는 것 |
|---|---|
| [기술 스택](tech-stack.md) | 언어·프레임워크·영속성·직렬화·테스트·인증 기술의 선택과 이유 |
| [패키지 구조](package-structure.md) | 모듈 우선 헥사고날 구조, 모듈 간 의존과 UseCase, 이름 규칙, 유스케이스 작성 관례, 아키텍처 테스트 |
| [예외 구조](exception.md) | 도메인 예외와 오류 분류, HTTP 상태 매핑, 메시지 소비의 예외 |
| [테스트](testing.md) | 계층별 테스트 전략, 외부 연동·인가·동시성 테스트, 픽스처 원칙 |
| [영속성](persistence.md) | Flyway·Exposed 역할 분담, 마이그레이션 규칙, 매핑, Repository, 트랜잭션, 시간 |
| [외부 연동과 운영 값](integration.md) | Catalog 호출과 캐시, 메시지 소비, 인증, 공개 경로, 운영 값 |

## 공통 원칙

Store는 **모듈 우선 헥사고날** 구조다([ADR-0005](../adr/0005-module-first-hexagonal-architecture.md)).

- 의존 방향은 `adapter → application → domain`이다. domain은 순수 Kotlin이고 HTTP·DB·메시징 개념을 모른다.
- 최상위 패키지는 도메인 모듈이고, 모듈 안은 `domain` / `application/port/{inbound,outbound}` / `application/service` / `adapter/{inbound,outbound}`로 나눈다. 모듈 밖에는 `core`(도메인 공통 타입)와 `common`(기술 공통)만 둔다.
- Repository 인터페이스는 `application/port/outbound`에 둔다.
- **다른 모듈은 그 모듈의 UseCase(`port/inbound`)로만 쓴다.** 예외는 ADR-0005에 있다.
- 애그리거트끼리는 ID로만 참조한다. 여러 애그리거트를 함께 보는 판단은 application 정책(I/O 없는 순수 클래스)에 둔다.
- 트랜잭션 경계는 application 서비스가 `TransactionRunner`로 연다. 다른 모듈의 UseCase는 같은 트랜잭션 안에서 불린다.
- **외부 서비스는 모두 포트 뒤에 둔다.** Catalog 조회는 outbound port와 `adapter/outbound/catalog`, Catalog 이벤트와 (2단계) 재고 이벤트는 `adapter/inbound/messaging`에서 Store의 Command로 번역해 UseCase를 부른다. 도메인과 application은 Kafka나 Catalog의 응답 형식을 모른다. 메시징 기술과 이벤트 형식이 정해지기 전에도 Store를 개발할 수 있는 이유다.

## 미정 사항

| 항목 | 내용 | 관련 문서 |
|---|---|---|
| 메시징 기술 | Kafka로 할지. 공유 클러스터, 토픽 이름 규칙, 메시지 봉투, 직렬화, 실패 처리(재시도·DLQ)는 서비스 공통 이벤트 규약으로 정한다. Catalog의 상품 이벤트 발행이 이 결정을 기다린다 | [외부 연동](integration.md#메시지-소비) |
| Kafka 클라이언트와 코루틴 | 리스너 스레드에서 `suspend` 유스케이스를 부르는 방식. 메시징 기술과 함께 정한다 | [기술 스택](tech-stack.md) |
| 캐시를 인스턴스마다 둘지 공유할지 | 지금 제안은 인스턴스별 인메모리 캐시(Caffeine). 인스턴스가 늘어 캐시 불일치나 Catalog 호출량이 문제가 되면 공유 캐시(Redis)를 검토한다. 정할 때 ADR로 남긴다 | [외부 연동](integration.md#catalog-호출과-캐시) |
| 요청 제한 위치 | 지금 제안은 Store 안의 인메모리 버킷. 게이트웨이나 CDN에서 제한할 수 있게 되면 옮긴다 | [외부 연동](integration.md#공개-경로) |
| 운영 값 | 캐시 시간, 요청 제한, 반영 지연 목표치 등. 지금은 임시 초기값이다 | [외부 연동](integration.md#운영-값) |
| 배포 환경 | 인스턴스 수, 마이그레이션 실행 시점, 비밀값 관리, 컨테이너 이미지와 릴리스 방식 | — |
