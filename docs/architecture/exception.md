# 예외 구조

> 아키텍처 개요는 [README](README.md)를 본다. 오류 응답의 형식과 코드 목록은 [API 명세](../api/README.md#오류-응답)에 있다.

도메인 규칙 위반은 `DomainException`을 상속한 애그리거트별 예외 클래스로 던진다. 각 예외는 `errorCode: ErrorCode`를 가지며, 코드 값은 애그리거트별 enum(`<Aggregate>ErrorCode`)으로 그 애그리거트의 `exception/` 패키지에 둔다. 여러 애그리거트가 함께 쓰는 코드는 `core`에 둔다.

```kotlin
// core
enum class ErrorType { INVALID_INPUT, NOT_FOUND, CONFLICT, BUSINESS_RULE_VIOLATION }

interface ErrorCode {
    val code: String
    val type: ErrorType
}

abstract class DomainException(
    val errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

// management/domain/store/exception
enum class StoreErrorCode(override val type: ErrorType) : ErrorCode {
    STORE_CLOSED(ErrorType.CONFLICT),
    ...;
    override val code: String get() = name
}
```

## 규칙

- **domain은 HTTP를 모른다.** `ErrorCode`는 프레임워크와 무관한 `ErrorType`까지만 가진다. `ErrorType`을 HTTP 상태로 바꾸는 일은 `common.web.GlobalExceptionHandler`에서 `else` 없는 `when` 한 곳에서만 한다. 예외가 늘어도 핸들러 분기는 늘지 않고, `ErrorType`을 추가하면 매핑 누락이 컴파일 에러로 드러난다.
- **예외 서브클래스를 둔다.** 타입 있는 생성자 파라미터(`StoreId` 등)로 메시지를 만들고, 테스트에서 `assertFailsWith<XxxException>`으로 구분하기 위해서다.
- **응답 본문**은 RFC 9457 Problem Details다. `code`에 `ErrorCode.code`(enum 이름)를, `detail`에 예외 메시지를 담는다. `code`는 클라이언트 계약이므로 enum 이름을 바꿀 때 주의한다.
- 새 `<Aggregate>ErrorCode` enum을 만들면 code 문자열 유일성 검사 테스트의 목록에도 추가한다.

## DomainException과 require/check의 구분

| 상황 | 쓰는 것 | 결과 |
|---|---|---|
| 사용자 요청으로 생길 수 있는 규칙 위반 (예: 폐점한 매장의 설정 변경, 이미 영업 중인 매장의 개점) | `DomainException` 서브클래스 | `ErrorType`에 따라 4xx와 Problem Details로 응답한다. 클라이언트가 이유를 알 수 있어야 한다 |
| 사용자가 일으킬 수 없고 호출하는 코드가 잘못됐을 때만 생기는 상황 (예: 정책에 다른 상품의 설정이 섞여 들어옴) | Kotlin `require`(인자 검증) / `check`(상태 검증) | 500 `INTERNAL_ERROR`로 응답하고 error 로그로 남긴다. 클라이언트 잘못처럼 4xx로 보고하지 않고 서버 버그로 드러나게 한다 |

- 사용자 요청으로 생길 수 있는지 애매하면 `DomainException`을 쓴다.

## ErrorType 분류 기준

| `ErrorType` | HTTP | 기준 |
|---|---|---|
| `INVALID_INPUT` | 400 | 요청 값 자체의 형식·범위 위반 (현재 상태와 무관) |
| `NOT_FOUND` | 404 | 대상이 존재하지 않음 |
| `CONFLICT` | 409 | 현재 리소스 상태와 충돌 (상태가 바뀌면 성공할 수 있음) |
| `BUSINESS_RULE_VIOLATION` | 422 | 값은 유효하지만 도메인 불변식 위반 |

## 도메인 예외가 아닌 오류의 처리 위치

| 상황 | 던지는 곳 | 응답 |
|---|---|---|
| Catalog 호출이 시간 초과·연결 실패·5xx로 끝남 | `catalogproduct.adapter.outbound.catalog`가 `CatalogUnavailableException`(도메인 예외 아님)으로 바꿔 던진다 | `GlobalExceptionHandler`가 `503 CATALOG_UNAVAILABLE`로 바꾼다. 원인은 warn 로그로 남긴다 |
| 공개 경로 요청 제한 초과 | `common.web`의 요청 제한 필터 | `429 TOO_MANY_REQUESTS`, `Retry-After` 헤더 |
| 매니저로 지정되지 않은 매장 | `management`의 `CheckStoreAccessUseCase`가 `StoreNotFoundException`을 던진다 | `404 STORE_NOT_FOUND`. 없는 매장과 같은 응답이다 |
| 토큰 없음·검증 실패, 역할 조건 불만족 | 인증 스타터 | `401 UNAUTHENTICATED`, `403 FORBIDDEN` (같은 Problem Details 형식) |

## HTTP가 아닌 경로 (메시지 소비)

메시지 소비에서는 예외가 HTTP 응답으로 바뀌지 않는다. 처리 방식은 이벤트 공통 규약(재시도·DLQ)이 정해지면 확정하고, 지금은 다음을 원칙으로 둔다.

| 예외 | 뜻 | 처리 |
|---|---|---|
| 순번이 오래됨·중복 | 정상 | 예외가 아니다. 반영하지 않았음을 돌려받고 메시지를 처리 완료로 둔다 |
| 계약 위반 (예: 재고 추적 여부가 바뀐 상품 상태, 형식이 틀린 메시지) | 보낸 쪽의 오류 | error 로그를 남기고 처리할 수 없는 메시지로 격리한다(DLQ 예정). 같은 메시지를 끝없이 재시도하지 않는다 |
| DB 연결 실패 등 일시적 오류 | Store 쪽 일시 장애 | 재시도한다. 한도를 넘으면 격리한다 |
