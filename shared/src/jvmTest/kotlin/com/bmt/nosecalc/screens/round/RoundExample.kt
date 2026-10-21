package com.bmt.nosecalc.screens.round

/**
 * 예시 치수(예전 처음 값)를 넣은 상태. 화면은 0 에서 시작하지만 스크린샷 · 골든 · 시험은 이 값으로 같은 계산 상태를 그린다.
 * 그림 모양대로 맞는 값이고, 비울 수 있는 칸 하나는 비워 자동으로 보인다. 형상 3 은 calc-design.md 의 손 계산 예(Ø20, SR12.5, CR1 → L = 5).
 * 수동은 어깨의 볼록 R10(X30 Z0 → X50 Z−10, 가운데 점은 45° 자리). Z 여유도 예전 처음 값 1.0
 */
internal fun roundExample(type: Int? = null, open: Boolean = false): RoundState =
    RoundState(type, open).apply {
        EXAMPLE_DIMS.forEach { (t, dims) -> dims.forEach { (key, v) -> setDim(t, key, v) } }
        EXAMPLE_POINTS.forEach { (name, v) -> setPoint(name, v) }
        setShared("zClear", "1.0")
    }

/** 내경 예시: 같은 치수의 구멍, 입구 구멍 깊이 10. 형상 3 은 calc-design 의 내경 손 계산(Ø20, SR12.5, CR1 → 바닥 Z−15) */
internal fun roundInnerExample(type: Int): RoundState = roundExample(type, open = true).apply {
    typeInner = true
    setShared("leg", "10")
}

/** 내경 형상 5 관통(calc-design 보링바 C): 입구 Ø40 깊이 5 → 구면 SR25 → Ø30 깊이 6, 모서리 R 없음. 비운 L2 는 5 */
internal fun roundInnerStep(): RoundState = roundInnerExample(5).apply {
    through = true
    mapOf("cr2" to "0", "mainR" to "25", "cr1" to "0", "d2" to "40", "d1" to "30", "l1" to "6", "l2" to "").forEach { (k, v) -> setDim(5, k, v) }
    setShared("leg", "5")
}

/** 내경 3점호 입구 R5(Ø30 끝면 → Ø20 구멍, CAD 점 셋째 자리) */
internal fun roundManualInnerMouth(): RoundState = roundExample(MANUAL, open = true).apply {
    inner = true
    mapOf("x1" to "30", "z1" to "0", "x2" to "22.929", "z2" to "-1.464", "x3" to "20", "z3" to "-5").forEach { (k, v) -> setPoint(k, v) }
}

/** 보링바 프리셋 A16R-SCLCR 09(Ø16 · f 11 · DMIN 20 · κr 95° · C 9)를 고른 상태 */
internal fun RoundState.withA16(): RoundState = apply { pickPreset(HOLDER_PRESETS.single { it.code == "A16R-SCLCR 09" }) }

/** 내경 · 역방향 3점호(보링 안쪽의 R10 을 Z+ 로, X50 Z−10 → X30 Z0): 그림이 위아래 · 좌우로 뒤집힌다 */
internal fun roundManualInnerRev(): RoundState = roundExample(MANUAL, open = true).apply {
    inner = true
    mapOf("x1" to "50", "z1" to "-10", "x2" to "35.8579", "z2" to "-7.0711", "x3" to "30", "z3" to "0").forEach { (k, v) -> setPoint(k, v) }
}

private val EXAMPLE_DIMS = mapOf(
    1 to mapOf("mainR" to "10", "cr1" to "1", "l1" to "12"),
    2 to mapOf("mainR" to "10", "cr1" to "1", "d1" to "10", "l1" to "5", "l2" to ""),
    3 to mapOf("cr1" to "1", "mainR" to "12.5", "d1" to "20", "l1" to ""),
    4 to mapOf("cr1" to "1", "mainR" to "10", "d1" to "12", "l1" to ""),
    5 to mapOf("cr2" to "1", "mainR" to "18", "cr1" to "1", "d2" to "30", "d1" to "10", "l1" to "5", "l2" to ""),
    6 to mapOf("cr2" to "1", "mainR" to "12", "cr1" to "1", "d2" to "40", "d1" to "8", "l1" to "5", "l2" to "8"),
)

private val EXAMPLE_POINTS = mapOf("x1" to "30", "z1" to "0", "x2" to "44.1421", "z2" to "-2.9289", "x3" to "50", "z3" to "-10")
