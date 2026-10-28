package com.bmt.nosecalc.screens.sim

import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.sim.ContactKind
import com.bmt.nosecalc.calc.sim.StockProfile
import com.bmt.nosecalc.calc.sim.StockSim
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 궤적 창 숫자 지킴이(`UiGoldenTest` 와 같은 방식, 골든 `sim-trajectory`): 예제마다 자동 소재 · 원점 · 전체 시간 · 끝 위치 · 닿음 · 나사 ·
 * 남은 소재 단면 넓이 · 재생 50 % 의 S/F 카드와 위치, 그리고 스크린샷 장면마다 좌표 카드 · S/F 카드 · 가공 시간 · 띠.
 * 바뀌면 실패 — 일부러 바꿨을 때만 `-DupdateGolden=true`.
 */
class TrajGoldenTest {
    private val dir = File("src/jvmTest/resources/golden")
    private val update = System.getProperty("updateGolden") == "true"

    private fun store() = KeyValueStore(File.createTempFile("bmt-sim", ".properties").apply { delete() })

    private fun golden(name: String, text: String) {
        val f = File(dir, "$name.txt")
        if (update) {
            dir.mkdirs()
            f.writeText(text)
            return
        }
        assertTrue(f.exists(), "골든 파일이 없다: $f (-DupdateGolden=true 로 만든다)")
        assertEquals(f.readText().replace("\r\n", "\n"), text, "$name: 궤적 창 숫자가 골든과 다르다")
    }

    private fun area(p: StockProfile): Double = p.pieces.sumOf { pc ->
        val o = pc.outline
        var s = 0.0
        for (i in o.indices) {
            val a = o[i]
            val b = o[(i + 1) % o.size]
            s += a.z * b.x - b.z * a.x
        }
        abs(s) / 2
    }

    private fun f3(v: Double) = Fmt.fixed3(v)

    private fun StringBuilder.stockLines(sim: SimState) {
        val run = sim.run
        val spec = stockOf(sim.stockText, run).first
        if (spec == null) {
            appendLine("   소재: 없음(이송 없음 또는 틀린 칸)")
            return
        }
        appendLine("   소재 Ø${numText(spec.dia)} · 돌출 ${numText(spec.length)} · 끝면 Z${numText(spec.faceZ)} · 구멍 Ø${numText(spec.boreDia)} · 척 Ø${numText(spec.chuckDia)}")
        val s = StockSim(spec, run, sim.tools)
        s.cutTo(run.totalSec)
        val cs = s.contacts
        appendLine("   닿음: " + if (cs.isEmpty()) "없음" else cs.joinToString(" / ") { c ->
            "${c.kind} ${lineLabel(c.line)} ${xzText(c.at)} @${f3(c.t)}초" + if (c.kind == ContactKind.RAPID_CUT) " 넓이 ${f3(c.area)}" else ""
        })
        val prof = s.profile()
        for (t in prof.threads) {
            appendLine("   나사: 골 X${f3(t.root.start.xDia)}~X${f3(t.root.end.xDia)} Z${f3(t.root.start.z)}~Z${f3(t.root.end.z)} · 리드 ${numText(t.lead)} · ${numText(t.angleDeg)}° · ${if (t.outer) "바깥" else "안"}")
        }
        appendLine("   남은 소재: 조각 ${prof.pieces.size} · 단면 넓이 ${f3(area(prof))} mm² (열 ${s.columns} · dz ${s.dz})")
    }

    @Test
    fun examples() {
        val text = buildString {
            for ((i, e) in exampleList.withIndex()) {
                val sim = SimState(store()).apply {
                    perform(Pending.Example(i))
                    run = runOf(editor.text, tools)
                }
                val run = sim.run
                appendLine("== ${i + 1}. ${e.name} [${e.text.substringBefore(' ')}]")
                appendLine("   원점 ${xzText(run.start)} · 전체 ${timeText(run.totalSec)} (${f3(run.totalSec)} 초) · 걸음 ${run.steps.size} · 고스트 ${run.ghosts.size} · 싱글 블록 멈춤 ${PlayState(run).bounds.size}")
                appendLine("   끝 위치 ${xzText(run.poseAt(run.totalSec).tip)}")
                stockLines(sim)
                val mid = run.poseAt(run.totalSec / 2)
                appendLine("   50 %: ${lineLabel(mid.line)} · ${sText(mid)} · S ${mid.rpm.roundToInt()} · ${fUnit(mid)} · F ${mid.feed?.let(::feedText) ?: "—"} · 위치 ${xzText(mid.tip)}")
            }
        }
        golden("sim-trajectory", text)
    }

    /** 스크린샷 장면의 숫자(좌표 카드 · S/F · 시간 · 띠) — 06 의 X34.000 Z-2.186 이 그대로 나와야 */
    @Test
    fun scenes() {
        val text = buildString {
            for (s in TrajScenes.all) {
                val sim = TrajScenes.sim(store(), s)
                val ui = TrajScenes.ui(sim, s)
                val run = sim.run
                val pose = run.poseAt(ui.lastT)
                appendLine("== ${s.name}")
                appendLine("   시각 ${timeText(ui.lastT)} / 전체 ${timeText(run.totalSec)} · 지금 ${lineLabel(pose.line)} · ${sText(pose)} · S ${pose.rpm.roundToInt()} · ${fUnit(pose)} · F ${pose.feed?.let(::feedText) ?: "—"}")
                val g = ui.pick?.let { run.ghosts[it.ghost] }
                when {
                    g != null && ui.pick!!.measure -> appendLine("   측정점 ${xzText(g.cmd)} (${lineLabel(g.line)})")
                    g != null -> appendLine("   보정 좌표 ${xzText(g.tip)} (${lineLabel(g.line)}) · 측정점 ${xzText(g.cmd)}")
                    else -> appendLine("   위치 (절대) ${xzText(pose.tip)}")
                }
                appendLine("   멈춤: ${run.stop?.let { "${lineLabel(it.line)} ${it.code}: ${simIssueText(it)}" } ?: "없음"}")
                stockLines(sim)
            }
        }
        golden("sim-trajectory-scenes", text)
        // 원본 06 손 계산(설계 9.2): 보정 좌표 X34.000 Z-2.186
        val sim = TrajScenes.sim(store(), TrajScenes.inner06)
        val g = sim.run.ghosts[assertNotNull(TrajScenes.ui(sim, TrajScenes.inner06).pick).ghost]
        assertEquals("X34.000 Z-2.186", xzText(g.tip))
    }
}
