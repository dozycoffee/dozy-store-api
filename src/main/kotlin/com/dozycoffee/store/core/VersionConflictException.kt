package com.dozycoffee.store.core

/** 낙관적 잠금 충돌. 관리자가 본 버전 이후에 다른 변경이 반영됐다. */
class VersionConflictException(
    val currentVersion: Long?,
) : DomainException(
        CommonErrorCode.VERSION_CONFLICT,
        "다른 변경이 먼저 반영되었습니다. 최신 값을 다시 불러오세요.",
    )
