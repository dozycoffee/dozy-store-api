# 노출 현황 API

> 공통 규약은 [API 명세](README.md)를 따른다. 경로 앞에 `/api/v1/admin`이 붙고, `store:admin` 또는 `store:viewer`가 호출한다. 규칙은 [요구사항](../requirements.md) 4장, 6장과 [시나리오](../scenarios.md) S7에 있다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/product-exposures` | 상품별 판매 가능 매장 수와 노출 중인 매장 수 |
| GET | `/product-exposures/{productId}/stores` | 한 상품의 매장별 노출 상태 |

- 상품 필터(카테고리, 태그, 상품 그룹)와 상품 정보는 Catalog가 맡는다. 본사 화면(또는 BFF)이 Catalog의 상품 목록에서 상품 한 페이지를 받고, 그 상품 ID로 이 API를 불러 합친다(요구사항 6장). 그래서 이 API는 상품 ID와 매장 수만 담는다.
- 매장 수로 정렬하거나 거르는 기능은 없다.

## GET `/product-exposures` — 매장 수

| 파라미터 | 설명 |
|---|---|
| `ids` | 필수. 상품 ID 목록(최대 100개) |

```json
[
  { "productId": 12, "sellableStoreCount": 120, "exposedStoreCount": 97 },
  { "productId": 15, "sellableStoreCount": 120, "exposedStoreCount": 0 }
]
```

| 필드 | 설명 |
|---|---|
| `sellableStoreCount` | 판매 범위에 포함되고 `OPEN`인 매장 수. 상품 상태와 관계없다(`DRAFT`도 그대로) |
| `exposedStoreCount` | 그중 노출 판단(4장)을 통과한 매장 수. 품절이어도 숨기지 않았으면 센다. 상품이 `ACTIVE`가 아니면 0 |

- 응답: 위 배열. `ids`의 순서를 따른다. Store가 모르는(복제되지 않았거나 삭제된) 상품은 빠진다.
- `ids`가 없으면 `INVALID_REQUEST`(400)다. 전체 상품을 한 번에 세는 요청을 막기 위해서다.

## GET `/product-exposures/{productId}/stores` — 매장별 노출 상태

판매 범위에 든 매장 중 폐점하지 않은 매장마다 노출 상태를 준다. 개점 준비·휴업 매장도 포함한다(요구사항 6장).

| 파라미터 | 설명 |
|---|---|
| `storeStatus` | 매장 상태로 거른다(`OPEN`, `PREPARING`, `SUSPENDED`). 여러 값은 쉼표로 구분 |
| `exposed` | `true`/`false`로 노출 여부를 거른다 |
| `page`, `size` | [페이징](README.md#목록-조회) |

```json
{
  "content": [
    {
      "storeId": 101,
      "storeCode": "ST-00000101",
      "storeName": "강남역점",
      "storeStatus": "OPEN",
      "exposed": true,
      "notExposedReason": null,
      "stockStatus": "SOLD_OUT"
    },
    {
      "storeId": 102,
      "storeCode": "ST-00000102",
      "storeName": "역삼점",
      "storeStatus": "OPEN",
      "exposed": false,
      "notExposedReason": "HIDDEN",
      "stockStatus": "ON_SALE"
    },
    {
      "storeId": 130,
      "storeCode": "ST-00000130",
      "storeName": "판교점",
      "storeStatus": "PREPARING",
      "exposed": false,
      "notExposedReason": "STORE_NOT_OPEN",
      "stockStatus": "ON_SALE"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1
}
```

| 필드 | 설명 |
|---|---|
| `storeCode`, `storeName`, `storeStatus` | 매장을 알아보기 위한 정보. 매장은 Store의 리소스라 함께 담는다 |
| `exposed` | 노출 판단(4장) 결과 |
| `notExposedReason` | 비노출이면 걸린 단계. `STORE_NOT_OPEN`(0단계) / `PRODUCT_NOT_ACTIVE`(1단계) / `HIDDEN`(4단계). 노출이면 `null`. 판매 범위 안의 매장만 담으므로 2단계에서 걸리는 경우는 없다 |
| `stockStatus` | 그 매장의 판매 가능 여부. 노출 여부와 관계없이 준다(개점 전 준비 상태 확인용) |

- 정렬: 매장명 가나다순
- `storeStatus`가 `OPEN`인 행의 수가 `sellableStoreCount`, 그중 `exposed`가 `true`인 행의 수가 `exposedStoreCount`와 같다.
- 오류: `PRODUCT_NOT_FOUND`(404, Store가 모르는 상품)
