package com.dozycoffee.store.management.adapter.outbound.persistence

import com.dozycoffee.store.management.application.port.outbound.StoreCodeGenerator
import com.dozycoffee.store.management.domain.store.StoreCode
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.springframework.stereotype.Component

/**
 * 전용 시퀀스(`store_code_seq`)에서 일련번호를 받아 매장 코드를 만든다.
 * 시퀀스는 트랜잭션이 롤백돼도 되돌아가지 않으므로 한 번 쓴 코드가 다시 나오지 않는다.
 */
@Component
class ExposedStoreCodeGenerator : StoreCodeGenerator {
    override suspend fun next(): StoreCode {
        val sequence =
            TransactionManager
                .current()
                .exec("SELECT nextval('store_code_seq')", emptyList(), StatementType.SELECT) { row ->
                    row.get(0, java.lang.Long::class.java)?.toLong()
                }?.single()
        checkNotNull(sequence) { "store_code_seq에서 일련번호를 받지 못했습니다" }
        return StoreCode.create(sequence)
    }
}
