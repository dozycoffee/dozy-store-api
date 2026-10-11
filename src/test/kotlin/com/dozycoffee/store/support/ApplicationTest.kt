package com.dozycoffee.store.support

import com.dozycoffee.store.common.TransactionRunner
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired

/** 유스케이스(application 서비스) 테스트의 뿌리. 가짜 저장소 없이 실제 Repository와 DB를 쓴다. */
abstract class ApplicationTest : IntegrationTest() {
    @Autowired
    protected lateinit var transactionRunner: TransactionRunner

    /** 결과 확인용. Repository를 직접 불러 상태를 확인할 때 쓴다. */
    protected fun <T> tx(block: suspend () -> T): T = runBlocking { transactionRunner.inTransaction(block) }

    /**
     * 검증 대상이 아닌 테이블(예: 매장 상품 테스트의 상품 복제본)을 SQL로 직접 채운다.
     * 다른 유스케이스를 거쳐 준비하면 그 유스케이스가 깨질 때 관계없는 테스트가 함께 실패한다.
     */
    protected fun execute(sql: String) {
        runBlocking {
            databaseClient
                .sql(sql)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }
}
