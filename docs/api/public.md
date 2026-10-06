# 손님 API

> 공통 규약은 [API 명세](README.md)를 따른다. 경로 앞에 `/api/v1/public`이 붙고, 로그인 없이 누구나 호출한다. 규칙은 [요구사항](../requirements.md) 1.4, 4장, 5.2에 있다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/stores` | 손님용 매장 목록 (매장 찾기) |
| GET | `/stores/{storeId}` | 손님용 매장 정보 |
| GET | `/stores/{storeId}/menu` | 매장 메뉴 |
| GET | `/stores/{storeId}/menu/{productId}` | 메뉴 상품 하나와 그 옵션 |

- **모든 채널(손님 앱·웹, 키오스크, POS 등)이 같은 API를 쓴다.** 메뉴는 채널과 관계없이 같다(요구사항 5.2).
- 로그인이 없으므로 IP 단위로 요청 수를 제한한다. 넘으면 `429 TOO_MANY_REQUESTS`다.
- 응답에 매장 코드, 매니저, Catalog의 상품 그룹·판매 범위·상품 상태를 담지 않는다.
- 주문은 이 API의 범위가 아니다. 주문·POS가 자기 인증으로 처리하고 주문 시점의 가격을 다시 검증한다.

## 캐시

- 응답은 공개 데이터라 HTTP 캐시와 CDN에 맡길 수 있다. `Cache-Control: public, max-age=…`를 붙인다. 품절 표시가 그만큼 늦어지므로 짧게 둔다(값은 운영하며 정함).
- Store는 Catalog의 표시용 상품 정보(이름, 이미지, 가격, 카테고리, 태그, 유효 옵션 구성)를 짧게 캐시해 쓴다. 상품 상태·판매 범위·숨김·품절은 캐시하지 않고 Store의 현재 값으로 판단한다(요구사항 5.2).

## 손님용 매장 (`CustomerStore`)

매장 상태에 따라 보여 주는 정보가 다르다(요구사항 1.4). 상세 정보는 `OPEN`인 매장에만 있다.

```json
{
  "storeId": 101,
  "name": "강남역점",
  "address": {
    "roadAddress": "서울특별시 강남구 강남대로 396",
    "detailAddress": "1층",
    "postalCode": "06232",
    "sido": "서울특별시",
    "sigungu": "강남구"
  },
  "status": "OPEN",
  "openingDate": null,
  "phone": "02-1234-5678"
}
```

| 필드 | `PREPARING` ("오픈 예정") | `OPEN` | `SUSPENDED` ("휴업 중") |
|---|---|---|---|
| `storeId`, `name`, `address`, `status` | 값 있음 | 값 있음 | 값 있음 |
| `openingDate` | 개점 예정일 | `null` | `null` |
| `phone` | `null` | 값 있음 | `null` |

- 폐점한 매장은 손님에게 보이지 않는다.
- 위치 좌표, 영업시간, 매장 사진처럼 나중에 추가할 정보도 요약(모든 상태)인지 상세(`OPEN`만)인지 정해 같은 방식으로 담는다.

## GET `/stores` — 손님용 매장 목록 (요구사항 1.4)

| 파라미터 | 설명 |
|---|---|
| `sido`, `sigungu` | 지역으로 거른다 |
| `q` | 매장명 부분 일치 |
| `page`, `size` | [페이징](README.md#목록-조회) |

- 응답: `CustomerStore`의 페이지. 폐점하지 않은 매장만 담는다. 매장명 가나다순이다.

## GET `/stores/{storeId}` — 손님용 매장 정보

- 응답: `CustomerStore`
- `PREPARING`·`SUSPENDED` 매장도 거부하지 않고 요약을 준다. 즐겨찾기나 공유 링크로 들어온 손님에게 오픈 예정·휴업 중으로 보여야 하기 때문이다(요구사항 1.4).
- 오류: `STORE_NOT_FOUND`(404, 없거나 폐점한 매장)

## GET `/stores/{storeId}/menu` — 매장 메뉴 (요구사항 5.2)

```json
{
  "storeId": 101,
  "storeStatus": "OPEN",
  "categories": [
    {
      "categoryId": 1,
      "name": "음료",
      "children": [
        { "categoryId": 5, "name": "커피" },
        { "categoryId": 6, "name": "티" }
      ]
    },
    {
      "categoryId": 3,
      "name": "상품",
      "children": [
        { "categoryId": 9, "name": "MD" }
      ]
    }
  ],
  "products": [
    {
      "productId": 12,
      "name": "아이스 아메리카노",
      "categoryId": 5,
      "imageUrl": "https://cdn.example.com/p/12.png",
      "basePrice": 4500,
      "displayStartingPrice": 4500,
      "tags": [ { "tagId": 2, "name": "베스트" } ],
      "stockStatus": "ON_SALE"
    },
    {
      "productId": 40,
      "name": "도지 머그컵",
      "categoryId": 9,
      "imageUrl": "https://cdn.example.com/p/40.png",
      "basePrice": 15000,
      "displayStartingPrice": 15000,
      "tags": [ { "tagId": 7, "name": "시즌한정" } ],
      "stockStatus": "SOLD_OUT"
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `storeStatus` | 매장 상태. `OPEN`이 아니면 메뉴가 없다 |
| `categories` | 메뉴에 있는 상품이 속한 카테고리를 대분류 → 소분류 구조로 담는다. 본사가 정한 노출 순서대로다. 상품이 하나도 없는 카테고리는 빠진다. `OPEN`이 아닌 매장이면 `null` |
| `products` | 노출 판단(4장)을 통과한 상품. 진열 순서대로이고, 정하지 않은 상품은 뒤에 상품 ID 순이다. `OPEN`이 아닌 매장이면 `null`(메뉴 없음)이고, 노출할 상품이 하나도 없으면 `[]`다 |
| `categoryId` | 상품의 소분류. 이름과 대분류는 `categories`에서 찾는다 |
| `tags` | 상품의 마케팅 태그(신메뉴, 시즌한정 등). 없으면 `[]` |
| `stockStatus` | `ON_SALE`이면 구매 가능, `SOLD_OUT`이면 보이지만 구매 불가 |

- 숨긴 상품, `ACTIVE`가 아닌 상품, 판매 범위 밖 상품은 담지 않는다.
- **필터 파라미터는 없다.** 매장의 노출 상품 전체를 한 번에 주고, 카테고리 탭·태그 모아 보기·판매중만 보기·이름 검색은 화면이 거른다. 조건마다 캐시가 쪼개지지 않게 하기 위해서다(요구사항 5.2).
- 카테고리 탭은 `categories`의 순서로 만들고, 탭 안의 상품은 `products`의 순서(진열 순서)를 그대로 따른다.
- 옵션은 담지 않는다. 상품을 고른 뒤 `/menu/{productId}`로 받는다. 메뉴 목록을 가볍게 유지하기 위해서다.
- 오류: `STORE_NOT_FOUND`(404, 없거나 폐점한 매장), `CATALOG_UNAVAILABLE`(503, 캐시에 없는 상품 정보를 Catalog에서 받지 못함)

## GET `/stores/{storeId}/menu/{productId}` — 메뉴 상품과 옵션

메뉴의 상품 하나에 유효 옵션 구성을 더한 것이다.

```json
{
  "productId": 12,
  "name": "아이스 아메리카노",
  "categoryId": 5,
  "categoryName": "커피",
  "imageUrl": "https://cdn.example.com/p/12.png",
  "basePrice": 4500,
  "displayStartingPrice": 4500,
  "tags": [ { "tagId": 2, "name": "베스트" } ],
  "stockStatus": "ON_SALE",
  "groups": [
    {
      "optionGroupId": 7,
      "name": "사이즈",
      "selectionType": "SINGLE",
      "required": true,
      "autoSelectedOptionKey": null,
      "options": [
        { "optionKey": "REGULAR", "name": "레귤러", "price": 0 },
        { "optionKey": "LARGE", "name": "라지", "price": 700 }
      ]
    }
  ]
}
```

- 메뉴 목록의 상품 필드에 `categoryName`과 `groups`를 더한 것이다. 상품 하나만 열어도 화면을 그릴 수 있도록 카테고리 이름을 함께 담는다.
- `groups`는 Catalog가 계산한 유효 옵션 구성이다. 손님에게는 가격 예외 여부(`priceOverridden`)를 담지 않는다. 손님이 고르는 데 필요 없는 본사 정보다.
- 매장이 `OPEN`이 아니거나, 그 상품이 이 매장 메뉴에 없으면(숨김, `ACTIVE` 아님, 판매 범위 밖) `PRODUCT_NOT_FOUND`(404)다.
- 오류: `STORE_NOT_FOUND`(404), `PRODUCT_NOT_FOUND`(404), `CATALOG_UNAVAILABLE`(503)
