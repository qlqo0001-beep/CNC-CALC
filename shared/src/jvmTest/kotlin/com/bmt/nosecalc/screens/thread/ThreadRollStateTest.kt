package com.bmt.nosecalc.screens.thread

import androidx.compose.runtime.saveable.SaverScope
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.CompanyHead
import com.bmt.nosecalc.calc.CompanyRolls
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.RollHeadCalc
import com.bmt.nosecalc.calc.RollInput
import com.bmt.nosecalc.calc.RollRatioBasis
import com.bmt.nosecalc.calc.RollRef
import com.bmt.nosecalc.calc.RollRpmBasis
import com.bmt.nosecalc.calc.RollSteel
import com.bmt.nosecalc.calc.RollStock
import com.bmt.nosecalc.calc.RollTipGroup
import com.bmt.nosecalc.calc.RollWarning
import com.bmt.nosecalc.calc.ThreadKind
import com.bmt.nosecalc.calc.ThreadTable
import com.bmt.nosecalc.calc.UnGroup
import com.bmt.nosecalc.screens.ThreadHelp
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 나사 화면의 전조 다이스(2026-11-14): 칸 넷 · 자리표시(쓰일 값의 숫자) · 회전수 · 비율 사슬 · 전조 조건 저장소 왕복(열쇠 셋) · 가공 방식이 바이트로 돌아감 ·
 * D15(빈 규격 → UN · 20UN) · U-7(머리 되돌림) · 저장 31칸과 옛 글 · 범위 밖 칸 = 계산기 BAD_INPUT · 경고 글 · 요령 방향 글 · 세 칸 차례.
 * 기대값은 손 계산(식과 수를 주석에 — calc RollHeadCalcTest 와 같은 수) · roll-heads.json 원문 글이다. 끝 Z −16.37 등 치수는 새로 지은 값이고,
 * 회사 값(전조 전 지름 · S)은 아직 없어(null) 회전수는 Fette 추천이다
 */
class ThreadRollStateTest {
    private fun file() = File.createTempFile("bmt-roll", ".properties").apply { delete() }
    private fun store(values: Map<RollField, String> = emptyMap()) = RollStore(KeyValueStore(file())).apply { values.forEach { (f, v) -> set(f, v) } }
    private fun near(e: Double, a: Double, tol: Double, what: String) = assertTrue(abs(e - a) <= tol, "$what: 기대 $e, 실제 $a")
    private fun un(size: String, tpi: Int) = ThreadTable.pitches(ThreadKind.UN, size).first { it.tpi == tpi }
    private fun metric(size: String, pitch: Double) = ThreadTable.pitches(ThreadKind.METRIC, size).first { it.pitch == pitch }
    private fun calc(s: ThreadState, r: RollStore) = RollHeadCalc.calc(s.rollInput(r))

    /**
     * [threadRoll]: 3/4-20 UNEF(d 19.05 · P 1.27), 2A 유효지름 18.079662 ~ 18.191546 → dV 18.049662 ~ 18.161546, 가운데 18.105604.
     * STS316 30 m/min: n = 30000 / (π × 18.105604) = 527.42 → S527, 쓴 v = π × 18.105604 × 527 / 1000 = 29.975987 → '30.0'.
     * 20산 → F1.22(비율 1.22 / 1.27 = 0.960630), 분당 527 × 1.22 = 642.94, 시간 = 60 × (5 + 16.37) / 642.94 + 1 = 2.994276 → '3.0'.
     * 머리 F34(회사 처음 값 · AT08), a 4 → 실제 끝 −16.37 − 4 = −20.37, 전조 길이 = (끝면 0 − (−16.37)) + 4 = 20.37(화면은 끝면 빈칸을 0 으로 넘긴다).
     * 바깥지름 2A 18.809596 ~ 19.016435. STS304(35 m/min) 면 35000 / (π × 18.105604) = 615.33 → S615
     */
    @Test
    fun rollInputFields() {
        val s = threadRoll()
        val r = store()
        assertTrue(s.isRoll)
        assertFalse(s.isDie)
        assertEquals(listOf(ThreadField.Z_START, ThreadField.RPM, ThreadField.Z_END, ThreadField.Z_REF), s.fields)
        assertEquals(
            RollInput(
                spec = un("3/4", 20), material = null, steel = RollSteel.STS316, head = null, stock = RollStock.HEX, rpm = null, zStart = null, zEnd = -16.37,
                ratio = null, dwell = 1.0, retractZ = 150.0, zFace = 0.0,
            ),
            s.rollInput(r),
        )
        val result = calc(s, r)
        assertEquals(emptyList(), result.warnings)
        val sol = assertNotNull(result.solution)
        assertEquals(527L, sol.rpm)
        assertEquals(RollRpmBasis.FETTE, sol.rpmBasis)
        near(18.105604, sol.blank.used, 1e-6, "전조 전 지름")
        near(29.975987, sol.vUsed, 1e-6, "쓴 v")
        near(1.22, sol.feed, 1e-12, "이송")
        assertEquals(RollRatioBasis.COMPANY, sol.ratioBasis)
        near(0.960630, sol.ratio, 1e-6, "비율")
        near(642.94, sol.feedPerMin, 1e-9, "분당 이송")
        near(2.994276, sol.timeSec, 1e-6, "시간")
        assertEquals("F34", sol.head.key)
        assertEquals(CompanyHead("F34", "AT08"), sol.companyHead)
        assertEquals(4.0, sol.openingMovement)
        near(-20.37, sol.rollEndZ!!, 1e-9, "실제 끝")
        near(20.37, sol.rolledLength!!, 1e-9, "전조 길이")
        assertNull(sol.maxRollLength)
        near(18.809596, sol.crest.min, 1e-6, "바깥지름 최소")
        near(19.016435, sol.crest.max, 1e-6, "바깥지름 최대")
        assertEquals(listOf("G97S527", "G0X0.Z20.", "Z5.", "G1Z-16.37F1.22", "G4U1.", "G0Z150."), sol.program)
        // 결과 칸(화면 · 골든이 같이 씀): 값과 아래 근거 글
        val cells = rollResultCells(s, r, sol)
        assertEquals(listOf("회전수 S", "전조 속도 v", "이송 F", "분당 이송", "가공 시간 (초)", "머리 · 롤", "실제 전조 끝", "전조 길이"), cells.map { it.first })
        assertEquals(listOf("S527", "30.0", "F1.22", "642.9", "3.0", "F34 · AT08", "Z-20.37", "20.370"), cells.map { it.second })
        assertEquals(
            listOf(
                "Fette 추천 · STS316 30 m/min 아래 끝", "범위 30–50 m/min", "비율 0.961 · 회사 20산", "mm/min = S × F", "G1 + 끝 멈춤 1 초",
                "회사 롤 · 카탈로그 Ø12–30", "끝 Z − 열림 행정 a 4", "끝면부터 + a",
            ),
            cells.map { it.third },
        )
        // 전조 전 지름 칸: 회사 값은 회사 표 규격인데 아직 없음, 추천 = 가운데(범위 18.050 ~ 18.162), 전조 뒤 바깥지름 2A
        assertEquals(
            listOf(
                Triple("회사 값 (자동핵사)", "—", "확인 전 — 없음"),
                Triple("추천 값", "Ø18.106", "Fette d2 − 0.03 · Ø18.050 ~ 18.162"),
                Triple("전조 뒤 바깥지름", "Ø18.810 ~ 19.016", "2A 바깥지름 범위"),
            ),
            rollBlankCells(s),
        )
        // NC 카드의 이송 사실 · 설명(열림 행정 a 4 · 물러남 Z150)
        assertEquals("F1.22 = 회사 20산", rollFeedFact(sol))
        val explain = rollNcExplain(150.0, sol.openingMovement)
        assertTrue("열림 행정 a 4 mm 만큼 더 나가며" in explain && "Z150. 로 물러납니다" in explain && "앞 공정 정삭 X" in explain, explain)
        // STS304 → S615(범위 615 ~ 967), 결과 칸 아래 글도 그 강종
        s.rollSteel = RollSteel.STS304
        val s304 = calc(s, r).solution!!
        assertEquals(615L, s304.rpm)
        assertEquals(615L to 967L, s304.rpmRange)
        assertEquals("Fette 추천 · STS304 35 m/min 아래 끝", rollResultCells(s, r, s304)[0].third)
        // 소재 종류를 바꿔도(회사 값이 아직 없어) 같은 결과, 칸 이름만 그 종류
        s.rollSteel = RollSteel.STS316
        s.rollStock = RollStock.FORGED
        assertEquals(527L, calc(s, r).solution!!.rpm)
        assertEquals("회사 값 (자동단조)", rollBlankCells(s)[0].first)
    }

    /** 자리표시(U-3): 쓰일 값의 숫자 — 회전수 '추천 527' · 시작 Z '기본 5' · 끝면 '기본 0' · 비율 '기본 0.961 (F1.22)'. 소재 · 규격 · 강종마다 바뀐다 */
    @Test
    fun placeholders() {
        val s = threadRoll()
        assertEquals(listOf("기본 5", "추천 527", "", "기본 0"), s.fields.map(s::placeholder))
        assertEquals(listOf("기본 0.961 (F1.22)", "기본 1", "기본 150"), RollField.entries.map { rollPlaceholder(s, it) })
        s.rollSteel = RollSteel.STS304
        assertEquals("추천 615", s.placeholder(ThreadField.RPM))
        // 스테인리스가 아니면 Fette 추천도 없다 → '넣으세요'(파랑 안내에 회전수)
        s.material = IsoGroup.P
        assertEquals("넣으세요", s.placeholder(ThreadField.RPM))
        assertTrue(s.rollRpmMissing)
        assertEquals(listOf("회전수 (이 소재는 추천 값이 없음)"), rollMissing(s))
        s.set(ThreadField.RPM, "700")
        assertEquals(emptyList(), rollMissing(s))
        s.material = IsoGroup.M
        assertEquals("추천 615", s.placeholder(ThreadField.RPM))
        // 20산이 아닌 규격: M12×1.5 — 6g 유효지름 가운데 − 0.03 = 10.893721, S = 30000 / (π × 10.893721) = 876.59 → 877, 비율 Fette 0.95
        val m12 = ThreadState(spec = metric("M12", 1.5), method = ThreadMethod.ROLL)
        assertEquals("추천 877", m12.placeholder(ThreadField.RPM))
        assertEquals("기본 0.95", rollPlaceholder(m12, RollField.RATIO))
        // 사이즈 전: 스테인리스(비움)는 '자동', 다른 소재는 '넣으세요'. 남은 값은 사이즈 · 끝 Z(시작 Z 는 빈칸 5 — F4)
        val e = threadRollEmpty()
        assertEquals(listOf("기본 5", "자동", "", "기본 0"), e.fields.map(e::placeholder))
        assertEquals("기본 0.95", rollPlaceholder(e, RollField.RATIO))
        assertEquals(listOf("사이즈", "가공 목표점 (Z)"), rollMissing(e))
        e.material = IsoGroup.N
        assertEquals("넣으세요", e.placeholder(ThreadField.RPM))
        assertEquals(listOf("사이즈", "가공 목표점 (Z)", "회전수 (이 소재는 추천 값이 없음)"), rollMissing(e))
        // 다이스 · 바이트의 자리표시는 그대로(시작 Z 는 비움)
        assertEquals("", threadDie().placeholder(ThreadField.Z_START))
        assertEquals("Vc 로", threadDie().placeholder(ThreadField.RPM))
        assertEquals("안 씀", threadOriginal().placeholder(ThreadField.RPM))
    }

    /**
     * 사슬: 회전수 = 넣은 값 → (회사 S — 아직 없음) → Fette 추천, 비율 = 넣은 값 → 20산 회사 F1.22 → Fette 0.95.
     * 넣은 비율 0.95 → F = 0.95 × 1.27 = 1.2065 → F1.207, M12×1.5 빈칸 → 0.95 × 1.5 = 1.425
     */
    @Test
    fun rpmAndRatioChains() {
        val s = threadRoll()
        val r = store()
        s.set(ThreadField.RPM, "600")
        calc(s, r).solution!!.let { sol ->
            assertEquals(600L, sol.rpm)
            assertEquals(RollRpmBasis.INPUT, sol.rpmBasis)
            assertEquals(527L, sol.baseRpm)
            assertEquals("넣은 값", rollResultCells(s, r, sol)[0].third)
        }
        // 다른 소재 + 넣은 회전수: 범위 · 방향 없음, 근거 글이 까닭(U-5)
        s.material = IsoGroup.P
        calc(s, r).solution!!.let { sol ->
            assertEquals(RollRpmBasis.INPUT, sol.rpmBasis)
            assertNull(sol.rpmRange)
            assertNull(sol.rpmDirection)
            assertEquals("넣은 값 · 이 소재는 Fette 추천 없음", rollResultCells(s, r, sol)[0].third)
            assertEquals("m/min = π × dV × S / 1000", rollResultCells(s, r, sol)[1].third)
        }
        // 다른 소재 + 빈칸 → 값 부족
        s.set(ThreadField.RPM, "")
        assertEquals(listOf(RollWarning.MISSING_INPUT), calc(s, r).warnings)
        s.material = null
        // 비율: 넣으면 그 값
        r.set(RollField.RATIO, "0.95")
        calc(s, r).solution!!.let { sol ->
            assertEquals(RollRatioBasis.INPUT, sol.ratioBasis)
            near(1.2065, sol.feed, 1e-12, "넣은 비율 F")
            assertEquals("G1Z-16.37F1.207", sol.program[3])
            assertEquals("넣은 비율 0.95", rollResultCells(s, r, sol)[2].third)
            assertEquals("F = 피치 × 0.95", rollFeedFact(sol))
        }
        r.set(RollField.RATIO, "")
        assertEquals(RollRatioBasis.COMPANY, calc(s, r).solution!!.ratioBasis)
        // 20산이 아닌 규격 → Fette 0.95, 회사 롤 없는 머리(F2 — 지름 범위에 들고 롤 목록에 피치가 있는 첫 머리)
        val m12 = ThreadState(spec = metric("M12", 1.5), values = mapOf("zEnd" to "-16.37"), method = ThreadMethod.ROLL)
        calc(m12, r).solution!!.let { sol ->
            assertEquals(877L, sol.rpm)
            assertEquals(RollRatioBasis.FETTE, sol.ratioBasis)
            near(1.425, sol.feed, 1e-12, "Fette F")
            assertEquals("비율 0.95 · Fette", rollResultCells(m12, r, sol)[2].third)
            assertEquals("F2 · 회사 롤 없음", rollResultCells(m12, r, sol)[5].second)
            assertEquals("카탈로그 Ø8–16", rollResultCells(m12, r, sol)[5].third)
        }
    }

    /** 저장소 왕복: 열쇠 셋 thread/roll/ratio · dwell · retract(다이스 조건 thread/die/… 와 따로), 새 저장소(앱을 다시 켬)가 같은 글을 읽는다. 빈칸 글은 처음 값 */
    @Test
    fun storeRoundTrip() {
        assertEquals(listOf("thread/roll/ratio", "thread/roll/dwell", "thread/roll/retract"), RollField.entries.map { RollStore.key(it) })
        val f = file()
        val a = RollStore(KeyValueStore(f))
        a.set(RollField.RATIO, "0.95")
        a.set(RollField.DWELL, "0.5")
        a.set(RollField.RETRACT, "120")
        val b = RollStore(KeyValueStore(f))
        for (field in RollField.entries) assertEquals(a.value(field), b.value(field), "$field")
        val kv = KeyValueStore(f)
        assertEquals("0.95", kv.get("thread/roll/ratio"))
        assertEquals("0.5", kv.get("thread/roll/dwell"))
        assertEquals("120", kv.get("thread/roll/retract"))
        // 다이스 조건과 섞이지 않는다
        assertNull(kv.get("thread/die/ratio"))
        assertEquals("", DieStore(KeyValueStore(f)).value(DieField.RATIO, null))
        val die = DieStore(KeyValueStore(f)).apply { set(DieField.RATIO, null, "0.98") }
        assertEquals("0.98", die.value(DieField.RATIO, null))
        assertEquals("0.95", RollStore(KeyValueStore(f)).value(RollField.RATIO))
        // 입력: 넣은 회사 값 → G1 F1.207 · G4U.5 · G0Z120.
        val s = threadRoll()
        val i = s.rollInput(b)
        assertEquals(listOf(0.95, 0.5, 120.0), listOf(i.ratio, i.dwell, i.retractZ))
        assertEquals(listOf("G97S527", "G0X0.Z20.", "Z5.", "G1Z-16.37F1.207", "G4U.5", "G0Z120."), RollHeadCalc.calc(i).solution!!.program)
        // 비우면 처음 값(비율은 사슬 — null, 멈춤 1 · 물러남 150)
        b.set(RollField.RATIO, "")
        b.set(RollField.DWELL, "")
        b.set(RollField.RETRACT, "")
        val back = s.rollInput(RollStore(KeyValueStore(f)))
        assertEquals(listOf(null, 1.0, 150.0), listOf(back.ratio, back.dwell, back.retractZ))
    }

    /** 가공 방식: 전조 다이스도 M · UN 숫나사만 — 내경 · 관용 테이퍼로 가면 바이트로 돌아가고, 못 고르는 상태에서는 받지 않는다(종류도 바꾸지 않음) */
    @Test
    fun methodFallsBack() {
        val s = threadRoll()
        assertTrue(s.dieAllowed)
        s.switchInner(true)
        assertEquals(ThreadMethod.BITE, s.method)
        assertFalse(s.isRoll)
        s.pickMethod(ThreadMethod.ROLL)
        assertEquals(ThreadMethod.BITE, s.method)
        assertEquals(un("3/4", 20), s.spec)
        s.switchInner(false)
        assertEquals(ThreadMethod.BITE, s.method)
        s.pickMethod(ThreadMethod.ROLL)
        assertTrue(s.isRoll)
        s.switchFamily(ThreadFamily.NPT)
        assertEquals(ThreadMethod.BITE, s.method)
        s.pickMethod(ThreadMethod.ROLL)
        assertEquals(ThreadMethod.BITE, s.method)
        assertEquals(ThreadFamily.NPT, s.family)
        s.switchFamily(ThreadFamily.UN)
        s.pickMethod(ThreadMethod.ROLL)
        assertTrue(s.isRoll)
        // 미터 ↔ UN 은 전조 그대로(사이즈만 비움)
        s.switchFamily(ThreadFamily.METRIC)
        assertTrue(s.isRoll)
        assertNull(s.spec)
        // 되살린 상태도 같은 규칙
        assertEquals(ThreadMethod.BITE, ThreadState(inner = true, method = ThreadMethod.ROLL).method)
        assertEquals(ThreadMethod.BITE, ThreadState(family = ThreadFamily.PT, method = ThreadMethod.ROLL).method)
        // 내경에서 고르면 바이트 그대로 · 종류도 그대로(D15 는 전조가 됐을 때만)
        val inner = ThreadState(inner = true)
        inner.pickMethod(ThreadMethod.ROLL)
        assertEquals(ThreadMethod.BITE, inner.method)
        assertEquals(ThreadFamily.METRIC, inner.family)
    }

    /**
     * D15: 전조를 고를 때 규격이 비었으면 UN · 20UN 묶음(회사 롤 일곱이 모두 UN 20산), 규격이 있으면 그대로. 사이즈 시트의 '회사 롤 규격' 은 그 20산 줄 · 묶음 20UN
     */
    @Test
    fun rollPicksUn20WhenEmpty() {
        val e = ThreadState()
        e.pickMethod(ThreadMethod.ROLL)
        assertTrue(e.isRoll)
        assertEquals(ThreadFamily.UN, e.family)
        assertEquals(UnGroup.UN20, e.unGroup)
        assertNull(e.spec)
        // UN 이지만 다른 묶음 · 규격 없음 → 20UN
        val unf = ThreadState(family = ThreadFamily.UN, unGroup = UnGroup.UNF)
        unf.pickMethod(ThreadMethod.ROLL)
        assertEquals(UnGroup.UN20, unf.unGroup)
        // 규격이 있으면 건드리지 않는다(미터 M16×2 · UNC 1/4-20)
        val die = threadDie()
        die.pickMethod(ThreadMethod.ROLL)
        assertTrue(die.isRoll)
        assertEquals(ThreadFamily.METRIC, die.family)
        assertEquals(metric("M16", 2.0), die.spec)
        val unc = threadOriginal()
        unc.pickMethod(ThreadMethod.ROLL)
        assertEquals(UnGroup.UNC, unc.unGroup)
        assertEquals(un("1/4", 20), unc.spec)
        // 절삭 다이스 · 바이트는 종류를 바꾸지 않는다
        val d = ThreadState().apply { pickMethod(ThreadMethod.DIE) }
        assertEquals(ThreadFamily.METRIC, d.family)
        // 이미 전조인데 '전조 다이스' 칸을 다시 누르면 그대로(V3-1-4 — Segmented 는 고른 칸도 onSelect): 전조에서 M 으로 바꿔 사이즈가 빈 상태
        val m = ThreadState().apply { pickMethod(ThreadMethod.ROLL) }
        m.switchFamily(ThreadFamily.METRIC)
        assertNull(m.spec)
        m.pickMethod(ThreadMethod.ROLL)
        assertTrue(m.isRoll)
        assertEquals(ThreadFamily.METRIC, m.family)
        assertNull(m.spec)
        // 되살린 빈 전조(골든 thread-roll-empty — 미터 · 빈 규격)도, UN · UNF 묶음 · 빈 규격 전조도 그대로
        val restored = threadRollEmpty()
        restored.pickMethod(ThreadMethod.ROLL)
        assertEquals(ThreadFamily.METRIC, restored.family)
        val unfRoll = ThreadState(family = ThreadFamily.UN, unGroup = UnGroup.UNF, method = ThreadMethod.ROLL)
        unfRoll.pickMethod(ThreadMethod.ROLL)
        assertEquals(UnGroup.UNF, unfRoll.unGroup)
        // 다른 방식에 갔다가 전조로 들어오면 다시 D15
        m.pickMethod(ThreadMethod.BITE)
        m.pickMethod(ThreadMethod.ROLL)
        assertEquals(ThreadFamily.UN, m.family)
        assertEquals(UnGroup.UN20, m.unGroup)
        // 회사 롤 규격 일곱(지름 순): 누르면 그 20산 줄, 묶음 20UN
        assertEquals(listOf("7/16", "1/2", "9/16", "5/8", "3/4", "7/8", "1"), CompanyRolls.specs.map { it.size })
        for (spec in CompanyRolls.specs) {
            val s = ThreadState(family = ThreadFamily.UN, unGroup = UnGroup.UNC, method = ThreadMethod.ROLL)
            s.pickCompanyRoll(spec)
            assertEquals(spec, s.spec)
            assertEquals(20, s.spec?.tpi)
            assertEquals(UnGroup.UN20, s.unGroup, spec.size)
        }
    }

    /**
     * 머리 · 롤(U-7): 시트 줄 = 회사 짝(처음 값이 먼저) + 지름 범위에 드는 다른 EVOline 머리. 3/4 → F34 · F233400(회사) · F23(Ø8–22) · F3(Ø12–22).
     * 고른 머리가 새 규격의 선택지에 없으면 처음 값으로: F233400(Ø16–36)은 1/2(Ø12.7)에 없다, 7/8(Ø22.225)에는 있다
     */
    @Test
    fun headChoicesAndReset() {
        val s = threadRoll()
        assertEquals(listOf("F34", "F233400", "F23", "F3"), s.rollHeadChoices)
        assertEquals(
            listOf("F34 · AT08" to "회사 롤 · 처음 값", "F233400 · AT02" to "회사 롤", "F23" to "회사 롤 없음 · Ø8–22", "F3" to "회사 롤 없음 · Ø12–22"),
            s.rollHeadChoices.map { rollHeadRow(s.spec!!, it) },
        )
        assertEquals("F34 · AT08", rollHeadText(s))
        s.rollHead = "F233400"
        assertEquals("F233400 · AT02", rollHeadText(s))
        assertEquals(CompanyHead("F233400", "AT02"), calc(s, store()).solution!!.companyHead)
        assertEquals(3.0, calc(s, store()).solution!!.openingMovement)
        // 7/8 에는 F233400 이 있다(회사 짝 F34 · AT07 + 지름 범위 F233400) → 그대로
        s.pickSpec(un("7/8", 20))
        assertEquals(listOf("F34", "F233400"), s.rollHeadChoices)
        assertEquals("F233400", s.rollHead)
        assertEquals("F233400 · 회사 롤 없음", rollHeadText(s))
        // 1/2 에는 없다 → 처음 값(F2 · AT06)
        s.pickSpec(un("1/2", 20))
        assertNull(s.rollHead)
        assertEquals(listOf("F2", "F23", "F3", "F34"), s.rollHeadChoices)
        assertEquals("F2 · AT06", rollHeadText(s))
        // 회사 짝이 아닌 머리를 고른 채 다른 규격으로: F3 은 3/4 에 있고 7/8 에 없다
        s.pickSpec(un("3/4", 20))
        s.rollHead = "F3"
        assertEquals("F3 · 회사 롤 없음", rollHeadText(s))
        s.pickSpec(un("7/8", 20))
        assertNull(s.rollHead)
        // 되살린 상태도 같은 규칙
        assertNull(ThreadState(family = ThreadFamily.UN, spec = un("1/2", 20), rollHead = "F233400").rollHead)
        assertEquals("F23", ThreadState(family = ThreadFamily.UN, spec = un("1/2", 20), rollHead = "F23").rollHead)
        // 회사 표 밖(M12×1.5): 지름 범위의 넷, 처음 값 F2 에 표
        val m12 = ThreadState(spec = metric("M12", 1.5), method = ThreadMethod.ROLL)
        assertEquals("F2" to "회사 롤 없음 · Ø8–16 · 처음 값", rollHeadRow(m12.spec!!, "F2"))
        assertEquals("F2 · 회사 롤 없음", rollHeadText(m12))
        // 사이즈 전 · 맞는 머리 없음(1-1/2-20 Ø38.1)
        assertEquals("사이즈를 고르면", rollHeadText(threadRollEmpty()))
        assertEquals(emptyList(), threadRollEmpty().rollHeadChoices)
        assertEquals("맞는 머리 없음", rollHeadText(ThreadState(family = ThreadFamily.UN, spec = un("1-1/2", 20), method = ThreadMethod.ROLL)))
    }

    /** 저장 · 되살리기: 31칸(전조 머리 · 소재 종류 · 강종이 맨 끝), 옛 글 28칸(2026-11-06) · 25칸(2026-11-05) · 20칸(그 전)은 새 칸이 처음 값 */
    @Test
    fun saverRoundTrip() {
        val scope = SaverScope { true }
        val s = threadRoll().apply {
            rollHead = "F233400"
            rollStock = RollStock.FORGED
            rollSteel = RollSteel.STS304
            material = IsoGroup.M
            set(ThreadField.Z_REF, "-1")
        }
        val saved = with(ThreadState.Saver) { scope.save(s) } as List<*>
        assertEquals(31, saved.size)
        assertEquals(listOf("F233400", "FORGED", "STS304"), saved.takeLast(3))
        val back = ThreadState.Saver.restore(saved.map { it as String })!!
        assertEquals(ThreadMethod.ROLL, back.method)
        assertEquals("F233400", back.rollHead)
        assertEquals(RollStock.FORGED, back.rollStock)
        assertEquals(RollSteel.STS304, back.rollSteel)
        assertEquals(UnGroup.UN20, back.unGroup)
        assertEquals(s.rollInput(store()), back.rollInput(store()))
        // 2026-11-06 글(28칸 — 가공 방식까지): 전조 칸은 처음 값
        val back28 = ThreadState.Saver.restore(saved.take(28).map { it as String })!!
        assertEquals(ThreadMethod.ROLL, back28.method)
        assertNull(back28.rollHead)
        assertEquals(RollStock.HEX, back28.rollStock)
        assertEquals(RollSteel.STS316, back28.rollSteel)
        val die28 = with(ThreadState.Saver) { scope.save(threadDie()) } as List<*>
        val backDie = ThreadState.Saver.restore(die28.take(28).map { it as String })!!
        assertEquals(ThreadMethod.DIE, backDie.method)
        assertEquals(RollStock.HEX, backDie.rollStock)
        // 2026-11-05 글(25칸): 바이트
        val back25 = ThreadState.Saver.restore(saved.take(25).map { it as String })!!
        assertEquals(ThreadMethod.BITE, back25.method)
        assertNull(back25.rollHead)
        assertEquals(UnGroup.UN20, back25.unGroup)
        assertEquals("-1", back25.value(ThreadField.Z_REF))
        // 그 전 글(20칸): 바이트, 묶음은 규격 줄의 계열(UNEF), 끝면 Z 칸 없음
        val back20 = ThreadState.Saver.restore(saved.take(20).map { it as String })!!
        assertEquals(ThreadMethod.BITE, back20.method)
        assertEquals(UnGroup.UNEF, back20.unGroup)
        assertEquals(un("3/4", 20), back20.spec)
        assertEquals("", back20.value(ThreadField.Z_REF))
        assertEquals(RollSteel.STS316, back20.rollSteel)
    }

    /** 칸마다 범위 밖 값: 화면이 짚는 칸(숫자칸 bad · 전조 조건 rollBad)이 있으면 계산기가 BAD_INPUT, 없으면 BAD_INPUT 이 아니다 */
    @Test
    fun badFieldsMatchCalc() {
        // 회전수 경계(검토 a1): 100000.3 은 찍힐 S 가 100000 이라 풀리고, 100000.5 는 S100001 · 0.99 는 넣은 값 < 1 이라 멈춘다
        val values = listOf(
            "-20000", "-1", "-0.1", "0", "0.0002", "0.5", "0.99", "1", "3", "100000", "100000.3", "100000.5", "100001", "6000", "20000", "2000000", "NaN", "Infinity",
        )
        var checked = 0
        var flagged = 0
        for (v in values) {
            for (f in threadRoll().fields) {
                val s = threadRoll()
                val r = store()
                s.set(f, v)
                val w = calc(s, r).warnings
                val bad = s.bad().map { it.name } + rollBad(s, r).map { it.name }
                assertEquals(bad.isNotEmpty(), RollWarning.BAD_INPUT in w, "$f = $v: $bad / $w")
                if (s.bad().isNotEmpty()) assertEquals(setOf(f), s.bad())
                if (bad.isNotEmpty()) flagged++
                checked++
            }
            for (f in RollField.entries) {
                val s = threadRoll()
                val r = store(mapOf(f to v))
                val w = calc(s, r).warnings
                val bad = rollBad(s, r)
                assertEquals(bad.isNotEmpty(), RollWarning.BAD_INPUT in w, "$f = $v: $bad / $w")
                if (bad.isNotEmpty()) assertEquals(setOf(f), bad)
                if (bad.isNotEmpty()) flagged++
                checked++
            }
        }
        assertTrue(checked >= 100 && flagged >= 40, "$checked / $flagged")
        // 회전수 위 한도는 찍힐 S(반올림)로 본다 — 끝 Z 가 비어 기다리는 동안에도 계산과 같게(빨간 테를 둘렀다가 그대로 쓰이지 않게)
        for (zEnd in listOf("", "-16.37")) {
            val near = threadRoll().apply { set(ThreadField.Z_END, zEnd); set(ThreadField.RPM, "100000.3") }
            assertEquals(emptySet(), near.bad(), "끝 Z '$zEnd'")
            val over = threadRoll().apply { set(ThreadField.Z_END, zEnd); set(ThreadField.RPM, "100000.5") }
            assertEquals(setOf(ThreadField.RPM), over.bad(), "끝 Z '$zEnd'")
        }
        assertEquals("G97S100000", calc(threadRoll().apply { set(ThreadField.RPM, "100000.3") }, store()).solution!!.program.first())
        assertEquals(listOf(RollWarning.BAD_INPUT), calc(threadRoll().apply { set(ThreadField.RPM, "100000.5") }, store()).warnings)
        // 짚은 칸이 있으면 빨간 글이 그 칸을 말한다
        val r = store(mapOf(RollField.RATIO to "0.0002", RollField.DWELL to "-1"))
        val s = threadRoll().apply { set(ThreadField.RPM, "0") }
        assertEquals(
            "넣은 값이 범위 밖입니다 — 회전수 1 ~ 100000 · 이송 비율 0 보다 크게 (F = 피치 × 비율이 0.001 mm 이상) · 끝 멈춤 0 이상.",
            rollWarningText(RollWarning.BAD_INPUT, s, r),
        )
    }

    /** 멈춤 · 확인 글과 짚는 칸: Z 순서 다섯 까닭(계산기 차례 — 시작 Z 빈칸은 5 로 씀, F4) · 맞는 머리 없음 · 숫자 든 노랑 */
    @Test
    fun warningTexts() {
        fun case(r: Map<RollField, String> = emptyMap(), edit: ThreadState.() -> Unit = {}): Triple<ThreadState, RollStore, List<RollWarning>> {
            val s = threadRoll().apply(edit)
            val st = store(r)
            return Triple(s, st, calc(s, st).warnings)
        }
        // 값 부족(파랑)
        assertEquals(
            "값을 넣으면 바로 계산합니다 — 남은 것: 사이즈 · 가공 목표점 (Z).",
            rollWarningText(RollWarning.MISSING_INPUT, threadRollEmpty(), store()),
        )
        // 시작 Z 빈칸(5) ≤ 끝 Z 6 → 두 Z, 글은 Z5.
        case { set(ThreadField.Z_END, "6") }.let { (s, r, w) ->
            assertEquals(listOf(RollWarning.Z_ORDER), w)
            assertEquals(setOf(ThreadField.Z_START, ThreadField.Z_END) to emptySet(), rollFlagged(s, r, w))
            assertEquals(
                "전조 다이스는 정가공(Z−)만 — Z축 시작점(Z5.)이 가공 목표점보다 커야 합니다 (척 쪽으로 전조, 예: 5 → −20).",
                rollWarningText(w[0], s, r),
            )
        }
        // 시작이 진입 Z20 보다 큼
        case { set(ThreadField.Z_START, "25") }.let { (s, r, w) ->
            assertEquals(setOf(ThreadField.Z_START) to emptySet(), rollFlagged(s, r, w))
            assertEquals("Z축 시작점(Z25.)이 진입 Z20 보다 큽니다 — Z20. 에서 시작점으로 급송하는 글이라 시작점은 그 이하로 하세요.", rollWarningText(w[0], s, r))
        }
        // 물러남 Z3 < 시작 Z(빈칸 5) → 시작 · 물러남
        case(mapOf(RollField.RETRACT to "3")).let { (s, r, w) ->
            assertEquals(listOf(RollWarning.Z_ORDER), w)
            assertEquals(setOf(ThreadField.Z_START) to setOf(RollField.RETRACT), rollFlagged(s, r, w))
            assertTrue(rollWarningText(w[0], s, r).startsWith("물러남 Z 가 Z축 시작점(Z5.)보다 작습니다"))
        }
        // 시작 Z −1 이 끝면(빈칸 0) 안 → 시작 · 끝면
        case { set(ThreadField.Z_START, "-1") }.let { (s, r, w) ->
            assertEquals(setOf(ThreadField.Z_START, ThreadField.Z_REF) to emptySet(), rollFlagged(s, r, w))
            assertTrue(rollWarningText(w[0], s, r).startsWith("Z축 시작점(Z-1.)이 공작물 끝면(Z0.) 안입니다"))
        }
        // 끝면 −16.37 ≤ 끝 Z → 끝 · 끝면
        case { set(ThreadField.Z_REF, "-16.37") }.let { (s, r, w) ->
            assertEquals(setOf(ThreadField.Z_END, ThreadField.Z_REF) to emptySet(), rollFlagged(s, r, w))
            assertTrue(rollWarningText(w[0], s, r).startsWith("공작물 끝면(Z-16.37)이 가공 목표점보다 작거나 같아"))
        }
        // 맞는 머리 없음: 1-1/2-20(Ø38.1) · 고른 머리의 범위 밖(9/16 Ø14.2875 에 F233400 Ø16–36)
        case { pickSpec(un("1-1/2", 20)) }.let { (s, r, w) ->
            assertEquals(listOf(RollWarning.OUT_OF_HEAD_RANGE), w)
            assertEquals(
                "이 규격(Ø38.1)에 맞는 EVOline 머리가 없습니다 — F2 · F23 · F233400 · F3 · F34 의 지름 범위는 Ø8 ~ 36 입니다(카탈로그). 바이트나 절삭 다이스로 깎으세요.",
                rollWarningText(w[0], s, r),
            )
        }
        case { pickSpec(un("9/16", 20)); rollHead = "F233400" }.let { (s, r, w) ->
            assertEquals(listOf(RollWarning.OUT_OF_HEAD_RANGE), w)
            assertEquals("이 규격(Ø14.288)은 고른 머리 F233400 의 지름 범위 Ø16–36(카탈로그) 밖입니다 — 머리 · 롤을 바꾸세요.", rollWarningText(w[0], s, r))
        }
        // 노랑: 비율 1.04 > 1(출처 — 설명서 인쇄 83)
        case(mapOf(RollField.RATIO to "1.04")).let { (s, r, w) ->
            assertEquals(listOf(RollWarning.RATIO_OVER_ONE), w)
            val sol = calc(s, r).solution
            assertEquals(
                "이송 비율 1.04 이 1 보다 큽니다 — 기계 이송이 피치보다 빨라, 공작물 1회전에 1피치씩 스스로 당겨 들어가는 머리를 기계가 앞서 밉니다. " +
                    "Fette 는 이송을 피치보다 5 % 작게(× 0.95) 줍니다 (F1–F3 설명서 인쇄 83).",
                rollWarningText(w[0], s, r, sol),
            )
        }
        // 노랑: 넣은 회전수 S880 이 범위 S527 ~ 879 밖
        case { set(ThreadField.RPM, "880") }.let { (s, r, w) ->
            assertEquals(listOf(RollWarning.RPM_OUT_OF_RANGE), w)
            assertEquals(
                "넣은 회전수 S880 이 Fette 재질 표 범위(STS316(1.4401) 30–50 m/min → S527 ~ 879 · 전조 전 지름 Ø18.106) 밖입니다 — " +
                    "나사면이 깨끗하지 않거나 두 줄이 겹친(이중 리드) 모양이 될 수 있습니다. 범위 안으로 맞추세요.",
                rollWarningText(w[0], s, r, calc(s, r).solution),
            )
        }
        // 노랑: 끝 멈춤 0 → G4 줄 없음
        case(mapOf(RollField.DWELL to "0")).let { (s, r, w) ->
            assertEquals(listOf(RollWarning.DWELL_ZERO), w)
            val sol = calc(s, r).solution!!
            assertFalse(sol.program.any { it.startsWith("G4") })
            assertEquals(
                "끝 멈춤이 0 이라(G4 줄 없음) 머리가 열리기 전에 급송(G0 물러남)으로 물러납니다 — Fette 는 머리가 열릴 만큼 충분히 머물라고 합니다(F1–F3 설명서 인쇄 79). " +
                    "끝 멈춤을 넣으세요(처음 값 1 초).",
                rollWarningText(w[0], s, r, sol),
            )
        }
        // 노랑: 전조 길이 (0 + 75) + 3 = 78 > F233400 선택표 73
        case { rollHead = "F233400"; set(ThreadField.Z_END, "-75") }.let { (s, r, w) ->
            assertEquals(listOf(RollWarning.ROLL_LENGTH_OVER), w)
            val sol = calc(s, r).solution!!
            assertEquals(
                "전조 길이 78.000 mm((끝면 Z − 끝 Z) + 열림 행정 a 3)가 F233400 의 최대 전조 길이 73 mm(카탈로그 선택표)보다 깁니다 — 가공 목표점이나 머리를 확인하세요.",
                rollWarningText(w[0], s, r, sol),
            )
            assertEquals("끝면부터 + a · 최대 73", rollResultCells(s, r, sol)[7].third)
        }
        // 노랑: M16×2 · F34 — Ø16 은 범위 안, 피치 2 는 롤 목록(1–1.5) 밖
        val m16 = ThreadState(spec = metric("M16", 2.0), values = mapOf("zEnd" to "-16.37"), method = ThreadMethod.ROLL, rollHead = "F34")
        val r16 = store()
        val w16 = calc(m16, r16).warnings
        assertEquals(listOf(RollWarning.ROLL_NOT_LISTED), w16)
        assertEquals(
            "피치 2 이 F34 카탈로그 롤 목록(피치 1–1.5 — 목록의 최소 · 최대, 공식 범위 칸이 아님) 밖입니다 — 맞는 롤이 있는지 따로 확인하세요.",
            rollWarningText(w16[0], m16, r16, calc(m16, r16).solution),
        )
    }

    /**
     * 요령 방향 안내(U-4): 넣은 회전수 · 비율이 기준과 다르면 그 묶음 첫 요령(출처 있는 글)을 한 줄로. 같으면 없음, 비율 내림(RATIO_DOWN)은 Fette 자료에
     * 출처 있는 글이 없어 안 띄운다. 요령 카드 글은 묶음마다 출처 표, 출처 표는 자료 이름 + 인쇄 쪽(PDF 쪽 · 독어판 쪽 뺌)
     */
    @Test
    fun directionTexts() {
        fun dir(rpm: String = "", ratio: String = ""): List<String> {
            val s = threadRoll().apply { set(ThreadField.RPM, rpm) }
            return rollDirectionText(calc(s, store(mapOf(RollField.RATIO to ratio))).solution!!)
        }
        assertEquals(
            listOf("회전수를 기준 527 보다 올렸습니다(620) — 올리면: 나사면이 깨끗하지 않거나 두 줄이 겹친(이중 리드) 모양 → 회전수를 재질 표의 범위 안으로 낮춤 (카탈로그 인쇄 242)"),
            dir(rpm = "620"),
        )
        assertEquals(
            listOf("회전수를 기준 527 보다 내렸습니다(500) — 내리면: 나사면이 깨끗하지 않거나 두 줄이 겹친(이중 리드) 모양 → 회전수를 재질 표의 범위 안으로 올림 (카탈로그 인쇄 242)"),
            dir(rpm = "500"),
        )
        assertEquals(emptyList(), dir(rpm = "527"))
        assertEquals(emptyList(), dir())
        assertEquals(
            listOf(
                "이송 비율을 기준 0.961 보다 올렸습니다(1) — 올리면: 기계 이송을 피치와 같게(또는 더 크게) 줌 → 이송을 피치보다 약 5 % 작게 줌(f ≈ 0.95·P) (F1–F3 설명서 인쇄 83)",
            ),
            dir(ratio = "1"),
        )
        // 비율 내림: 방향은 있지만 출처 있는 요령이 없어 안내 없음 · 비율 같음(1.22 / 1.27 = 0.960630 — 반올림 차 1e-6 안)
        val down = calc(threadRoll(), store(mapOf(RollField.RATIO to "0.9"))).solution!!
        assertEquals(RollTipGroup.RATIO_DOWN, down.ratioDirection)
        assertEquals(emptyList(), rollDirectionText(down))
        assertEquals(emptyList(), dir(ratio = "0.96063"))
        // 검토 B1: 자리표시 '기본 0.961 (F1.22)' 의 숫자를 그대로 넣으면 계산기는 기준 0.960630 보다 크다고 보지만(RATIO_UP) 보이는 두 값이 0.961 로 같고
        // F 도 1.22 그대로(0.961 × 1.27 = 1.22047) — '0.961 보다 올렸습니다(0.961)' 를 띄우지 않는다. 0.9614 도 같다(보이는 값 0.961 · F1.221 — 자리 수 밖 차이)
        val shown = calc(threadRoll(), store(mapOf(RollField.RATIO to "0.961"))).solution!!
        assertEquals(RollTipGroup.RATIO_UP, shown.ratioDirection)
        assertEquals("G1Z-16.37F1.22", shown.program.first { it.startsWith("G1") })
        assertEquals(emptyList(), rollDirectionText(shown))
        assertEquals(emptyList(), dir(ratio = "0.9614"))
        // 보이는 값이 0.962 로 바뀌면(F1.222) 안내한다
        assertEquals("이송 비율을 기준 0.961 보다 올렸습니다(0.962) — 올리면: ", dir(ratio = "0.962").single().substringBefore("기계 이송"))
        // 보이는 비율은 달라도(0.95 → 0.951) 찍힐 F 가 같으면 안내 없음: M8×0.75 · Fette 0.95 → F.713(0.7125), 넣은 0.9505 → 0.712875 도 F.713
        val fine = ThreadState(family = ThreadFamily.METRIC, spec = metric("M8", 0.75), values = mapOf("zEnd" to "-10"), method = ThreadMethod.ROLL)
        val same = calc(fine, store(mapOf(RollField.RATIO to "0.9505"))).solution!!
        assertEquals(RollTipGroup.RATIO_UP, same.ratioDirection)
        assertEquals(listOf("0.95", "0.951"), listOf(plain(same.baseRatio), plain(same.ratio)))
        assertEquals("G1Z-10.F.713", same.program.first { it.startsWith("G1") })
        assertEquals(emptyList(), rollDirectionText(same))
        // F 가 바뀌면(0.96 → F.72) 안내한다
        assertEquals(1, rollDirectionText(calc(fine, store(mapOf(RollField.RATIO to "0.96"))).solution!!).size)
        // 둘 다 고치면 두 줄(회전수 먼저)
        assertEquals(2, dir(rpm = "620", ratio = "1").size)
        // 다른 소재 + 넣은 회전수는 기준이 없어 안내 없음
        val p = threadRoll().apply { material = IsoGroup.P; set(ThreadField.RPM, "700") }
        assertEquals(emptyList(), rollDirectionText(calc(p, store()).solution!!))

        // 요령 카드 글: 출처 있는 글만, 묶음 일곱 차례
        assertEquals(
            listOf(RollTipGroup.RPM_UP, RollTipGroup.RPM_DOWN, RollTipGroup.RATIO_UP, RollTipGroup.RATIO_DOWN, RollTipGroup.BLANK_BIG, RollTipGroup.BLANK_SMALL, RollTipGroup.SETUP),
            ROLL_TIP_GROUPS,
        )
        assertEquals(listOf("Fette 자료에 없음"), rollTipsText(RollTipGroup.RATIO_DOWN))
        assertEquals(
            "나사면이 깨끗하지 않거나 두 줄이 겹친(이중 리드) 모양 → 회전수를 재질 표의 범위 안으로 낮춤 " +
                "(카탈로그 인쇄 242 · F1–F3 설명서 인쇄 62 · F1–F3 설명서 인쇄 80, Tab. 6 · F RN 설명서 인쇄 69, Table 13 · F01/K01 설명서 인쇄 50, Table 8)",
            rollTipsText(RollTipGroup.RPM_UP).first(),
        )
        val setup = rollTipsText(RollTipGroup.SETUP)
        assertEquals((1..9).map { "$it. " }, setup.map { it.substringBefore(". ") + ". " })
        for (g in ROLL_TIP_GROUPS + RollTipGroup.OTHER) {
            for (t in rollTipsText(g) + rollTipsFull(g)) assertTrue(t == "Fette 자료에 없음" || (t.endsWith(")") && " (" in t), "$g: $t")
        }
        // 검토 V3-1-2: 화면 · 도움말 요령 글은 소수점이 점(0.95 · ±0.015 — 독일식 쉼표 0,95 가 아니게, 같은 화면의 '× 0.95' 와 같은 표기)
        val decimalComma = Regex("\\d,\\d")
        for (g in ROLL_TIP_GROUPS + RollTipGroup.OTHER) {
            for (t in rollTipsText(g) + rollTipsFull(g)) assertTrue(!decimalComma.containsMatchIn(t), "$g: $t")
        }
        assertTrue(rollTipsText(RollTipGroup.BLANK_BIG).any { "±0.015 mm" in it })
        // 검토 V1-1-3: 요령 글의 열림 행정 · 롤 경사각에 F3(a 4 mm · 2°40′ — 설명서 Tab. 8 · 카탈로그 인쇄 237)도
        assertTrue(setup[4].contains("(2·23·233400 = 3 mm, 3·34 = 4 mm)"), setup[4])
        val other = rollTipsFull(RollTipGroup.OTHER)
        assertTrue(other.any { "233400 = 1°15′, 3 = 2°40′, 34 = 1°15′" in it }, "롤 경사각")
        assertTrue(other.any { "(a: 2·23·233400 = 3 mm, 3·34 = 4 mm)" in it }, "lU 에 못 미침")
        // 출처 표: PDF 쪽 · 독어판 쪽(';' 뒤)을 뺀다
        assertEquals("카탈로그 인쇄 242", rollSourceText(RollRef("FET_CAT_ROLL", "인쇄 242 (PDF 244쪽)")))
        assertEquals("F1–F3 설명서 인쇄 80", rollSourceText(RollRef("FET_OI_F1F3_EVO", "인쇄 80 (PDF 82쪽); 독어 인쇄 30 (PDF 32쪽)")))
        assertEquals("F1–F3 설명서 인쇄 84, Tab. 8", rollSourceText(RollRef("FET_OI_F1F3_EVO", "인쇄 84 (PDF 86쪽), Tab. 8")))
    }

    /** 가공 방식 세 칸(결정 D9 · F3): 화면 차례 [바이트 | 전조 다이스 | 절삭 다이스] — enum 차례(BITE · DIE · ROLL)와 다르다. 고른 칸 번호 · 누른 칸 번호 모두 이 목록으로 */
    @Test
    fun methodSegmentOrder() {
        assertEquals(listOf(ThreadMethod.BITE, ThreadMethod.ROLL, ThreadMethod.DIE), METHOD_ORDER)
        assertEquals(listOf(ThreadMethod.BITE, ThreadMethod.DIE, ThreadMethod.ROLL), ThreadMethod.entries)
        assertEquals(listOf("바이트 (G92 · G76)", "전조 다이스", "절삭 다이스"), METHOD_ORDER.map { methodText(it) })
        assertEquals(listOf("바이트", "전조 다이스", "절삭 다이스"), METHOD_ORDER.map { methodText(it, narrow = true) })
        val s = threadOriginal()
        for ((i, m) in METHOD_ORDER.withIndex()) {
            // 화면: 칸 i 를 누르면 pickMethod(METHOD_ORDER[i]), 고른 칸 = METHOD_ORDER.indexOf(method)
            s.pickMethod(METHOD_ORDER[i])
            assertEquals(m, s.method)
            assertEquals(i, METHOD_ORDER.indexOf(s.method))
        }
        assertEquals(listOf(true, false), listOf(s.isDie, s.isRoll))
        s.pickMethod(METHOD_ORDER[1])
        assertEquals(listOf(false, true), listOf(s.isDie, s.isRoll))
    }

    /**
     * 검토 V3-1-3: 줄표(–)로 이은 출처 · 범위는 화면 · 전조 도움말 · 머리 시트에서 줄 끝에 갈리지 않는다 — 줄표 양옆에 낱말 이음(\u2060).
     * 360dp 130% 전조 조건 참고 글이 'F1– / F3 설명서', 412dp 도움말이 'F3 Ø12– / 22' 로 갈렸다. 골든 · 시험이 보는 글 함수(rollReferenceText · rollHeadRow)는 그대로
     */
    @Test
    fun dashKeptTogether() {
        val wj = "\u2060"
        assertEquals("F1$wj–${wj}F3 설명서 · Ø12$wj–${wj}22", keepDash("F1–F3 설명서 · Ø12–22"))
        val ref = rollReferenceText(threadRoll())
        assertTrue("F1–F3 설명서" in ref && "30–50" in ref, ref)
        val shown = screenText(ref)
        assertTrue("F1$wj–${wj}F3 설명서" in shown && "30$wj–${wj}50 m/${wj}min" in shown, shown)
        // 결과 칸 아래 글(screenText 를 거침): '범위 30–50 m/min' · '카탈로그 Ø12–30'
        assertEquals("범위 30$wj–${wj}50 m/${wj}min", screenText("범위 30–50 m/min"))
        // 도움말 '전조 다이스' 묶음: 모든 줄표가 이음 사이 — 머리 범위 'F3 Ø12–22' · 'F1–F3 설명서 Tab. 6' · 속도 '30–50' · 요령 전문의 '10–30°'
        val help = ThreadHelp.single { it.title == "전조 다이스" }.items
        val loose = Regex("(?<!$wj)–|–(?!$wj)")
        for (p in help) assertFalse(loose.containsMatchIn(p), p)
        val all = help.joinToString(" ")
        assertTrue("F3 Ø12$wj–${wj}22" in all && "F1$wj–${wj}F3 설명서 Tab. 6" in all, "도움말 범위 · 출처")
        // 다른 도움말 묶음(테이퍼 'T1–T2' 등 — 공용 글)은 건드리지 않는다
        assertTrue(ThreadHelp.filter { it.title != "전조 다이스" }.all { sec -> sec.items.none { wj + "–" in it } })
        // 머리 시트 아래 글(화면은 keepDash 를 거침 — rollHeadRow 글 자체는 그대로)
        assertEquals("F3" to "회사 롤 없음 · Ø12–22", rollHeadRow(un("3/4", 20), "F3"))
        // 검토 V3-2-1: 요령 카드 '자세히' 펼친 글도 screenText 를 거쳐 줄표가 이음 사이(412dp 'F1– / F3 설명서 인쇄 85' 로 갈렸다) — 글 자체는 rollTipsText 그대로
        val hint = rollTipsHint()
        assertFalse(loose.containsMatchIn(hint), hint)
        assertTrue("F1$wj–${wj}F3 설명서 인쇄 85" in hint, "요령 카드 출처")
        assertEquals(
            ROLL_TIP_GROUPS.joinToString("\n\n") { g -> rollTipGroupTitle(g) + "\n" + rollTipsText(g).joinToString("\n") { "· $it" } },
            hint.replace(wj, "").replace(' ', ' '),
        )
        // 검토 V3-2-2 · V3-2-4: 요령 카드 · 도움말 글에 붙임표 'F1-F3'(keepDash 가 못 묶어 'F1- / F3' 로 갈렸다) · 코드 이름(BLANK_BIG 등)이 없다
        val codeName = Regex("[A-Z]+_[A-Z]+")
        for (t in help + hint) {
            assertFalse("F1-F3" in t, t)
            assertFalse(codeName.containsMatchIn(t), t)
        }
        assertTrue("('전조 전 지름이 크면' · '작으면' 참고)" in all && "('전조 전 지름이 크면' 참고)" in all, "도움말 묶음 이름")
        // 검토 V3-2-3: '그 밖' 의 '전조 결과가 틀림' — 증상 뒤에 출처 이름을 다시 적지 않는다(설명서 이름은 끝 출처 괄호에 두 번만)
        val wrong = rollTipsFull(RollTipGroup.OTHER).single { it.startsWith("전조 결과가 틀림") }
        assertTrue(wrong.startsWith("전조 결과가 틀림 → "), wrong)
        assertEquals(2, wrong.split("F1–F3 설명서").size - 1, wrong)
        // 검토 V3-3-1: 영숫자 사이 빗금('F01/K01' · 'm/min') 뒤에 낱말 이음, 출처 이름 'F RN' 은 줄 안 바꿈 빈칸 — 412dp 도움말 'F01/ / K01 설명서',
        // 360dp 130% 요령 카드 '20 m/ / min', 도움말 'F / RN·F01/K01' 로 갈렸다. 앞뒤 빈칸이 있는 빗금('STS304 / STS316')과 글 함수(rollSourceText)는 그대로
        val nb = "\u00A0"
        assertEquals(
            "F${nb}RN 설명서 · F01/${wj}K01 설명서 · 30$wj–${wj}50 m/${wj}min · STS304 / STS316",
            keepDash("F RN 설명서 · F01/K01 설명서 · 30–50 m/min · STS304 / STS316"),
        )
        val looseSlash = Regex("[A-Za-z0-9]/(?!$wj)[A-Za-z0-9]")
        for (t in help + hint) {
            assertFalse(looseSlash.containsMatchIn(t), t)
            assertFalse("F RN" in t, t)
        }
        assertTrue("F${nb}RN·F01/${wj}K01 계열" in all && "Table 13 · F01/${wj}K01 설명서 인쇄 50" in all && "35$wj–${wj}55 m/${wj}min" in all, "도움말 출처 이름 · 단위")
        assertTrue("전조 속도를 20 m/${wj}min 아래로" in hint, "요령 카드 단위")
    }
}
