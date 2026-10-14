-- Catalog 상품 복제본 (docs/erd.md catalog_products, catalog_product_target_stores)
-- 노출 판단에 필요한 최소 상태만 이벤트로 복제해 둔다(ADR-0002). 이름·가격 같은 표시용 정보는 갖지 않는다.

CREATE TABLE catalog_products (
    -- Catalog가 발급한 상품 ID. identity가 아니다
    product_id       BIGINT      NOT NULL,
    -- 상품 식별. 과거 데이터에는 없을 수 있다. 2단계에서 재고 이벤트 매칭 키로 쓴다
    sku              VARCHAR,
    status           VARCHAR(20) NOT NULL,
    -- LIMITED면 대상 매장은 catalog_product_target_stores
    store_scope      VARCHAR(20) NOT NULL,
    -- 처음 복제한 값에서 바뀌지 않는다
    tracks_inventory BOOLEAN     NOT NULL,
    -- 마지막으로 반영한 이벤트의 상품별 순번. 이보다 크지 않은 이벤트는 무시한다
    sequence         BIGINT      NOT NULL,
    -- 삭제 표시. 한번 true가 되면 바뀌지 않는다
    deleted          BOOLEAN     NOT NULL DEFAULT false,
    -- 마지막으로 반영한 이벤트의 발생 시각. 기록용
    last_event_at    TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT catalog_products_pkey PRIMARY KEY (product_id),
    CONSTRAINT catalog_products_sku_key UNIQUE (sku),
    CONSTRAINT catalog_products_status_check CHECK (status IN ('DRAFT', 'ACTIVE', 'DISCONTINUED')),
    CONSTRAINT catalog_products_store_scope_check CHECK (store_scope IN ('ALL', 'LIMITED'))
);

-- 매장 상품 목록·손님 메뉴에서 ACTIVE 상품 고르기
CREATE INDEX catalog_products_status_idx ON catalog_products (status) WHERE NOT deleted;

-- 복제한 판매 범위의 대상 매장. 복제본을 교체할 때 지우고 다시 넣는다.
-- 감사 컬럼은 두지 않는다. 교체 때마다 다시 만들어지는 행이라 시각에 의미가 없다.
CREATE TABLE catalog_product_target_stores (
    product_id BIGINT NOT NULL,
    -- Catalog가 보낸 매장 ID. FK가 없다: 모르는 매장 ID가 오면 FK 위반으로 이벤트 처리가 막혀 같은 메시지를 끝없이 재시도하게 된다
    store_id   BIGINT NOT NULL,
    CONSTRAINT catalog_product_target_stores_pkey PRIMARY KEY (product_id, store_id),
    CONSTRAINT catalog_product_target_stores_product_id_fkey FOREIGN KEY (product_id)
        REFERENCES catalog_products (product_id) ON DELETE CASCADE
);

-- "이 매장이 대상인 상품" 조회
CREATE INDEX catalog_product_target_stores_store_id_idx ON catalog_product_target_stores (store_id);
