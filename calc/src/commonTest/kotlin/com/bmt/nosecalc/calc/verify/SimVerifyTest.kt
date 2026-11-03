package com.bmt.nosecalc.calc.verify

import com.bmt.nosecalc.calc.Arc
import com.bmt.nosecalc.calc.Arc3Input
import com.bmt.nosecalc.calc.DoubleTaperInput
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.Line
import com.bmt.nosecalc.calc.NoseComp
import com.bmt.nosecalc.calc.Pt
import com.bmt.nosecalc.calc.RoundCalc
import com.bmt.nosecalc.calc.RoundInput
import com.bmt.nosecalc.calc.RoundResult
import com.bmt.nosecalc.calc.RoundSolution
import com.bmt.nosecalc.calc.RoundWarning
import com.bmt.nosecalc.calc.Seg
import com.bmt.nosecalc.calc.Side
import com.bmt.nosecalc.calc.TaperCalc
import com.bmt.nosecalc.calc.TaperInput
import com.bmt.nosecalc.calc.TaperResult
import com.bmt.nosecalc.calc.TaperSolution
import com.bmt.nosecalc.calc.TaperWarning
import com.bmt.nosecalc.calc.sim.CompStartup
import com.bmt.nosecalc.calc.sim.DecimalMode
import com.bmt.nosecalc.calc.sim.DwellStep
import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.GText
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.IssueLevel
import com.bmt.nosecalc.calc.sim.MachineSetup
import com.bmt.nosecalc.calc.sim.MoveKind
import com.bmt.nosecalc.calc.sim.MoveStep
import com.bmt.nosecalc.calc.sim.RapidPath
import com.bmt.nosecalc.calc.sim.SimInput
import com.bmt.nosecalc.calc.sim.SimRun
import com.bmt.nosecalc.calc.sim.StepSrc
import com.bmt.nosecalc.calc.sim.ToolOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * 시뮬레이터(`calc.sim`) 독립 검증 — 4단계 검증 에이전트(2026-10-28). 구현을 고치지 않고 이 파일만 더했다.
 *
 * 1. 출처 예제를 손으로 펼친 경로 · 사이클 타임과 맞춰 본다. 기대값은 이 파일에서 손 계산(식을 주석에)으로 만들고,
 *    시뮬레이터의 GTime · GComp · GCycles 를 부르지 않는다. 출처: H = Haas ST/TL 워크북 2015(인쇄 쪽, inch 숫자를 mm 로 읽음),
 *    S = Siemens SINUMERIK ISO Turning 04/2007 — 옮긴 글(S 4-92 · 4-109, H O00072 · O00075 · O00110)은 두 PDF 의 글 뽑기와 다시 맞춰 봤다.
 *    기계 설정은 처음 값(급송 X 20 m/min 반경 · Z 24 m/min, 원점 X200 Z100, 공구 교환 1 초 · 처음 T 0 초).
 * 2. 테이퍼 · 라운드 계산기의 G41/G42 글과 직접 보정(G40) 글을 시뮬레이터에 넣어, 시뮬레이터 노즈 중심 경로가
 *    계산기의 노즈 중심 경로(`center`)와 0.001 안인지 — 경로 전체를 0.05 mm 마다 찍어 양쪽으로(앞 → 뒤, 뒤 → 앞) 본다.
 *    G41/G42 글의 끝은 따로 짠 제어기 모형(안쪽 = 교점, 바깥 · 이어짐 = 끝 법선)과 맞추고, 계산기 끝과의 차이를 갈래별로 모은다
 *    (계산기 문서가 말한 u·(팁 벡터·u) 만 '문서대로'). 또 글 전체 노즈가 계산기 도면선에 r 보다 가까이 가는지(파고듦) 본다.
 * 3. 오류 처리: 출처 · 손으로 만든 오류 글이 줄 번호와 함께 멈추는지, 무작위 글(글자 · 낱말 · 사이클 모양 · 출처 글 변형)에서
 *    예외 없이 줄 번호 · 글자 자리 · 시간이 맞는지.
 * 4. FANUC 공식 설명서(F0 = B-61394E/08, FF = B-64604EN-1/01)의 사이클 규칙 · 예제와 맞춰 본다.
 * 시뮬레이터가 아닌 앱 쪽 어긋남(라운드 기계 보정 글이 축 끝을 덜 깎음 등)은 알리기만 한다(println — 테스트 결과 XML 의 system-out).
 */
class SimVerifyTest {

    // ================================================================ 공용 — 구현과 따로 짠 기하

    private val tol = 1e-3

    /** 앞에 붙이는 준비 줄(공구 · 회전 · 이송) */
    private val pre = listOf("T0101", "G97 S1000 M03", "G99 F0.2")

    /**
     * 계산기 G41/G42 글은 형식 A · B 기계를 전제로 만든 것이라(설계 13.7 · 13.9) 이 시험은 보정 시작 · 취소 형식 A · B 로 돌린다 —
     * 형식 C(시뮬레이터 처음 값)의 이동 없는 G40 은 설계 18절 · `CompTest`
     */
    private val ab = MachineSetup(compStartup = CompStartup.AB)

    private fun sim(lines: List<String>, tools: Map<Int, ToolOffset> = emptyMap(), machine: MachineSetup = ab): SimRun =
        GSim.run(SimInput(lines.joinToString("\n"), machine, tools))

    private fun moves(r: SimRun) = r.steps.filterIsInstance<MoveStep>()

    private fun xz(p: Pt) = "X${Fmt.fixed3(p.xDia)} Z${Fmt.fixed3(p.z)}"

    private fun ang(v: Pt) = atan2(v.x, v.z)

    private fun wrap(a: Double): Double {
        val t = 2 * PI
        var r = a % t
        if (r < 0) r += t
        return r
    }

    private fun radiusOf(a: Arc) = (a.start - a.center).length

    private fun sweepOf(a: Arc): Double {
        if ((a.end - a.start).length < 1e-12) return 0.0
        val d = ang(a.end - a.center) - ang(a.start - a.center)
        return wrap(if (a.cw) -d else d)
    }

    private fun lenOf(s: Seg): Double = when (s) {
        is Line -> (s.end - s.start).length
        is Arc -> radiusOf(s) * sweepOf(s)
    }

    private fun at(s: Seg, f: Double): Pt = when (s) {
        is Line -> s.start + (s.end - s.start) * f
        is Arc -> {
            val a = ang(s.start - s.center) + (if (s.cw) -1.0 else 1.0) * sweepOf(s) * f
            s.center + Pt(cos(a), sin(a)) * radiusOf(s)
        }
    }

    /** 토막 위 점들 (비율, 점) — 0.05 mm 마다, 양 끝 포함 */
    private fun samples(s: Seg, step: Double = 0.05): List<Pair<Double, Pt>> {
        val n = max(4, ceil(lenOf(s) / step).toInt())
        return (0..n).map { val f = it.toDouble() / n; f to at(s, f) }
    }

    private fun pts(s: Seg) = samples(s).map { it.second }

    private fun dist(p: Pt, s: Seg): Double = when (s) {
        is Line -> {
            val d = s.end - s.start
            val l2 = d dot d
            if (l2 < 1e-24) (p - s.start).length else (s.start + d * (((p - s.start) dot d) / l2).coerceIn(0.0, 1.0) - p).length
        }
        is Arc -> {
            val sw = sweepOf(s)
            val rad = radiusOf(s)
            val u = wrap((if (s.cw) -1.0 else 1.0) * (ang(p - s.center) - ang(s.start - s.center)))
            if (rad > 1e-12 && sw > 0 && u <= sw) abs((p - s.center).length - rad) else min((p - s.start).length, (p - s.end).length)
        }
    }

    private fun distSet(p: Pt, segs: List<Seg>) = segs.minOf { dist(p, it) }

    /** 반직선(q 에서 단위 방향 u 쪽)까지 거리 */
    private fun rayDist(p: Pt, q: Pt, u: Pt): Double {
        val t = (p - q) dot u
        return if (t <= 0) (p - q).length else abs((p - q) cross u)
    }

    private fun unitOf(v: Pt) = v * (1 / v.length)

    /** 진행 방향 단위 접선(원호: 반지름 방향을 왼쪽으로 90° — 반시계, 시계면 반대) */
    private fun tanStart(s: Seg): Pt = when (s) {
        is Line -> unitOf(s.end - s.start)
        is Arc -> unitOf(s.start - s.center).let { Pt(-it.x, it.z) * (if (s.cw) -1.0 else 1.0) }
    }

    private fun tanEnd(s: Seg): Pt = when (s) {
        is Line -> unitOf(s.end - s.start)
        is Arc -> unitOf(s.end - s.center).let { Pt(-it.x, it.z) * (if (s.cw) -1.0 else 1.0) }
    }

    /** 점이 토막의 어디쯤인가(0 = 시작, 1 = 끝, 밖이면 0 아래 · 1 위) */
    private fun param(s: Seg, p: Pt): Double = when (s) {
        is Line -> (s.end - s.start).let { d -> ((p - s.start) dot d) / (d dot d) }
        is Arc -> {
            val sw = sweepOf(s)
            val u = wrap((if (s.cw) -1.0 else 1.0) * (ang(p - s.center) - ang(s.start - s.center)))
            if (sw <= 0) 0.0 else if (u > sw / 2 + PI) (u - 2 * PI) / sw else u / sw
        }
    }

    /** 급송 시간(초) — 축마다 따로, 큰 쪽(설계 4.2 · S 2-23). X 는 반경 */
    private fun rapidSec(a: Pt, b: Pt) = max(abs(b.x - a.x) / 20_000.0, abs(b.z - a.z) / 24_000.0) * 60

    /** 결과 모음(사례 수 · 가장 큰 차이 · 실패 · 메모) */
    private class Tally(val name: String) {
        var cases = 0
        var worst = 0.0
        var worstAt = ""
        val fails = mutableListOf<String>()
        val notes = mutableListOf<String>()

        /** G41/G42 글의 끝 노즈가 계산기 노즈 경로 끝과 0.001 넘게 다른 사례 */
        val ends = mutableListOf<EndDiff>()

        /** 끝 말고 경로 가운데(앞 → 뒤 · 뒤 → 앞)가 0.001 넘게 다른 사례 수 */
        var pathFails = 0

        /** 노즈가 도면선에 r 보다 가까이 간 사례(깊이 mm, 그 블록 글, 이동 종류, 사례, 글) */
        val gouges = mutableListOf<Gouge>()

        fun gougeSummary(): List<String> = gouges.groupBy { "${it.kind} ${it.block.substringBefore(' ')}… 직선 급송이면 ${if (it.linearToo) "그래도 파고듦" else "안 파고듦"}" }.toSortedMap().map { (k, v) ->
            val w = v.maxBy { it.depth }
            "${v.size}개 $k 블록: 깊이 ${Fmt.fixed3(v.minOf { it.depth })}~${Fmt.fixed3(w.depth)} mm — 가장 깊은 보기 ${w.tag.substringBefore(", approach")} | ${w.block} | ${w.nc}"
        }

        /** 갈래별 요약(사례 수 · 차이 ÷ r 범위 · 보기 글 하나). [doc] = 계산기 문서대로인가(라운드: u·(팁 벡터·u), 테이퍼: 끝 법선) */
        fun endSummary(doc: (EndDiff) -> Boolean = { it.documented }): List<String> {
            val key = Regex("""inner=(\w+), type=(\d), reverse=(\w+).*end=([^,]+), flip=(\w+)""")
            return ends.groupBy { e ->
                val m = key.find(e.tag)?.groupValues
                val shape = if (m == null) "겹테이퍼" else "${if (m[1] == "true") "내경" else "외경"} TYPE${m[2]}${if (m[3] == "true") " 역방향" else ""}" +
                    " 끝점${if (m[4] == "null") " 비움" else " 넣음"}${if (m[5] == "true") " 뒤집기" else ""}"
                "${if (doc(e)) "문서대로" else "문서에 없음"} · ${if (e.alone) "이동 없는 G40" else if (e.innerCorner) "취소가 안쪽 모서리" else "취소가 바깥 · 이어짐"} · " +
                    "${if (e.simLeft > e.calcLeft + 1e-3) "끝 꼭짓점에 살이 더 남음" else "깎인 모양 같음"} · " +
                    "${if (e.past) "계산기 끝을 지나감" else "계산기 끝 앞에서 멈춤"} · $shape"
            }.toSortedMap().map { (k, v) ->
                "${v.size}개 $k: 차이 ${Fmt.nc(v.minOf { it.ratio })}r~${Fmt.nc(v.maxOf { it.ratio })}r · 끝 꼭짓점에 남는 살 시뮬레이터 " +
                    "${Fmt.fixed3(v.minOf { it.simLeft })}~${Fmt.fixed3(v.maxOf { it.simLeft })} / 계산기 ${Fmt.fixed3(v.minOf { it.calcLeft })}~${Fmt.fixed3(v.maxOf { it.calcLeft })} mm" +
                    " — 보기 ${v.first().tag.substringBefore(", approach")} | ${v.first().nc}"
            }
        }

        fun see(d: Double, tag: String) {
            if (d > worst) {
                worst = d
                worstAt = tag
            }
        }

        fun report() = "$name: 사례 $cases · 가장 큰 차이 $worst ($worstAt) · 실패 ${fails.size} · 메모 ${notes.size}"
    }

    // ================================================================ 1. 출처 예제를 손으로 펼침 — 경로 · 사이클 타임

    /** 기대 이동 하나: 줄 · 급송인지 · 끝점(z, x 반경) · 이송 속도(mm/min, 급송 null) */
    private class Ex(val line: Int, val rapid: Boolean, val to: Pt, val mmMin: Double? = null)

    /** G41/G42 글 끝 노즈 ↔ 계산기 노즈 경로 끝: [innerCorner] = 취소가 안쪽 모서리(교점), [documented] = 차이가 u·(팁 벡터·u), [ratio] = 차이 ÷ r, [past] = 계산기 끝을 지나감 */
    /** [linearToo] = 급송을 '직선'(RapidPath.LINEAR)으로 돌려도 같은 블록(줄)이 파고드는가(꺾인 급송 탓인지 가린다 — 2026-10-28 전에는 글 전체의 가장 깊은 곳이라 다른 블록이 섞였다) */
    private class Gouge(val depth: Double, val block: String, val kind: MoveKind, val tag: String, val nc: String, val linearToo: Boolean)

    /**
     * 글의 노즈 경로가 도면선(계산기 `drawing` — 깎지 않는 참고 요소 포함)에 r 보다 가까이 간 가장 깊은 곳. 0.001 이하는 null(글 반올림).
     * 기계 원점에서 첫 G00(진입점)으로 오는 이동은 뺀다 — 그 앞 자리는 글 밖이라 시뮬레이터의 원점 가정에 달렸다.
     */
    private fun gougeOf(run: SimRun, lines: List<String>, drawFull: List<Seg>, rr: Double, tag: String, tools: Map<Int, ToolOffset>): Gouge? {
        fun deepest(r: SimRun): Pair<Double, MoveStep>? {
            val ms = moves(r)
            val firstLine = ms.firstOrNull { it.line > pre.size }?.line ?: return null
            return ms.filter { it.line > firstLine }.map { (rr - pts(it.nose).minOf { p -> distSet(p, drawFull) }) to it }.maxByOrNull { it.first }
        }
        val (d, m) = deepest(run)?.takeIf { it.first > tol } ?: return null
        val lin = moves(sim(lines, tools, ab.copy(rapidPath = RapidPath.LINEAR))).filter { it.line == m.line }
            .maxOfOrNull { rr - pts(it.nose).minOf { p -> distSet(p, drawFull) } } ?: 0.0
        return Gouge(d, lines[m.line - 1], m.kind, tag, lines.drop(pre.size).joinToString(" / "), lin > tol)
    }

    private class EndDiff(
        val innerCorner: Boolean, val documented: Boolean, val ratio: Double, val past: Boolean, val tag: String, val nc: String,
        /** 도면 끝 꼭짓점에 남는 살(mm) — G41/G42 글을 시뮬레이터로 · 계산기 노즈 경로로 */
        val simLeft: Double, val calcLeft: Double,
        /** 끝 노즈가 마지막 형상 블록 끝 법선(노즈 접점 = 그 끝점)인가 · 취소가 이동 없는 G40 인가 */
        val atNormal: Boolean, val alone: Boolean,
        /** 넣은 끝좌표 너머가 막힌 끝(척 쪽 수평 · 재료 쪽 단면, 외경 X0 빼고) — G41/G42 는 노즈 끝이 끝좌표에서 멈춘다(교점) */
        val wall: Boolean = false,
    )

    private data class Got(val line: Int, val rapid: Boolean, val end: Pt, val sec: Double)

    /** 시뮬레이터 이동을 블록 이동으로 묶는다 — 꺾인 급송의 두 토막(같은 명령 경로 · 이어짐)은 하나 */
    private fun logical(r: SimRun): List<Got> {
        val out = mutableListOf<Got>()
        var prev: MoveStep? = null
        for (m in moves(r)) {
            val p = prev
            if (p != null && p.kind == MoveKind.RAPID && m.kind == MoveKind.RAPID && p.line == m.line && p.cmd == m.cmd &&
                (p.tip.end - m.tip.start).length < 1e-12
            ) {
                val last = out.removeAt(out.lastIndex)
                out += Got(m.line, true, m.tip.end, last.sec + (m.t1 - m.t0))
            } else {
                out += Got(m.line, m.kind == MoveKind.RAPID, m.tip.end, m.t1 - m.t0)
            }
            prev = m
        }
        return out
    }

    /** 손으로 펼친 이동 목록과 하나씩(줄 · 종류 · 끝점 1e-9 · 시간 1e-9) 맞춘다. 손 계산 전체 시간을 돌려준다 */
    private fun checkExpanded(r: SimRun, exp: List<Ex>, start: Pt, tag: String): Double {
        val got = logical(r)
        assertEquals(exp.size, got.size, "$tag 이동 수 — 실제 ${got.map { "${it.line}:${if (it.rapid) "G00" else "G01"} ${xz(it.end)}" }}")
        var at = start
        var total = 0.0
        for ((k, pair) in exp.zip(got).withIndex()) {
            val (e, g) = pair
            val sec = if (e.rapid) rapidSec(at, e.to) else (e.to - at).length / e.mmMin!! * 60
            assertEquals(e.line, g.line, "$tag ${k}번 이동의 줄 (기대 ${xz(e.to)})")
            assertEquals(e.rapid, g.rapid, "$tag ${k}번 이동 종류 (${xz(e.to)})")
            assertTrue((e.to - g.end).length < 1e-9, "$tag ${k}번 이동 끝 — 기대 ${xz(e.to)} 실제 ${xz(g.end)}")
            assertEquals(sec, g.sec, 1e-9, "$tag ${k}번 이동 시간 (${xz(e.to)})")
            total += sec
            at = e.to
        }
        return total
    }

    /**
     * S 4-92 그림 4-2 G90(글은 CycleTest 와 같다). 손으로: 시작점 A = X94 Z62(반경 47).
     * 패스 k(X80 · X70 · X60 → 반경 40 · 35 · 30): 급송 X 47 → x_k, 이송 Z 62 → 20(42 mm), 이송 X x_k → 47, 급송 Z 20 → 62.
     * 이송 = F0.3 × S1000 = 300 mm/min. 시간 = 원점(X200 Z100) → A 0.159 초(X 53/20000 분) + Σ(급송 X (7 · 12 · 17)/20000 분
     * + 이송 (42 + 7 · 12 · 17)/300 분 + 급송 Z 42/24000 분) = 0.159 + 0.108 + 32.4 + 0.315 = **32.982 초**.
     */
    @Test
    fun siemens92G90ExpandedByHand() {
        val r = sim(listOf("T0101", "G97 S1000 M03", "G99", "N10 G00 X94. Z62.", "N11 G90 X80. W-42. F0.3", "N12 X70.", "N13 X60.", "N14 G00", "M30"))
        assertNull(r.stop, "${r.stop}")
        val exp = mutableListOf(Ex(4, true, Pt(62.0, 47.0)))
        for ((line, x) in listOf(5 to 40.0, 6 to 35.0, 7 to 30.0)) {
            exp += Ex(line, true, Pt(62.0, x))
            exp += Ex(line, false, Pt(20.0, x), 300.0)
            exp += Ex(line, false, Pt(20.0, 47.0), 300.0)
            exp += Ex(line, true, Pt(62.0, 47.0))
        }
        val total = checkExpanded(r, exp, Pt(100.0, 100.0), "S 4-92")
        assertEquals(32.982, total, 1e-9, "손 계산 합")
        assertEquals(total, r.totalSec, 1e-9, "사이클 타임")
    }

    /**
     * S 4-109 그림 4-22 G71 + G70(글은 CycleTest 와 같다 — 체계 B 의 G90 은 뺌). 설계 1.6.3 · 12.5 의 모형을 손으로 펼친다.
     * A = Z171 X142(반경 71), Δd 4 · e 1, 여유 (W2, U4/2 = 2). 윤곽 A′(Z171 X40) → … → B(Z40 X140), 윤곽′ = 윤곽 + (2, 2).
     * 층(반경) 67 · 63 · … · 23 + 마지막 22(= 20 + 2). 층 끝 Z = 윤곽′ 이 층을 넘는 첫 점:
     *  67 · 63 · 59 · 55 → (62, 52)–(42, 72) 위 z = 62 − (L − 52): 47 · 51 · 55 · 59 / 51 · 47 · 43 · 39 · 35 → (92, 32)–(82, 52) 위 z = 92 − (L − 32)/2:
     *  82.5 · 84.5 · 86.5 · 88.5 · 90.5 / 31 · 27 · 23 → (142, 22)–(112, 32) 위 z = 142 − 3(L − 22): 115 · 127 · 139 / 22 → 수평 토막(높이 22)이라 그 끝 142.
     * 층마다: 급송 Z171 로 절입 → 이송 층 끝 → 45° 이송 후퇴(+1, +1) → 급송 Z171. 여유 패스: 급송 (173, 22) → 윤곽′ 이송 → 급송 A.
     * G71 이송 = F0.3 × S550 = 165 mm/min, G70 = F0.15 × S700 = 105 mm/min(P~Q 의 F · S — S 4-108 주 1).
     */
    @Test
    fun siemens109ExpandedByHand() {
        val r = sim(
            listOf(
                "T0101", "G97 S1000 M03", "G99",
                "N010 G00 X200.0 Z220.0", "N011 X142.0 Z171.0", "N012 G71 U4.0 R1.0", "N013 G71 P014 Q020 U4.0 W2.0 F0.3 S550",
                "N014 G00 X40.0 F0.15 S700", "N015 G01 Z140.0", "N016 X60.0 Z110.0", "N017 Z90.0", "N018 X100.0 Z80.0", "N019 Z60.0",
                "N020 X140.0 Z40.0", "N021 G70 P014 Q020", "N022 G00 X200. Z220.", "M30",
            ),
        )
        assertNull(r.stop, "${r.stop}")
        val exp = mutableListOf(Ex(4, true, Pt(220.0, 100.0)), Ex(5, true, Pt(171.0, 71.0)))
        val levels = listOf(67.0, 63.0, 59.0, 55.0, 51.0, 47.0, 43.0, 39.0, 35.0, 31.0, 27.0, 23.0, 22.0)
        val ends = listOf(47.0, 51.0, 55.0, 59.0, 82.5, 84.5, 86.5, 88.5, 90.5, 115.0, 127.0, 139.0, 142.0)
        for ((lv, ze) in levels.zip(ends)) {
            exp += Ex(7, true, Pt(171.0, lv))
            exp += Ex(7, false, Pt(ze, lv), 165.0)
            exp += Ex(7, false, Pt(ze + 1, lv + 1), 165.0)
            exp += Ex(7, true, Pt(171.0, lv + 1))
        }
        exp += Ex(7, true, Pt(173.0, 22.0))
        for (p in listOf(Pt(142.0, 22.0), Pt(112.0, 32.0), Pt(92.0, 32.0), Pt(82.0, 52.0), Pt(62.0, 52.0), Pt(42.0, 72.0))) exp += Ex(7, false, p, 165.0)
        exp += Ex(7, true, Pt(171.0, 71.0))
        exp += Ex(8, true, Pt(171.0, 20.0))
        for ((line, p) in listOf(9 to Pt(140.0, 20.0), 10 to Pt(110.0, 30.0), 11 to Pt(90.0, 30.0), 12 to Pt(80.0, 50.0), 13 to Pt(60.0, 50.0), 14 to Pt(40.0, 70.0))) {
            exp += Ex(line, false, p, 105.0)
        }
        exp += Ex(15, true, Pt(171.0, 71.0))
        exp += Ex(16, true, Pt(220.0, 100.0))
        val total = checkExpanded(r, exp, Pt(100.0, 100.0), "S 4-109")
        assertEquals(total, r.totalSec, 1e-9, "사이클 타임")
        println("S 4-109 손 계산 사이클 타임 ${Fmt.fixed3(total)} 초 = 시뮬레이터 ${Fmt.fixed3(r.totalSec)} 초")
    }

    /**
     * H 107~108 O00110 G75 여러 자리 홈(Haas 1줄 `G75 X1.75 Z-1.5 I0.05 K0.2 F0.005` → FANUC 2줄 P50 Q200, 쪼기 되돌아감 e 는
     * Haas 설정 22 라 값이 없어 0.02 로 정함 · `G28` → `G28 U0. W0.`). 손으로: 시작 A = Z−0.75 X2.1(반경 1.05).
     * 자리 Z −0.75 · −0.95 · −1.15 · −1.35 · −1.5(마지막 간격만 짧다 — H 106), 깊이(반경) 0.05 · 0.10 · 0.15 · 0.175(마지막 짧다).
     * 한 자리: 이송 1.00 → 급송 1.02 → 이송 0.95 → 급송 0.97 → 이송 0.90 → 급송 0.92 → 이송 0.875 → 급송 1.05.
     * 이송 길이 0.05 + 0.07 + 0.07 + 0.045 = 0.235 × 5 자리, F0.005 × S960 = 4.8 mm/min → 14.6875 초.
     */
    @Test
    fun haas108G75ExpandedByHand() {
        val r = sim(
            listOf(
                "N1 G28 U0. W0.", "N2 T505 (.25 WIDE O.D. GROOVE TOOL)", "N3 G97 S960 M03", "N4 G54 G00 X2.1 Z0.1 M08", "N5 Z-0.75",
                "G75 R0.02", "N6 G75 X1.75 Z-1.5 P50 Q200 F0.005", "N7 M09", "N8 G28 U0. W0.", "N9 M30",
            ),
        )
        assertNull(r.stop, "${r.stop}")
        val exp = mutableListOf(Ex(4, true, Pt(0.1, 1.05)), Ex(5, true, Pt(-0.75, 1.05)))
        val f = 0.005 * 960
        for ((k, z) in listOf(-0.75, -0.95, -1.15, -1.35, -1.5).withIndex()) {
            if (k > 0) exp += Ex(7, true, Pt(z, 1.05))
            for ((x, back) in listOf(1.0 to 1.02, 0.95 to 0.97, 0.90 to 0.92)) {
                exp += Ex(7, false, Pt(z, x), f)
                exp += Ex(7, true, Pt(z, back))
            }
            exp += Ex(7, false, Pt(z, 0.875), f)
            exp += Ex(7, true, Pt(z, 1.05))
        }
        exp += Ex(7, true, Pt(-0.75, 1.05))
        exp += Ex(9, true, Pt(100.0, 100.0))
        val total = checkExpanded(r, exp, Pt(100.0, 100.0), "H 108 G75")
        // 이송만: 0.235 × 5 / 4.8 분
        assertEquals(0.235 * 5 / 4.8 * 60, logical(r).filter { !it.rapid }.sumOf { it.sec }, 1e-9)
        assertEquals(total, r.totalSec, 1e-9, "사이클 타임")
        assertEquals(15.2906, total, 1e-9, "손 계산 합(급송 0.29685 + 0.002125 + 0.003525 + 0.001875 + 0.001875 + 0.29685)")
    }

    private val haasGroove = listOf(
        "N301 (O.D. GROOVE)",
        "N302 G28 U0. W0.",
        "N303 T303 (.250 WIDE x .016R GROOVE TOOL)",
        "N304 G50 S2500",
        "N305 G97 S920 M03",
        "N306 G54 G00 X1.35 Z0.1 M08",
        "N307 G96 S325",
        "N308 G00 Z-1.8",
        "N309 G41 G01 X1.25 F0.02",
        "N310 X1. F0.006",
        "N311 Z-1.625 F0.003",
        "N312 G03 X1.25 Z-1.5 R0.125",
        "N313 G40 G00 X1.35",
        "N314 G97 S920",
        "N315 M09",
        "N316 G28 U0. W0.",
        "N317 M30",
    )

    /** H 74 표: 보정 3 = R .016 · 팁 4(홈 공구, '깎는 모서리가 X− Z+ 쪽' — H 76 주) */
    private val grooveTool = mapOf(3 to ToolOffset(0.016, 4))

    /**
     * H 75~76 O00075 N301~N317(홈 · G41 · 팁 4 · 오목 원호, `G28` → `G28 U0. W0.`). 손 계산(반경, r = .016, 팁 벡터 (+r, −r)):
     * - N309 시작 블록(−X, 다음 N310 도 −X — 이어짐): 노즈 (−1.816, .691) → e + n·r = (−1.784, .625), 인선 Z−1.768 X1.218.
     * - N310 → N311(−X → +Z, G41 = 진행 왼쪽 — 안쪽 모서리): 보정선 z = −1.8 + r, x = .5 + r 의 교점 (−1.784, .516) → 인선 Z−1.768 X1.000.
     * - N311 → N312(오목 원호 R.125, 중심 (−1.625, .625), 시작 접선 +Z — 이어짐): (−1.625, .516) → 인선 Z−1.609 X1.000.
     * - N312 끝 → N313 취소(+X — 이어짐): (−1.516, .625) → 인선 Z−1.5 X1.218, 보정 원호 반지름 .125 − .016 = .109.
     * - 시간: G96 S325 의 회전수 1000·325/(2π·x) 는 x ≤ .675 에서 76 600 rpm 넘음 → G50 S2500 으로 잘려 늘 2500.
     *   N309 √(.032² + .066²)/(.02·2500) 분 · N310 .109/(.006·2500) · N311 .159/(.003·2500) · N312 (.109·π/2)/(.003·2500),
     *   급송 N306 (X 99.325/20000 분 · Z 99.9/24000 → 0.297975 초) · N308 1.9/24000 분 · N313 .066/20000 분 · N316 0.297975 초.
     */
    @Test
    fun haas76GrooveCompAndTimeByHand() {
        val r = sim(haasGroove, grooveTool)
        assertNull(r.stop, "${r.stop}")
        val want = mapOf(
            9 to Pt(-1.768, 0.609), 10 to Pt(-1.768, 0.5), 11 to Pt(-1.609, 0.5), 12 to Pt(-1.5, 0.609), 13 to Pt(-1.5, 0.675),
        )
        for ((line, p) in want) {
            val g = r.ghosts.last { it.line == line }
            assertTrue((g.tip - p).length < 1e-9, "N${line + 300} 보정 좌표 기대 ${xz(p)} 실제 ${xz(g.tip)}")
        }
        // 보정 원호: 같은 중심 · 반지름 R − r
        val arc = moves(r).single { it.line == 12 }.nose as Arc
        assertTrue((arc.center - Pt(-1.625, 0.625)).length < 1e-9 && abs(radiusOf(arc) - 0.109) < 1e-9, "보정 원호 $arc")
        val sec = mapOf(
            6 to max(99.325 / 20000, 99.9 / 24000) * 60,
            8 to 1.9 / 24000 * 60,
            9 to sqrt(0.032 * 0.032 + 0.066 * 0.066) / (0.02 * 2500) * 60,
            10 to 0.109 / (0.006 * 2500) * 60,
            11 to 0.159 / (0.003 * 2500) * 60,
            12 to 0.109 * PI / 2 / (0.003 * 2500) * 60,
            13 to 0.066 / 20000 * 60,
            16 to max(99.325 / 20000, 101.5 / 24000) * 60,
        )
        for ((line, s) in sec) assertEquals(s, moves(r).filter { it.line == line }.sumOf { it.t1 - it.t0 }, 1e-9, "N${line + 300} 시간")
        assertTrue(moves(r).filter { it.line in 9..12 }.all { it.rpm0 == 2500.0 && it.rpm1 == 2500.0 }, "G96 이 G50 S2500 으로 잘림")
        assertEquals(sec.values.sum(), r.totalSec, 1e-9, "사이클 타임")
        println("H 76 홈: 손 계산 사이클 타임 ${sec.values.sum()} 초")
    }

    /**
     * H 74~76 O00075 전체(원문 N111 의 Haas 1줄 G71 … D0.1 은 P 블록에 X · Z 가 다 있는 형식 II 라 G70 P112 Q124 로 바꿔 P~Q 를 다듬질로 돌림,
     * `G28` → `G28 U0. W0.`). G42 · r .0312 · 팁 3 의 보정 좌표를 손으로(닫힌 식 ΔZ = ΔX = r(1 − tan 22.5°) — calc-design '일반 해법'):
     * - N112 시작(G00 −X) → N113(−Z, 안쪽): 보정선 z = .1 − r, x = .275 + r 의 교점 → 인선 Z .1 − 2r = .0376, X .55
     * - N113 → N114(+X, 안쪽): 교점 (r, .275 + r) → 인선 Z0 X.55
     * - N114 → N115(45° 면취, 바깥 45°): 인선 Z0 X 2(.325 − r(1 − tan 22.5°))
     * - N115 → N116(−Z, 바깥 45°): 인선 Z −.05 − r(1 − tan 22.5°) X.75
     * - N116 → N117(G02 R.25 오목, 이어짐): 노즈 (−.75, .375 + r) → 인선 Z −.75 − r X.75
     * - N117 → N118(원호 끝 +X → −Z, 바깥 90°): 보정 원(중심 (−.75, .625), .25 − r)과 x = .625 + r 의 교점 → 인선 Z −.75 − √((.25 − r)² − r²) − r X1.25
     */
    @Test
    fun haas75FullProgramComp() {
        val text = listOf(
            "%", "O00075", "(GENERAL TNC EXAMPLE)",
            "N101 (ROUGH O.D.)", "N102 G28 U0. W0.", "N103 T101 (55 Deg. O.D. TOOL x .0312 TNR) (Select tool 1, offset 1)",
            "N104 G50 S2500", "N105 G97 S591 M03", "N106 G54 G00 X2.1 Z0.1 M08", "N107 G96 S325", "N108 Z0.005", "N109 G01 X-0.063 F0.01",
            "N110 G00 X2.1 Z0.1", "N111 G70 P112 Q124",
            "N112 G42 G00 X0.55 Z0.1", "N113 G01 Z0. F0.004", "N114 X0.65", "N115 X0.75 Z-0.05", "N116 Z-0.75", "N117 G02 X1.25 Z-1. R0.25",
            "N118 G01 Z-1.5", "N119 Z-1.72 X1. F0.006", "N120 G01 Z-2.5", "N121 G02 X1.25 Z-2.625 R0.125", "N122 G01 Z-3.5 F0.004",
            "N123 X2. Z-3.75 F0.008", "N124 G40 G00 X2.1", "N125 G97 S591", "N126 M09", "N127 G28 U0. W0.", "N128 M01",
            "N201 (FINISH O.D.)", "N202 G28 U0. W0.", "N203 T202 (55 Deg. O.D. TOOL x .0312 TNR) (Select tool 2, offset 2)", "N204 G50 S2500",
            "N205 G97 S1655 M03", "N206 G54 G00 X0.75 Z0.1 M08", "N207 G96 S325", "N208 Z0.", "N209 G01 X-0.063 F0.01", "N210 G00 X2.1 Z0.1",
            "N211 G70 P112 Q124", "N212 G97 S591", "N213 M09", "N214 G28 U0. W0.", "N215 M01",
        ) + haasGroove + "%"
        val tools = mapOf(1 to ToolOffset(0.0312, 3), 2 to ToolOffset(0.0312, 3)) + grooveTool
        val r = sim(text, tools)
        assertNull(r.stop, "${r.stop}")
        fun line(n: String) = text.indexOfFirst { it.startsWith("$n ") } + 1
        val rr = 0.0312
        val k = rr * (1 - tan(PI / 8))
        val want = mapOf(
            "N112" to Pt(0.1 - 2 * rr, 0.275),
            "N113" to Pt(0.0, 0.275),
            "N114" to Pt(0.0, 0.325 - k),
            "N115" to Pt(-0.05 - k, 0.375),
            "N116" to Pt(-0.75 - rr, 0.375),
            "N117" to Pt(-0.75 - sqrt((0.25 - rr) * (0.25 - rr) - rr * rr) - rr, 0.625),
        )
        for ((n, p) in want) {
            val g = r.ghosts.last { it.line == line(n) }
            assertTrue((g.tip - p).length < 1e-9, "$n 보정 좌표 기대 ${xz(p)} 실제 ${xz(g.tip)}")
        }
        // P~Q 는 세 번 돈다: G70 다음 블록은 G70 블록 바로 다음(FF 4.2.4 · F0 13.2.4 주 2 · H 82 'returns to the block following the G70 call')이라
        // N111 G70 뒤 N112~N124 를 보통 블록으로 한 번 더, 그리고 N211 G70. 원문 N111 의 G71 이면 Q 다음(FF 4.2.1)으로 가 두 번이다 — 바꾼 글의 흐름(설계 13절)
        assertEquals(3, moves(r).count { it.line == line("N116") })
        assertTrue(r.issues.none { it.level == IssueLevel.STOP }, "${r.issues}")
    }

    /**
     * H 72 O00072 '보정 취소를 잘못 한 예'(TNR .0156 팁 2 보링바, `G28` → `G28 U0. W0.`): N9 X0.33 은 0.0138(직경) 만 움직여 H 가 말하는
     * 2r(직경 0.031)보다 짧다 → 취소 블록 N10(−X, 이어짐)의 보정 자리 e + n·r 이 N9 의 안쪽 모서리 교점보다 뒤(+X 0.0087)라 공구가 거꾸로 간다.
     * H 는 '깎아 들어간다(overcutting)', 시뮬레이터는 COMP_INTERFERENCE 로 N9 줄에서 멈춰야(틀린 경로를 그리지 않음).
     * 2r 넘게(X0.31) 움직이면 멈추지 않는다.
     */
    @Test
    fun haas72BadDeparture() {
        val text = listOf(
            "N1 G28 U0. W0.", "N2 T808 (BORING BAR .015 TNR)", "N3 G97 S2400 M03", "N4 G54 G00 X0.49 Z0.1", "N5 G41 G01 X0.5156 F0.02",
            "N6 Z-0.05 F0.004", "N7 X0.3438 Z-0.25", "N8 Z-0.5", "N9 X0.33", "N10 G40 G00 X0.25", "N11 Z0.05", "N12 G28 U0. W0.", "N13 M30",
        )
        val tools = mapOf(8 to ToolOffset(0.0156, 2))
        val r = sim(text, tools)
        val st = assertNotNull(r.stop, "멈춰야 한다 ${r.issues}")
        assertEquals(IssueCode.COMP_INTERFERENCE, st.code, "$st")
        assertEquals(9, st.line, "$st")
        assertTrue(moves(r).any { it.line == 8 }, "N8 까지는 그린다")
        val ok = sim(text.map { if (it == "N9 X0.33") "N9 X0.31" else it }, tools)
        assertNull(ok.stop, "${ok.stop}")
        // N8 → N9 안쪽 모서리 교점: 보정선 x = .1719 − r, z = −.5 + r
        val n8 = ok.ghosts.last { it.line == 8 }
        assertTrue((n8.nose - Pt(-0.5 + 0.0156, 0.1719 - 0.0156)).length < 1e-9, "N8 끝 노즈 ${n8.nose}")
    }

    /**
     * H 36~38 O00042 · O00043 · O00044(Haas G01 의 R · K · A 모서리): 원문 그대로면 N1 의 G53 에서 WORK_OFFSET,
     * G53 줄을 `G28 U0. W0.` 로 바꾸면 R · K 는 G01_CORNER_IKR(N8), A 는 HAAS_FORMAT(N9) — 모두 그 줄 번호로 멈춰야.
     */
    @Test
    fun haas36to38CornerFormats() {
        val head = listOf("N1 G53 G00 X0. Z0. T0", "N2 T101 (O.D. TOOL x .031 TNR)", "N3 G50 S3000", "N4 G97 S3000 M03", "N5 G54 G00 X0.3 Z0.1 M08", "N6 G96 S390", "N7 G42 G01 Z0. F0.01")
        val tail = listOf("N9 G01 Z-0.5", "N10 G01 X0.75 R-0.05", "N11 G01 Z-1.0 R0.05", "N12 G01 X1.25 R-0.05", "N13 G01 Z-1.5", "N14 G40 G00 U0.01 Z0.1 M09", "N15 G53 G00 X0. Z0. T0", "N16 M30")
        val o42 = head + "N8 G01 X0.5 R-0.05" + tail
        val o43 = head + "N8 G01 X0.5 K-0.05" + tail.map { it.replace(" R-0.05", " K-0.05").replace(" R0.05", " I0.05") }
        val o44 = listOf(
            "N1 G53 G00 X0. Z0. T0", "N2 T101 (O.D. TOOL x .031 TNR)", "N3 G50 S2800", "N4 G97 S1490 M03", "N5 G54 G00 X1.25 Z0.1 M08", "N6 G96 S390",
            "N7 G42 G01 Z0. F0.01", "N8 G01 X1.375 (Start point)", "N9 G01 X1.5 A170. (10 Degree Angle using A)", "N10 Z-0.5",
            "N11 G40 G00 U0.01 Z0.1 M09", "N12 G53 G00 X0. Z0. T0", "N13 M30",
        )
        val tools = mapOf(1 to ToolOffset(0.031, 3))
        val fails = mutableListOf<String>()
        fun expect(name: String, text: List<String>, code: IssueCode, line: Int) {
            val r = try {
                sim(text, tools)
            } catch (e: Throwable) {
                fails += "$name 예외 $e"
                return
            }
            val st = r.stop
            if (st == null || st.code != code || st.line != line) fails += "$name 기대 $code ${line}줄, 실제 $st"
        }
        for ((name, t) in listOf("O00042" to o42, "O00043" to o43, "O00044" to o44)) expect("$name 원문", t, IssueCode.WORK_OFFSET, 1)
        fun noG53(t: List<String>) = t.map { if (it.startsWith("N1 G53")) "N1 G28 U0. W0." else it }
        expect("O00042", noG53(o42), IssueCode.G01_CORNER_IKR, 8)
        expect("O00043", noG53(o43), IssueCode.G01_CORNER_IKR, 8)
        expect("O00044", noG53(o44), IssueCode.HAAS_FORMAT, 9)
        assertTrue(fails.isEmpty(), fails.joinToString("\n"))
    }

    /**
     * H 75 O00075 의 황삭 줄: 원문(Haas 1줄 `G71 … D0.1`)은 HAAS_FORMAT, FANUC 2줄로 바꾸면 P 블록 `G42 G00 X0.55 Z0.1` 이 형식 II 라 G71_TYPE2(N112 줄),
     * P 블록을 `G42 G00 X0.55` 로(시작점 Z 가 같다) 하면 N119(X1.25 → X1.0 되돌아감 — H 가 '포켓'이라 형식 II 를 쓴 까닭)에서 G71_NOT_MONOTONE.
     */
    @Test
    fun haas75RoughFormats() {
        val head = listOf(
            "N103 T101", "N104 G50 S2500", "N105 G97 S591 M03", "N106 G54 G00 X2.1 Z0.1 M08", "N107 G96 S325", "N110 G00 X2.1 Z0.1",
        )
        val pq = listOf(
            "N112 G42 G00 X0.55 Z0.1", "N113 G01 Z0. F0.004", "N114 X0.65", "N115 X0.75 Z-0.05", "N116 Z-0.75", "N117 G02 X1.25 Z-1. R0.25",
            "N118 G01 Z-1.5", "N119 Z-1.72 X1. F0.006", "N120 G01 Z-2.5", "N121 G02 X1.25 Z-2.625 R0.125", "N122 G01 Z-3.5 F0.004",
            "N123 X2. Z-3.75 F0.008", "N124 G40 G00 X2.1", "N125 M30",
        )
        val tools = mapOf(1 to ToolOffset(0.0312, 3))
        val haas = head + "N111 G71 P112 Q124 U0.02 W0.005 D0.1 F0.012" + pq
        val r1 = sim(haas, tools)
        assertEquals(IssueCode.HAAS_FORMAT, r1.stop?.code, "${r1.stop}")
        assertEquals(7, r1.stop?.line)
        val fanuc = head + listOf("G71 U0.1 R0.05", "N111 G71 P112 Q124 U0.02 W0.005 F0.012") + pq
        val r2 = sim(fanuc, tools)
        assertEquals(IssueCode.G71_TYPE2, r2.stop?.code, "${r2.stop}")
        assertEquals(9, r2.stop?.line)
        val r3 = sim(fanuc.map { if (it.startsWith("N112")) "N112 G42 G00 X0.55" else it }, tools)
        assertEquals(IssueCode.G71_NOT_MONOTONE, r3.stop?.code, "${r3.stop}")
        assertEquals(16, r3.stop?.line, "N119 줄")
        assertEquals(listOf("X"), r3.stop?.args)
    }

    // ================================================================ 2. 계산기 글 → 시뮬레이터 = 계산기 보정 경로

    private fun TaperInput.flipped() =
        copy(x1 = x2, z1 = z2, x2 = x1, z2 = z1, cornerR1 = cornerR2, cornerR2 = cornerR1, start = end, end = start, flip = !flip)

    private fun DoubleTaperInput.flipped() =
        copy(x1 = x3, z1 = z3, x3 = x1, z3 = z1, cornerR1 = cornerR3, cornerR3 = cornerR1, start = end, end = start, flip = !flip)

    /** InteropTest 와 같은 사례 묶음(외경/내경 × 정/역 × TYPE 1~5 × 3각 × r 2 × 모서리 R 4 × 시작 · 끝점 × 뒤집기, 겹테이퍼) */
    private fun taperCases(): List<Triple<String, Double, TaperResult>> {
        val out = mutableListOf<Triple<String, Double, TaperResult>>()
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (type in 1..5) {
            for (deg in listOf(20.0, 45.0, 70.0)) for (r in listOf(0.4, 0.8)) for ((r1, r2) in listOf(0.0 to 0.0, 1.5 to 0.0, 0.0 to 1.5, 2.0 to 0.6)) {
                val up = if (inner == (type == 5)) 1.0 else -1.0
                val x2 = Fmt.nc(60 + up * 2 * 6 * tan(deg * PI / 180)).toDouble()
                val inp = TaperInput(inner, type, reverse, r, null, 60.0, 0.0, x2, -6.0, cornerR1 = r1, cornerR2 = r2)
                val right = if (type <= 2) inp.copy(start = 60 - up * 12) else inp.copy(start = 6.0)
                val left = if (type == 2 || type == 4) inp.copy(end = Fmt.nc(x2 + up * 12).toDouble()) else inp.copy(end = -12.0)
                for (i in listOf(inp, right, left).flatMap { listOf(it, it.flipped()) }) out += Triple(i.toString(), r, TaperCalc.calc(i))
            }
        }
        val shapes = listOf(listOf(40.0, 50.0, 40.0), listOf(50.0, 40.0, 50.0), listOf(40.0, 46.0, 52.0))
        for (inner in listOf(false, true)) for (reverse in listOf(false, true)) for (x in shapes) for (rc in listOf(0.0, 1.0)) {
            val inp = DoubleTaperInput(inner, reverse, 0.4, null, x[0], 0.0, x[1], -5.0, x[2], -10.0, cornerR1 = rc, cornerR2 = rc, cornerR3 = rc)
            for (i in listOf(inp, inp.copy(start = 4.0), inp.copy(end = -14.0), inp.copy(start = 4.0, end = -14.0)).flatMap { listOf(it, it.flipped()) }) {
                out += Triple(i.toString(), 0.4, TaperCalc.calcDouble(i))
            }
        }
        return out
    }

    /** 깎는 요소의 노즈 중심 경로(시작 · 끝 요소를 깎지 않으면 그 '참고' 보정선을 뺌, 길이 0 뺌) */
    private fun cutOf(segs: List<Seg>, fromStart: Boolean, toEnd: Boolean): List<Seg> {
        var l = segs
        if (!fromStart) l = l.drop(1)
        if (!toEnd) l = l.dropLast(1)
        return l.filter { lenOf(it) > 1e-9 }
    }

    /**
     * 직접 보정(G40) 글: 첫 G01(진입 → 첫 깎는 점)을 뺀 이송 노즈 경로가 계산기 노즈 경로 위(앞 → 뒤), 계산기 노즈 경로가 이송 노즈 경로 위(뒤 → 앞).
     * 돌려준 값 = 가장 큰 차이(멈추면 null)
     */
    private fun checkDirect(tag: String, nc: List<String>, tools: Map<Int, ToolOffset>, cen: List<Seg>, t: Tally, drawFull: List<Seg>? = null): Double? {
        t.cases++
        val d = sim(pre + nc, tools)
        if (d.stop != null) {
            t.fails += "$tag 직접 보정 글이 멈춤 ${d.stop}\n${nc.joinToString("\n")}"
            return null
        }
        if (drawFull != null) gougeOf(d, pre + nc, drawFull, tools.values.first().noseR!!, tag, tools)?.let { t.gouges += it }
        val feeds = moves(d).filter { it.kind == MoveKind.FEED }
        if (feeds.isEmpty()) {
            t.fails += "$tag 이송 없음"
            return null
        }
        val first = feeds.first().line
        val fwd = feeds.filter { it.line != first }.flatMap { pts(it.nose) }.maxOfOrNull { distSet(it, cen) } ?: 0.0
        val cov = cen.flatMap { pts(it) }.maxOf { p -> feeds.minOf { dist(p, it.nose) } }
        val worst = max(fwd, cov)
        t.see(worst, tag)
        if (worst > tol) t.fails += "$tag 직접 보정 경로 차이(앞 → 뒤 $fwd, 뒤 → 앞 $cov)\n${nc.joinToString("\n")}"
        return worst
    }

    /**
     * G41/G42 글을 돌린 노즈 경로를 계산기 노즈 경로와 맞춘다. [startLine] = G41/G42 시작 블록 줄, [cancelLine] = G40 취소 블록 줄(둘 다 글 전체 줄 번호).
     * - 시작 블록 끝 노즈 · 형상 블록 노즈 경로(앞 → 뒤): 계산기 경로 또는 그 첫 요소를 뒤로 · 끝 요소를 앞으로 늘인 반직선 위(시작 · 끝 블록은
     *   설계상 같은 보정선 위 다른 점에 선다 — calc-design '진입점과 NC').
     * - 끝 노즈 = 따로 짠 제어기 모형(취소가 안쪽 모서리면 두 보정선의 교점, 바깥 · 이어짐이면 끝 법선 — FF 5.4.4 · 설계 3.2)이어야 하고,
     *   계산기 끝과 0.001 넘게 다르면 실패 — 단 calc-design 이 말한 끝 차이 u·(팁 벡터·u) 는 '문서대로'(실패 아님). 차이는 [Tally.ends] 에 모은다.
     * - 뒤 → 앞: 계산기 경로 가운데 시작 블록 끝 앞 · 끝 노즈 뒤를 뺀 곳이 시뮬레이터 노즈 경로 위.
     * 취소가 이동 없는 G40 한 줄이면([cancelLine] 이 그 줄) 끝 노즈 = 끝 법선(FF 5.4.4 그림 5.4.4(e))이고 취소 이동은 그 다음 줄들.
     * [vEnd0] = 끝 꼭짓점(깎는 도면의 끝). null 이면 마지막 형상 블록의 끝점
     */
    private fun checkMachine(
        tag: String, lines: List<String>, tools: Map<Int, ToolOffset>, cen: List<Seg>, tv: Pt, startLine: Int, cancelLine: Int,
        t: Tally, drawFull: List<Seg>? = null, vEnd0: Pt? = null, endWall: Boolean = false, onStop: (SimRun) -> String?,
    ): SimRun? {
        t.cases++
        val m = sim(lines, tools)
        if (m.stop != null) {
            val note = onStop(m)
            if (note != null) t.notes += note else t.fails += "$tag G41/G42 글이 멈춤 ${m.stop}\n${lines.joinToString("\n")}"
            return null
        }
        val ms = moves(m)
        val startMv = ms.filter { it.line == startLine }
        val shape = ms.filter { it.line in (startLine + 1) until cancelLine }
        if (startMv.isEmpty() || shape.isEmpty()) {
            t.fails += "$tag 시작 · 형상 이동이 없음\n${lines.joinToString("\n")}"
            return null
        }
        val startNose = startMv.last().nose.end
        val endNose = shape.last().nose.end
        val rays = listOf(cen.first().start to tanStart(cen.first()) * -1.0, cen.last().end to tanEnd(cen.last()))
        // 띄운 진입(lead)은 노즈가 계산기 첫 점 바로 앞(TaperCalc.LEAD_PAST = 0.01 — 깎지 않는 앞 요소 보정선 위)에서 선다 — 그 안은 경로로 본다
        fun dExt(p: Pt) = if ((p - cen.first().start).length <= 0.0105) 0.0 else min(distSet(p, cen), rays.minOf { (q, u) -> rayDist(p, q, u) })
        val fwd = (listOf(startNose) + shape.flatMap { pts(it.nose) }).maxOf { dExt(it) }
        // 끝: 따로 짠 제어기 모형(설계 3.2 · FANUC 보정 취소 — 마지막 보정 블록과 취소 블록의 이음점)으로 기대 노즈를 만든다.
        // 안쪽 모서리 = 두 보정선의 교점, 바깥 · 접해서 이어짐 = 마지막 요소 끝 법선(노즈 접점이 도면 끝 — calc-design '두 프로그램의 끝 블록은 일부러 다르다').
        val endD: Double
        val lastCmd = shape.last().cmd
        // 이동 없는 G40: 취소 블록은 움직이지 않고 다음 이동이 벡터를 지운다
        val alone = lines[cancelLine - 1].trim() == "G40"
        val cancelCmd = ms.firstOrNull { it.line == (if (alone) cancelLine + 1 else cancelLine) }?.cmd
        if (lastCmd == null || cancelCmd == null) {
            t.fails += "$tag 마지막 형상 · 취소 블록의 명령 경로가 없음\n${lines.joinToString("\n")}"
            return null
        }
        val side = if (lines[startLine - 1].contains("G41")) 1.0 else -1.0
        val rr = shape.last().r
        val tA = tanEnd(lastCmd)
        val tB = unitOf(cancelCmd.end - cancelCmd.start)
        fun toolN(tg: Pt) = if (side > 0) Pt(-tg.x, tg.z) else Pt(tg.x, -tg.z)
        val e = lastCmd.end
        val turn = tA cross tB
        // 시뮬레이터와 같은 '접해서 이어짐' 한도(설계 11.4: sin θ < min(1e-3, 0.0005/r)) — 그 안에서는 두 규칙이 r·θ ≤ 0.0005 차이
        val inner = !alone && turn * side > min(1e-3, 0.0005 / rr)
        val modelTol = if (abs(turn) < 2e-3) 6e-4 else 1e-6
        val want: Pt = if (!inner) {
            e + toolN(tA) * rr
        } else {
            // 취소 직선의 보정선: e + nB·r 을 지나고 방향 tB
            val q = e + toolN(tB) * rr
            when (lastCmd) {
                is Line -> {
                    val pA = e + toolN(tA) * rr
                    val den = tA cross tB
                    pA + tA * (((q - pA) cross tB) / den)
                }
                is Arc -> {
                    val c = lastCmd.center
                    val rho = (e + toolN(tA) * rr - c).length
                    val foot = q + tB * ((c - q) dot tB)
                    val h = (c - foot).length
                    val s = sqrt(max(0.0, rho * rho - h * h))
                    val near = e + (toolN(tA) + toolN(tB)) * (rr / 2)
                    listOf(foot + tB * s, foot - tB * s).minBy { (it - near).length }
                }
            }
        }
        val model = (endNose - want).length
        val toCalc = (endNose - cen.last().end).length
        if (model > modelTol) {
            t.fails += "$tag 끝 노즈가 제어기 모형과 다름(${if (inner) "안쪽 교점" else "끝 법선"} 기대 $want, 실제 $endNose, 차이 $model)\n${lines.joinToString("\n")}"
        }
        // 계산기 문서(calc-design '진입점과 NC' 끝 줄 · TaperCalcTest): 끝 점만 (G41/G42) − (직접 보정) = u·(팁 벡터·u) — 그 차이는 '문서대로'
        val uEnd = tanEnd(cen.last())
        val documented = (endNose - (cen.last().end + uEnd * (tv dot uEnd))).length <= tol
        endD = if (documented) 0.0 else toCalc
        // 깎인 모양: 도면(형상 블록의 명령 경로) 끝 꼭짓점까지 노즈 경로(취소 이동 포함)의 거리 − r = 거기 남는 살.
        // 시작 · 취소 이동의 노즈가 도면에 r 보다 가까이 가면 파고듦.
        val drawing = shape.mapNotNull { it.cmd }.filter { lenOf(it) > 1e-9 }
        val cancelMv = ms.filter { it.line >= cancelLine }
        val vEnd = vEnd0 ?: drawing.last().end
        val simLeft = (shape + cancelMv).flatMap { pts(it.nose) }.minOf { (it - vEnd).length } - rr
        val calcLeft = cen.flatMap { pts(it) }.minOf { (it - vEnd).length } - rr
        // 막힌 끝: 노즈(끝 블록 · 취소 · 후퇴)가 끝좌표 너머(끝 요소 방향)로 가지 않는다 — calc-design '넣은 시작·끝점 너머로 넘지 않기'(검증 2026-10-29)
        if (endWall) {
            val uE = tanEnd(cen.last())
            val beyond = (shape + cancelMv).flatMap { pts(it.nose) }.maxOf { it dot uE } + rr - (vEnd dot uE)
            if (beyond > tol) t.fails += "$tag G41/G42 노즈가 막힌 끝좌표 너머로 ${Fmt.fixed3(beyond)}\n${lines.joinToString("\n")}"
        }
        if (drawFull != null) gougeOf(m, lines, drawFull, rr, tag, tools)?.let { t.gouges += it }
        if (toCalc > tol) {
            val past = param(cen.last(), endNose) > 1 + 1e-9
            val atNormal = (endNose - (e + toolN(tA) * rr)).length <= tol
            t.ends += EndDiff(inner, documented, toCalc / rr, past, tag, lines.drop(pre.size).joinToString(" / "), simLeft, calcLeft, atNormal, alone, endWall)
        }
        val p0 = param(cen.first(), startNose)
        val p1 = param(cen.last(), endNose)
        val noses = (startMv + shape).map { it.nose }
        var cov = 0.0
        for ((k, sg) in cen.withIndex()) for ((f, p) in samples(sg)) {
            if (k == 0 && f < p0 - 1e-9) continue
            if (k == cen.lastIndex && f > p1 + 1e-9) continue
            cov = max(cov, noses.minOf { dist(p, it) })
        }
        val worst = maxOf(fwd, endD, cov)
        if (max(fwd, cov) > tol) t.pathFails++
        t.see(worst, tag)
        // 끝만 다른 것(endD — 끝 노즈가 제어기 모형과는 같고 계산기 끝과 다름)은 시뮬레이터가 아니라 계산기 글의 어긋남이라 [Tally.ends] 로만 모은다.
        // 실패는 경로 가운데(fwd · cov)와 위의 제어기 모형 — 테이퍼 · 라운드 시험이 ends 를 따로 본다(설계 13절)
        if (max(fwd, cov) > tol) {
            t.fails += "$tag G41/G42 경로 차이(앞 → 뒤 $fwd, 끝 $endD, 뒤 → 앞 $cov) 시작 노즈 ${startNose} 끝 노즈 ${endNose}\n${lines.joinToString("\n")}"
        }
        return m
    }

    /**
     * 테이퍼 · 겹테이퍼 계산기의 두 글(ncG40 · ncG41G42)을 시뮬레이터로 — 노즈 중심 경로가 계산기 `center` 와 0.001 안.
     * 계산기가 이미 알린 모양은 멈춰도 메모만: '오목 R ≤ r'(CONCAVE_R_LE_NOSE)이면 COMP_ARC_SMALL(H 61), 바이트 반대(AGAINST_TOOL)면 무엇이든.
     */
    @Test
    fun taperBothProgramsFollowCalculatorPath() {
        val direct = Tally("테이퍼 직접 보정(G40)")
        val machine = Tally("테이퍼 G41/G42")
        for ((tag, r, res) in taperCases()) {
            val s = res.solution ?: continue
            val tools = mapOf(1 to ToolOffset(r, s.tip))
            val cen = cutOf(s.center.segs, s.fromStart, s.toEnd)
            if (cen.isEmpty()) {
                direct.notes += "$tag 깎는 요소 없음"
                continue
            }
            checkDirect(tag, s.ncG40, tools, cen, direct, s.drawing.segs)
            val lines = s.ncG41G42
            val iStart = lines.indexOfFirst { "G41" in it || "G42" in it }
            val iCancel = lines.indexOfFirst { "G40" in it }
            // 넣은 끝좌표 너머가 막힌 끝: 척 쪽(−Z)으로 깎아 끝난 수평, 재료 쪽(외경 X− · 내경 X+)으로 깎아 끝난 단면(외경 X0 빼고) — 이 사례는 화면 역방향(거울)이 없다
            val endSeg = s.drawing.segs.last()
            val uE = (endSeg.end - endSeg.start).unit()
            val innerCase = "inner=true" in tag
            val endWall = s.toEnd && if (abs(uE.x) >= abs(uE.z)) (uE.x < 0) != innerCase && !(!innerCase && endSeg.end.x <= 1e-9) else uE.z < 0
            // 막힌 끝에서 팁 벡터·u < r 이면 취소 앞에 보정 중 직각 후퇴 한 줄(노즈가 벽을 따라 올라감) — 그 줄부터를 취소 이동으로 본다
            val tvc = NoseComp.tipVector(s.tip, r)
            val iCut = if (endWall && (tvc dot uE) < r - 1e-9 && lines[iCancel].trim() != "G40") iCancel - 1 else iCancel
            checkMachine(
                tag, pre + lines, tools, cen, tvc, pre.size + iStart + 1, pre.size + iCut + 1, machine, s.drawing.segs,
                cutOf(s.drawing.segs, s.fromStart, s.toEnd).last().end, endWall,
            ) { m ->
                val st = m.stop!!
                when {
                    TaperWarning.CONCAVE_R_LE_NOSE in res.warnings && st.code == IssueCode.COMP_ARC_SMALL -> "오목 R < r → COMP_ARC_SMALL: $tag"
                    TaperWarning.AGAINST_TOOL in res.warnings ->
                        "바이트 반대(AGAINST_TOOL) 사례가 ${st.code} ${st.line}줄(${lines.getOrNull(st.line - pre.size - 1)})에서 멈춤: $tag | ${lines.joinToString(" / ")}"
                    else -> null
                }
            }
        }
        println(direct.report())
        println(machine.report())
        for (n in machine.notes.filter { it.startsWith("바이트") }.take(20)) println("  $n")
        // 계산기 문서(calc-design '진입점과 NC', 2026-10-28 고침): G41/G42 끝 노즈는 취소가 안쪽 모서리면 교점(= 계산기 노즈 경로 끝 — 차이 없음),
        // 바깥 · 이어짐 · 이동 없는 G40 이면 마지막 형상 블록 끝 법선(노즈 접점 = 끝 좌표 · 마지막 꺾임점). 끝 법선이 아닌 차이는 '문서에 없음'.
        // 너머가 막힌 끝은 늘 공구 쪽 직각 취소의 교점(2026-10-29 — 노즈 끝이 끝좌표에서 멈춤): 계산기(직접 보정) 노즈 경로가 끝좌표 너머로 가는 팁
        // (계산기 역방향 + 뒤집기의 역바이트 팁 등)이면 끝이 달라 끝 꼭짓점에 살이 더 남지만, 그것은 직접 보정이 벽을 파고들어 깎은 자리라 '남김' 으로 세지 않는다
        // (대신 checkMachine 이 막힌 끝 너머로 가지 않는지 본다)
        val doc: (EndDiff) -> Boolean = { it.atNormal || (it.wall && it.innerCorner) }
        val leftover = machine.ends.filter { it.simLeft > it.calcLeft + tol && !it.wall }
        println(
            "테이퍼 G41/G42 끝 노즈가 계산기 노즈 경로 끝과 0.001 넘게 다른 사례 ${machine.ends.size}(문서에 없음 ${machine.ends.count { !doc(it) }} · " +
                "끝 꼭짓점에 직접 보정보다 살이 더 남음 ${leftover.size} · 이동 없는 G40 ${machine.ends.count { it.alone }}):",
        )
        for (l in machine.endSummary(doc)) println("  $l")
        for (f in machine.fails.filter { "제어기 모형과 다름" in it }.take(10)) println("  모형 실패: " + f.lineSequence().first())
        println("  경로 가운데가 다른 사례 ${machine.pathFails}")
        for ((name, tl) in listOf("테이퍼 직접 보정(G40)" to direct, "테이퍼 G41/G42" to machine)) {
            println("$name 글의 노즈가 도면선을 0.001 넘게 파고든 사례 ${tl.gouges.size}:")
            for (l in tl.gougeSummary()) println("  $l")
        }
        assertTrue(direct.cases > 2400 && machine.cases > 2400, "사례 ${direct.cases} · ${machine.cases}")
        assertTrue(direct.fails.isEmpty(), "${direct.report()}\n" + direct.fails.take(5).joinToString("\n\n"))
        // 시뮬레이터 쪽: 경로 가운데 · 끝 노즈(따로 짠 제어기 모형)가 맞아야 한다
        assertTrue(machine.fails.isEmpty(), "${machine.report()}\n" + machine.fails.take(5).joinToString("\n\n"))
        assertTrue(direct.gouges.isEmpty(), "도면을 파고든 직접 보정 글 ${direct.gouges.size}")
        // 계산기 쪽(TaperCalc.ncG41G42 의 끝 블록 · 시작 블록 · 취소 후퇴 — 설계 13.7, 2026-10-28 고침): 깎인 모양이 직접 보정과 같고
        // (끝 꼭짓점 남김 · 도면 파고듦 없음 — 급송은 기계 처음 값 '꺾인 경로'), 끝 노즈 자리는 문서대로
        assertTrue(leftover.isEmpty(), "끝 꼭짓점에 직접 보정보다 살이 더 남는 G41/G42 글 ${leftover.size}:\n" + leftover.take(5).joinToString("\n") { "${it.tag} | ${it.nc}" })
        assertTrue(machine.ends.all(doc), "문서에 없는 끝 차이 ${machine.ends.count { !doc(it) }}:\n" + machine.endSummary(doc).joinToString("\n"))
        assertTrue(machine.gouges.isEmpty(), "도면을 파고든 G41/G42 글 ${machine.gouges.size}:\n" + machine.gougeSummary().joinToString("\n"))
    }

    /** RoundCalcTest 의 대표 18모양 */
    private val roundShapes = listOf(
        RoundInput(type = 1, mainR = 15.0, l1 = 20.0, cornerR1 = 1.0),
        RoundInput(type = 1, mainR = 15.0, l1 = 20.0, cornerR1 = 0.0),
        RoundInput(type = 1, mainR = 10.0, l1 = 6.0, cornerR1 = 2.5),
        RoundInput(type = 2, mainR = 10.0, d1 = 8.0, l1 = 5.0, cornerR1 = 1.0),
        RoundInput(type = 2, mainR = 10.0, d1 = 8.0, l1 = 5.0, cornerR1 = 0.0),
        RoundInput(type = 2, mainR = 6.0, d1 = 10.0, l1 = 3.0, cornerR1 = 1.5),
        RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 1.0),
        RoundInput(type = 3, mainR = 12.5, d1 = 20.0, cornerR1 = 0.0),
        RoundInput(type = 3, mainR = 30.0, d1 = 20.0, cornerR1 = 3.0),
        RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 1.0),
        RoundInput(type = 4, mainR = 8.0, d1 = 10.0, cornerR1 = 0.0),
        RoundInput(type = 4, mainR = 5.0, d1 = 4.0, cornerR1 = 1.5),
        RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 16.0, l1 = 6.0, cornerR1 = 1.0, cornerR2 = 2.0),
        RoundInput(type = 5, mainR = 25.0, d2 = 40.0, d1 = 16.0, l1 = 6.0, cornerR1 = 0.0, cornerR2 = 0.0),
        RoundInput(type = 5, mainR = 30.0, d2 = 50.0, d1 = 10.0, l1 = 4.0, cornerR1 = 1.2, cornerR2 = 1.5),
        RoundInput(type = 6, mainR = 20.0, d2 = 60.0, d1 = 12.0, l1 = 5.0, l2 = 10.0, cornerR1 = 1.2, cornerR2 = 1.5),
        RoundInput(type = 6, mainR = 20.0, d2 = 60.0, d1 = 12.0, l1 = 5.0, l2 = 10.0, cornerR1 = 0.0, cornerR2 = 0.0),
        RoundInput(type = 6, mainR = 15.0, d2 = 50.0, d1 = 10.0, l1 = 3.0, l2 = 20.0, cornerR1 = 1.2, cornerR2 = 1.2),
    )

    /** 3점호(수동 모드) — 외경 볼록 · 오목 홈(calc-design 의 예) · 역방향 · 내경 · 한쪽으로 오르는 호 */
    private val arc3Shapes = listOf(
        Arc3Input(false, 0.4, null, 40.0, 0.0, 46.0, -3.0, 40.0, -6.0),
        Arc3Input(false, 0.4, null, 40.0, 0.0, 35.0, -3.0, 40.0, -6.0),
        Arc3Input(false, 0.4, null, 40.0, -6.0, 46.0, -3.0, 40.0, 0.0),
        Arc3Input(true, 0.4, null, 40.0, 0.0, 45.0, -3.0, 40.0, -6.0),
        Arc3Input(true, 0.4, null, 40.0, 0.0, 36.0, -3.0, 40.0, -6.0),
        Arc3Input(false, 0.4, null, 20.0, 0.0, 30.0, -5.0, 36.0, -12.0),
    )

    /**
     * 라운드 '기계 보정' 글: 옛 화면 안내(2026-10-28 전 '보정 시작 · 취소 블록과 공구 보정 R · T 는 기계에 맞게 직접 넣습니다')대로 노즈R 0 풀이(도면 좌표) NC 의
     * 첫 G01 에 G41/G42 를 걸고(설계 9.3 InteropTest 의 라운드 줄 — 새 안내대로 쓴 시작은 [roundGuideStartLeavesNothing]), 취소는 마지막 요소를 끝 접선으로 2 mm 곧게 늘인 `G40 G01` — 이어진 취소라
     * 제어기가 끝 법선으로 보정해 노즈 접점이 도면 끝에 온다(직각으로 빼면 안쪽 모서리 교점으로 끝을 덜 깎는다 — calc-design 테이퍼 후퇴).
     */
    private fun roundMachineNc(drawn: RoundSolution, side: Side): List<String> {
        val nc = drawn.nc.toMutableList()
        val i = nc.indexOfFirst { it.startsWith("G01") }
        nc[i] = "$side ${nc[i]}"
        val last = drawn.drawing.segs.last { lenOf(it) > 1e-9 }
        val p = last.end + tanEnd(last) * 2.0
        nc += "G40 G01 X${Fmt.nc(p.xDia)} Z${Fmt.nc(p.z)}"
        return nc
    }

    private class RCase(val tag: String, val r: Double, val res: RoundResult, val drawn: RoundResult)

    /**
     * 라운드 계산기(TYPE 1~6 외경 · 내경 2~6(관통 포함) · 3점호) × r 0.4 · 0.8:
     * 직접 보정 NC(`nc`)와 위 기계 보정 글을 시뮬레이터로 — 노즈 중심 경로가 계산기 `center` 와 0.001 안.
     * 계산기가 알린 모양(오목 R ≤ r · 바이트 반대)에서 멈추면 메모만. 축에서 시작하는 외경은 기계 보정 글이 노즈를 축까지 못 보내
     * 끝(축) 소재가 남는다 — 남는 두께를 알린다(시뮬레이터가 아니라 '첫 G01 에 G42' 글의 한계).
     */
    @Test
    fun roundBothProgramsFollowCalculatorPath() {
        val direct = Tally("라운드 직접 보정(G40)")
        val machine = Tally("라운드 기계 보정(G41/G42)")
        val pips = mutableListOf<String>()
        val cases = mutableListOf<RCase>()
        for (shape in roundShapes) for (inner in listOf(false, true)) for (r in listOf(0.4, 0.8)) {
            if (inner && shape.type == 1) continue
            for (th in if (inner && shape.type >= 5) listOf(false, true) else listOf(false)) {
                val i = shape.copy(noseR = r, inner = inner, through = th)
                cases += RCase(i.toString(), r, RoundCalc.calc(i), RoundCalc.calc(i.copy(noseR = 0.0)))
            }
        }
        for (a in arc3Shapes) for (r in listOf(0.4, 0.8)) {
            val i = a.copy(noseR = r)
            cases += RCase(i.toString(), r, RoundCalc.arc3(i), RoundCalc.arc3(i.copy(noseR = 0.0)))
        }
        var solved = 0
        for (c in cases) {
            val s = c.res.solution
            if (s == null) {
                direct.notes += "${c.tag} 계산기가 풀지 않음 ${c.res.warnings}"
                continue
            }
            solved++
            val tools = mapOf(1 to ToolOffset(c.r, s.tip))
            val tv = NoseComp.tipVector(s.tip, c.r)
            val cen = s.center.segs.filter { lenOf(it) > 1e-9 }
            checkDirect(c.tag, s.nc, tools, cen, direct, s.drawing.segs)
            val dr = c.drawn.solution
            if (dr == null) {
                machine.notes += "${c.tag} 도면 좌표 풀이 없음"
                continue
            }
            val nc = roundMachineNc(dr, s.side)
            val lines = pre + nc
            val run = checkMachine(c.tag, lines, tools, cen, tv, pre.size + 2, lines.size, machine) { m ->
                val st = m.stop!!
                when {
                    RoundWarning.CONCAVE_R_LE_NOSE in c.res.warnings && st.code == IssueCode.COMP_ARC_SMALL -> "오목 R < r → COMP_ARC_SMALL: ${c.tag}"
                    RoundWarning.AGAINST_TOOL in c.res.warnings -> "바이트 반대 사례가 ${st.code} ${st.line}줄에서 멈춤: ${c.tag} | ${nc.joinToString(" / ")}"
                    else -> null
                }
            } ?: continue
            // 도면 첫 점(외경은 축 위)에 남는 소재: 노즈 경로에서 그 점까지 거리 − r
            val p0 = s.drawing.segs.first().start
            val gap = moves(run).filter { it.kind == MoveKind.FEED }.flatMap { pts(it.nose) }.minOf { (it - p0).length } - c.r
            if (gap > tol) pips += "${Fmt.fixed3(gap)} mm 남음(${xz(p0)}): ${c.tag}"
        }
        println(direct.report())
        println(machine.report())
        println("라운드 기계 보정 글이 도면 첫 점을 덜 깎는 사례 ${pips.size}:")
        for (p in pips) println("  $p")
        for (n in machine.notes) println("  메모 $n")
        for (n in direct.notes) println("  메모 $n")
        println("라운드 기계 보정 글의 끝 노즈가 계산기 노즈 경로 끝과 0.001 넘게 다른 사례 ${machine.ends.size} · 경로 가운데가 다른 사례 ${machine.pathFails}")
        for (l in machine.endSummary()) println("  $l")
        println("라운드 직접 보정(G40) 글의 노즈가 도면선을 0.001 넘게 파고든 사례 ${direct.gouges.size}:")
        for (l in direct.gougeSummary()) println("  $l")
        assertTrue(solved >= 85, "푼 사례 $solved / ${cases.size}")
        assertTrue(direct.fails.isEmpty(), "${direct.report()}\n" + direct.fails.take(5).joinToString("\n\n"))
        assertTrue(machine.fails.isEmpty(), "${machine.report()}\n" + machine.fails.take(5).joinToString("\n\n"))
        assertTrue(machine.ends.none { !it.documented }, "라운드 기계 보정 글의 끝이 계산기 끝과 다름 ${machine.ends.count { !it.documented }}:\n" + machine.endSummary().joinToString("\n"))
        assertTrue(direct.gouges.isEmpty(), "도면을 파고든 라운드 직접 보정 글 ${direct.gouges.size}")
    }

    /**
     * 라운드 기계 보정 안내(2026-10-28 고침 · 2026-10-29 판정 고침, 설계 13.7 문제 8 · 13.9): '시작은 첫 점보다 노즈R + 0.01 앞(첫 요소 · 원호면 시작 접선의
     * 반대쪽) 점에서 공구 쪽으로 Z 여유 + 노즈R × 2 떨어진 G00 → 그 점에 첫 요소와 직각으로 G42 G01 → G01 첫 점' 대로 쓰면 모든 라운드 모양(TYPE 1~6 외경 · 내경,
     * 3점호 — 오목하게 시작하는 홈 포함)의 첫 점에 남는 살이 없고 도면을 파고들지 않는다.
     * (G00 을 Z 여유만 띄우거나 첫 요소와 한 직선으로 들어오면 G40 노즈 = G00 − 팁 벡터가 첫 요소 보정선 안쪽이라 오목 시작 3점호의 첫 점 모서리를 0.025 · 0.16 mm 깎았다
     * — 검증 2026-10-29. 0.01 은 첫 점 줄의 보정 노즈 이동이 0 이 되지 않게 — TaperCalc.LEAD_PAST)
     * 옛 안내(첫 G01 = Z 로 내려오는 줄에 G42)는 [roundBothProgramsFollowCalculatorPath] 가 남는 살을 출력한다(0.166 mm 쯤 — 시작 모서리가 안쪽이라 교점).
     * 취소: 움직임 없는 `G40` 뒤 마지막 요소의 공구 쪽으로 `G01`(노즈R 의 2배 넘게 — G40 뒤 노즈 = 지령점 − 팁 벡터).
     */
    @Test
    fun roundGuideStartLeavesNothing() {
        var checked = 0
        val bad = mutableListOf<String>()
        class G(val tag: String, val r: Double, val s: RoundSolution, val dr: RoundSolution)
        val sols = mutableListOf<G>()
        for (shape in roundShapes) for (inner in listOf(false, true)) for (r in listOf(0.4, 0.8)) {
            if (inner && shape.type == 1) continue
            val i = shape.copy(noseR = r, inner = inner)
            val s = RoundCalc.calc(i).solution ?: continue
            val dr = RoundCalc.calc(i.copy(noseR = 0.0)).solution ?: continue
            sols += G(i.toString(), r, s, dr)
        }
        for (a in arc3Shapes) for (r in listOf(0.4, 0.8)) {
            val i = a.copy(noseR = r)
            val s = RoundCalc.arc3(i).solution ?: continue
            val dr = RoundCalc.arc3(i.copy(noseR = 0.0)).solution ?: continue
            sols += G(i.toString(), r, s, dr)
        }
        for (g in sols) {
            val tag = g.tag
            val r = g.r
            val s = g.s
            val dr = g.dr
            val first = s.drawing.segs.first { lenOf(it) > 1e-9 }
            val p0 = first.start
            val t0 = tanStart(first)
            val n0 = if (s.side == Side.G41) Pt(-t0.x, t0.z) else Pt(t0.x, -t0.z)
            val c = max(1.0, r)
            fun at(p: Pt) = "X${Fmt.nc(p.xDia)} Z${Fmt.nc(p.z)}"
            // 도면 좌표 NC(노즈R 0 풀이)의 첫 G01(첫 점으로 가는 줄) 뒤 줄들 = 형상
            val k = dr.nc.indexOfFirst { it.startsWith("G01") }
            val body = dr.nc.drop(k + 1)
            // 취소(안내): 움직임 없는 G40 한 줄 뒤 마지막 요소의 공구 쪽으로 G01 — 노즈 접점이 끝 점에 선다(FF 5.4.4 그림 5.4.4(e)).
            // G40 뒤 노즈 = 지령점 − 팁 벡터라 팁 방향에 따라 노즈R 의 2배까지 끝 요소 쪽으로 온다 — 그만큼(lift) 더 뺀다
            val last = dr.drawing.segs.last { lenOf(it) > 1e-9 }
            val tE = tanEnd(last)
            val nE = if (s.side == Side.G41) Pt(-tE.x, tE.z) else Pt(tE.x, -tE.z)
            val liftE = max(0.0, r + (nE dot NoseComp.tipVector(s.tip, r)))
            val cancel = listOf("G40", "G01 ${at(last.end + nE * (c + liftE))}")
            val lead = p0 - t0 * (r + 0.01)
            val nc = listOf("G00 ${at(lead + n0 * (c + 2 * r))}", "${s.side} G01 ${at(lead)}", "G01 ${at(p0)}") + body + cancel
            val tools = mapOf(1 to ToolOffset(r, s.tip))
            val run = sim(pre + nc, tools)
            checked++
            if (run.stop != null) {
                bad += "멈춤 ${run.stop}: $tag | ${nc.joinToString(" / ")}"
                continue
            }
            val gap = moves(run).filter { it.kind == MoveKind.FEED }.flatMap { pts(it.nose) }.minOf { (it - p0).length } - r
            if (gap > tol) bad += "첫 점 ${xz(p0)} 에 ${Fmt.fixed3(gap)} 남음: $tag | ${nc.joinToString(" / ")}"
            gougeOf(run, pre + nc, s.drawing.segs, r, tag, tools)?.let { bad += "파고듦 ${Fmt.fixed3(it.depth)} [${it.block}]: $tag | ${nc.joinToString(" / ")}" }
        }
        println("라운드 안내대로 쓴 시작 블록(TYPE 1~6 외경 · 내경, 3점호): 글 $checked, 문제 ${bad.size}")
        for (b in bad.take(10)) println("  $b")
        assertTrue(checked >= 60, "확인한 글 $checked")
        assertTrue(bad.isEmpty(), bad.take(5).joinToString("\n"))
    }

    // ================================================================ 3. 오류 처리 — 줄 번호 · 무작위 글

    private class Bad(val name: String, val lines: List<String>, val code: IssueCode, val line: Int?, val drawnBefore: Int? = null)

    /** 오류 글이 그 줄 번호로 멈추고(예외 없음), 그 앞까지는 그린다 */
    @Test
    fun errorsStopWithLineNumbers() {
        val head = pre + "G00 X50. Z5."
        fun one(bad: String) = head + bad + "M30"
        val cases = listOf(
            Bad("G32", one("G32 Z-10. F1.5"), IssueCode.UNSUPPORTED_G, 5, 4),
            Bad("G20", one("G20"), IssueCode.INCH, 5, 4),
            Bad("G55", one("G55 G00 X10."), IssueCode.WORK_OFFSET, 5, 4),
            Bad("G50 X Z", one("G50 X100. Z50."), IssueCode.G50_COORD, 5, 4),
            Bad("G12.1", one("G12.1"), IssueCode.UNSUPPORTED_G, 5, 4),
            Bad("G17", one("G17"), IssueCode.UNSUPPORTED_G, 5, 4),
            Bad("G01 A", one("G01 X10. Z-5. A30."), IssueCode.HAAS_FORMAT, 5, 4),
            Bad("D", one("G00 X10. D1."), IssueCode.UNKNOWN_ADDRESS, 5, 4),
            Bad(",C ,R", one("G01 X40. ,C1. ,R1."), IssueCode.CORNER_BOTH, 5, 4),
            Bad("원호 중심 없음", one("G02 X60. Z-10."), IssueCode.ARC_NO_CENTER, 5, 4),
            Bad("G04 시간 없음", one("G04"), IssueCode.WORD_MISSING, 5, 4),
            Bad("주석 안 닫힘", one("G01 Z-10. (안 닫힌 주석"), IssueCode.COMMENT_OPEN, 5, 4),
            Bad("M99 P 의 없는 순번", head + listOf("M98 P1001", "M30", "O1001", "G01 Z-5.", "M99 P77"), IssueCode.SUB_NOT_FOUND, 9, 8),
            Bad("받지 않는 글자", one("G01 Z-10. #1"), IssueCode.BAD_CHAR, 5, 4),
            Bad("한 줄 세 블록의 셋째", one("G01 Z0. ; G01 X40. ; G32 Z-10. F1.5"), IssueCode.UNSUPPORTED_G, 5, 5),
            Bad("주축 정지", listOf("T0101", "G97 S1000", "G99 F0.2", "G00 X50. Z5.", "G01 X40. Z-10.", "M30"), IssueCode.SPINDLE_STOPPED, 5, 4),
            Bad("F 없음", listOf("T0101", "G97 S1000 M03", "G99", "G00 X50. Z5.", "G01 X40. Z-10.", "M30"), IssueCode.FEED_MISSING, 5, 4),
            Bad("공구 자료 없음", listOf("T0202", "G97 S1000 M03", "G99 F0.2", "G00 X50. Z5.", "G42 G01 X40. Z0.", "G01 Z-10.", "G40 G00 X60.", "M30"), IssueCode.TOOL_DATA_MISSING, 5, 4),
            Bad("G70 Q 없음", head + listOf("G70 P10", "M30", "N10 G00 X30.", "N20 G01 Z-10."), IssueCode.WORD_MISSING, 5, 4),
            Bad("G70 Q 블록 없음", head + listOf("G70 P10 Q99", "M30", "N10 G00 X30.", "N20 G01 Z-10."), IssueCode.CYCLE_PQ_NOT_FOUND, 5, 4),
            Bad("G70 P 블록 없음", head + listOf("G70 P11 Q20", "M30", "N10 G00 X30.", "N20 G01 Z-10."), IssueCode.CYCLE_PQ_NOT_FOUND, 5, 4),
            Bad("G70 Q 가 P 앞", head + listOf("G70 P20 Q10", "M30", "N10 G00 X30.", "N20 G01 Z-10."), IssueCode.CYCLE_PQ_ORDER, 5, 4),
            Bad("G71 첫 줄 없음", head + listOf("G71 P10 Q20 U0.4 W0.1 F0.25", "N10 G00 X30.", "N20 G01 Z-10.", "M30"), IssueCode.CYCLE_FIRST_LINE, 5, 4),
            Bad("G71 형식 II", head + listOf("G71 U2. R0.5", "G71 P10 Q20 U0.4 W0.1 F0.25", "N10 G00 X30. Z2.", "N20 G01 Z-10.", "M30"), IssueCode.G71_TYPE2, 7, 4),
            Bad("G73 패스 1", head + listOf("G73 U4. W2. R1", "M30"), IssueCode.G73_COUNT, 5, 4),
            Bad("G74 바닥 도피", head + listOf("G74 R0.5", "G74 X20. Z-10. P2000 Q3000 R0.5 F0.1", "M30"), IssueCode.G7X_RELIEF, 6, 4),
            Bad("G76 첫 줄 없음", head + listOf("G76 X44. Z-20. P1000 Q300 F1.5", "M30"), IssueCode.CYCLE_FIRST_LINE, 5, 4),
            Bad("G70 이 부른 P~Q 의 나쁜 블록", head + listOf("G70 P10 Q30", "G00 X100.", "M30", "N10 G00 X30.", "N20 G01 Z-10. P5", "N30 G01 X50."), IssueCode.WORD_NOT_USED, 9, 8),
            Bad("부프로그램 안 오류", head + listOf("M98 P1001", "M30", "O1001", "G01 Z-5.", "G01 X40. P5", "M99"), IssueCode.WORD_NOT_USED, 9, 8),
            Bad("부프로그램 M99 없음", head + listOf("M98 P1001", "M30", "O1001", "G01 Z-5.", "G01 X40."), IssueCode.SUB_NO_M99, 9, 8),
            Bad("M99 P 고리", listOf("T0101", "G97 S1000 M03", "N5 M98 P1000", "M30", "O1000", "G00 U1.", "M99 P5"), IssueCode.EXEC_LIMIT, null, 6),
            Bad("보정 중 사이클", head + listOf("G42 G01 X40. Z0. T0101", "M30"), IssueCode.T_WITH_FEED, 5, 4),
        )
        val tools = mapOf(1 to ToolOffset(0.4, 3))
        val fails = mutableListOf<String>()
        for (c in cases) {
            val r = try {
                sim(c.lines, tools)
            } catch (e: Throwable) {
                fails += "${c.name}: 예외 ${e::class.simpleName} ${e.message}\n${c.lines.joinToString("\n")}"
                continue
            }
            val st = r.stop
            when {
                st == null -> fails += "${c.name}: 멈추지 않음 ${r.issues}"
                st.code != c.code || (c.line != null && st.line != c.line) || st.line < 1 ->
                    fails += "${c.name}: 기대 ${c.code} ${c.line}줄, 실제 ${st.code} ${st.line}줄 $st\n${c.lines.joinToString("\n")}"
                c.drawnBefore != null && moves(r).none { it.line == c.drawnBefore } -> fails += "${c.name}: ${c.drawnBefore}줄 이동을 그리지 않음"
            }
        }
        assertTrue(fails.isEmpty(), fails.joinToString("\n\n"))
    }

    // ---------------------------------------------------------------- 무작위 글

    private val chars = "GXZUWRIKFSTMNOPQLDAHYCgxzm00112233445566778899..--++     ;,()/%#\t@가"

    private fun num(rnd: Random): String = when (rnd.nextInt(10)) {
        0 -> rnd.nextInt(0, 200).toString()
        1 -> "-" + rnd.nextInt(0, 200) + "."
        2 -> (rnd.nextInt(0, 2000) / 10.0).toString()
        3 -> "-" + (rnd.nextInt(0, 2000) / 100.0)
        4 -> "." + rnd.nextInt(0, 999)
        5 -> "0"
        6 -> "99999999"
        7 -> "0.001"
        8 -> "+" + rnd.nextInt(0, 50) + "."
        else -> rnd.nextInt(0, 100).toString() + "." + rnd.nextInt(0, 999)
    }

    private fun <T> List<T>.pick(rnd: Random) = this[rnd.nextInt(size)]

    private val gs = listOf(
        "G00", "G0", "G01", "G1", "G02", "G03", "G04", "G18", "G20", "G21", "G28", "G32", "G40", "G41", "G42", "G50", "G54", "G55", "G70", "G71",
        "G72", "G73", "G74", "G75", "G76", "G80", "G90", "G92", "G94", "G96", "G97", "G98", "G99", "G12.1", "G-1",
    )

    private fun word(rnd: Random): String = when (rnd.nextInt(22)) {
        0, 1, 2 -> gs.pick(rnd)
        3, 4 -> "X" + num(rnd)
        5, 6 -> "Z" + num(rnd)
        7 -> "U" + num(rnd)
        8 -> "W" + num(rnd)
        9 -> "R" + num(rnd)
        10 -> listOf("I", "K").pick(rnd) + num(rnd)
        11 -> "F" + listOf("0.2", "0.05", "100", "0", "6.", "-1", ".3").pick(rnd)
        12 -> "S" + listOf("1000", "200", "0", "50000", "-5", "550.").pick(rnd)
        13 -> "T" + listOf("0101", "0202", "0303", "0100", "1", "0", "00", "101", "12345", "0909").pick(rnd)
        14 -> "M" + listOf("00", "01", "02", "03", "04", "05", "08", "09", "30", "98", "99", "23", "3.5").pick(rnd)
        15 -> "P" + listOf("10", "20", "30", "1000", "1001", "30077", "0", "1.5", "12", "012055", "100", "200").pick(rnd)
        16 -> "Q" + listOf("20", "30", "40", "100", "1000", "1.5", "0").pick(rnd)
        17 -> "L" + listOf("2", "0", "3", "10000").pick(rnd)
        18 -> listOf(",C", ",R").pick(rnd) + num(rnd)
        19 -> "N" + listOf("10", "20", "30", "40", "5", "123456").pick(rnd)
        20 -> listOf("(주석)", "(", ";", "/", ")", "%", "D0.1", "A30.", "#1", ",X").pick(rnd)
        else -> "O" + listOf("1000", "1001", "0077", "1.5").pick(rnd)
    }

    private fun randomChars(rnd: Random): String =
        (0 until rnd.nextInt(1, 20)).joinToString("\n") { (0 until rnd.nextInt(0, 30)).map { chars[rnd.nextInt(chars.length)] }.joinToString("") }

    private fun soup(rnd: Random): String {
        val lines = mutableListOf<String>()
        if (rnd.nextInt(3) > 0) lines += listOf("T0101", "G97 S1000 M03", "G99 F0.2", "G00 X60. Z5.").take(rnd.nextInt(1, 5))
        val numbered = rnd.nextBoolean()
        repeat(rnd.nextInt(1, 18)) { k ->
            val ws = (0 until rnd.nextInt(0, 6)).map { word(rnd) }
            lines += (if (numbered) "N${(k + 1) * 10} " else "") + ws.joinToString(if (rnd.nextInt(5) == 0) "" else " ")
        }
        if (rnd.nextInt(4) == 0) lines += listOf("M30", "O1000", "G00 U2.", "G01 W-1. F0.1", "M99")
        if (rnd.nextInt(3) == 0) lines += "M30"
        return lines.joinToString("\n")
    }

    /** 사이클 모양 글 — 값은 일부러 이상한 것(0 · 음수 · 큰 값 · 되돌아가는 윤곽 · 원호 · 모서리)을 섞는다 */
    private fun cycleSoup(rnd: Random): String {
        fun v(vararg xs: String) = xs[rnd.nextInt(xs.size)]
        val l = mutableListOf("T0101", "G97 S800 M03", "G99")
        l += "G00 X${v("60.", "100.", "20.", "0.", "200.")} Z${v("5.", "2.", "0.", "-10.")}"
        when (rnd.nextInt(9)) {
            0, 1, 2 -> {
                val g = v("71", "72", "73")
                l += when (g) {
                    "71" -> "G71 U${v("2.", "0.5", "0.01", "5.", "0.001")} R${v("1.", "0.5", "0.", "3.")}"
                    "72" -> "G72 W${v("2.", "0.5", "0.01", "5.")} R${v("1.", "0.", "0.5")}"
                    else -> "G73 U${v("4.", "-2.", "0.", "10.")} W${v("2.", "0.", "-1.")} R${v("3", "2", "1", "5")}"
                }
                val comp = rnd.nextInt(3)
                val k = rnd.nextInt(2, 7)
                val q = 10 * (k + if (comp != 0) 1 else 0)
                l += "G$g P10 Q${v("$q", "$q", "10", "990")} U${v("0.4", "-0.4", "0.", "1.")} W${v("0.1", "-0.1", "0.")} F${v("0.2", "0.05")}"
                for (i in 0 until k) {
                    val n = "N${10 * (i + 1)} "
                    l += if (i == 0) {
                        n + v("G00 ", "G01 ", "") + (if (comp == 1) "G42 " else if (comp == 2) "G41 " else "") +
                            (if (g == "72") "Z${v("-20.", "-5.", "0.", "-40.")}" else if (g == "71") "X${v("20.", "40.", "0.", "60.")}" else "X${v("20.", "40.")} Z${v("1.", "0.")}")
                    } else {
                        n + v("G01 ", "G02 ", "G03 ", "", "G00 ") + "X${v("20.", "30.", "40.", "50.", "60.", "45.", "38.")} Z${v("0.", "-5.", "-10.", "-15.", "-20.", "-12.5", "-30.")}" +
                            v("", "", " R5.", " R2.", " R-5.", " ,C1.", " ,R1.", " I2. K0.", " R0.4")
                    }
                }
                if (comp != 0) l += "N$q G40 G00 X${v("70.", "100.")}"
                l += "G70 P10 Q$q"
            }
            3, 4 -> {
                val g = v("74", "75")
                l += "G$g R${v("0.5", "0.", "1.", "-1.")}"
                val w = mutableListOf("G$g", "X${v("20.", "40.", "0.", "60.", "-10.")}", "Z${v("-10.", "-0.5", "5.", "-30.")}")
                if (rnd.nextBoolean()) w += "P${v("2000", "0", "500", "1")}"
                if (rnd.nextBoolean()) w += "Q${v("3000", "0", "100", "1")}"
                if (rnd.nextInt(4) == 0) w += "R${v("0.", "0.5")}"
                w += "F${v("0.1", "0.02")}"
                l += w.joinToString(" ")
            }
            5 -> {
                l += "G00 X70. Z105."
                l += "G76 P${v("011060", "021060", "001060", "010060", "1060", "9999999")} Q${v("100", "0", "50")} R${v("0.2", "0.", "0.05", "5.")}"
                l += "G76 X${v("60.64", "56.", "70.", "64.4", "80.")} Z${v("25.", "-20.", "105.")} P${v("3680", "1000", "0", "100")} Q${v("1800", "500", "0")}" +
                    v("", " R-1.", " R1.") + " F${v("6.0", "1.5", "2.")}"
            }
            else -> {
                val g = v("90", "92", "94")
                l += "G$g X${v("40.", "50.", "80.", "0.")} Z${v("-20.", "0.", "-50.")}" + v("", " R-2.", " R3.", " I-1.", " K1.") + " F${v("0.2", "2.", "0.3")}"
                repeat(rnd.nextInt(0, 4)) { l += v("X38.", "X36.", "Z-25.", "Z-30.", "U-2.", "W-1.", "G04 X0.5", "M08", "X30. M05") }
                l += "G00 X100."
            }
        }
        l += "G00 X200. Z100."
        l += "M30"
        return l.joinToString("\n")
    }

    private fun mutate(base: String, rnd: Random): String {
        var s = base
        repeat(rnd.nextInt(1, 5)) {
            val i = if (s.isEmpty()) 0 else rnd.nextInt(s.length)
            val ls = s.split('\n').toMutableList()
            s = when (rnd.nextInt(6)) {
                0 -> if (s.isEmpty()) s else s.removeRange(i, i + 1)
                1 -> s.substring(0, i) + chars[rnd.nextInt(chars.length)] + s.substring(i)
                2 -> s.substring(0, i) + "0123456789"[rnd.nextInt(10)] + s.substring(min(s.length, i + 1))
                3 -> {
                    val a = rnd.nextInt(ls.size)
                    val b = rnd.nextInt(ls.size)
                    val t = ls[a]
                    ls[a] = ls[b]
                    ls[b] = t
                    ls.joinToString("\n")
                }
                4 -> {
                    val a = rnd.nextInt(ls.size)
                    ls.add(a, ls[a])
                    ls.joinToString("\n")
                }
                else -> {
                    if (ls.size > 1) ls.removeAt(rnd.nextInt(ls.size))
                    ls.joinToString("\n")
                }
            }
        }
        return s
    }

    private fun fin(p: Pt) = p.z.isFinite() && p.x.isFinite()

    private fun finSeg(s: Seg): Boolean = when (s) {
        is Line -> fin(s.start) && fin(s.end)
        is Arc -> fin(s.start) && fin(s.end) && fin(s.center)
    }

    /**
     * 한 글을 돌려 본다: 예외 없음 · 멈춘 줄과 모든 문제 줄이 글 안(STOP 은 1 이상) · 문제의 글자 자리가 그 줄 안(편집기 밑줄) ·
     * 걸음 시간이 차례로 늘고 유한 · 좌표 유한 · 전체 시간 = 마지막 걸음 끝 · 재생(poseAt · blockStart/End)과 구문 색(tokens)이 예외 없음.
     */
    private fun probe(text: String, tools: Map<Int, ToolOffset>, machine: MachineSetup, bad: MutableList<String>, slow: MutableList<String>) {
        val lines = text.split('\n').map { it.removeSuffix("\r") }
        val n = lines.size
        val mark = TimeSource.Monotonic.markNow()
        val r = try {
            GSim.run(SimInput(text, machine, tools))
        } catch (e: Throwable) {
            bad += "예외 ${e::class.simpleName}: ${e.message}\n---\n$text\n---"
            return
        }
        val ms = mark.elapsedNow().inWholeMilliseconds
        if (ms > 2000) slow += "${ms}ms\n$text"
        val before = bad.size
        fun fail(msg: String) {
            if (bad.size == before) bad += "$msg\n---\n$text\n---"
        }
        r.stop?.let { st ->
            if (st.line !in 1..n) fail("멈춘 줄 ${st.line} 이 글 밖(줄 수 $n): $st")
            if (st.level != IssueLevel.STOP) fail("멈춘 문제가 STOP 이 아님: $st")
            if (st !in r.issues) fail("멈춘 문제가 문제 목록에 없음: $st")
        }
        for (i in r.issues) {
            if (i.line !in 0..n) fail("문제 줄 ${i.line} 이 글 밖(줄 수 $n): $i")
            if (i.level == IssueLevel.STOP && i.line < 1) fail("STOP 문제에 줄 번호가 없음: $i")
            val c = i.cols
            if (c != null && i.line in 1..n) {
                val len = lines[i.line - 1].length
                if (c.first < 0 || c.last >= len || c.first > c.last) fail("문제 글자 자리 $c 가 ${i.line}줄(길이 $len) 밖: $i")
            }
        }
        var prev = 0.0
        for ((k, st) in r.steps.withIndex()) {
            if (!st.t0.isFinite() || !st.t1.isFinite() || st.t1 < st.t0 - 1e-12 || st.t0 < prev - 1e-9) {
                fail("걸음 $k 시간이 이상함: $st (앞 걸음 끝 $prev)")
                break
            }
            prev = st.t1
            if (st.line !in 1..n) {
                fail("걸음 $k 의 줄 ${st.line} 이 글 밖: $st")
                break
            }
            if (st is MoveStep && !(finSeg(st.tip) && finSeg(st.nose))) {
                fail("걸음 $k 좌표가 수가 아님: $st")
                break
            }
        }
        if (!r.totalSec.isFinite() || abs(r.totalSec - prev) > 1e-9 * max(1.0, prev)) fail("전체 시간 ${r.totalSec} ≠ 마지막 걸음 끝 $prev")
        for (g in r.ghosts) if (!(fin(g.tip) && fin(g.cmd) && fin(g.nose))) {
            fail("고스트 좌표가 수가 아님 $g")
            break
        }
        try {
            for (f in listOf(0.0, 0.37, 1.0)) {
                val pose = r.poseAt(r.totalSec * f)
                if (!(fin(pose.tip) && fin(pose.nose) && pose.rpm.isFinite())) fail("poseAt(${r.totalSec * f}) 이 수가 아님 $pose")
            }
            if (r.steps.isNotEmpty()) {
                val k = r.steps.size / 2
                r.blockStart(k)
                r.blockEnd(k)
            }
            for (l in lines) for ((c, _) in GText.tokens(l)) if (c.first < 0 || c.last >= l.length) fail("구문 색 자리 $c 가 줄 밖: '$l'")
        } catch (e: Throwable) {
            fail("재생 · 구문 색 함수 예외 ${e::class.simpleName}: ${e.message}")
        }
    }

    private val fuzzTools = listOf(
        mapOf(1 to ToolOffset(0.4, 3), 2 to ToolOffset(0.8, 2), 3 to ToolOffset(0.0, 3)),
        mapOf(1 to ToolOffset(0.8, 2)),
        mapOf(1 to ToolOffset(1.2, 9), 2 to ToolOffset(null, 3)),
        emptyMap(),
    )

    private val fuzzMachines = listOf(
        MachineSetup(),
        MachineSetup(decimal = DecimalMode.CALCULATOR),
        MachineSetup(rapidPath = RapidPath.LINEAR, blockSkip = true, g92ChamferLeads = 1.0),
        MachineSetup(decimal = DecimalMode.CALCULATOR, g92ChamferLeads = 2.0, maxRpm = 2000.0),
    )

    /** 출처 글(변형의 바탕) */
    private val bases = listOf(
        (pre + listOf(
            "N010 G00 X200.0 Z220.0", "N011 X142.0 Z171.0", "N012 G71 U4.0 R1.0", "N013 G71 P014 Q020 U4.0 W2.0 F0.3 S550",
            "N014 G00 X40.0 F0.15 S700", "N015 G01 Z140.0", "N016 X60.0 Z110.0", "N017 Z90.0", "N018 X100.0 Z80.0", "N019 Z60.0",
            "N020 X140.0 Z40.0", "N021 G70 P014 Q020", "N022 G00 X200. Z220.", "M30",
        )).joinToString("\n"),
        haasGroove.joinToString("\n"),
        (pre + listOf("G00 X70. Z105.", "G76 P011060 Q100 R200", "G76 X60640 Z25000 P3680 Q1800 F6.0", "G00 X100.", "M30")).joinToString("\n"),
        (pre + listOf(
            "G00 X40. Z2.", "G42 G01 X44. Z0. ,R1.", "G01 Z-30. ,C1.", "G01 X54. ,R1.", "G02 X60. Z-33. R3.", "G01 Z-40.", "G40 G00 X70.",
            "M98 P2000 L2", "M30", "O2000", "G00 U-2.", "G01 W-5.", "G00 W5.", "M99",
        )).joinToString("\n"),
        listOf(
            "N1 G28 U0. W0.", "N2 T505", "N3 G97 S960 M03", "N4 G54 G00 X2.1 Z0.1 M08", "N5 Z-0.75", "G75 R0.02",
            "N6 G75 X1.75 Z-1.5 P50 Q200 F0.005", "N7 M09", "N8 G28 U0. W0.", "N9 M30",
        ).joinToString("\n"),
        (pre + listOf("G00 X94. Z62.", "G90 X80. W-42. F0.3", "X70.", "X60.", "G00 X87. Z72.", "G92 X66.4 Z25.4 F6.", "X65.", "G00 X100.", "M30")).joinToString("\n"),
    )

    /**
     * 무작위 글 13 000 개(글자 3 000 · 낱말 4 000 · 사이클 모양 4 000 · 출처 글 변형 2 000) × 공구표 · 기계 설정 4가지 돌려 가며.
     * 실패는 재현 글과 함께(앞 5개). 2 초 넘게 걸린 글도 알린다(화면은 고칠 때마다 다시 돌린다 — 설계 8.6).
     */
    @Test
    fun fuzzNeverThrowsAndKeepsLineNumbers() {
        val rnd = Random(20261028)
        val bad = mutableListOf<String>()
        val slow = mutableListOf<String>()
        var k = 0
        fun go(text: String) {
            probe(text, fuzzTools[k % fuzzTools.size], fuzzMachines[(k / fuzzTools.size) % fuzzMachines.size], bad, slow)
            k++
        }
        repeat(3000) { go(randomChars(rnd)) }
        repeat(4000) { go(soup(rnd)) }
        repeat(4000) { go(cycleSoup(rnd)) }
        repeat(2000) { go(mutate(bases.pick(rnd), rnd)) }
        println("무작위 글 $k 개 · 실패 ${bad.size} · 2 초 넘음 ${slow.size}")
        for (s in slow.take(5)) println("느림: $s")
        assertTrue(bad.isEmpty(), "실패 ${bad.size}:\n" + bad.take(5).joinToString("\n\n"))
    }

    // ================================================================ 4. FANUC 설명서(공식)와 맞춰 봄
    // F0 = FANUC Series 0/00/0-Mate for Lathe Operator's Manual B-61394E/08, FF = FANUC Series 0+-MODEL F for Lathe System Operator's Manual
    // B-64604EN-1/01(쪽 = 인쇄 쪽). 설계 0.2 는 'FANUC 공개본을 보지 못했다' 고 적었으나 이 작업의 자료 폴더에 두 설명서가 있어 맞춰 봤다.

    private val cyc90 = listOf("T0101", "G97 S1000 M03", "G99", "G00 X60. Z2.", "G90 X50. Z-20. F0.2")

    /**
     * FF 4.1.6 '- Block in which no move command is specified'(50쪽) · F0 13.1 주 2 (1)(3):
     * G90/G92/G94 모드에서 이동 없는 블록 — EOB 만, 또는 M · S · T 만 — 에서도 같은 사이클을 다시 돈다(M · S · T 는 사이클과 함께 실행).
     * FF 는 'T 를 바꾸려면 G00 으로 먼저 끝내라'(`N011 G00 T0202 ;`)고 예를 든다. 시뮬레이터는 그 줄에서 사이클을 다시 그리거나(정확)
     * '지원 안 함'으로 멈춰야 한다 — 보통 블록으로 넘기면 실제 기계보다 한 번 덜 깎고, T 만인 줄은 새 공구로 한 번 더 깎는 것을 숨긴다.
     */
    @Test
    fun fanucCycleRepeatsOnBlockWithoutMove() {
        val tools = mapOf(1 to ToolOffset(0.4, 3), 2 to ToolOffset(0.4, 3))
        val fails = mutableListOf<String>()
        for (x in listOf("M08", "S800", "F0.15", "T0202", "(MEMO)", ";")) {
            val text = cyc90 + x + "G00 X100." + "M30"
            val r = try {
                sim(text, tools)
            } catch (e: Throwable) {
                fails += "'$x' 예외 $e"
                continue
            }
            val st = r.stop
            val again = moves(r).filter { it.line == 6 }
            val repeated = again.size == 4 && (again.last().tip.end - Pt(2.0, 30.0)).length < 1e-9 &&
                again.any { it.kind == MoveKind.FEED && abs(it.tip.end.z + 20) < 1e-9 }
            if (!(repeated || (st != null && st.line == 6))) {
                fails += "'$x': 6줄에서 사이클을 다시 돌지 않고 멈추지도 않음(6줄 이동 ${again.size}, 멈춤 $st)\n${text.joinToString("\n")}"
            }
        }
        for (f in fails) println("FANUC 사이클 모드 이동 없는 블록: " + f.lineSequence().first())
        assertTrue(fails.isEmpty(), "FANUC 은 사이클 모드의 이동 없는 블록에서도 사이클을 다시 돈다(FF 4.1.6 · F0 13.1 주 2):\n" + fails.joinToString("\n\n"))
    }

    /** FF 4.1.6(48쪽): 사이클 모드에서 G04 는 드웰만 하고 사이클을 돌지 않는다. 모드 · 모달 값은 남아 다음 `X45.` 는 Z-20 으로 사이클 */
    @Test
    fun fanucG04InCycleMode() {
        val r = sim(cyc90 + listOf("G04 X1.", "X45.", "G00 X100.", "M30"))
        assertNull(r.stop, "${r.stop}")
        assertTrue(moves(r).none { it.line == 6 }, "G04 줄에 이동이 있음")
        assertEquals(1.0, r.steps.filterIsInstance<DwellStep>().single { it.line == 6 }.sec, 1e-12)
        val m7 = moves(r).filter { it.line == 7 }
        assertEquals(4, m7.size, "X45. 줄 이동 ${m7.map { xz(it.tip.end) }}")
        assertTrue((m7[1].tip.end - Pt(-20.0, 22.5)).length < 1e-9, "X45. 줄 깎기 끝 ${xz(m7[1].tip.end)}")
    }

    /** FF 4.2.4 '- Return to the cycle start point' 주(75쪽): G70 은 매개변수 LRP 와 상관없이 늘 비직선(축마다 따로) 급송으로 시작점에 돌아간다 */
    @Test
    fun fanucG70ReturnIsNonlinear() {
        val text = listOf("T0101", "G97 S1000 M03", "G99 F0.2", "G00 X62. Z2.", "G70 P10 Q20", "G00 X100.", "M30", "N10 G00 X40.", "G01 Z-20.", "N20 X70. Z-25.")
        val fails = mutableListOf<String>()
        for (rp in RapidPath.values()) {
            val r = sim(text, machine = MachineSetup(rapidPath = rp))
            assertNull(r.stop, "${r.stop}")
            val back = moves(r).filter { it.line == 5 && it.src == StepSrc.CYCLE_RETURN }
            // 축마다 따로면 두 토막 — Z −25 → 2(27 mm, 24 m/min) · X 반경 35 → 31(4 mm, 20 m/min): X 가 0.0002 분에 먼저 끝나 Z −20.2 에서 꺾임
            if (back.size != 2 || (back[0].tip.end - Pt(-20.2, 31.0)).length > 1e-9) {
                fails += "급송 설정 $rp: G70 복귀가 축마다 따로가 아님 — 토막 ${back.map { "${xz(it.tip.start)} → ${xz(it.tip.end)}" }}"
            }
        }
        for (f in fails) println("FANUC G70 복귀: $f")
        assertTrue(fails.isEmpty(), fails.joinToString("\n"))
    }

    /**
     * FF 4.2.1 '- Operations'(53쪽): "After rough cutting as finishing, the block next to the sequence block specified at Q is executed."
     * P~Q 가 사이클 줄 바로 뒤에 없을 때(여기선 M30 뒤) FANUC 은 Q(N20) 다음 12줄로 간다. 시뮬레이터는 FANUC 처럼 가거나 멈춰야 한다.
     */
    @Test
    fun fanucG71ContinuesAfterQ() {
        val text = listOf(
            "T0101", "G97 S1000 M03", "G99", "G00 X62. Z2.", "G71 U2. R0.5", "G71 P10 Q20 U0.4 W0.1 F0.25",
            "G00 X100. Z50.", "M30", "N10 G00 X40.", "G01 Z-20.", "N20 X62.", "G00 X150. Z80.", "M30",
        )
        val r = sim(text)
        val st = r.stop
        if (st != null && st.line == 6) return
        assertNull(st, "$st")
        val after = moves(r).filter { it.line != 6 && it.line > 4 }.map { it.line }.distinct()
        println("G71 뒤 실행한 이동 줄: $after (FANUC: 12)")
        assertEquals(listOf(12), after, "FANUC 은 G71 뒤 Q(N20) 다음 줄 12 `G00 X150. Z80.` 을 실행한다 — 시뮬레이터는 $after 줄")
    }

    /**
     * 보정과 복합 사이클 — 출처가 서로 다르다(알리기만):
     * - FF 4.2.1 '- Tool nose radius compensation'(61~62쪽) · 4.2.8(89쪽): G41/G42 는 사이클 **앞에**, G40 은 P~Q 밖. P~Q 안 보정은 알람 PS0325
     *   (매개변수 5106 NT1 = 1 이면 G71~G73 에서 무시, NT2 = 1 이면 G70 에서만 받음).
     * - F0 13.2.1 주 4(149쪽): G71 사이클 동안 노즈R 보정을 하지 않는다.
     * - H 77(설계 1.6 · 3.4): 사이클 앞 보정 금지, 보정은 P 블록에서 — 시뮬레이터는 이것을 따르고 황삭 층을 보정 윤곽으로 깎는다.
     */
    @Test
    fun compAndRepetitiveCycleSourcesDisagree() {
        val head = listOf("T0101", "G97 S1000 M03", "G99", "G00 X62. Z2.")
        val inside = sim(head + listOf("G71 U2. R0.5", "G71 P10 Q20 U0.4 W0.1 F0.25", "N10 G00 G42 X40.", "G01 Z-20. F0.1", "X50. Z-25.", "N20 G40 X62.", "G70 P10 Q20", "M30"), mapOf(1 to ToolOffset(0.4, 3)))
        val before = sim(head + listOf("G42 G00 X62. Z3.", "G71 U2. R0.5", "G71 P10 Q20 U0.4 W0.1 F0.25", "N10 G00 X40.", "G01 Z-20. F0.1", "X50. Z-25.", "N20 X62.", "G70 P10 Q20", "G40 G00 X100.", "M30"), mapOf(1 to ToolOffset(0.4, 3)))
        println("P~Q 안 G42(H 식 — FF 는 알람 PS0325, F0 는 황삭에 보정 없음): 시뮬레이터 멈춤 = ${inside.stop}, 황삭 층 ${moves(inside).count { it.line == 6 && it.model }}개")
        println("사이클 앞 G42(FF 식): 시뮬레이터 멈춤 = ${before.stop}")
        assertNull(inside.stop, "${inside.stop}")
    }

    /**
     * FF 4.1.6 예(48~49쪽) `N030 G90 U-8.0 W-66.0 F0.4 ; N031 U-16.0 ; N032 U-24.0 ; N033 U-32.0 ;` — 시작점은 그림에 수가 없어 X100 Z2 로 정함.
     * 손으로: U · W 는 시작점 A 에서(W 모달), 패스 k: 급송 X 반경 50 → 50 − 4k, 이송 Z 2 → −64, 이송 X → 50, 급송 Z → 2. F0.4 × S500 = 200 mm/min.
     */
    @Test
    fun fanucG90ModalExampleByHand() {
        val r = sim(listOf("T0101", "G97 S500 M03", "G99", "G00 X100. Z2.", "N030 G90 U-8.0 W-66.0 F0.4", "N031 U-16.0", "N032 U-24.0", "N033 U-32.0", "G00 X120.", "M30"))
        assertNull(r.stop, "${r.stop}")
        val exp = mutableListOf(Ex(4, true, Pt(2.0, 50.0)))
        for (k in 1..4) {
            val x = 50.0 - 4 * k
            exp += Ex(4 + k, true, Pt(2.0, x))
            exp += Ex(4 + k, false, Pt(-64.0, x), 200.0)
            exp += Ex(4 + k, false, Pt(-64.0, 50.0), 200.0)
            exp += Ex(4 + k, true, Pt(2.0, 50.0))
        }
        exp += Ex(9, true, Pt(2.0, 60.0))
        val total = checkExpanded(r, exp, Pt(100.0, 100.0), "FF 4.1.6 G90")
        // 손 합: 급송 0.245 + Σ_k (4k/20000 + (66 + 4k)/200 + 66/24000) 분 + 10/20000 분
        val hand = 0.245 + (1..4).sumOf { k -> (4.0 * k / 20000 + (66.0 + 4 * k) / 200 + 66.0 / 24000) * 60 } + 10.0 / 20000 * 60
        assertEquals(hand, total, 1e-9)
        assertEquals(total, r.totalSec, 1e-9, "사이클 타임")
    }

    /**
     * FF 4.2.7 그림 4.2.7(e)(87쪽): `G00 X80.0 Z130.0 ; G76 P011060 Q100 R200 ; G76 X60.64 Z25.0 P3680 Q1800 F6.0 ;`(글에 'G80' 으로 찍힌 것은 G00 으로).
     * 손으로(FF 그림 4.2.7(b) · dmin 설명: n 번째 깊이 Δd√n, 한 번 깊이가 Δdmin 보다 작으면 Δdmin, 여유 d 를 남기고 다듬질 m 번):
     * k 3.68 · Δd 1.8 · d 0.2 · m 1 → 깊이 1.8 · 2.546 · 3.118 · (3.6 → k − d = 3.48) · 3.68 → 곧은 나사 X(직경) = 68 − 2·깊이.
     * k − d 로 자르는 것 · Z 시작 옮김 · 끝 빼기는 설계 '모형' — 깊이와 끝 Z 만 본다.
     */
    @Test
    fun fanucG76ExampleDepths() {
        val r = sim(listOf("T0101", "G97 S300 M03", "G99", "G00 X80. Z130.", "G76 P011060 Q100 R200", "G76 X60.64 Z25.0 P3680 Q1800 F6.0", "G00 X100.", "M30"))
        assertNull(r.stop, "${r.stop}")
        val th = moves(r).filter { it.kind == MoveKind.THREAD }
        val straight = th.filter { abs(it.tip.start.x - it.tip.end.x) < 1e-9 }
        val want = listOf(1.8, 1.8 * sqrt(2.0), 1.8 * sqrt(3.0), 3.48, 3.68).map { 68.0 - 2 * it }
        assertEquals(want.size, straight.size, "곧은 나사 토막 ${straight.map { xz(it.tip.start) }}")
        for ((w, g) in want.zip(straight)) assertEquals(w, g.tip.start.xDia, 1e-9, "나사 X")
        assertEquals(25.0, th.last().tip.end.z, 1e-9, "마지막 나사 끝 Z")
    }

    /**
     * FF 4.2.3 그림 4.2.3(b) O0001(72쪽): `G00X100.0Z0.0; G73U6.0W6.0R3.0; G73P10Q30U6.0W3.0F1.0S500; N10G00X60.0W0.0; N15G01Z-10.0;
     * N20G02X80.0Z-20.0R10.0; N30G01X100.0;`. FF 그림 4.2.3(a): 첫 패스 = 윤곽 + (k + w, i + u/2) = +(9, 9), 마지막 = 윤곽 + (w, u/2) = +(3, 3),
     * 패스 d = 3 번, 끝나면 A(주 3). 윤곽(z, x 반경): A′(0, 30) → (−10, 30) → G02 R10 중심 (−10, 40) → (−20, 40) → (−20, 50).
     * 패스 사이 이동은 설계 '모형' 이라 보지 않는다. 이송 F1.0 × S500 = 500 mm/min.
     */
    @Test
    fun fanucG73ExamplePasses() {
        val text = listOf(
            "T0101", "G97 S500 M03", "G99", "G00X100.0Z0.0", "G73U6.0W6.0R3", "G73P10Q30U6.0W3.0F1.0S500",
            "N10G00X60.0W0.0", "N15G01Z-10.0", "N20G02X80.0Z-20.0R10.0", "N30G01X100.0", "M30",
        )
        // 원문은 `R3.0` — FF 4.2.3 주: d 는 소수점을 받아 정수로 반올림. 시뮬레이터가 그대로 받는지 알린다(여기선 R3 으로 돌림)
        val verbatim = sim(text.map { if (it == "G73U6.0W6.0R3") "G73U6.0W6.0R3.0" else it })
        println("FF O0001 원문 `G73U6.0W6.0R3.0`: 시뮬레이터 멈춤 = ${verbatim.stop}")
        val r = sim(text)
        assertNull(r.stop, "${r.stop}")
        val cyc = moves(r).filter { it.line == 6 }
        assertEquals(listOf(1, 2, 3), cyc.mapNotNull { it.pass }.distinct())
        val ends = listOf(Pt(-10.0, 30.0), Pt(-20.0, 40.0), Pt(-20.0, 50.0))
        for ((j, off) in listOf(Pt(9.0, 9.0), Pt(6.0, 6.0), Pt(3.0, 3.0)).withIndex()) {
            val feeds = cyc.filter { it.pass == j + 1 && it.kind == MoveKind.FEED }
            assertEquals(3, feeds.size, "패스 ${j + 1} 이송 ${feeds.map { xz(it.tip.end) }}")
            assertTrue((feeds[0].tip.start - Pt(0.0, 30.0) - off).length < 1e-9, "패스 ${j + 1} 시작 ${xz(feeds[0].tip.start)}")
            for ((k, e) in ends.withIndex()) assertTrue((feeds[k].tip.end - (e + off)).length < 1e-9, "패스 ${j + 1} ${k}번 끝 ${xz(feeds[k].tip.end)}")
            val arc = feeds[1].tip as Arc
            assertTrue(arc.cw && (arc.center - (Pt(-10.0, 40.0) + off)).length < 1e-9, "패스 ${j + 1} 원호 $arc")
            val sec = feeds.sumOf { it.t1 - it.t0 }
            assertEquals((10.0 + 10.0 * PI / 2 + 10.0) / 500 * 60, sec, 1e-9, "패스 ${j + 1} 이송 시간")
        }
        assertTrue((cyc.last().tip.end - Pt(0.0, 50.0)).length < 1e-9, "끝나면 A ${xz(cyc.last().tip.end)}")
    }
}
