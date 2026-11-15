package com.bmt.nosecalc.screens.thread

import androidx.compose.runtime.saveable.SaverScope
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.DieHeadCalc
import com.bmt.nosecalc.calc.DieHeadCatalog
import com.bmt.nosecalc.calc.DieInput
import com.bmt.nosecalc.calc.DieWarning
import com.bmt.nosecalc.calc.InsertForm
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.PipeKind
import com.bmt.nosecalc.calc.PipeTable
import com.bmt.nosecalc.calc.ThreadInsert
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 나사 화면의 다이스 헤드 · 인서트(2026-11-06): 다이스 입력 칸 · 회사 값 저장소 왕복 · 가공 방식이 바이트로 돌아감 · 범위 밖 칸 = 계산기 BAD_INPUT ·
 * 멈춤 글 · 인서트 고르기 · 카탈로그 줄. 기대값은 손 계산(식과 수를 주석에) · 카탈로그 원문 이름이다. 치수 · 절삭 속도는 새로 지은 값이다
 */
class ThreadDieStateTest {
    private fun file() = File.createTempFile("bmt-die", ".properties").apply { delete() }
    private fun store(values: Map<String, String> = emptyMap()) = DieStore(KeyValueStore(file()).apply { values.forEach { (k, v) -> put(k, v) } })
    private fun near(e: Double, a: Double, tol: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a")
    private fun metric(size: String, pitch: Double) = ThreadTable.pitches(ThreadKind.METRIC, size).first { it.pitch == pitch }
    private fun un(size: String, tpi: Int) = ThreadTable.pitches(ThreadKind.UN, size).first { it.tpi == tpi }

    /**
     * [threadDie] + [threadDieStore]: M16×2(d 16 · P 2), P 군 Vc 8, Z 5 → −24, 끝면 빈칸 → 0, 비율 · 멈춤 · 물러남 빈칸 → 0.96 · 1 · 150, 플로트 1.5 · 목 2.5.
     * n = 1000 × 8 / (π × 16) = 159.155 → S159, 쓴 Vc = π × 16 × 159 / 1000 = 7.992212, F = 0.96 × 2 = 1.92, 분당 1.92 × 159 = 305.28,
     * 시간 = 60 × (5 + 24) / 305.28 + 1 = 5.699686 + 1 = 6.699686 초, 당김 = 0.04 / 0.96 × (0 + 24) = 1.0 ≤ 플로트 1.5, 완전 나사 끝 = −24 + 2.5 × 2 = −19,
     * 멈춤 동안 체이서 이동 = 159 / 60 × 1 × 2 = 5.3(1.0 + 5.3 ≥ 1.5 — 멈춤 동안 열림), 가장 깊은 체이서 끝 = −24 − 1.5 = −25.5
     */
    @Test
    fun dieInputFields() {
        val s = threadDie()
        val d = store(threadDieStore())
        assertTrue(s.isDie)
        assertEquals(
            DieInput(
                spec = metric("M16", 2.0), vc = 8.0, rpm = null, zStart = 5.0, zEnd = -24.0, ratio = 0.96, dwell = 1.0, retractZ = 150.0,
                approachZ = 20.0, floatTravel = 1.5, throatThreads = 2.5, zFace = 0.0,
            ),
            s.dieInput(d),
        )
        val r = DieHeadCalc.calc(s.dieInput(d))
        assertEquals(emptyList(), r.warnings)
        val sol = assertNotNull(r.solution)
        assertEquals(159L, sol.rpm)
        near(7.992211710, sol.vcUsed, 1e-8, "쓴 Vc")
        near(1.92, sol.feed, 1e-12, "이송")
        near(305.28, sol.feedPerMin, 1e-9, "분당 이송")
        near(6.699685535, sol.timeSec, 1e-8, "시간")
        near(1.0, sol.floatPull, 1e-12, "당김")
        near(-19.0, sol.fullThreadEndZ!!, 1e-12, "완전 나사 끝")
        near(5.3, sol.dwellTravel, 1e-12, "멈춤 동안 이동")
        near(-25.5, sol.chaserEndZ!!, 1e-12, "체이서 끝")
        assertEquals(listOf("G97S159", "G0X0.Z20.", "Z5.", "G1Z-24.F1.92", "G4U1.", "G0Z150."), sol.program)
        // 결과 칸(화면 · 골든이 같이 씀)의 값: S · F · 분당 · 시간(소수 한 자리) · 당김 · 완전 나사 끝 · 멈춤 동안 이동 · 체이서 끝
        assertEquals(listOf("S159", "F1.92", "305.3", "6.7", "1.000", "Z-19.", "5.300", "Z-25.5"), dieResultCells(s, d, sol).map { it.second })
        // 회전수를 넣으면 Vc 대신(200), 끝면 Z 를 넣으면 그 값(−2 → 당김 0.04 / 0.96 × 22 = 0.916667)
        s.set(ThreadField.RPM, "200")
        s.set(ThreadField.Z_REF, "-2")
        assertEquals(200.0, s.dieInput(d).rpm)
        near(22.0 / 24, DieHeadCalc.calc(s.dieInput(d)).solution!!.floatPull, 1e-12, "끝면 −2 의 당김")
        assertEquals(200L, DieHeadCalc.calc(s.dieInput(d)).solution!!.rpm)
    }

    /** Vc 는 가공 소재마다 따로: P 에만 넣었으니 M 으로 바꾸면 빈칸 → 회전수도 없으면 값 부족('절삭 속도 Vc (또는 회전수)') */
    @Test
    fun vcPerMaterial() {
        val s = threadDie()
        val d = store(threadDieStore())
        s.material = IsoGroup.M
        assertNull(s.dieInput(d).vc)
        assertEquals(listOf(DieWarning.MISSING_INPUT), DieHeadCalc.calc(s.dieInput(d)).warnings)
        assertEquals(listOf("절삭 속도 Vc (또는 회전수)"), dieMissing(s, d))
        d.set(DieField.VC, IsoGroup.M, "4")
        assertEquals(4.0, s.dieInput(d).vc)
        s.material = null
        assertNull(s.dieInput(d).vc)
        d.set(DieField.VC, null, "6")
        assertEquals(6.0, s.dieInput(d).vc)
        s.material = IsoGroup.P
        assertEquals(8.0, s.dieInput(d).vc)
        assertEquals(listOf("사이즈", "Z축 시작점", "가공 목표점 (Z)", "절삭 속도 Vc (또는 회전수)"), dieMissing(threadDieEmpty(), store()))
    }

    /** 저장소 왕복: 고친 값은 바로 파일에 쓰이고, 새 저장소(앱을 다시 켬)가 같은 글을 읽는다. 열쇠는 thread/die/…, 빈칸 글은 처음 값 */
    @Test
    fun storeRoundTrip() {
        val f = file()
        val a = DieStore(KeyValueStore(f))
        a.set(DieField.RATIO, null, "0.98")
        a.set(DieField.DWELL, null, "0.5")
        a.set(DieField.RETRACT, null, "120")
        a.set(DieField.FLOAT, null, "2")
        a.set(DieField.THROAT, null, "3")
        a.set(DieField.VC, IsoGroup.N, "30")
        a.set(DieField.VC, null, "7")
        val b = DieStore(KeyValueStore(f))
        for (field in DieField.entries) for (m in listOf(null) + IsoGroup.entries) assertEquals(a.value(field, m), b.value(field, m), "$field $m")
        assertEquals("0.98", b.value(DieField.RATIO, IsoGroup.K))
        assertEquals("30", b.value(DieField.VC, IsoGroup.N))
        assertEquals("7", b.value(DieField.VC, null))
        assertEquals("", b.value(DieField.VC, IsoGroup.P))
        val kv = KeyValueStore(f)
        assertEquals("0.98", kv.get("thread/die/ratio"))
        assertEquals("120", kv.get("thread/die/retract"))
        assertEquals("30", kv.get("thread/die/vc/N"))
        assertEquals("7", kv.get("thread/die/vc"))
        // 입력: 넣은 회사 값, 비운 칸은 처음 값(사용자 결정 0.96 · 1 · 150)
        val s = threadDie().apply { material = IsoGroup.N }
        val i = s.dieInput(b)
        assertEquals(listOf(30.0, 0.98, 0.5, 120.0, 2.0, 3.0), listOf(i.vc, i.ratio, i.dwell, i.retractZ, i.floatTravel, i.throatThreads))
        b.set(DieField.RATIO, null, "")
        b.set(DieField.DWELL, null, "")
        b.set(DieField.RETRACT, null, "")
        val back = s.dieInput(DieStore(KeyValueStore(f)))
        assertEquals(listOf(0.96, 1.0, 150.0), listOf(back.ratio, back.dwell, back.retractZ))
    }

    /** 가공 방식: 다이스 헤드는 M · UN 숫나사만 — 내경 · 관용 테이퍼로 가면 바이트로 돌아가고, 되돌아와도 바이트 그대로. 못 고르는 상태에서는 받지 않는다 */
    @Test
    fun methodFallsBack() {
        val s = threadDie()
        assertTrue(s.dieAllowed)
        s.switchInner(true)
        assertEquals(ThreadMethod.BITE, s.method)
        assertFalse(s.isDie)
        assertFalse(s.dieAllowed)
        s.pickMethod(ThreadMethod.DIE)
        assertEquals(ThreadMethod.BITE, s.method)
        s.switchInner(false)
        assertEquals(ThreadMethod.BITE, s.method)
        s.pickMethod(ThreadMethod.DIE)
        assertTrue(s.isDie)
        s.switchFamily(ThreadFamily.PT)
        assertEquals(ThreadMethod.BITE, s.method)
        s.pickMethod(ThreadMethod.DIE)
        assertEquals(ThreadMethod.BITE, s.method)
        s.switchFamily(ThreadFamily.UN)
        s.pickMethod(ThreadMethod.DIE)
        assertTrue(s.isDie)
        // 같은 종류를 다시 고르거나 미터 ↔ UN 은 그대로
        s.switchFamily(ThreadFamily.METRIC)
        assertTrue(s.isDie)
        // 되살린 상태도 같은 규칙(내경 · 관용 테이퍼에 다이스가 남지 않음)
        assertEquals(ThreadMethod.BITE, ThreadState(inner = true, method = ThreadMethod.DIE).method)
        assertEquals(ThreadMethod.BITE, ThreadState(family = ThreadFamily.NPT, method = ThreadMethod.DIE).method)
        // 다이스 화면의 칸은 Z 시작 · 회전수 · Z 끝 · 끝면 Z 넷, 회전수 자리표시는 'Vc 로'
        assertEquals(listOf(ThreadField.Z_START, ThreadField.RPM, ThreadField.Z_END, ThreadField.Z_REF), threadDie().fields)
        assertEquals("Vc 로", threadDie().placeholder(ThreadField.RPM))
        assertEquals("안 씀", threadOriginal().placeholder(ThreadField.RPM))
    }

    /** 저장 · 되살리기: 가공 방식 · 인서트 · 카탈로그 줄까지 그대로, 2026-11-05 글(셋이 없음)은 바이트 · 없음 */
    @Test
    fun saverRoundTrip() {
        val scope = SaverScope { true }
        val s = threadDie()
        val back = ThreadState.Saver.restore(with(ThreadState.Saver) { scope.save(s) }!!)!!
        assertEquals(ThreadMethod.DIE, back.method)
        assertEquals(s.dieSpeed, back.dieSpeed)
        assertEquals(s.dieInput(store()), back.dieInput(store()))
        val withInsert = threadOriginal().apply { pickInsert(insertChoices.first()) }
        val back2 = ThreadState.Saver.restore(with(ThreadState.Saver) { scope.save(withInsert) }!!)!!
        assertEquals("VDX_TT|3ER20UN", back2.insert)
        assertEquals(withInsert.shownInsert, back2.shownInsert)
        assertEquals(ThreadMethod.BITE, back2.method)
        // 2026-11-05 글: 앞 20칸 + 관용 크기 · 묶음 · 낱말 + 새 칸 둘 = 25칸
        val saved = with(ThreadState.Saver) { scope.save(threadPtInt()) } as List<*>
        val old = saved.take(25)
        assertEquals(31, saved.size)
        val back3 = ThreadState.Saver.restore(old)!!
        assertEquals(ThreadMethod.BITE, back3.method)
        assertNull(back3.insert)
        assertNull(back3.dieSpeed)
        assertEquals(threadPtInt().pipeInput(), back3.pipeInput())
    }

    /** 칸마다 범위 밖 값: 화면이 짚는 칸(숫자칸 bad · 다이스 조건 dieBad)이 있으면 계산기가 BAD_INPUT, 없으면 BAD_INPUT 이 아니다 */
    @Test
    fun badFieldsMatchCalc() {
        // 회전수 경계(검토 a1 — 전조와 같은 꼴): 위 한도는 찍힐 S(반올림)로 — 100000.3 은 S100000 으로 풀리고 100000.5 는 멈춘다
        val values = listOf(
            "-20000", "-1", "-0.1", "0", "0.0002", "0.5", "0.99", "1", "3", "100000", "100000.3", "100000.5", "100001", "6000", "20000", "2000000", "NaN", "Infinity",
        )
        var checked = 0
        var flagged = 0
        for (v in values) {
            for (f in threadDie().fields) {
                val s = threadDie()
                val d = store(threadDieStore())
                s.set(f, v)
                val w = DieHeadCalc.calc(s.dieInput(d)).warnings
                val bad = s.bad() + dieBad(s, d).map { it.name }
                assertEquals(bad.isNotEmpty(), DieWarning.BAD_INPUT in w, "$f = $v: $bad / $w")
                if (s.bad().isNotEmpty()) assertEquals(setOf(f), s.bad())
                if (bad.isNotEmpty()) flagged++
                checked++
            }
            for (f in DieField.entries) {
                val s = threadDie()
                val d = store(threadDieStore())
                d.set(f, s.material, v)
                val w = DieHeadCalc.calc(s.dieInput(d)).warnings
                val bad = dieBad(s, d)
                // Vc 큰 값(Ø16 에 S > 100000)은 지름과 엮인 것 — 칸은 짚지 않고 글이 회전수 S 를 말한다
                // (Vc 0.0002 처럼 아주 작은 값은 S 가 0 으로 — 같은 갈래)
                val sOver = f == DieField.VC && v.toDouble().let { vc ->
                    val n = vc * 1000 / (Math.PI * 16)
                    vc.isFinite() && vc > 0 && vc <= 1_000_000 && (n < 0.5 || n > 100_000.5)
                }
                if (sOver) {
                    assertEquals(listOf(DieWarning.BAD_INPUT), w, "$f = $v")
                    assertTrue(bad.isEmpty())
                    assertEquals("계산한 회전수 S 가 1 ~ 100000 밖입니다 — 절삭 속도 Vc(또는 회전수)와 사이즈를 확인하세요.", dieWarningText(DieWarning.BAD_INPUT, s, d))
                } else {
                    assertEquals(bad.isNotEmpty(), DieWarning.BAD_INPUT in w, "$f = $v: $bad / $w")
                    if (bad.isNotEmpty()) assertEquals(setOf(f), bad)
                }
                if (bad.isNotEmpty()) flagged++
                checked++
            }
        }
        assertTrue(checked >= 150 && flagged >= 50, "$checked / $flagged")
    }

    /** 멈춤 · 확인 글과 짚는 칸: Z 순서 다섯 까닭(계산기 차례), 숫자 든 노랑 */
    @Test
    fun warningTexts() {
        fun case(edit: ThreadState.(DieStore) -> Unit): Triple<ThreadState, DieStore, List<DieWarning>> {
            val s = threadDie()
            val d = store(threadDieStore())
            s.edit(d)
            return Triple(s, d, DieHeadCalc.calc(s.dieInput(d)).warnings)
        }
        // 시작 ≤ 끝 → 두 Z
        case { set(ThreadField.Z_START, "-30") }.let { (s, d, w) ->
            assertEquals(listOf(DieWarning.Z_ORDER), w)
            assertEquals(setOf(ThreadField.Z_START, ThreadField.Z_END) to emptySet(), dieFlagged(s, d, w))
            assertTrue(dieWarningText(w[0], s, d).startsWith("다이스 헤드는 정가공(Z−)만"))
        }
        // 시작이 진입 Z20 보다 큼
        case { set(ThreadField.Z_START, "25") }.let { (s, d, w) ->
            assertEquals(setOf(ThreadField.Z_START) to emptySet(), dieFlagged(s, d, w))
            assertEquals("Z축 시작점(Z25.)이 진입 Z20 보다 큽니다 — Z20. 에서 시작점으로 급송하는 글이라 시작점은 그 이하로 하세요.", dieWarningText(w[0], s, d))
        }
        // 물러남 Z3 < 시작 Z5 → 시작 · 물러남
        case { it.set(DieField.RETRACT, null, "3") }.let { (s, d, w) ->
            assertEquals(setOf(ThreadField.Z_START) to setOf(DieField.RETRACT), dieFlagged(s, d, w))
            assertTrue(dieWarningText(w[0], s, d).startsWith("물러남 Z 가 Z축 시작점(Z5.)보다 작습니다"))
        }
        // 시작 Z −1 이 끝면(빈칸 0) 안 → 시작 · 끝면
        case { set(ThreadField.Z_START, "-1") }.let { (s, d, w) ->
            assertEquals(listOf(DieWarning.Z_ORDER), w)
            assertEquals(setOf(ThreadField.Z_START, ThreadField.Z_REF) to emptySet(), dieFlagged(s, d, w))
            assertTrue(dieWarningText(w[0], s, d).startsWith("Z축 시작점(Z-1.)이 공작물 끝면(Z0.) 안입니다"))
        }
        // 끝면 −24 ≤ 끝 Z −24 → 끝 · 끝면
        case { set(ThreadField.Z_REF, "-24") }.let { (s, d, w) ->
            assertEquals(setOf(ThreadField.Z_END, ThreadField.Z_REF) to emptySet(), dieFlagged(s, d, w))
            assertTrue(dieWarningText(w[0], s, d).startsWith("공작물 끝면(Z-24.)이 가공 목표점보다 작거나 같아"))
        }
        // 플로트 0.5 < 당김 1.0 → 노랑, 숫자 든 글
        case { it.set(DieField.FLOAT, null, "0.5") }.let { (s, d, w) ->
            assertEquals(listOf(DieWarning.FLOAT_EXCEEDED), w)
            val sol = DieHeadCalc.calc(s.dieInput(d)).solution
            assertEquals(
                "끝 당김 길이(1.000 mm)가 플로트(0.500 mm)보다 깁니다 — G1 끝 전에 머리가 열려 나사가 덜 깎일 수 있습니다. 이송 비율을 1 에 가깝게 하거나 플로트를 확인하세요.",
                dieWarningText(w[0], s, d, sol),
            )
        }
        // 비율 1.02 → 노랑 + 비율 ≠ 1 이라 카탈로그 안내도(계산기 note)
        case { it.set(DieField.RATIO, null, "1.02") }.let { (s, d, w) ->
            assertEquals(listOf(DieWarning.RATIO_OVER_ONE), w)
            assertNotNull(DieHeadCalc.calc(s.dieInput(d)).solution!!.note)
        }
        // 비율 1 → 카탈로그 안내 없음
        case { it.set(DieField.RATIO, null, "1") }.let { (s, d, _) -> assertNull(DieHeadCalc.calc(s.dieInput(d)).solution!!.note) }
    }

    /**
     * 인서트: 내외 · 종류 · 산수에 맞는 카탈로그 줄(VARDEX · Carmex 원문 이름). UN 20산 외경 → 3ER20UN · 16 ER 20 UN, 내경 → 3IR20UN · 16 IR 20 UN,
     * PT R 3/4(14산) → BSPT, NPT 3/4(14산) → NPT, 미터는 자료가 없어 칸이 없다, UN 28산은 맞는 줄 없음
     */
    @Test
    fun insertChoices() {
        val s = threadOriginal()
        assertTrue(s.hasInsertData)
        assertEquals(listOf("3ER20UN · VARDEX", "16 ER 20 UN · Carmex"), s.insertChoices.map(::insertText))
        s.pickInsert(s.insertChoices[0])
        assertEquals("3ER20UN · VARDEX", s.shownInsert?.let(::insertText))
        // UN 줄은 날 끝 R 이 카탈로그에 없다 → 인선 R 칸은 그대로(빈칸), 비우면 규격 기본 R(숫나사 H/6)
        assertEquals("", s.value(ThreadField.TIP_R))
        assertEquals(
            "날 끝 R 은 카탈로그에 없음 — 비우면 규격 기본 R(숫나사 H/6), 인서트에 적힌 R 이 있으면 넣으세요. 풀 프로파일이라 산봉우리도 만듦" +
                "(소재가 목표보다 크면 첫 회가 깊어짐) · IC 3/8\" · 출처: VARDEX Thread Turning 카탈로그 26쪽",
            insertNote(s),
        )
        // 내경으로 바꾸면 그 줄이 맞지 않아 안 보이고, 돌아오면 다시 보인다
        s.switchInner(true)
        assertEquals(listOf("3IR20UN · VARDEX", "16 IR 20 UN · Carmex"), s.insertChoices.map(::insertText))
        assertNull(s.shownInsert)
        s.switchInner(false)
        assertEquals("3ER20UN · VARDEX", s.shownInsert?.let(::insertText))
        // 카탈로그 R 이 있는 줄이면 곧은 나사의 인선 R 칸에 넣는다: 0.137P × 25.4/20 = 0.17399 → "0.174"
        val withR = ThreadInsert("Vargus (VARDEX)", "TEST", "TESTL", false, InsertForm.UN, 20.0, "3/8\"", true, 0.137, "VDX_TT", "0")
        s.pickInsert(withR)
        assertEquals("0.174", s.value(ThreadField.TIP_R))
        s.pickInsert(null)
        assertNull(s.insert)
        // 미터: 자료 없음
        val m = ThreadState(spec = metric("M10", 1.5))
        assertFalse(m.hasInsertData)
        assertEquals(emptyList(), m.insertChoices)
        // UN 28산: 칸은 있지만 맞는 줄 없음
        val unf = ThreadState(family = ThreadFamily.UN, spec = un("1/4", 28))
        assertEquals(emptyList(), unf.insertChoices)
        assertEquals("인서트 자료(VARDEX · Carmex)는 UN 20산 · PT(BSPT) · NPT 만입니다.", insertNote(unf))
        // 관용 테이퍼: PT 는 BSPT, 이름 · 풀 프로파일만(R 이 있어도 칸에 넣지 않음 — 인선 R 칸이 없다)
        val pt = ThreadState(family = ThreadFamily.PT, pipe = PipeTable.of(PipeKind.PT).single { it.size == "3/4" })
        assertEquals(listOf("3ER14BSPT · VARDEX", "16 ER 14 BSPT · Carmex"), pt.insertChoices.map(::insertText))
        pt.pickInsert(pt.insertChoices[0])
        assertEquals("", pt.value(ThreadField.TIP_R))
        assertTrue(insertNote(pt)!!.startsWith("풀 프로파일"))
        val npt = ThreadState(inner = true, family = ThreadFamily.NPT, pipe = PipeTable.of(PipeKind.NPT).single { it.size == "3/4" })
        assertEquals(listOf("3IR14NPT · VARDEX", "16 IR 14 NPT · Carmex"), npt.insertChoices.map(::insertText))
    }

    /**
     * 카탈로그 절삭 속도 줄: 열쇠가 18줄 안에서 겹치지 않고, 고른 줄의 출처 글 = 출처 이름(괄호 풀이 뺌) + 쪽. 체이서 줄 출처는 회사 이름이 앞에
     */
    @Test
    fun catalogTexts() {
        val hits = DieHeadCatalog.speeds()
        assertEquals(18, hits.size)
        assertEquals(hits.size, hits.map(::dieSpeedKey).toSet().size)
        val s = threadDie()
        // 범위 안은 줄 안 바꿈 빈칸(좁은 칸에서 '10 ~' 로 갈리지 않게)
        assertEquals("구조용강 10\u00A0~\u00A025", s.shownDieSpeed?.speed?.let(::speedShort))
        // 원문 'N/m²' 는 N/mm² 의 오식으로 보인다는 풀이, 이 줄의 S 범위(Ø16: 1000 × 10/(π × 16) = 198.9 → S199, 25 → 497.4 → S497)
        assertEquals(
            "Vc 는 'P 강' 에 저장. 출처: WAGNER Operating Manual ZR 26-I 29쪽 (14.1, Table 4) · 원문 'Construction steel up to 500 N/m²' (N/mm² 의 오식으로 보임)." +
                " 이 줄은 Vc 10 ~ 25 → S199 ~ 497 (Ø16).",
            dieSpeedNote(s),
        )
        assertEquals("Landis Standard Stock & Non Stock Chasers 1쪽", dieSourceText("LAN_CH", "1쪽"))
        // M16×2 체이서: Landis mm 줄 가운데 피치 2.00 이 든 다섯
        val rows = chaserRows(metric("M16", 2.0))
        assertEquals(listOf("5/8 x 1-1/8", "3/4 x 1-5/8", "15/16 x 2-1/8", "1-3/64 x 3", "1-3/64 x 4"), rows.map { it.size })
        assertTrue(rows.all { it.maker == "Landis" && it.thread == "미터 피치 2" && it.set == "4" && it.sourceId == "LAN_CH" })
        assertEquals(listOf("1쪽", "1쪽", "1쪽", "1쪽", "2쪽"), rows.map { it.page })
        assertEquals(listOf("Landis Standard Stock & Non Stock Chasers 1쪽 · 2쪽"), chaserSources(rows))
        // 20산 UN 도 같은 다섯 크기(UN 꼴 줄)
        assertEquals(rows.map { it.size }, chaserRows(un("1/2", 20)).map { it.size })
        assertEquals("UN 20산", chaserRows(un("1/2", 20)).first().thread)
    }
}
