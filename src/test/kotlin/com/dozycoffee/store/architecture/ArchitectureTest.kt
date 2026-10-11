package com.dozycoffee.store.architecture

import com.dozycoffee.store.architecture.ArchitectureRules.BASE
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue as kotlinAssertTrue

/** docs/architecture/package-structure.md의 아키텍처 테스트 표. 판정은 [ArchitectureRules]가 한다. */
@DisplayName("아키텍처 (Konsist)")
class ArchitectureTest {
    private val files = Konsist.scopeFromProduction().files

    private fun KoFileDeclaration.pkg(): String = packagee?.name.orEmpty()

    private fun KoFileDeclaration.importNames(): List<String> = imports.map { it.name }

    private fun KoFileDeclaration.isDomain() = ".domain" in pkg() && ArchitectureRules.layerOf(pkg()) == "domain"

    @Test
    fun `프로덕션 코드를 읽고 있다 (규칙이 비어서 통과하는 일을 막는다)`() {
        kotlinAssertTrue(files.any { it.pkg() == "$BASE.core" }, "core 패키지의 파일을 찾지 못했다")
        kotlinAssertTrue(files.any { it.pkg().startsWith("$BASE.common") }, "common 패키지의 파일을 찾지 못했다")
    }

    @Test
    fun `domain은 순수하다`() {
        files
            .filter { it.isDomain() }
            .assertTrue { file -> file.importNames().all(ArchitectureRules::isDomainImportPure) }
    }

    @Test
    fun `core는 순수 Kotlin이고 모듈을 모른다`() {
        files
            .filter { it.pkg() == "$BASE.core" || it.pkg().startsWith("$BASE.core.") }
            .assertTrue { file ->
                file.importNames().all {
                    ArchitectureRules.isDomainImportPure(it) && ArchitectureRules.moduleOf(it) == null && !it.startsWith("$BASE.common")
                }
            }
    }

    @Test
    fun `application은 adapter를 import하지 않는다`() {
        files
            .filter { ArchitectureRules.layerOf(it.pkg()) == "application" }
            .assertTrue { file -> file.importNames().all(ArchitectureRules::isApplicationImportAllowed) }
    }

    @Test
    fun `다른 모듈은 UseCase와 허용된 이벤트로만 쓰고, 모듈 간 의존은 정해진 방향만 허용한다`() {
        files.assertTrue { file -> file.importNames().all { ArchitectureRules.isModuleImportAllowed(file.pkg(), it) } }
    }

    @Test
    fun `UseCase는 port inbound에 둔다`() {
        Konsist
            .scopeFromProduction()
            .interfaces()
            .withNameEndingWith("UseCase")
            .assertTrue { it.resideInPackage("..application.port.inbound..") }
    }

    @Test
    fun `Repository 인터페이스는 port outbound에 둔다`() {
        Konsist
            .scopeFromProduction()
            .interfaces()
            .withNameEndingWith("Repository")
            .assertTrue { it.resideInPackage("..application.port.outbound..") }
    }

    @Test
    fun `컨트롤러는 adapter inbound web에 둔다`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("Controller")
            .assertTrue { it.resideInPackage("..adapter.inbound.web..") }
    }

    @Test
    fun `Exposed Repository 구현은 adapter outbound persistence에 둔다`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .filter { it.name.startsWith("Exposed") && it.name.endsWith("Repository") }
            .assertTrue { it.resideInPackage("..adapter.outbound.persistence..") }
    }
}
