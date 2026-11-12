package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Pt
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 2026-11-12 통합 고침 S2(90° 를 아주 조금 넘는 바깥 꺾임) 독립 검증. 구현자의 시험과 따로 짠 검사다.
 * 표 `Verify1112SimCorners.tsv` 는 검증자가 py 로 손셈한 것(GSim 의 원호 중심식 · 보정선 교점 · 형식 C 끼운 토막 P1 = e + nA·r + tA·r, P2 = e + nB·r − tB·r 를
 * 0.001 로 반올림한 NC 좌표에서 다시 셈): 직선끼리 세 꼴(G42 산 · G41 골 · G42 계단) × 꺾임 89.95° ~ 179° × r 0.05 ~ 5, 직선 → 원호(G41 · G42 · 오목 · 볼록 ·
 * R1 ~ 5000), 원호 끝 → 직선(V7-4 꼴 G2X40.Z-7.R… → G1Z-10.), 직선 → 원호 시작(격자 꼴 X30. → G2X40.Z-7.R…). 형식 C · A/B 둘 다.
 * 기대: 받음(그 줄 앞 이음의 노즈 = 늘인 교점 X — 1e-6) · 멈춤(COMP_ACUTE_OUTER, 그 줄) · 둘 다(꼭 90° 의 소수 잡음이 원호 이음에서 부호에 따라 갈리는 것).
 * 고침 단계(2026-11-13, V12-1)에서 표를 `v_s2_fix.py` 로 다시 만들었다: dmax 열 = 90° 를 넘은 몫(끝 접선을 늘인 교점 XL 과 P1 · P2 의 차이 — 직선끼리는 XL = X 라 전과 같음).
 * 원호가 휘는 몫(X − XL)은 꼭 90° 에서도 생기는 볼록 꼭짓점 밖 공기라 받는 가름에 넣지 않는다(설계 22.4). 그래서 '둘 다' 는 없어지고 V7-4 · 격자 꼴은 모두 받음.
 */
class Verify1112SimCornerTest {
    private data class Case(
        val kind: String, val name: String, val prog: String, val r: Double, val tip: Int, val deg: Double,
        val expect: String, val why: String, val x: Pt?, val dmax: Double?, val line: Int,
    )

    private fun cases(): List<Case> {
        val text = Verify1112SimCornerTest::class.java.getResourceAsStream("/Verify1112SimCorners.tsv")!!.readBytes().decodeToString()
        val progs = HashMap<Int, String>()
        val out = ArrayList<Case>()
        for (ln in text.lineSequence()) {
            if (ln.isBlank() || ln.startsWith("#")) continue
            val f = ln.split('\t')
            when (f[0]) {
                "P" -> progs[f[1].toInt()] = f[2].replace('|', '\n')
                "C" -> out += Case(
                    f[1], f[2], progs.getValue(f[3].toInt()), f[4].toDouble(), f[5].toInt(), f[6].toDouble(), f[7], f[8],
                    f[9].takeIf { it != "-" }?.split(',')?.let { Pt(it[0].toDouble(), it[1].toDouble()) }, f[10].takeIf { it != "-" }?.toDouble(), f[11].toInt(),
                )
            }
        }
        return out
    }

    @Test
    fun handTableMatches() {
        val all = cases()
        assertEquals(954, all.size, "표 줄 수")
        val bad = ArrayList<String>()
        val either = HashMap<String, Int>()
        var accepted = 0
        var acceptedOver90 = 0
        for (c in all) for (form in listOf(CompStartup.C, CompStartup.AB)) {
            val run = GSim.run(SimInput(c.prog, MachineSetup(compStartup = form), mapOf(1 to ToolOffset(c.r, c.tip))))
            val stop = run.stop
            val tag = "${c.kind} ${c.name} r ${c.r} 꺾임 ${String.format(Locale.ROOT, "%.6f", c.deg)}° 형식 $form (py: ${c.why}, dmax ${c.dmax})"
            val stopped = stop != null && stop.code == IssueCode.COMP_ACUTE_OUTER && stop.line == c.line
            when (c.expect) {
                "stop" -> if (!stopped) bad += "$tag — 멈춰야 하는데 ${stop ?: "돎"}"
                "either" -> {
                    if (stop != null && !stopped) bad += "$tag — 다른 멈춤 $stop"
                    either.merge(if (stopped) "멈춤" else "받음", 1, Int::plus)
                }
                else -> {
                    if (stop != null) {
                        bad += "$tag — 돌아야 하는데 멈춤 $stop"
                        continue
                    }
                    for ((a, b) in run.steps.filterIsInstance<MoveStep>().zipWithNext()) {
                        if ((a.nose.end - b.nose.start).length > 1e-9) bad += "$tag — 노즈 경로 끊김 ${a.line} → ${b.line}"
                    }
                    if (c.why == "ok") {
                        accepted++
                        val nose = run.ghosts.last { it.line == c.line - 1 }.nose
                        val x = c.x!!
                        if ((nose - x).length > 1e-6) bad += "$tag — 이음 노즈 ${nose} ≠ py 교점 $x (차이 ${(nose - x).length})"
                        if (c.deg > 90) {
                            acceptedOver90++
                            // 받은 90° 넘음은 넘은 몫(끝 접선을 늘인 교점과 형식 C 끼운 토막 P1 · P2 의 차이)이 0.001 안(py 의 dmax)
                            if (c.dmax!! > 1e-3) bad += "$tag — 90° 넘음을 받았는데 넘은 몫 ${c.dmax} mm"
                        }
                    }
                }
            }
        }
        println("Verify1112SimCornerTest: 사례 ${all.size} × 형식 2, 바깥 꺾임 받음 $accepted (90° 넘음 $acceptedOver90), 꼭 90° 원호 잡음 $either")
        assertTrue(acceptedOver90 > 0, "90° 넘음을 받은 사례가 없다 — 표가 틀렸다")
        assertTrue(bad.isEmpty(), "어긋남 ${bad.size}:\n" + bad.take(40).joinToString("\n"))
    }

    /**
     * V7-4 의 실제 꼴(계산기 테이퍼 원호 — 최소 R5 · 받는 띠 R4.999): 고침 단계(V12-1 — 설계 22.4)부터 멈추지 않고 돈다(노즈 경로 끊기지 않음).
     * 검증 때는 '여전히 멈춘다' 를 확인하던 시험(v74CalculatorFormStillStops) — 22.3 ① 을 기본값으로 골라 기대를 뒤집었다
     */
    @Test
    fun v74CalculatorFormRuns() {
        val head = listOf("T0101", "G97S1000M03", "G99")
        fun prog(vararg l: String) = (head + l.toList() + "M30").joinToString("\n")
        // 받는 띠(최소 R 바로 아래): 원호 끝 → G1Z-10. 이 90.011° — 노즈 0.2 에서도 끼운 토막과 0.0042 mm
        val below = prog("G42", "G0X30.Z20.", "Z-1.", "G1Z-2.F.1", "G2X40.Z-7.R4.999", "G1Z-10.", "X42.F.3", "G40", "G0Z200.")
        // 꼭 R5: X30. → G2 시작이 꼭 90° 인데 소수 잡음(내적 −8.9e-17)
        val exact = prog("G42", "G0X24.Z20.", "Z-1.", "G1Z-2.F.1", "X30.", "G2X40.Z-7.R5.", "G1X42.F.3", "G40", "G0Z200.")
        for (r in listOf(0.2, 0.4, 0.8)) for (form in listOf(CompStartup.C, CompStartup.AB)) {
            val m = MachineSetup(compStartup = form)
            val t = mapOf(1 to ToolOffset(r, 3))
            for ((name, text) in listOf("R4.999" to below, "R5" to exact)) {
                val run = GSim.run(SimInput(text, m, t))
                assertEquals(null, run.stop, "$name r $r $form")
                for ((a, b) in run.steps.filterIsInstance<MoveStep>().zipWithNext()) {
                    assertTrue((a.nose.end - b.nose.start).length <= 1e-9, "$name r $r $form — 노즈 경로 끊김 ${a.line} → ${b.line}")
                }
            }
        }
    }

    /** 무작위 꺾임(씨앗 고정): 받은 바깥 90° 넘음은 모두 넘은 각 sin ≤ 0.01 이고 노즈가 형식 C 끼운 토막의 두 끝에서 0.001 안 — 모양 · 쪽 · r 을 섞어 */
    @Test
    fun randomOuterCornersNeverAcceptFar() {
        val rnd = kotlin.random.Random(1112)
        var acceptedOver = 0
        val bad = ArrayList<String>()
        repeat(3000) {
            val g42 = rnd.nextBoolean()
            val r = listOf(0.05, 0.1, 0.2, 0.4, 0.8, 1.2, 2.0)[rnd.nextInt(7)]
            // A: (Z0, X40) 에서 각 a 로 4 mm, B: 바깥으로 90 + δ 꺾어 4 mm(δ 는 −0.2° ~ 1°)
            val a = rnd.nextDouble(0.0, 2 * Math.PI)
            val delta = rnd.nextDouble(-0.2, 1.0)
            val ta = Pt(kotlin.math.cos(a), kotlin.math.sin(a))
            val phi = Math.toRadians(90 + delta) * (if (g42) 1 else -1)
            val tb = Pt(ta.z * kotlin.math.cos(phi) - ta.x * kotlin.math.sin(phi), ta.z * kotlin.math.sin(phi) + ta.x * kotlin.math.cos(phi))
            val s = Pt(0.0, 40.0)
            val e = s + ta * 4.0
            val f = e + tb * 4.0
            fun w(p: Pt) = "X" + String.format(Locale.ROOT, "%.3f", p.xDia) + "Z" + String.format(Locale.ROOT, "%.3f", p.z)
            // 시작은 A 의 연장선 뒤에서 곧게(같은 방향 — 시작 블록 모서리 없음), 끝은 B 연장선으로 곧게 G40
            val st = s - ta * 3.0
            val en = f + tb * 3.0
            val side = if (g42) "G42" else "G41"
            val text = listOf("T0101", "G97S1000M03", "G99", "G00${w(st)}", "${side}G01${w(s)}F.1", "G01${w(e)}", "G01${w(f)}", "G40G01${w(en)}", "M30").joinToString("\n")
            val run = GSim.run(SimInput(text, MachineSetup(), mapOf(1 to ToolOffset(r, 3))))
            // 반올림한 NC 좌표로 다시 잰 접선
            val E = Pt(e.z.r3(), (e.xDia.r3()) / 2)
            val S = Pt(s.z.r3(), (s.xDia.r3()) / 2)
            val F = Pt(f.z.r3(), (f.xDia.r3()) / 2)
            val ua = (E - S).let { it * (1 / it.length) }
            val ub = (F - E).let { it * (1 / it.length) }
            val dt = ua dot ub
            val stop = run.stop
            if (dt >= 0) return@repeat
            if (stop != null) {
                if (stop.code != IssueCode.COMP_ACUTE_OUTER || stop.line != 7) bad += "δ $delta r $r $side — 뜻밖의 멈춤 $stop\n$text"
                return@repeat
            }
            acceptedOver++
            val sg = if (g42) -1.0 else 1.0
            val na = Pt(-ua.x, ua.z) * sg
            val nb = Pt(-ub.x, ub.z) * sg
            val p1 = E + na * r + ua * r
            val p2 = E + nb * r - ub * r
            val nose = run.ghosts.last { it.line == 6 }.nose
            val far = maxOf((nose - p1).length, (nose - p2).length)
            if (-dt > 0.01 + 1e-12 || far > 1e-3 + 1e-9) bad += "δ $delta r $r $side — 받았는데 sin ${-dt} · 끼운 토막과 $far\n$text"
        }
        println("Verify1112SimCornerTest.random: 받은 90° 넘음 $acceptedOver / 3000")
        assertTrue(acceptedOver > 0)
        assertTrue(bad.isEmpty(), bad.take(10).joinToString("\n\n"))
    }

    private fun Double.r3() = Math.round(this * 1000) / 1000.0
}
