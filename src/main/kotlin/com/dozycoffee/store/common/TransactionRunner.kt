package com.dozycoffee.store.common

/**
 * application 서비스가 유스케이스의 트랜잭션 경계를 여는 방법.
 * 안쪽에서 다시 호출하면 바깥 트랜잭션을 이어 쓴다. 블록이 예외로 끝나면 변경이 모두 롤백된다.
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
