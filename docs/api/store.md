# 매장 API

> 공통 규약은 [API 명세](README.md)를 따른다. 규칙은 [요구사항](../requirements.md) 1.1, 1.2, 1.4에 있다.

본사와 매장 매니저가 쓰는 매장 API다. 손님이 매장을 찾고 보는 API(로그인 없음)는 [손님 API](public.md)의 `/public/stores`에 있다.

| 메서드 | 경로 | 설명 | `If-Match` | 호출자 |
|---|---|---|---|---|
| POST | `/admin/stores` | 매장 등록 | | `store:admin` |
| GET | `/admin/stores` | 매장 목록 | | `store:admin`, `store:viewer` |
| GET | `/admin/stores/{storeId}` | 매장 조회 | | `store:admin`, `store:viewer` |
| PUT | `/admin/stores/{storeId}` | 매장 정보 수정 | ✅ | `store:admin` |
| POST | `/admin/stores/{storeId}/open` | 개점 | | `store:admin` |
| POST | `/admin/stores/{storeId}/suspend` | 휴업 | | `store:admin` |
| POST | `/admin/stores/{storeId}/resume` | 재개 | | `store:admin` |
| POST | `/admin/stores/{storeId}/close` | 폐점 | | `store:admin` |
| GET | `/stores/{storeId}` | 한 매장의 정보 (매니저 화면) | | 매니저, `store:admin`, `store:viewer` |
| GET | `/me/stores` | 내가 매니저인 매장 목록 | | 매니저 |

## 매장 (`Store`)

```json
{
  "storeId": 101,
  "code": "ST-00000101",
  "name": "강남역점",
  "address": {
    "roadAddress": "서울특별시 강남구 강남대로 396",
    "detailAddress": "1층",
    "postalCode": "06232",
    "sido": "서울특별시",
    "sigungu": "강남구"
  },
  "phone": "02-1234-5678",
  "status": "OPEN",
  "openingDate": "2026-11-01",
  "closingDate": null,
  "version": 3
}
```

| 필드 | 설명 |
|---|---|
| `code` | 매장 코드. 등록할 때 시스템이 부여하며 바뀌지 않는다 |
| `address.detailAddress` | 없으면 `null` |
| `status` | `PREPARING` / `OPEN` / `SUSPENDED` / `CLOSED` |
| `openingDate` | `PREPARING`이면 개점 예정일, 개점 후에는 실제 개점일 |
| `closingDate` | 폐점한 매장만 값이 있다 |
| `version` | 낙관적 잠금 버전. 본사 경로(`/admin/**`)의 응답에만 있다 |

## POST `/admin/stores` — 매장 등록 (요구사항 1.1)

```json
{
  "name": "강남역점",
  "address": {
    "roadAddress": "서울특별시 강남구 강남대로 396",
    "detailAddress": "1층",
    "postalCode": "06232",
    "sido": "서울특별시",
    "sigungu": "강남구"
  },
  "phone": "02-1234-5678",
  "openingDate": "2026-11-01"
}
```

- 매장은 `PREPARING`으로 생성되고 매장 코드가 부여된다.
- 응답: `201 Created`, `Location: /api/v1/admin/stores/{storeId}`, `Store`
- 오류: `STORE_NAME_DUPLICATED`(409)

## GET `/admin/stores` — 매장 목록 (요구사항 1.4)

| 파라미터 | 설명 |
|---|---|
| `ids` | 매장 ID로 거른다 |
| `status` | 상태로 거른다. 여러 값은 쉼표로 구분(`OPEN,SUSPENDED`). 주지 않으면 `CLOSED`를 뺀 모든 상태 |
| `sido`, `sigungu` | 지역으로 거른다 |
| `q` | 매장명 부분 일치 (대소문자 무시) |
| `page`, `size` | [페이징](README.md#목록-조회) |

- 응답: `Store`의 페이지. 매장명 가나다순이다.
- Catalog에서 판매 범위 대상 매장을 고를 때도 이 목록을 쓴다. 폐점한 매장은 지정할 수 없으므로 `status`를 주지 않은 기본 조회를 쓴다(요구사항 7.2).

## GET `/admin/stores/{storeId}` — 매장 조회

- 응답: `Store`. 폐점한 매장도 조회된다.
- 오류: `STORE_NOT_FOUND`(404)

## PUT `/admin/stores/{storeId}` — 매장 정보 수정 (요구사항 1.1, 1.2)

등록과 같은 본문으로 매장 정보 전체를 바꾼다. `If-Match`가 필요하다.

- `openingDate`는 `PREPARING`일 때만 바꿀 수 있다. 다른 상태에서는 지금 값과 같아야 한다.
- 매장 코드와 상태는 이 요청으로 바꾸지 않는다.
- 응답: `Store`
- 오류: `STORE_NOT_FOUND`(404), `STORE_NAME_DUPLICATED`(409), `OPENING_DATE_NOT_CHANGEABLE`(409), `VERSION_CONFLICT`(409)

## 상태 전환 (요구사항 1.2)

| 경로 | 전이 | 기록 |
|---|---|---|
| `POST /admin/stores/{storeId}/open` | `PREPARING → OPEN` | 개점일 = 오늘 |
| `POST /admin/stores/{storeId}/suspend` | `OPEN → SUSPENDED` | |
| `POST /admin/stores/{storeId}/resume` | `SUSPENDED → OPEN` | |
| `POST /admin/stores/{storeId}/close` | `PREPARING` / `OPEN` / `SUSPENDED` → `CLOSED` | 폐점일 = 오늘 |

- 본문은 없다. 즉시 반영한다(예약 없음).
- "오늘"은 업무 시간대(한국 시간) 기준이다.
- 폐점해도 Catalog의 판매 범위와 매니저 지정은 그대로다(요구사항 1.2, 1.3).
- 응답: `Store`
- 오류: `STORE_NOT_FOUND`(404), `INVALID_STORE_STATUS_TRANSITION`(409)

## GET `/stores/{storeId}` — 한 매장의 정보

매니저 화면의 머리글처럼 매장 정보를 보여 줄 때 쓴다.

- 응답: `Store`에서 `version`을 뺀 것. 폐점한 매장도 조회된다(매니저는 읽기 전용, 요구사항 1.4).
- 오류: `STORE_NOT_FOUND`(404, 없거나 매니저로 지정되지 않은 매장)

## GET `/me/stores` — 내 매장 목록 (요구사항 1.3, 1.4)

토큰의 principal key가 매니저로 지정된 매장 목록이다.

```json
[
  { "storeId": 101, "code": "ST-00000101", "name": "강남역점", "status": "OPEN" },
  { "storeId": 87, "code": "ST-00000087", "name": "역삼점", "status": "CLOSED" }
]
```

- 폐점한 매장도 포함한다. 화면은 `status`로 읽기 전용을 표시한다.
- 정렬: 폐점하지 않은 매장이 앞에, 그 안에서 매장명 가나다순
- 페이징하지 않는다.
