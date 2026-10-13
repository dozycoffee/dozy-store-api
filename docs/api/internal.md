# 내부 API

> 공통 규약은 [API 명세](README.md)를 따른다. 경로 앞에 `/api/v1/internal`이 붙고, Catalog의 서비스 계정(`store:catalog_agent`)이 호출한다. 규칙은 [요구사항](../requirements.md) 7.2에 있다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/stores/assignability-check` | 판매 범위 대상으로 지정할 수 없는 매장 고르기 |

- 다른 서비스(POS, 재고 서비스 등)가 매장을 ID나 매장 코드로 조회해야 하는 연동이 생기면 이 경로에 조회 API와 그 서비스용 역할을 추가한다(요구사항 1.4의 외부 서비스 조회).

## POST `/stores/assignability-check` — 판매 범위 대상 매장 확인 (요구사항 7.2)

Catalog가 판매 범위를 즉시 바꾸거나 판매 범위 예약을 등록할 때, 대상 매장 중 지정할 수 없는 매장을 묻는다. 예약을 적용할 때는 부르지 않는다.

조회지만 매장 ID 목록이 길 수 있어 본문으로 받는다.

```json
{ "storeIds": [101, 102, 999, 87] }
```

```json
{
  "unassignableStores": [
    { "storeId": 999, "reason": "NOT_FOUND" },
    { "storeId": 87, "reason": "CLOSED" }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `storeIds` | 확인할 매장 ID. 최대 1,000개 |
| `unassignableStores` | 지정할 수 없는 매장과 이유. `NOT_FOUND`(없는 매장) / `CLOSED`(폐점한 매장). 모두 지정할 수 있으면 `[]` |

- 호출자: 서비스 계정(realm `internal`의 `system`)이면서 `store:catalog_agent` 역할을 가진 호출자만 부를 수 있다. 본사 직원은 그 역할을 가져도, 점주는 `403`이다. 토큰이 없거나 틀리면 `401`이다.
- 처리 순서는 인가 → 요청 값 검증이다.
- 응답: `200 OK`. 지정할 수 없는 매장이 있어도 오류가 아니다. 거부할지는 Catalog가 정한다.
- `unassignableStores`는 요청한 `storeIds`의 순서를 따르고, 같은 ID가 여러 번 와도 한 번만 판단한다.
- `storeIds`가 없거나(`null` 포함) 비었거나 1,000개를 넘거나, 항목이 `null`이거나 양의 정수가 아니면 `INVALID_REQUEST`(400)다. 개수는 중복을 빼기 전의 항목 수로 센다.
- 개점 준비·영업 중·휴업 매장은 지정할 수 있다(요구사항 1.4).
