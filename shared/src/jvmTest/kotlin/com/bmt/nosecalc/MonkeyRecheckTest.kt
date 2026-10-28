package com.bmt.nosecalc

import androidx.compose.runtime.saveable.SaverScope
import com.bmt.nosecalc.UiMonkeyTest.Plan
import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.ThreadDir
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.calc.sim.ToolOffset
import com.bmt.nosecalc.screens.CuttingScreen
import com.bmt.nosecalc.screens.PolarScreen
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.ThreadScreen
import com.bmt.nosecalc.screens.TriangleScreen
import com.bmt.nosecalc.screens.cutting.CutBox
import com.bmt.nosecalc.screens.cutting.cutHand
import com.bmt.nosecalc.screens.cutting.drillIscarRows
import com.bmt.nosecalc.screens.cutting.drillSandvik
import com.bmt.nosecalc.screens.polar.SimStart
import com.bmt.nosecalc.screens.polar.polarOriginal07
import com.bmt.nosecalc.screens.polar.polarTriangle
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundInnerBlindC
import com.bmt.nosecalc.screens.round.roundInnerExample
import com.bmt.nosecalc.screens.round.roundManualBackBoring
import com.bmt.nosecalc.screens.sim.Pending
import com.bmt.nosecalc.screens.sim.ProgramStore
import com.bmt.nosecalc.screens.sim.ShiftState
import com.bmt.nosecalc.screens.sim.SimAsk
import com.bmt.nosecalc.screens.sim.SimMonkeyTest
import com.bmt.nosecalc.screens.sim.SimSheet
import com.bmt.nosecalc.screens.sim.SimShotsTest
import com.bmt.nosecalc.screens.sim.SimState
import com.bmt.nosecalc.screens.sim.TrajSheet
import com.bmt.nosecalc.screens.sim.exampleList
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.example
import com.bmt.nosecalc.screens.thread.Pick
import com.bmt.nosecalc.screens.thread.ThreadField
import com.bmt.nosecalc.screens.thread.ThreadState
import com.bmt.nosecalc.screens.thread.threadMetricInner
import com.bmt.nosecalc.screens.thread.threadOriginal
import com.bmt.nosecalc.screens.triangle.TriangleState
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 몽키 다시 확인(10-29): 고친 판(213d3d6 — 공구 설정의 빈칸 팁 '고르기')에서 두 몽키 시험([SimMonkeyTest] · [UiMonkeyTest])을
 * **다른 씨앗 · 다른 시작 상태 · 깊이 5** 로 다시 돌린다. 시험 틀은 두 시험의 것을 그대로 쓰고 시작 상태만 새로 둔다.
 *
 * 노린 곳(앞선 사냥의 빈 곳 · 고친 곳 둘레):
 * - 시뮬레이터: 팁이 빈칸인 카드의 종류마다(홈 · 나사 · 둥근 · 넷 모두 빈칸 · 저장 복원) '고르기', 공구 카드 차례를 앞에서 안 쓴 설정(412 큰 글꼴 다크 ·
 *   360 라이트)으로. 궤적 창 예제 7~11(앞에서 고스트를 한 번도 못 고른 곳)은 겨눈 손짓을 모든 층에서(처음 층 10×8) · 두 손가락 핀치(2D · 3D)를 더해서.
 *   편집기는 앞에서 시간 안에 못 간 깊은 곳(⋯ · 저장본 · 문제 목록 시트, 묻는 창 셋, 찾기 + SHIFT 고정)에서 시작한다.
 * - 다른 화면: 남은 큐가 컸던 테이퍼 홑 · 2중 · 라운드 내경 · 나사 · 드릴을 키패드 · 시트가 열린 상태나 팁을 바꾼 상태(0 · 1 · 7 · 8)에서,
 *   손가락만 받는 곳(형상 뷰어 '크게 보기' 안 끌기 · 벌리기 · 두 번 누름, 직각삼각형 그림, 극좌표 속도 막대)은 손가락을 그리로 모아서.
 *
 * 씨앗: 시뮬레이터는 [SEED](앞은 20261028), 다른 화면은 시작 상태 이름 · 설정에서 만든다(이름이 모두 새것이라 씨앗도 새것).
 * 한 부분(시험 함수) 15분 안. BMT_MONKEY 가 있을 때만 돈다(없으면 건너뜀 — 평소 jvmTest 에 70분이 끼지 않게).
 * 시뮬레이터 부분은 다른 화면 부분과 따로 돌린다(같은 시험 JVM 이면 앞 부분이 키운 메모리에 시뮬레이터 몽키의 메모리 지킴이가 걸린다):
 * `cmd /c "set BMT_MONKEY=1&& gwh.bat :shared:jvmTest --tests com.bmt.nosecalc.MonkeyRecheckTest.sim* --rerun"`
 * 결과는 build/monkey/recheck-*.txt
 */
class MonkeyRecheckTest {
    @BeforeTest
    fun onlyWhenAsked() = assumeTrue(System.getenv("BMT_MONKEY") != null, "BMT_MONKEY 가 없어 건너뜀(70분 걸리는 다시 확인)")

    // ───────────────────────── 시뮬레이터(SimMonkeyTest 의 틀)

    /** 고친 곳 둘레: 팁이 빈칸(—)인 카드를 종류마다 · 공구 넷 모두 빈칸 · 저장 뒤 복원한 빈칸, 그리고 공구 카드 차례(앞과 다른 설정) */
    @Test
    fun simTools() = SimMonkeyTest().recheck(
        "recheck-tools", SEED,
        listOf(
            SimJob(tools("빈칸 팁 홈 카드(G42)", 12 to ToolOffset(kind = ToolKind.GROOVE)), SimMonkeyTest.C412L, 5),
            SimJob(tools("빈칸 팁 나사 카드(노즈 R 만)", 12 to ToolOffset(noseR = 0.2, kind = ToolKind.THREAD)), SimMonkeyTest.C360S, 5),
            SimJob(tools("빈칸 팁 둥근 카드(노즈 R 0)", 12 to ToolOffset(noseR = 0.0, kind = ToolKind.ROUND)), SimMonkeyTest.C360, 5),
            SimJob(
                tools("빈칸 팁 바이트 V · κr 끝 값", 12 to ToolOffset(noseR = 50.0, insert = InsertShape.V, kappaDeg = 179.9), 7 to ToolOffset(kind = ToolKind.GROOVE, width = 100.0)),
                SimMonkeyTest.C412, 5,
            ),
            SimJob(SimMonkeyTest.Start("공구 넷 모두 빈칸", traj = true, sheet = TrajSheet.TOOLS, prep = { editor.replaceAll(SimMonkeyTest.MULTI) }), SimMonkeyTest.C360, 5),
            SimJob(RESTORED_BLANK_TIP, SimMonkeyTest.C412L, 5),
        ),
        matrix = listOf(SimMonkeyTest.TOOL_CARD to SimMonkeyTest.C412L, SimMonkeyTest.TOOL_CARD to SimMonkeyTest.C360S),
    )

    /** 궤적 창 예제 7~11: 겨눈 손짓을 모든 층에서(고스트 · 측정점 · 속도 막대 · 핀치), 설정은 앞(trajectory-2)과 어긋나게 */
    @Test
    fun simTrajectory() = SimMonkeyTest().recheck(
        "recheck-trajectory", SEED,
        (6 until exampleList.size).map { i ->
            SimJob(SimMonkeyTest.Start("예제 ${i + 1} 궤적 창 · 재검", traj = true, prep = { perform(Pending.Example(i)) }), SimMonkeyTest.CFGS[(i + 2) % 4], 5)
        },
    )

    /** 편집기의 깊은 곳(앞에서 시간 안에 못 간 곳)에서 시작: 시트 셋 · 묻는 창 셋 · 찾기 + SHIFT 고정 + 끝 커서, 그리고 오류 · 공구 없음 궤적 창의 공구 시트 */
    @Test
    fun simEditor() = SimMonkeyTest().recheck(
        "recheck-editor", SEED,
        listOf(
            SimJob(SimMonkeyTest.Start("⋯ 시트 열림 · 예제 3", prep = { perform(Pending.Example(2)); sheet = SimSheet.MORE }), SimMonkeyTest.C412L, 5),
            SimJob(SimMonkeyTest.Start("저장본 목록 열림(저장 셋)", kv = ::savedThree, prep = { sheet = SimSheet.PROGRAMS }), SimMonkeyTest.C360, 5),
            SimJob(SimMonkeyTest.Start("문제 목록 열림(오류 글)", prep = { editor.replaceAll(SimShotsTest.ERRORS); sheet = SimSheet.ISSUES }), SimMonkeyTest.C412, 5),
            SimJob(
                SimMonkeyTest.Start("묻는 창: 저장 안 한 글 → 예제 5", kv = ::savedThree, prep = { editor.replaceAll(exampleList[0].text + "\nG00 X1."); ask = SimAsk.Unsaved(Pending.Example(4)) }),
                SimMonkeyTest.C360S, 5,
            ),
            SimJob(SimMonkeyTest.Start("묻는 창: 저장본 지우기", kv = ::savedThree, prep = { ask = SimAsk.Delete(2001) }), SimMonkeyTest.C412L, 5),
            SimJob(SimMonkeyTest.Start("묻는 창: 번호 없음(저장)", prep = { editor.replaceAll("G00 X10. Z0.\nM30"); ask = SimAsk.Number }), SimMonkeyTest.C360, 5),
            SimJob(
                SimMonkeyTest.Start("찾기 · SHIFT 고정 · 끝 커서(극단 값 글)", prep = {
                    editor.replaceAll(SimMonkeyTest.EXTREME)
                    editor.moveTo(editor.text.length)
                    openFind()
                    editQuery("999")
                    shift = ShiftState.LOCK
                }),
                SimMonkeyTest.C412, 5,
            ),
            SimJob(SimMonkeyTest.Start("오류 글 궤적 창 · 공구 시트", traj = true, sheet = TrajSheet.TOOLS, prep = { editor.replaceAll(SimShotsTest.ERRORS) }), SimMonkeyTest.C360S, 5),
        ),
    )

    // ───────────────────────── 다른 화면(UiMonkeyTest 의 틀)

    /** 테이퍼: 키패드가 열린 채 · 팁 0 · 7 · 8 · 직접 보정 · 2중 내경 역(앞에서 큐가 남은 곳), 형상 뷰어 '크게 보기' 안에서 손가락 */
    @Test
    fun uiTaper() = UiMonkeyTest().run {
        monkey(
            "recheck-taper",
            listOf(
                app("테이퍼 처음(빈칸) · 재검", Feature.TAPER) to Plan(depth = 5, seconds = 130, touches = 60, restoreSteps = 12),
                screen("테이퍼 홑 내경 역 형상 2 · 키패드(x1)", Feature.TAPER) {
                    TaperScreen(taper(TaperKind.INNER, true, 2) { editing = "x1" })
                } to Plan(depth = 5, seconds = 90),
                screen("테이퍼 2중 외경 정 형상 3 · 팁 7", Feature.TAPER) { TaperScreen(taper(TaperKind.OUTER_DOUBLE, false, 3) { tip = 7 }) } to Plan(depth = 5, seconds = 90, restoreSteps = 8),
                screen("테이퍼 2중 내경 역 형상 1 · 팁 0 · 직접 보정", Feature.TAPER, { it.compMode = CompMode.DIRECT }) {
                    TaperScreen(taper(TaperKind.INNER_DOUBLE, true, 1) { tip = 0 })
                } to Plan(depth = 5, seconds = 80),
                screen("테이퍼 홈 형상 2 · 팁 8 · 키패드(z2)", Feature.TAPER) {
                    TaperScreen(taper(TaperKind.GROOVE, false, 2) { tip = 8; editing = "z2" })
                } to Plan(depth = 5, seconds = 70),
                screen("테이퍼 홑 외경 정 형상 1 · 크게 보기 손가락", Feature.TAPER) {
                    TaperScreen(taper(TaperKind.OUTER, false, 1))
                } to Plan(depth = 2, seconds = 15, touches = 200, lead = listOf("크게 보기")),
            ),
            cfgShift = 1,
        )
    }

    /** 라운드 내경 · 3점호(팁 1 · 키패드), 직각삼각형 그림 · 극좌표 시뮬레이션 막대 · 형상 뷰어 크게 보기에 손가락을 모아서 */
    @Test
    fun uiRound() = UiMonkeyTest().run {
        monkey(
            "recheck-round",
            listOf(
                screen("라운드 예시 내경 형상 5 · 재검", Feature.ROUND) { RoundScreen(roundInnerExample(5)) } to Plan(depth = 5, seconds = 110, restoreSteps = 8),
                screen("라운드 보링바 C 막힘 · 재검", Feature.ROUND) { RoundScreen(roundInnerBlindC()) } to Plan(depth = 5, seconds = 90),
                screen("라운드 3점호 뒤보링 · 팁 1 · 키패드(x2)", Feature.ROUND) {
                    RoundScreen(roundManualBackBoring().apply { tip = 1; editing = "x2" })
                } to Plan(depth = 5, seconds = 80),
                screen("라운드 예시 외경 형상 2 · 키패드(l2)", Feature.ROUND) { RoundScreen(roundExample(2, open = true).apply { editing = "l2" }) } to Plan(depth = 5, seconds = 50),
                screen("라운드 3점호 · 크게 보기 손가락", Feature.ROUND) {
                    RoundScreen(roundExample(MANUAL, open = true))
                } to Plan(depth = 2, seconds = 15, touches = 160, lead = listOf("크게 보기")),
                screen("직각삼각형 b40 · B35 · 그림 손가락", Feature.TRIANGLE) {
                    TriangleScreen(TriangleState(mapOf("b" to "40", "angB" to "35")))
                } to Plan(depth = 4, seconds = 50, touches = 180),
                screen("극좌표 삼각 시뮬레이션(싱글 블록) · 막대 손가락", Feature.POLAR) {
                    PolarScreen(polarTriangle(), sim = SimStart(0, 0.5, single = true))
                } to Plan(depth = 2, seconds = 25, touches = 180),
            ),
            cfgShift = 2,
        )
    }

    /** 나사 · 드릴 · 절삭(앞에서 큐가 남은 곳): 시트 · 키패드가 열린 상태에서 깊이 5, 극좌표 도형 시트 */
    @Test
    fun uiThreadDrill() = UiMonkeyTest().run {
        monkey(
            "recheck-thread-drill",
            listOf(
                screen("나사 원본 08 · 피치 시트", Feature.THREAD) { ThreadScreen(threadOriginal(), sheet = Pick.PITCH) } to Plan(depth = 5, seconds = 90, restoreSteps = 8),
                screen("나사 내경 M10 · 급수 시트", Feature.THREAD) { ThreadScreen(threadMetricInner(), sheet = Pick.CLASS) } to Plan(depth = 5, seconds = 80),
                screen("나사 극단 역 · 키패드(첫 절입)", Feature.THREAD) {
                    ThreadScreen(
                        ThreadState(
                            spec = ThreadTable.pitches(ThreadKind.METRIC, "M10").first(), material = IsoGroup.N, dir = ThreadDir.REVERSE, inner = true,
                            values = mapOf("zStart" to "-999999999999", "zEnd" to "999999999999", "firstCut" to "0.00000000001", "rpm" to "999999999999"), advOpen = true,
                        ),
                        editing = ThreadField.FIRST_CUT,
                    )
                } to Plan(depth = 5, seconds = 60),
                screen("드릴 ISCAR P 군 · 키패드(드릴 지름)", Feature.CUTTING) { CuttingScreen(drillIscarRows(), editing = CutBox.DRILL_D) } to Plan(depth = 5, seconds = 100, restoreSteps = 8),
                screen("드릴 Sandvik · 재검", Feature.CUTTING) { CuttingScreen(drillSandvik()) } to Plan(depth = 5, seconds = 90),
                screen("절삭 손 계산 · 키패드(노즈 R)", Feature.CUTTING) { CuttingScreen(cutHand(), editing = CutBox.NOSE_R) } to Plan(depth = 5, seconds = 70),
                screen("극좌표 원본 07 · 도형 시트", Feature.POLAR) { PolarScreen(polarOriginal07(), sheet = true) } to Plan(depth = 5, seconds = 60),
            ),
            cfgShift = 3,
        )
    }

    private companion object {
        /** 시뮬레이터 몽키 씨앗(앞 사냥은 20261028) */
        const val SEED = 20261029L

        fun SimJob(start: SimMonkeyTest.Start, cfg: SimMonkeyTest.Cfg, depth: Int) = SimMonkeyTest.Job(start, cfg, depth)

        /** 공구 시트가 열린 궤적 창(T1212 · G42 프로그램)에 공구표를 넣는다 */
        fun tools(name: String, vararg t: Pair<Int, ToolOffset>) = SimMonkeyTest.Start(name, traj = true, sheet = TrajSheet.TOOLS, prep = {
            editor.replaceAll(SimMonkeyTest.NO_TOOL)
            tools = t.toMap()
        })

        /** 저장한 프로그램 셋(예제 1 · 오류 · 극단 값) */
        fun savedThree(kv: KeyValueStore) {
            val p = ProgramStore(kv)
            p.save(1001, exampleList[0].text)
            p.save(2001, SimShotsTest.ERRORS)
            p.save(3001, SimMonkeyTest.EXTREME)
        }

        /** 공구표(빈칸 팁 홈 · 팁 없는 노즈 R)를 화면 상태 Saver 로 저장했다 되살려 공구 시트를 연다 — 폰이 앱을 되살린 뒤 '고르기' */
        val RESTORED_BLANK_TIP = SimMonkeyTest.Start(
            "저장 뒤 복원 · 빈칸 팁 공구표", traj = true, sheet = TrajSheet.TOOLS,
            make = { kv ->
                val a = SimState(kv).apply {
                    editor.replaceAll(SimMonkeyTest.NO_TOOL)
                    tools = mapOf(12 to ToolOffset(kind = ToolKind.GROOVE, width = 3.0), 3 to ToolOffset(noseR = 0.8))
                }
                val saver = SimState.saver(kv)
                saver.restore(with(saver) { SaverScope { true }.save(a) }!!)!!
            },
        )

        fun taper(kind: TaperKind, reverse: Boolean, type: Int, edit: TaperState.() -> Unit = {}) = TaperState(kind, reverse, type, open = true).example().apply(edit)
    }
}
