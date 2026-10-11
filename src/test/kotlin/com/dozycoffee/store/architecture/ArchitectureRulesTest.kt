package com.dozycoffee.store.architecture

import com.dozycoffee.store.architecture.ArchitectureRules.BASE
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("아키텍처 규칙 판정")
class ArchitectureRulesTest {
    @Nested
    @DisplayName("패키지 해석")
    inner class Parsing {
        @Test
        fun `모듈과 층을 꺼낸다`() {
            assertEquals("storeproduct", ArchitectureRules.moduleOf("$BASE.storeproduct.domain.display"))
            assertEquals("domain", ArchitectureRules.layerOf("$BASE.storeproduct.domain.display"))
            assertEquals("adapter", ArchitectureRules.layerOf("$BASE.menu.adapter.inbound.web"))
        }

        @Test
        fun `core, common, 외부 패키지는 모듈이 아니다`() {
            assertNull(ArchitectureRules.moduleOf("$BASE.core"))
            assertNull(ArchitectureRules.moduleOf("$BASE.common.web"))
            assertNull(ArchitectureRules.moduleOf("org.springframework.stereotype"))
            assertNull(ArchitectureRules.layerOf("$BASE.core"))
        }
    }

    @Nested
    @DisplayName("모듈 간 의존")
    inner class ModuleDependency {
        private val from = "$BASE.storeproduct.application.service"

        @Test
        fun `자기 모듈 안은 자유롭다`() {
            assertTrue(ArchitectureRules.isModuleImportAllowed(from, "$BASE.storeproduct.domain.display.StoreDisplaySetting"))
        }

        @Test
        fun `다른 모듈은 UseCase로 쓴다`() {
            assertTrue(ArchitectureRules.isModuleImportAllowed(from, "$BASE.management.application.port.inbound.CheckStoreAccessUseCase"))
            assertTrue(ArchitectureRules.isModuleImportAllowed(from, "$BASE.management.application.port.inbound.result.StoreAccess"))
        }

        @Test
        fun `다른 모듈의 도메인, Repository, adapter는 쓸 수 없다`() {
            assertFalse(ArchitectureRules.isModuleImportAllowed(from, "$BASE.management.domain.store.Store"))
            assertFalse(ArchitectureRules.isModuleImportAllowed(from, "$BASE.management.application.port.outbound.StoreRepository"))
            assertFalse(
                ArchitectureRules.isModuleImportAllowed(from, "$BASE.catalogproduct.adapter.outbound.persistence.CatalogProductsTable"),
            )
        }

        @Test
        fun `내보낸 도메인 이벤트는 구독할 수 있다`() {
            assertTrue(ArchitectureRules.isModuleImportAllowed(from, "$BASE.catalogproduct.domain.event.CatalogProductScopeChanged"))
        }

        @Test
        fun `표에 없는 방향은 막는다 (순환 방지)`() {
            assertFalse(
                ArchitectureRules.isModuleImportAllowed(
                    "$BASE.management.application.service",
                    "$BASE.storeproduct.application.port.inbound.X",
                ),
            )
            assertFalse(
                ArchitectureRules.isModuleImportAllowed(
                    "$BASE.catalogproduct.application.service",
                    "$BASE.storeproduct.application.port.inbound.X",
                ),
            )
            assertFalse(
                ArchitectureRules.isModuleImportAllowed("$BASE.storeproduct.application.service", "$BASE.menu.application.port.inbound.X"),
            )
        }

        @Test
        fun `menu는 세 모듈의 UseCase를 모두 쓸 수 있다`() {
            val menu = "$BASE.menu.application.service"
            listOf("management", "catalogproduct", "storeproduct").forEach {
                assertTrue(ArchitectureRules.isModuleImportAllowed(menu, "$BASE.$it.application.port.inbound.AnyUseCase"))
            }
        }

        @Test
        fun `노출 현황 집계 조회만 다른 모듈의 테이블을 직접 읽을 수 있다`() {
            val table = "$BASE.catalogproduct.adapter.outbound.persistence.CatalogProductsTable"

            assertTrue(ArchitectureRules.isModuleImportAllowed("$BASE.storeproduct.adapter.outbound.query", table))
            assertFalse(ArchitectureRules.isModuleImportAllowed("$BASE.storeproduct.adapter.outbound.persistence", table))
            assertFalse(ArchitectureRules.isModuleImportAllowed("$BASE.menu.adapter.outbound.query", table))
        }

        @Test
        fun `core와 common은 모듈을 모른다`() {
            assertFalse(ArchitectureRules.isModuleImportAllowed("$BASE.core", "$BASE.management.application.port.inbound.X"))
            assertFalse(ArchitectureRules.isModuleImportAllowed("$BASE.common.web", "$BASE.management.domain.store.Store"))
        }

        @Test
        fun `모듈 밖의 타입은 누구나 쓸 수 있다`() {
            assertTrue(ArchitectureRules.isModuleImportAllowed(from, "$BASE.core.StoreId"))
            assertTrue(ArchitectureRules.isModuleImportAllowed(from, "$BASE.common.TransactionRunner"))
        }
    }

    @Nested
    @DisplayName("domain 순수성과 계층 방향")
    inner class Layers {
        @Test
        fun `domain은 프레임워크와 바깥 층을 import하지 않는다`() {
            assertFalse(ArchitectureRules.isDomainImportPure("org.springframework.stereotype.Component"))
            assertFalse(ArchitectureRules.isDomainImportPure("org.jetbrains.exposed.v1.core.Table"))
            assertFalse(ArchitectureRules.isDomainImportPure("tools.jackson.databind.ObjectMapper"))
            assertFalse(ArchitectureRules.isDomainImportPure("$BASE.management.application.service.StoreService"))
            assertFalse(ArchitectureRules.isDomainImportPure("$BASE.management.adapter.inbound.web.StoreController"))
        }

        @Test
        fun `domain은 순수 Kotlin과 core, 자기 도메인은 import할 수 있다`() {
            assertTrue(ArchitectureRules.isDomainImportPure("java.time.LocalDate"))
            assertTrue(ArchitectureRules.isDomainImportPure("kotlin.collections.List"))
            assertTrue(ArchitectureRules.isDomainImportPure("$BASE.core.StoreId"))
            assertTrue(ArchitectureRules.isDomainImportPure("$BASE.management.domain.store.StoreStatus"))
        }

        @Test
        fun `application은 adapter를 import하지 않는다`() {
            assertFalse(ArchitectureRules.isApplicationImportAllowed("$BASE.management.adapter.outbound.persistence.StoresTable"))
            assertTrue(ArchitectureRules.isApplicationImportAllowed("$BASE.management.domain.store.Store"))
            assertTrue(ArchitectureRules.isApplicationImportAllowed("org.springframework.stereotype.Service"))
        }
    }
}
