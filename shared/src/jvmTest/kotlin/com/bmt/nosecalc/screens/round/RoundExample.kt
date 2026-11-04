package com.bmt.nosecalc.screens.round

/**
 * 예시 치수(예전 처음 값)를 넣은 상태. 화면은 빈칸에서 시작하지만 스크린샷 · 골든 · 시험은 이 값으로 같은 계산 상태를 그린다.
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

/** 보링바 칸을 직접 넣은 상태(프리셋 없음): D · f · 인서트 모양 · 날 길이 · κr(· DMIN) */
internal fun RoundState.withBar(dia: String, f: String, shape: String, edge: String, kr: String, dmin: String = ""): RoundState = apply {
    mapOf("dia" to dia, "f" to f, "shape" to shape, "edge" to edge, "kr" to kr, "dmin" to dmin).forEach { (k, v) -> setHolder(k, v) }
}

/** calc-design.md 보링바 예 A: 내경 돔 바닥(Ø20 · 깊이 10, SR12.5, CR1) + 바 Ø16 · f 11 · C 9.67 · κr 95° — 구 원호에서 바가 맞은편 벽 */
internal fun roundInnerDomeA(): RoundState = roundInnerExample(3).withBar("16", "11", "C", "9.67", "95")

/**
 * calc-design.md 보링바 예 C: 내경 형상 5 막힘(입구 Ø40 깊이 5 → SR25 → Ø30 깊이 6 → 바닥 면, 모서리 R 없음) + 바 A — 바닥 면을 깎는 동안 바 앞면이
 * 면에서 0.226(깎는 면 예외 — 겹치지 않음만), 바 맞은편이 안전 여유에 닿는 X9 에서 멈춘다
 */
internal fun roundInnerBlindC(): RoundState = roundInnerStep().apply { through = false }.withBar("16", "11", "C", "9.67", "95")

/** 보링바 예 F: 내경 소켓(형상 4, Ø10 · 깊이 5, SR8, CR1) + 바 Ø8 · f 4.5 · D 7.75 · κr 93° — 아래 인서트 조각이 맞은편 벽부터 */
internal fun roundInnerSocketF(): RoundState = roundInnerExample(4).apply {
    setDim(4, "mainR", "8")
    setDim(4, "d1", "10")
    setShared("leg", "5")
}.withBar("8", "4.5", "D", "7.75", "93")

/**
 * 보링바 뒤보링 예: 3점호 역방향 내경(방 안 뒷면 X30.4 Z−20 → X24.4 Z−19 → R5 → 입구 X20.4 Z−15, 팁 1 · G42) + 바 Ø12 · f 12 · C 9.67 · κr 95° · DMIN 22 —
 * calc-design 보링바 예 '뒤보링'(X30 · X24 · X20)을 반경 0.2 키운 같은 꼴(헤드 Ø22 가 입구 Ø20.4 보다 커 기계 보정은 Z20. 자리부터 못 들어감)
 */
internal fun roundManualBackBoring(): RoundState = roundExample(MANUAL, open = true).apply {
    inner = true
    mapOf("x1" to "30.4", "z1" to "-20", "x2" to "24.4", "z2" to "-19", "x3" to "20.4", "z3" to "-15").forEach { (k, v) -> setPoint(k, v) }
}.withBar("12", "12", "C", "9.67", "95", dmin = "22")

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
