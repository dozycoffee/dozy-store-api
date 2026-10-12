# API 명세

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다.

Store가 외부에 제공하는 HTTP API의 계약을 다룬다. 규칙 자체는 [요구사항](../requirements.md)이 원본이며, 각 엔드포인트의 `(요구사항 N.N)`은 근거가 되는 절을 가리킨다. 이 문서에는 모든 API에 공통인 규약을 두고, 엔드포인트는 리소스별 문서에 둔다.

| 문서 | 호출자 | 다루는 리소스 |
|---|---|---|
| [매장](store.md) | 본사 직원, 매장 매니저 | 매장 등록·수정·상태 전환·목록, 내 매장 목록 |
| [매장 매니저](manager.md) | 본사 직원 | 매니저 지정·해제·조회 |
| [매장 상품](store-product.md) | 매장 매니저, 본사 직원 | 매장 상품 목록, 숨김·진열 순서·수동 품절, 유효 옵션 구성 |
| [노출 현황](exposure.md) | 본사 직원 | 상품별 판매 가능·노출 중 매장 수, 매장별 상세 |
| [손님](public.md) | 누구나 (로그인 없음) | 손님용 매장 목록·정보(매장 찾기), 매장 메뉴 |
| [내부](internal.md) | Catalog | 판매 범위 대상 매장 확인 |

API는 클라이언트와의 계약이다. 필드 이름, 경로, 오류 코드를 바꾸는 변경은 하위 호환을 깨므로 새 버전 경로나 새 필드로 추가한다.

## 설계 원칙

- **화면이 아니라 리소스 단위로 설계한다.** 한 화면에 필요한 정보는 클라이언트가 여러 API를 불러 조립한다.
- **리소스는 자기 데이터만 담고, 다른 리소스는 ID로 가리킨다.** 단, 대상을 알아볼 최소 정보는 함께 담을 수 있다(예: 노출 현황의 매장별 상태에 매장명).
- **업무 규칙이 들어간 계산은 백엔드가 제공한다.** 노출 판단(요구사항 4장)과 노출 매장 수(6장)는 클라이언트가 다시 구현하지 않도록 계산 결과를 리소스로 준다.
- **목록 API는 `ids`로 여러 건을 한 번에 조회할 수 있게 한다.** 클라이언트가 건수만큼 호출하지 않게 하기 위해서다.

예외는 둘이다. 둘 다 **호출자가 Catalog를 부를 수 없어서** Store가 Catalog의 표시용 상품 정보를 합쳐 준다.

| 리소스 | 합치는 것 | 이유 |
|---|---|---|
| 매장 상품 (`/stores/{storeId}/products`) | 상품 기준 정보, 카테고리 이름, 표시용 시작가, 유효 옵션 구성 | 점주 토큰은 Store만 부를 수 있다. 매장 매니저가 쓰는 화면은 Store가 합쳐 줘야 한다 (요구사항 5.1) |
| 손님 메뉴 (`/public/stores/{storeId}/menu`) | 상품 기준 정보, 태그, 카테고리 구조(노출 순서), 표시용 시작가, 유효 옵션 구성 | 손님은 로그인하지 않는다. 메뉴는 모든 채널에 같아야 하므로 한곳에서 만들어 준다 (요구사항 5.2) |

본사 직원은 Catalog와 Store를 모두 부를 수 있으므로, 본사 화면에 필요한 결합(노출 현황의 상품 정보, 매니저의 이름 등)은 본사 화면이나 BFF가 한다(요구사항 1.3, 6장).

## 경로와 인가

| 경로 | 호출자 | 토큰 | 담는 것 |
|---|---|---|---|
| `/api/v1/admin/**` | 본사 직원 | 직원 access token (realm `internal`) | 매장 등록·수정·상태 전환·목록, 매니저 지정, 노출 현황 |
| `/api/v1/stores/{storeId}/**` | 매장 매니저, 본사 직원 | 점주 token (realm `partner`) 또는 직원 access token | 한 매장의 정보와 그 매장의 상품 설정 |
| `/api/v1/me/**` | 매장 매니저 | 점주 token 또는 직원 access token | 내가 매니저인 매장 목록 |
| `/api/v1/internal/**` | Catalog | 서비스 token (realm `internal`) | 판매 범위 대상 매장 확인 |
| `/api/v1/public/**` | 누구나 | 없음 | 손님용 매장 목록·정보, 매장 메뉴 |

- Store는 realm `internal`과 `partner`를 받고 audience는 `store`다.
- **매장 아래 리소스(`/stores/{storeId}/**`)는 매니저와 본사 직원이 같은 경로를 쓴다.** 같은 매장 설정을 두 호출자가 바꾸므로, 경로를 나누면 같은 API가 두 벌이 된다. 인가는 경로가 아니라 요청자와 매니저 지정으로 판단한다(아래 인가 규칙).
- **응답에 본사 내부 정보를 담지 않는다.** 상품 그룹, 판매 범위는 어떤 Store 응답에도 없다.

### 역할 (role)

역할은 Auth에 `store:{code}` 형식으로 등록하고, 토큰의 `roles`에도 그 형식으로 담긴다. 점주(파트너)는 역할이 없다. Store가 토큰을 받으면 인증 스타터가 `store:` 역할만 남기고 접두사를 떼므로, Store 안에서는 코드(`admin`, `viewer`, `manager`, `catalog_agent`)로 다룬다. 다른 서비스의 역할은 Store에 들어오지 않는다.

| 역할 | 부여 대상 | 허용 범위 |
|---|---|---|
| `store:admin` | 매장 관리 직원 | `/admin/**` 전체, 모든 매장의 `/stores/{storeId}/**` (조회·변경) |
| `store:viewer` | 본사 조회가 필요한 직원(상품 담당 등) | `/admin/**`와 모든 매장의 `/stores/{storeId}/**` 중 조회(`GET`)만 |
| `store:manager` | 매장 매니저로 지정될 직원(직영점 점장) | 매니저로 지정된 매장의 `/stores/{storeId}/**`, `/me/**` |
| (없음) | 점주(파트너 계정) | `store:manager`와 같음 |
| `store:catalog_agent` | Catalog의 서비스 계정 | `/internal/**` |

- `store:manager`만으로는 어떤 매장도 다룰 수 없다. 다룰 수 있는 매장은 매니저 지정으로 정해진다(요구사항 1.3).
- Catalog의 서비스 계정에 `store:admin`을 주지 않는다. 그 비밀값이 노출돼도 매장 등록·폐점·매니저 지정까지 열리지 않게 하기 위해서다.

### 인가 규칙

| 경로 | 조회 (`GET`) | 변경 |
|---|---|---|
| `/admin/**` | `store:admin` 또는 `store:viewer` | `store:admin` |
| `/stores/{storeId}/**` | `store:admin`, `store:viewer`, 또는 그 매장의 매니저 | `store:admin` 또는 그 매장의 매니저 |
| `/me/**` | 파트너, 또는 `store:manager`를 가진 직원 | — |
| `/internal/**` | `store:catalog_agent` | `store:catalog_agent` |
| `/public/**` | 누구나 | — |

- **그 매장의 매니저**: 토큰의 principal key(`principalType`, `principalId`)와 같은 매니저 지정이 그 매장에 있다(요구사항 1.3, 2장).
- 역할 조건을 만족하지 못하면 `403 FORBIDDEN`으로 거부한다(예: `store:viewer`만 가진 직원의 변경 요청, 파트너가 `/admin/**` 요청).
- 한 사람이 여러 역할을 가지면 허용되는 범위는 합쳐진다. 예를 들어 `store:viewer`와 `store:manager`를 함께 가진 직원은 모든 매장을 읽을 수 있고, 매니저로 지정된 매장은 쓸 수도 있다.
- **매니저로 지정되지 않은 매장**은 `404 STORE_NOT_FOUND`로 거부한다. 없는 매장과 구분하지 않아, 매장 ID를 바꿔 가며 다른 매장이 있는지 알아낼 수 없게 한다.

### 경로 규칙

- 리소스 이름은 복수형 kebab-case(`product-exposures`), 식별자는 경로 변수로 둔다.
- 필드 교체로 표현하기 어려운 행위는 `POST /{리소스}/{id}/{동사}`로 둔다(예: `POST /admin/stores/{storeId}/open`).

## 요청·응답 형식

- 본문은 JSON(`application/json`), 필드 이름은 camelCase다.
- **ID**: 숫자(`long`). 매장 코드·SKU는 문자열이다.
- **principal key**: Auth의 표현을 그대로 쓴다. `principalType`은 소문자(`partner`, `employee`), `principalId`는 소문자 UUID 문자열이다. 경로에 넣을 때는 `partner:0199a3c4-…` 형식을 쓴다. 대문자 상태값 규칙의 예외다. 토큰의 값과 그대로 비교하기 위해서다.
- **금액**: 원 단위 정수. 0 이상이다.
- **날짜**: ISO-8601 날짜(`2026-10-01`). 업무 시간대(한국 시간) 기준이다.
- **시각**: ISO-8601 UTC(`2026-10-01T00:00:00Z`).
- **상태값**: 대문자 문자열(`OPEN`, `VISIBLE` 등). 도메인 모델의 enum 이름과 같다.
- 값이 없는 선택 필드는 `null`로 보낸다. 응답에서도 필드를 빼지 않고 `null`로 준다.
- 목록 필드가 비었으면 `[]`다. `null`로 주지 않는다. (예외: 손님 메뉴의 `products`는 메뉴가 없는 매장에서 `null`이다. [손님 API](public.md))

### 상태 코드

| 상황 | 상태 코드 | 본문 |
|---|---|---|
| 생성 | `201 Created` + `Location` 헤더 | 생성된 리소스 |
| 조회·변경·상태 전환 | `200 OK` | 변경 후 리소스 |
| 삭제(매니저 지정 해제) | `204 No Content` | 없음 |

## 목록 조회

- `ids`: 쉼표로 구분한 ID 목록. 주면 그 ID의 리소스만 돌려준다. 없는 ID는 오류 없이 빠진다. 최대 100개다.
- 다른 필터와 함께 주면 모두 만족하는 것만 남는다.
- 페이징하는 목록: 본사의 매장 목록, 손님의 매장 목록, 노출 현황의 매장별 상세
- 한 번에 주는 목록: 매장 상품, 손님 메뉴, 매니저 목록, 내 매장 목록

### 페이징

- 요청: `page`(0부터, 기본 0), `size`(기본 20, 최대 100)
- 응답:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 135,
  "totalPages": 7
}
```

## 낙관적 잠금

매장 정보 수정(`PUT /admin/stores/{storeId}`)만 관리자가 화면에서 보던 버전을 함께 보낸다(ERD 동시성 처리).

- 응답의 `version` 필드가 현재 버전이다. 단건 응답은 헤더 `ETag`에도 `"3"`처럼 같은 값을 담는다.
- 버전이 필요한 요청은 `If-Match: "3"` 헤더로 보낸다.
- `If-Match`는 `"3"`처럼 따옴표로 감싼 정수 하나만 받는다. 응답의 `ETag` 값을 그대로 보내면 된다. 따옴표가 없는 값, 약한 검증자(`W/"3"`), `*`, 여러 값, 음수, 숫자가 아닌 값은 `400 INVALID_REQUEST`(`detail`에 `If-Match`)로 거부한다.
- 헤더가 없으면 `428 Precondition Required`(`VERSION_REQUIRED`)로 거부한다.
- 그 사이 다른 변경이 반영됐으면 `409 Conflict`(`VERSION_CONFLICT`)로 거부하고, 알 수 있으면 현재 버전을 `currentVersion`에 담는다. 서버는 자동으로 재시도하지 않는다. 클라이언트가 최신 값을 다시 불러와 관리자가 다시 판단한다.
- 상태 전환과 매니저 지정은 버전을 받지 않는다. 다만 상태 전환도 버전을 올리므로, 전환하기 전에 본 버전으로 보낸 정보 수정은 `409 VERSION_CONFLICT`다.

## 오류 응답

RFC 9457 Problem Details를 쓴다. `Content-Type`은 `application/problem+json`이다. 인증 라이브러리가 401·403을 같은 형식으로 응답하므로 한 API의 오류 형식이 하나로 유지된다.

```json
{
  "type": "https://docs.dozycoffee.com/errors/store-closed",
  "title": "Store closed",
  "status": 409,
  "detail": "폐점한 매장의 설정은 바꿀 수 없습니다: 매장 101",
  "instance": "/api/v1/stores/101/products/12/visibility",
  "code": "STORE_CLOSED",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `type` | ✅ | `https://docs.dozycoffee.com/errors/{code를 kebab-case로}` |
| `title` | ✅ | 오류 종류의 짧은 영어 이름 |
| `status` | ✅ | HTTP 상태 코드 |
| `detail` | | 사람이 읽는 설명. 클라이언트가 그대로 보여 주지 않는다 |
| `instance` | ✅ | 요청 경로 |
| `code` | ✅ | 클라이언트가 분기에 쓰는 값 |
| `traceId` | ✅ | 응답 헤더 `X-Trace-Id`와 같은 값 |

- 클라이언트는 `code`로만 분기하고 문구는 클라이언트가 정한다.
- `VERSION_CONFLICT`만 `currentVersion`(숫자 또는 `null`)을 확장 필드로 담는다.
- `500`은 내부 정보(스택, SQL, 클래스 이름)를 담지 않는다.
- 도메인 규칙 위반의 상태 코드는 오류 분류(`ErrorType`)로 정해진다([예외 구조](../architecture/exception.md)).

| `ErrorType` | 상태 코드 |
|---|---|
| `INVALID_INPUT` | 400 |
| `NOT_FOUND` | 404 |
| `CONFLICT` | 409 |
| `BUSINESS_RULE_VIOLATION` | 422 |

### 도메인 오류 코드

| 코드 | 상태 코드 | 상황 |
|---|---|---|
| `STORE_NOT_FOUND` | 404 | 매장이 없음, 또는 매니저로 지정되지 않은 매장. 손님 경로에서는 폐점 매장도 |
| `STORE_NAME_DUPLICATED` | 409 | 폐점하지 않은 다른 매장과 같은 이름 (요구사항 1.1) |
| `INVALID_STORE_STATUS_TRANSITION` | 409 | 허용되지 않는 상태 전환 (요구사항 1.2) |
| `OPENING_DATE_NOT_CHANGEABLE` | 409 | `PREPARING`이 아닌 매장의 개점 예정일 변경 (요구사항 1.2) |
| `STORE_CLOSED` | 409 | 폐점한 매장의 설정 변경, 폐점한 매장에 매니저 지정 (요구사항 1.3, 1.4, 3.1) |
| `STORE_MANAGER_ALREADY_ASSIGNED` | 409 | 이미 그 매장의 매니저인 계정을 다시 지정 (요구사항 1.3) |
| `STORE_MANAGER_NOT_FOUND` | 404 | 해제하려는 매니저 지정이 없음 |
| `INVALID_MANAGER_PRINCIPAL_TYPE` | 400 | 매니저로 지정할 수 없는 계정 종류(`system`, `customer`) (요구사항 1.3) |
| `PRODUCT_NOT_FOUND` | 404 | 상품이 없거나 삭제됐거나, 이 매장이 판매 범위에 들지 않음 (요구사항 3.1) |
| `DUPLICATE_DISPLAY_ORDER_PRODUCT` | 400 | 진열 순서 목록에 같은 상품이 두 번 (요구사항 3.3) |
| `STOCK_STATUS_NOT_MANUALLY_EDITABLE` | 422 | (2단계) 재고 추적 상품의 수동 품절 변경. 코드만 예약해 둔다 (요구사항 8.2) |

### 도메인 오류가 아닌 오류

| 코드 | 상태 코드 | 상황 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 본문을 읽을 수 없음, 필수 필드 누락, 타입 불일치, 허용되지 않는 enum 값, `ids`·`size` 상한 초과, 형식이 틀린 `If-Match`나 principal key. `detail`에 문제가 된 필드나 파라미터 이름을 담는다 |
| `VERSION_REQUIRED` | 428 | 버전이 필요한 요청에 `If-Match`가 없음 |
| `VERSION_CONFLICT` | 409 | 버전 충돌 |
| `UNAUTHENTICATED` | 401 | 토큰 없음·검증 실패 |
| `FORBIDDEN` | 403 | 역할 조건 불만족 |
| `TOO_MANY_REQUESTS` | 429 | `/public/**`의 요청 제한 초과. `Retry-After` 헤더를 담는다 (요구사항 5.2) |
| `CATALOG_UNAVAILABLE` | 503 | Catalog의 표시용 상품 정보가 필요한 조회에서 Catalog가 응답하지 않음. 일부만 보여 주지 않고 요청 전체를 실패로 돌려준다 (요구사항 5.1) |
| `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE` 등 | 404, 405, 415 등 | 없는 경로 등 HTTP 수준의 오류. `code`는 HTTP 상태 이름이다. 대상 리소스가 없는 경우는 도메인 오류(`STORE_NOT_FOUND` 등)로 따로 응답한다 |
| `INTERNAL_ERROR` | 500 | 그 밖의 서버 오류. 서버 로그에 error로 남는다 |

리소스별 문서의 각 엔드포인트에는 그 요청이 받을 수 있는 도메인 오류 코드를 적는다. 도메인 오류가 아닌 오류는 모든 엔드포인트에 공통이라 따로 적지 않는다.
