-- 매장 매니저 지정 (docs/erd.md store_managers)
-- (매장, 계정 종류, 계정 ID) 한 쌍이 곧 키다. PK가 같은 쌍의 중복 지정을 막는다(요구사항 1.3).
-- 값이 바뀌지 않는 연결이라 updated_at이 없다.

CREATE TABLE store_managers (
    store_id       BIGINT      NOT NULL,
    principal_type VARCHAR(20) NOT NULL,
    -- Auth의 계정 ID. FK가 없고 존재 여부를 확인하지 않는다(요구사항 1.3)
    principal_id   UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT store_managers_pkey PRIMARY KEY (store_id, principal_type, principal_id),
    CONSTRAINT store_managers_store_id_fkey FOREIGN KEY (store_id) REFERENCES stores (id),
    -- 매니저로 지정할 수 있는 계정 종류
    CONSTRAINT store_managers_principal_type_check CHECK (principal_type IN ('PARTNER', 'EMPLOYEE'))
);

-- "이 계정이 매니저인 매장" 조회 (인가, 매니저의 매장 목록)
CREATE INDEX store_managers_principal_idx ON store_managers (principal_type, principal_id);
