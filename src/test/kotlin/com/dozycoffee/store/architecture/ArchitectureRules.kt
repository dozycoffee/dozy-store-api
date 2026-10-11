package com.dozycoffee.store.architecture

/**
 * 모듈·계층 의존 규칙의 판정 로직(docs/architecture/package-structure.md, ADR-0005).
 * Konsist에 얹기 전에 순수 함수로 두어, 규칙 자체를 위반·허용 사례로 단위 테스트한다.
 */
object ArchitectureRules {
    const val BASE = "com.dozycoffee.store"

    /** 모듈과, 그 모듈이 부를 수 있는 다른 모듈(패키지 구조 문서의 모듈 간 의존). 순환은 만들지 않는다. */
    val allowedModuleDependencies: Map<String, Set<String>> =
        mapOf(
            "management" to emptySet(),
            "catalogproduct" to emptySet(),
            "storeproduct" to setOf("management", "catalogproduct"),
            "menu" to setOf("management", "catalogproduct", "storeproduct"),
        )

    val modules: Set<String> = allowedModuleDependencies.keys

    /** domain(과 core)이 import하면 안 되는 기술 패키지 */
    val forbiddenInDomain: List<String> =
        listOf("org.springframework", "org.jetbrains.exposed", "org.apache.kafka", "io.r2dbc", "tools.jackson", "com.fasterxml")

    /** `storeproduct`의 노출 현황 집계 조회가 다른 모듈의 테이블을 읽기 전용으로 직접 참조하는 예외(ADR-0005) */
    private const val EXPOSURE_QUERY_PACKAGE = "$BASE.storeproduct.adapter.outbound.query"

    /** 패키지 이름에서 모듈을 꺼낸다. 모듈 밖(`core`, `common`, 루트)이면 null. */
    fun moduleOf(packageName: String): String? =
        packageName
            .removePrefix("$BASE.")
            .takeIf { packageName.startsWith("$BASE.") }
            ?.substringBefore('.')
            ?.takeIf { it in modules }

    /** 패키지 이름에서 모듈 안의 층(`domain`, `application`, `adapter`)을 꺼낸다. */
    fun layerOf(packageName: String): String? {
        val module = moduleOf(packageName) ?: return null
        return packageName
            .removePrefix("$BASE.$module")
            .removePrefix(".")
            .substringBefore('.')
            .takeIf { it.isNotEmpty() }
    }

    /** [fromPackage]의 코드가 [importName]을 import해도 되는가. 모듈 사이의 규칙만 본다. */
    fun isModuleImportAllowed(
        fromPackage: String,
        importName: String,
    ): Boolean {
        val target = moduleOf(importName) ?: return true
        val from = moduleOf(fromPackage)
        if (from == target) return true
        if (from == null) return false // core·common·루트는 모듈을 모른다
        if (target !in allowedModuleDependencies.getValue(from)) return false
        if (fromPackage.startsWith(EXPOSURE_QUERY_PACKAGE) && importName.contains(".adapter.outbound.persistence.")) return true
        // 다른 모듈은 UseCase(port.inbound)와 내보낸 도메인 이벤트(domain.event)로만 쓴다
        return importName.startsWith("$BASE.$target.application.port.inbound.") ||
            importName.startsWith("$BASE.$target.domain.event.")
    }

    /** domain(과 core)의 import가 순수한가. */
    fun isDomainImportPure(importName: String): Boolean {
        if (forbiddenInDomain.any { importName.startsWith("$it.") || importName == it }) return false
        val layer = layerOf(importName)
        return layer != "application" && layer != "adapter"
    }

    /** application이 adapter를 import하면 계층 방향 위반이다. */
    fun isApplicationImportAllowed(importName: String): Boolean = layerOf(importName) != "adapter"
}
