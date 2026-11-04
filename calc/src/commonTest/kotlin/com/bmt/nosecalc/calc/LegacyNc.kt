package com.bmt.nosecalc.calc

/**
 * 회사 표기 NC 글(`G42G1X40.Z-.5` — 낱말 붙여 씀 · G0~G3 · 앞 0 뺌 · 이동 G 는 바뀔 때만)을 옛 꼴(`G42 G01 X40. Z-0.5` — 이동 줄마다 G)로
 * 되돌린다. 시험 전용: 표기를 바꾸기(2026-11) 전부터 있던 검증기 · 손 계산 시험이 글자로 견주며 읽던 꼴이다. 앱이 내는 글은
 * `Legacy.nc(새 글)` 이 옛 글과 글자까지 같고(숫자 · 축은 그대로, 빈칸 · G 이름 · 앞 0 · 빠진 이동 G 만 다르다),
 * 두 글이 시뮬레이터에서 같은 경로인지는 `NotationTest` 가 본다.
 *
 * - 주석 줄 `(…)` 은 그대로, 낱말 사이에 빈칸 하나.
 * - G0~G3 → G00~G03.
 * - 앞 0: 좌표 · 이송 주소(X Z U W R I K F C `,C` `,R`)의 `.5` → `0.5`, `-.5` → `-0.5`. 정수 주소(N O G M T S P Q L)는 그대로.
 * - 이동 G: 줄에 그룹 01 G(G00~G03 · G90 · G92 · G94)가 없고 축(X Z U W C)이 있으며 그룹 00 · 사이클 G(G04 · G28 · G50 · G70~G76 …)가 없으면
 *   지금 이동 G(G00~G03 일 때만 — 단일형 사이클 모드의 X 만 줄은 그대로)를 N · 보정 G(G40 · G41 · G42) 뒤에 다시 쓴다.
 *   G12.1 · G13.1 은 지금 이동 G 를 지운다(극좌표 글은 그 뒤 첫 이동 줄이 G 를 쓴다).
 */
internal object Legacy {
    private val DECIMAL = setOf("X", "Z", "U", "W", "R", "I", "K", "F", "C", ",C", ",R")
    private val AXES = setOf('X', 'Z', 'U', 'W', 'C')
    private val GROUP1 = setOf(0, 1, 2, 3, 90, 92, 94)

    fun nc(lines: List<String>): List<String> {
        var motion: Int? = null
        return lines.map { line ->
            if (line.startsWith("(")) return@map line
            val words = ncWords(line).map(::wordOld).toMutableList()
            val gs = words.filter { it.startsWith("G") }.map { it.drop(1) }
            if ("12.1" in gs || "13.1" in gs) motion = null
            val ints = gs.mapNotNull { it.toIntOrNull() }
            val own = ints.lastOrNull { it in GROUP1 }
            val other = ints.any { it !in GROUP1 && it !in 40..42 && it !in setOf(96, 97, 98, 99) }
            val m = motion
            if (own != null) {
                motion = own
            } else if (!other && m != null && m in 0..3 && words.any { it.isNotEmpty() && it[0] in AXES }) {
                val at = words.indexOfFirst { !(it.startsWith("N") || it == "G40" || it == "G41" || it == "G42") }.let { if (it < 0) words.size else it }
                words.add(at, "G0$m")
            }
            words.joinToString(" ")
        }
    }

    private fun wordOld(w: String): String {
        val key = if (w.startsWith(",")) w.take(2) else w.take(1)
        val v = w.drop(key.length)
        return when {
            key == "G" && v.length == 1 && v[0] in '0'..'3' -> "G0$v"
            key !in DECIMAL -> w
            v.startsWith(".") -> "${key}0$v"
            v.startsWith("-.") -> "$key-0${v.drop(1)}"
            else -> w
        }
    }
}

/** 옛 꼴로 되돌린 글([Legacy.nc]) — 글자로 견주는 시험이 `s.ncG40.legacy` 처럼 읽는다 */
internal val List<String>.legacy: List<String> get() = Legacy.nc(this)
