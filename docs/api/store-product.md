# 매장 상품 API

> 공통 규약은 [API 명세](README.md)를 따른다. 규칙은 [요구사항](../requirements.md) 3장, 4장, 5.1과 [시나리오](../scenarios.md) S4에 있다.

| 메서드 | 경로 | 설명 | 호출자 |
|---|---|---|---|
| GET | `/stores/{storeId}/products` | 매장 상품 목록 | 매니저, `store:admin`, `store:viewer` |
| PUT | `/stores/{storeId}/products/{productId}/visibility` | 숨김·노출 전환 | 매니저, `store:admin` |
| PUT | `/stores/{storeId}/products/{productId}/stock-status` | 수동 품절 설정·해제 (1단계에서는 모든 상품) | 매니저, `store:admin` |
| GET | `/stores/{storeId}/products/{productId}/effective-options` | 유효 옵션 구성과 표시용 시작가 | 매니저, `store:admin`, `store:viewer` |

- 매장별 가격을 바꾸는 API는 없다(전 매장 동일가, 요구사항 2장).
- 응답에 Catalog의 상품 그룹, 판매 범위, 상품 상태를 담지 않는다.

## 매장 상품 (`StoreProduct`)

한 매장에서 한 상품의 설정과, 그 상품을 알아볼 표시용 정보다.

```json
{
  "productId": 12,
  "sku": "DZ-00000012",
  "name": "아이스 아메리카노",
  "categoryId": 5,
  "imageUrl": "https://cdn.example.com/p/12.png",
  "basePrice": 4500,
  "displayStartingPrice": 4500,
  "tracksInventory": false,
  "visibility": "VISIBLE",
  "stockStatus": "ON_SALE"
}
```

| 필드 | 출처 | 설명 |
|---|---|---|
| `sku`, `name`, `categoryId`, `imageUrl`, `basePrice`, `displayStartingPrice`, `tracksInventory` | Catalog | 표시용 상품 정보. 조회할 때마다 Catalog에서 받는다 |
| `visibility` | Store | `VISIBLE` / `HIDDEN`. 개별 설정이 없으면 `VISIBLE` |
| `stockStatus` | Store | `ON_SALE` / `SOLD_OUT`. 매니저의 수동 품절(없으면 `ON_SALE`). 1단계에서는 재고 추적 상품도 같다(요구사항 3.4). 숨겼어도 값을 준다 |

## GET `/stores/{storeId}/products` — 매장 상품 목록 (요구사항 5.1)

`ACTIVE`이고 이 매장이 판매 범위에 든 상품이 대상이다. 숨긴 상품도 포함한다. `DRAFT` 상품은 보여 주지 않는다.

| 파라미터 | 설명 |
|---|---|
| `ids` | 상품 ID로 거른다 |

- 응답: `StoreProduct` 배열. 페이징하지 않는다.
- 정렬: 상품 ID 순(본사 등록 순, 요구사항 3.3). 매장마다 순서가 다르지 않다.
- 매장 상태와 관계없이 조회된다. 개점 준비·휴업 중에도 노출 설정을 준비할 수 있고, 폐점한 매장은 읽기 전용으로 본다(요구사항 1.4).
- 오류: `STORE_NOT_FOUND`(404), `CATALOG_UNAVAILABLE`(503)

## PUT `/stores/{storeId}/products/{productId}/visibility` — 숨김·노출 전환 (요구사항 3.2)

```json
{ "visibility": "HIDDEN" }
```

- 개별 설정이 없으면 기본값으로 만든 뒤 바꾼다.
- 판매 범위에 든 상품이면 상품 상태와 관계없이 바꿀 수 있다(요구사항 3.1).
- 응답: `StoreSetting`
- 오류: `STORE_NOT_FOUND`(404), `STORE_CLOSED`(409), `PRODUCT_NOT_FOUND`(404)

## PUT `/stores/{storeId}/products/{productId}/stock-status` — 수동 품절 (요구사항 3.4)

```json
{ "stockStatus": "SOLD_OUT" }
```

- 숨김·노출 전환과 같이 판매 범위에 든 상품이면 상품 상태와 관계없이 바꿀 수 있다.
- 응답: `StoreSetting`
- 오류: `STORE_NOT_FOUND`(404), `STORE_CLOSED`(409), `PRODUCT_NOT_FOUND`(404)
- 재고 연동(2단계) 후에는 재고 추적 상품의 수동 품절이 `STOCK_STATUS_NOT_MANUALLY_EDITABLE`(422)로 거부된다.

### 변경 응답 (`StoreSetting`)

변경 요청은 Store의 설정만 돌려준다. Catalog를 부르지 않으므로, Catalog가 응답하지 않아도 숨김·품절 변경은 된다. 표시용 정보가 필요하면 목록을 다시 조회한다.

```json
{ "productId": 12, "visibility": "HIDDEN", "stockStatus": "ON_SALE" }
```

## GET `/stores/{storeId}/products/{productId}/effective-options` — 유효 옵션 구성 (요구사항 5.1)

Catalog가 계산한 유효 옵션 구성과 표시용 시작가를 그대로 전달한다(요구사항 7.1). 판매 범위 확인은 Store가 먼저 한다.

```json
{
  "productId": 12,
  "basePrice": 4500,
  "displayStartingPrice": 4500,
  "groups": [
    {
      "optionGroupId": 7,
      "name": "사이즈",
      "selectionType": "SINGLE",
      "required": true,
      "autoSelectedOptionKey": null,
      "options": [
        { "optionKey": "REGULAR", "name": "레귤러", "price": 0, "priceOverridden": false },
        { "optionKey": "LARGE", "name": "라지", "price": 700, "priceOverridden": true }
      ]
    }
  ]
}
```

- 오류: `STORE_NOT_FOUND`(404), `PRODUCT_NOT_FOUND`(404, 없거나 이 매장에서 판매할 수 없는 상품), `CATALOG_UNAVAILABLE`(503)
