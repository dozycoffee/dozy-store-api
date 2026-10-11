package com.dozycoffee.store.management.application.service

import com.dozycoffee.store.common.TransactionRunner
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.core.VersionConflictException
import com.dozycoffee.store.management.application.port.inbound.GetStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.RegisterStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.UpdateStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.command.RegisterStoreCommand
import com.dozycoffee.store.management.application.port.inbound.command.UpdateStoreCommand
import com.dozycoffee.store.management.application.port.inbound.result.StoreResult
import com.dozycoffee.store.management.application.port.outbound.StoreCodeGenerator
import com.dozycoffee.store.management.application.port.outbound.StoreRepository
import com.dozycoffee.store.management.domain.store.NewStore
import com.dozycoffee.store.management.domain.store.exception.StoreNameDuplicatedException
import com.dozycoffee.store.management.domain.store.exception.StoreNotFoundException
import org.springframework.stereotype.Service

/** 매장의 등록, 조회, 정보 수정. 유스케이스마다 트랜잭션 경계를 연다. */
@Service
class StoreService(
    private val stores: StoreRepository,
    private val codes: StoreCodeGenerator,
    private val transactions: TransactionRunner,
) : RegisterStoreUseCase,
    GetStoreUseCase,
    UpdateStoreUseCase {
    override suspend fun register(command: RegisterStoreCommand): StoreResult =
        transactions.inTransaction {
            // 매장명 유일은 조회가 필요한 규칙이라 여기서 확인한다. 확인과 저장 사이에 같은 이름이 들어오는 경우는 DB의 부분 UNIQUE가 막는다.
            if (stores.existsActiveByName(command.name)) throw StoreNameDuplicatedException(command.name)

            val newStore =
                NewStore(
                    code = codes.next(),
                    name = command.name,
                    address = command.address,
                    phone = command.phone,
                    openingDate = command.openingDate,
                )
            StoreResult.from(stores.insert(newStore))
        }

    override suspend fun update(command: UpdateStoreCommand): StoreResult =
        transactions.inTransaction {
            val store = stores.findById(command.id) ?: throw StoreNotFoundException(command.id)
            // 화면이 본 뒤에 다른 변경이 반영됐으면 바로 거부한다. 읽은 뒤 저장하기 전에 끼어든 변경은 저장소의 낙관적 잠금이 막는다.
            if (store.version != command.expectedVersion) throw VersionConflictException(store.version)
            if (stores.existsActiveByName(command.name, excluding = store.id)) throw StoreNameDuplicatedException(command.name)

            store.update(command.name, command.address, command.phone, command.openingDate)
            StoreResult.from(stores.save(store))
        }

    override suspend fun get(id: StoreId): StoreResult =
        transactions.inTransaction {
            StoreResult.from(stores.findById(id) ?: throw StoreNotFoundException(id))
        }
}
