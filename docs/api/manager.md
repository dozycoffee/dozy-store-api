# 매장 매니저 API

> 공통 규약은 [API 명세](README.md)를 따른다. 규칙은 [요구사항](../requirements.md) 1.3에 있다.

| 메서드 | 경로 | 설명 | 호출자 |
|---|---|---|---|
| GET | `/admin/stores/{storeId}/managers` | 매장의 매니저 목록 | `store:admin`, `store:viewer` |
| POST | `/admin/stores/{storeId}/managers` | 매니저 지정 | `store:admin` |
| DELETE | `/admin/stores/{storeId}/managers/{principalKey}` | 매니저 지정 해제 | `store:admin` |
| GET | `/admin/store-managers` | 한 계정이 매니저인 매장 목록 | `store:admin`, `store:viewer` |

- **Store는 Auth를 호출하지 않는다.** 지정할 계정을 찾고 본인인지 확인하는 일, 매니저 목록에 이름 같은 계정 정보를 붙이는 일은 본사 화면(또는 BFF)이 Auth로 한다. Store는 principal key만 저장하고 돌려준다(요구사항 1.3).
- Store는 계정이 실제로 있는지 확인하지 않는다. 없는 계정이 지정되더라도 그 계정으로 받을 토큰이 없으므로 아무도 매장을 관리할 수 없다.

## 매니저 지정 (`StoreManager`)

```json
{
  "storeId": 101,
  "principalType": "partner",
  "principalId": "0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f",
  "assignedAt": "2026-10-05T01:00:00Z"
}
```

| 필드 | 설명 |
|---|---|
| `principalType` | `partner`(점주) 또는 `employee`(본사 직원). Auth의 표현 그대로다 |
| `principalId` | Auth의 계정 ID |
| `assignedAt` | 지정한 시각 |

## GET `/admin/stores/{storeId}/managers` — 매니저 목록

- 응답: `StoreManager` 배열. 지정한 순서다. 폐점한 매장도 조회된다.
- 오류: `STORE_NOT_FOUND`(404)

## POST `/admin/stores/{storeId}/managers` — 매니저 지정

```json
{ "principalType": "partner", "principalId": "0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f" }
```

- 본사 직원을 지정할 때는 그 직원이 Auth에서 `store:manager` 역할을 가져야 실제로 매장을 다룰 수 있다. Store는 이를 확인하지 않는다.
- 응답: `201 Created`, `Location: /api/v1/admin/stores/{storeId}/managers/{principalType}:{principalId}`, `StoreManager`
- 폐점한 매장에는 지정할 수 없다. 폐점 전에 지정된 매니저는 남아 있고, 해제는 할 수 있다.
- 오류: `STORE_NOT_FOUND`(404), `STORE_CLOSED`(409), `STORE_MANAGER_ALREADY_ASSIGNED`(409), `INVALID_MANAGER_PRINCIPAL_TYPE`(400, `system`·`customer`)
- `principalId`가 UUID 형식이 아니면 `INVALID_REQUEST`(400)다.

## DELETE `/admin/stores/{storeId}/managers/{principalKey}` — 매니저 지정 해제

`principalKey`는 Auth의 `sub` 형식(`partner:0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f`)이다.

- 매장의 개별 설정(진열·노출·수동 품절)은 바뀌지 않는다.
- 응답: `204 No Content`
- 오류: `STORE_NOT_FOUND`(404), `STORE_MANAGER_NOT_FOUND`(404)

## GET `/admin/store-managers` — 한 계정이 매니저인 매장 목록

탈퇴·퇴사한 계정의 지정을 정리할 때처럼, 계정 쪽에서 매장을 찾을 때 쓴다(요구사항 1.3).

| 파라미터 | 설명 |
|---|---|
| `principalKey` | 필수. `partner:…` 또는 `employee:…` |

- 응답: `StoreManager` 배열. 폐점한 매장의 지정도 포함한다.
