-- 매장 (docs/erd.md stores)

-- 매장 코드(ST-00000123)의 일련번호. 롤백돼도 되돌리지 않으므로 한 번 쓴 코드가 다시 나오지 않는다.
CREATE SEQUENCE store_code_seq;

CREATE TABLE stores (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    code           VARCHAR(16) NOT NULL,
    name           VARCHAR     NOT NULL,
    road_address   VARCHAR     NOT NULL,
    detail_address VARCHAR,
    postal_code    VARCHAR     NOT NULL,
    sido           VARCHAR     NOT NULL,
    sigungu        VARCHAR     NOT NULL,
    phone          VARCHAR     NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PREPARING',
    opening_date   DATE        NOT NULL,
    closing_date   DATE,
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT stores_pkey PRIMARY KEY (id),
    CONSTRAINT stores_code_key UNIQUE (code),
    CONSTRAINT stores_status_check CHECK (status IN ('PREPARING', 'OPEN', 'SUSPENDED', 'CLOSED')),
    -- 폐점일은 폐점한 매장에만 있다
    CONSTRAINT stores_closing_date_check CHECK ((status = 'CLOSED') = (closing_date IS NOT NULL))
);

-- 매장명은 폐점하지 않은 매장 사이에서 유일하다 (요구사항 1.1)
CREATE UNIQUE INDEX stores_name_active_key ON stores (name) WHERE status <> 'CLOSED';

-- 본사관리자 매장 목록의 지역·상태 필터 (요구사항 1.4)
CREATE INDEX stores_sido_sigungu_idx ON stores (sido, sigungu);
CREATE INDEX stores_status_idx ON stores (status);
