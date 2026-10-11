package com.dozycoffee.store.fixture

/** 검증 대상이 아닌 매장 행(폐점 매장 등)을 SQL로 직접 만든다. 다른 유스케이스를 거쳐 준비하면 그 유스케이스가 깨질 때 관계없는 테스트가 함께 실패한다. */
fun insertStoreSql(
    code: String = "ST-00000001",
    name: String = "강남역점",
    status: String = "PREPARING",
    closingDate: String? = null,
) = """
    INSERT INTO stores (code, name, road_address, postal_code, sido, sigungu, phone, status, opening_date, closing_date)
    VALUES ('$code', '$name', '도로명', '06232', '서울특별시', '강남구', '02-1234-5678', '$status', '2026-11-01',
            ${closingDate?.let { "'$it'" } ?: "NULL"})
    """.trimIndent()
