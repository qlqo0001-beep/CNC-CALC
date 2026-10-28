package com.bmt.nosecalc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.calc.HolderStop
import com.bmt.nosecalc.screens.RoundScreen
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.screens.TriangleScreen
import com.bmt.nosecalc.screens.round.MANUAL
import com.bmt.nosecalc.screens.round.RoundState
import com.bmt.nosecalc.screens.round.blankable
import com.bmt.nosecalc.screens.round.dims
import com.bmt.nosecalc.screens.round.roundExample
import com.bmt.nosecalc.screens.round.roundInnerExample
import com.bmt.nosecalc.screens.taper.TaperExample
import com.bmt.nosecalc.screens.taper.TaperKind
import com.bmt.nosecalc.screens.taper.TaperState
import com.bmt.nosecalc.screens.taper.example
import com.bmt.nosecalc.screens.triangle.TriField
import com.bmt.nosecalc.screens.triangle.TriangleState
import com.bmt.nosecalc.ui.NumEdit
import com.bmt.nosecalc.ui.theme.DarkColors
import com.bmt.nosecalc.ui.theme.LightColors
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 독립 검증(2026-10-28 사용자 요청 "다음 을 확인으로 변경하고 넘어가게 하지마" · "0 이 적힌 좌표들을 다 빈칸으로"):
 * 구현 도우미의 판정(waiting 등)을 쓰지 않고 실제 화면(ImageComposeScene)의 글 · 픽셀 색으로 본다.
 * 1) 처음 값: 0 이던 칸은 모두 빈칸, 0 이 아닌 처음 값(노즈 R · 인서트 폭 · 겹침 · 끝 직선 길이 · 안전 여유)은 그대로
 * 2) 첫 화면: 오류색 픽셀 · 경고 글 · 결과 · NC 가 없고 파란 안내만. 값을 한 자리씩 넣는 동안 모자라면 빨강 없음, 다 넣으면 예시(예전 처음 값) 화면과 글자까지 같음
 * 3) '비우면 0' 칸: 비운 화면 = 0 을 넣은 화면(그 칸 글만 다르다)
 * 4) 키패드: 확인 = 닫기만(다음 칸 안 열림) · "-" 만이면 되돌림(Esc · Enter 와 같음) · Enter 닫기 · Tab / Shift+Tab 다음 · 앞 · 마지막 Tab 닫기
 * 5) 되살린 0(예전 처음 값 · 사용자가 넣은 0)은 0 그대로이고 빨강 없이 안내
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class VerifyKeypadBlankTest {
    /** 화면 하나(밀도 1, 412 폭). 상태는 밖에서 바꾸고 frames 로 다시 그린다 */
    private class Screen(dark: Boolean = false, height: Int = 4200, content: @Composable () -> Unit) : AutoCloseable {
        val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() })).apply { setTheme(dark) }
        private val scene = ImageComposeScene(412, height, Density(1f)) { AppHost(app) { content() } }
        private var t = 0L
        private val bad: Set<Int> = (if (dark) DarkColors else LightColors).let { c ->
            setOf(c.error.bg, c.error.border, c.error.label, c.red).map { it.toArgb() }.toSet()
        }

        init {
            frames(8)
        }

        fun frames(n: Int = 3) = repeat(n) {
            Snapshot.sendApplyNotifications()
            scene.render(t).close()
            t += 100_000_000L
        }

        private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)

        /** 화면 글(keepWords 의 낱말 이음 ⁠ · 붙임 공백은 지워 읽는다) */
        fun texts(): List<String> = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text.replace("⁠", "").replace(' ', ' ') } }

        fun clickable(text: String): List<SemanticsNode> = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.filter { n ->
            n.config.getOrNull(SemanticsActions.OnClick) != null && n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
        }

        fun tap(text: String) {
            clickable(text).single().config[SemanticsActions.OnClick].action!!.invoke()
            frames(4)
        }

        fun key(k: Key, shift: Boolean = false) {
            app.handleKey(KeyEvent(k, KeyEventType.KeyDown, isShiftPressed = shift))
            frames(3)
        }

        val keypadOpen: Boolean get() = app.overlays.isNotEmpty()

        /** 오류색(빨강 띠 바탕 · 테 · 글, 빨강) 그대로인 픽셀 수 */
        fun redPixels(): Int {
            Snapshot.sendApplyNotifications()
            val img = scene.render(t)
            t += 100_000_000L
            val bytes = img.encodeToData()!!.bytes
            img.close()
            val bi = ImageIO.read(ByteArrayInputStream(bytes))
            return bi.getRGB(0, 0, bi.width, bi.height, null, 0, bi.width).count { it in bad }
        }

        override fun close() = scene.close()
    }

    /** 빨간 띠 · 노란 띠 · 칸 아래 빨간 글에 나오는 말 */
    private val alarms = listOf("계산 멈춤", "확인 필요", "입력이 잘못됐습니다", "맞지 않습니다", "침범", "계산 안 됨", "나오지 않습니다", "들어가지 않습니다", "닿지 못합니다")
    private fun alarmTexts(t: List<String>) = t.filter { s -> alarms.any { it in s } }

    private val observations = mutableListOf<String>()

    // ---------------------------------------------------------------- 1) 처음 값

    @Test
    fun initialValues() {
        for (kind in TaperKind.entries.filter { !it.groove }) {
            for (type in 1..kind.types) for (rev in listOf(false, true)) {
                val s = TaperState(kind, rev, type, open = true)
                for (i in 1..kind.points) for (n in listOf("x$i", "z$i", "r$i")) assertEquals("", s.point(n), "${kind.label} $type $rev $n")
                for (n in listOf("ang", "start", "end")) assertEquals("", s.point(n))
                assertEquals("", s.shared("zClear"))
                assertEquals("0.4", s.shared("noseR"))
            }
        }
        val g = TaperState(TaperKind.GROOVE, type = 1, open = true)
        for (n in listOf("zStart", "width", "od", "bottom", "xClear")) assertEquals("", g.shared(n), n)
        assertEquals("3", g.shared("insert"))
        assertEquals("0.5", g.shared("overlap"))
        val r = RoundState()
        for (t in 1..6) for (d in dims(t)) assertEquals("", r.dim(t, d.key), "round $t ${d.key}")
        for (p in listOf("x1", "z1", "x2", "z2", "x3", "z3")) assertEquals("", r.point(p))
        assertEquals("", r.shared("zClear"))
        assertEquals("0.4", r.shared("noseR"))
        assertEquals("5.0", r.shared("leg"))
        for (h in listOf("dia", "f", "shape", "edge", "kr", "ovh", "dmin")) assertEquals("", r.holder(h), h)
        assertEquals("0.5", r.holder("safe"))
        assertEquals(HolderStop.SAFE_DEFAULT, r.holderSafe())
        val tri = TriangleState()
        for (f in TriField.entries) assertEquals("", tri.value(f))
    }

    // ---------------------------------------------------------------- 2) 첫 화면

    private fun firstScreen(name: String, dark: Boolean, wantBlankZero: Int, content: @Composable () -> Unit) {
        Screen(dark) { content() }.use { sc ->
            val t = sc.texts()
            val red = sc.redPixels()
            val alarm = alarmTexts(t)
            val all = t.joinToString(" | ")
            assertEquals(0, red, "$name ${if (dark) "다크" else "라이트"} 오류색 픽셀: $all")
            assertTrue(alarm.isEmpty(), "$name 경고 글 $alarm")
            assertTrue(t.none { it == "0" || it == "0.0" }, "$name 칸에 0: $all")
            assertTrue(t.any { it.startsWith("값을 넣으면") || it.startsWith("세 점의 X · Z") || it.startsWith("시작 Z · 홈 폭") || it.startsWith("값 두 개를") }, "$name 안내: $all")
            assertTrue(t.none { "G00" in it || "G01" in it || it.endsWith("프로그램") }, "$name NC 가 보인다: $all")
            assertEquals(wantBlankZero, t.count { it == "비우면 0" }, "$name '비우면 0' 수: $all")
            assertFalse(sc.keypadOpen)
        }
    }

    @Test
    fun firstScreensCalm() {
        for (dark in listOf(false, true)) {
            for (kind in TaperKind.entries.filter { !it.groove }) for (type in 1..kind.types) for (rev in listOf(false, true)) {
                firstScreen("${kind.label} $type ${if (rev) "역" else "정"}", dark, kind.points + 1) { TaperScreen(TaperState(kind, rev, type, open = true)) }
            }
            for (type in 1..2) firstScreen("홈 $type", dark, 1) { TaperScreen(TaperState(TaperKind.GROOVE, type = type, open = true)) }
            for (type in 1..6) {
                val corners = dims(type).count { it.key.startsWith("cr") }
                firstScreen("라운드 외경 $type", dark, corners + 1) { RoundScreen(RoundState(type, open = true)) }
                if (type >= 2) firstScreen("라운드 내경 $type", dark, corners + 1) { RoundScreen(RoundState(type, open = true).apply { typeInner = true }) }
            }
            firstScreen("3점호 외경", dark, 1) { RoundScreen(RoundState(MANUAL, open = true)) }
            firstScreen("3점호 내경", dark, 1) { RoundScreen(RoundState(MANUAL, open = true).apply { inner = true }) }
            firstScreen("직각삼각형", dark, 0) { TriangleScreen(TriangleState()) }
        }
    }

    /** 대조군: 진짜 멈춤 화면은 오류색 픽셀 · 경고 글이 잡힌다(검사가 빨강을 놓치지 않는지) */
    @Test
    fun redDetectorWorks() {
        for (dark in listOf(false, true)) {
            val cases = listOf<Pair<String, @Composable () -> Unit>>(
                "테이퍼 모순" to @Composable { TaperScreen(TaperState(type = 3, open = true).example().apply { setPoint("ang", "30") }) },
                "홈 침범" to @Composable { TaperScreen(TaperState(TaperKind.GROOVE, type = 2, open = true).example().apply { setShared("insert", "9") }) },
                "라운드 모순" to @Composable { RoundScreen(roundExample(2, open = true).apply { setDim(2, "l2", "30") }) },
                "삼각형 각도만" to @Composable { TriangleScreen(TriangleState(mapOf("angA" to "30", "angB" to "60"))) },
            )
            for ((name, content) in cases) Screen(dark) { content() }.use { sc ->
                val red = sc.redPixels()
                assertTrue(red > 100, "$name 대조군 빨강 $red")
                assertTrue(alarmTexts(sc.texts()).isNotEmpty(), "$name 대조군 경고 글")
            }
            // 풀린 예시 화면(기계 보정)은 빨강 0 — 첫 화면의 0 이 우연이 아니다
            Screen(dark) { TaperScreen(TaperState(type = 1, open = true).example()) }.use { assertEquals(0, it.redPixels(), "풀린 테이퍼") }
            Screen(dark) { RoundScreen(roundExample(3, open = true)) }.use { assertEquals(0, it.redPixels(), "풀린 라운드") }
        }
    }

    /**
     * 칸을 차례로 한 자리씩(키패드가 바로 넣는 글 = NumEdit.live) 넣는다. 모자라는 동안(incomplete)은 빨강 · 경고 글이 없어야 하고,
     * 다 넣은 뒤 화면 글은 target(예시 = 예전 처음 값 상태 · 골든과 같은 상태)과 글자까지 같아야 한다
     */
    private fun flow(
        name: String,
        content: @Composable () -> Unit,
        fields: List<Pair<String, (String) -> Unit>>,
        values: List<String>,
        incomplete: () -> Boolean,
        target: @Composable () -> Unit,
        checkPixels: Boolean = true,
    ) {
        val typed = Screen { content() }.use { sc ->
            fields.zip(values).forEach { (f, v) ->
                val (field, set) = f
                val lives = (1..v.length).mapNotNull { NumEdit.live(v.take(it)) }
                for (p in lives) {
                    set(p)
                    sc.frames(2)
                    if (!checkPixels) continue
                    val t = sc.texts()
                    val red = sc.redPixels()
                    val alarm = alarmTexts(t)
                    if (incomplete()) {
                        assertEquals(0, red, "$name $field=$p 모자라는 동안 빨강")
                        assertTrue(alarm.isEmpty(), "$name $field=$p 모자라는 동안 경고 $alarm")
                    } else if (red > 0 || alarm.isNotEmpty()) {
                        observations += "$name $field=$p (값은 다 참) 빨강 $red ${alarm.map { it.take(40) }}"
                    }
                }
            }
            sc.texts()
        }
        val want = Screen { target() }.use { it.texts() }
        assertEquals(want, typed, "$name: 한 칸씩 넣은 화면과 예시 화면이 다르다")
    }

    private fun taperFields(s: TaperState): List<Pair<String, (String) -> Unit>> =
        (1..s.kind.points).flatMap { i -> listOf("x$i", "z$i") }.map { n -> n to { v: String -> s.setPoint(n, v) } } +
            listOf("zClear" to { v: String -> s.setShared("zClear", v) })

    private fun taperValues(kind: TaperKind, type: Int, rev: Boolean) =
        (1..kind.points).flatMap { i -> listOf("x$i", "z$i") }.map { TaperExample.point(kind, type, rev, it) } + "1.0"

    /** 예전 처음 값(2e5abc5: 점 X · Z 와 Z 여유 0)을 저장된 값처럼 되살린 상태 */
    private fun oldTaper(kind: TaperKind, type: Int, rev: Boolean) = TaperState(
        kind, rev, type, open = true,
        values = (1..kind.points).flatMap { i -> listOf("x$i", "z$i") }.associate { "p/${kind.name}/$type/${if (rev) "r" else "f"}/$it" to "0" } + ("s/zClear" to "0"),
    )

    @Test
    fun typingFromBlankTaper() {
        for (kind in TaperKind.entries.filter { !it.groove }) for (type in 1..kind.types) for (rev in listOf(false, true)) {
            val s = TaperState(kind, rev, type, open = true)
            val need = (1..kind.points).flatMap { listOf("x$it", "z$it") } + if (kind.double) emptyList() else listOf("ang")
            val enough = if (kind.double) need.size else 4
            val name = "${kind.label} $type ${if (rev) "역" else "정"}"
            flow(
                name, { TaperScreen(s) }, taperFields(s), taperValues(kind, type, rev),
                { need.count { s.point(it).isNotEmpty() } < enough },
                { TaperScreen(TaperState(kind, rev, type, open = true).example()) },
            )
            // 예전 처음 값(0)에서 같은 키를 눌렀을 때의 화면과도 같다
            val old = oldTaper(kind, type, rev)
            flow(
                "$name (예전 0 시작)", { TaperScreen(old) }, taperFields(old), taperValues(kind, type, rev), { false },
                { TaperScreen(TaperState(kind, rev, type, open = true).example()) }, checkPixels = false,
            )
        }
        val gNames = listOf("zStart", "width", "od", "bottom", "xClear")
        for (type in 1..2) {
            val g = TaperState(TaperKind.GROOVE, type = type, open = true)
            flow(
                "홈 $type", { TaperScreen(g) }, gNames.map { n -> n to { v: String -> g.setShared(n, v) } }, gNames.map { TaperExample.groove.getValue(it) },
                { gNames.take(4).any { g.shared(it).isEmpty() } },
                { TaperScreen(TaperState(TaperKind.GROOVE, type = type, open = true).example()) },
            )
        }
        println("값이 다 찬 뒤 한 자리 치는 사이 빨강(관찰):\n" + observations.joinToString("\n"))
    }

    @Test
    fun typingFromBlankRound() {
        for (inner in listOf(false, true)) for (type in (if (inner) 2 else 1)..6) {
            val target = if (inner) roundInnerExample(type) else roundExample(type, open = true)
            val s = RoundState(type, open = true).apply { typeInner = inner }
            val keys = dims(type).map { it.key }.filter { target.dim(type, it).isNotEmpty() }
            val fields = keys.map { k -> k to { v: String -> s.setDim(type, k, v) } } +
                listOf("zClear" to { v: String -> s.setShared("zClear", v) }) +
                (if (inner) listOf("leg" to { v: String -> s.setShared("leg", v) }) else emptyList())
            val values = keys.map { target.dim(type, it) } + "1.0" + (if (inner) listOf("10") else emptyList())
            val group = blankable(type)
            val must = dims(type).map { it.key }.filter { !it.startsWith("cr") && it !in group }
            flow(
                "라운드 ${if (inner) "내경" else "외경"} $type", { RoundScreen(s) }, fields, values,
                { must.any { s.dim(type, it).isEmpty() } || group.count { s.dim(type, it).isEmpty() } > 1 },
                { RoundScreen(target) },
            )
        }
        val pts = listOf("x1", "z1", "x2", "z2", "x3", "z3")
        val m = RoundState(MANUAL, open = true)
        flow(
            "3점호", { RoundScreen(m) }, pts.map { n -> n to { v: String -> m.setPoint(n, v) } } + listOf("zClear" to { v: String -> m.setShared("zClear", v) }),
            pts.map { roundExample().point(it) } + "1.0",
            { pts.any { m.point(it).isEmpty() } },
            { RoundScreen(roundExample(MANUAL, open = true)) },
        )
        val tri = TriangleState()
        flow(
            "직각삼각형", { TriangleScreen(tri) },
            listOf("a" to { v: String -> tri.set(TriField.A_SIDE, v) }, "angA" to { v: String -> tri.set(TriField.ANGLE_A, v) }), listOf("10", "25"),
            { TriField.entries.count { tri.given(it) } < 2 },
            { TriangleScreen(TriangleState(mapOf("a" to "10", "angA" to "25"))) },
        )
        println("값이 다 찬 뒤 한 자리 치는 사이 빨강(관찰):\n" + observations.joinToString("\n"))
    }

    // ---------------------------------------------------------------- 3) '비우면 0'

    /** 같은 화면을 칸 하나만 "" · "0" 으로 그려 글을 맞댄다: 다른 곳은 그 칸(자리표시 '비우면 0' ↔ 값 "0") 하나뿐 */
    private fun blankIsZero(name: String, make: (String) -> (@Composable () -> Unit)) {
        val blank = Screen { make("")() }.use { it.texts() }
        val zero = Screen { make("0")() }.use { it.texts() }
        assertEquals(zero.size, blank.size, "$name 글 수")
        val diff = blank.indices.filter { blank[it] != zero[it] }.map { blank[it] to zero[it] }
        assertEquals(listOf("비우면 0" to "0"), diff, name)
    }

    @Test
    fun blankEqualsZero() {
        blankIsZero("테이퍼 Z 여유") { v -> @Composable { TaperScreen(TaperState(type = 1, open = true).example().apply { setShared("zClear", v) }) } }
        blankIsZero("테이퍼 모서리 R1") { v -> @Composable { TaperScreen(TaperState(type = 3, open = true).example().apply { setPoint("r1", v) }) } }
        blankIsZero("2중 모서리 R2") { v -> @Composable { TaperScreen(TaperState(TaperKind.OUTER_DOUBLE, type = 2, open = true).example().apply { setPoint("r2", v) }) } }
        blankIsZero("홈 X 여유") { v -> @Composable { TaperScreen(TaperState(TaperKind.GROOVE, type = 1, open = true).example().apply { setShared("xClear", v) }) } }
        blankIsZero("라운드 Z 여유") { v -> @Composable { RoundScreen(roundExample(3, open = true).apply { setShared("zClear", v) }) } }
        blankIsZero("라운드 모서리 R") { v -> @Composable { RoundScreen(roundExample(3, open = true).apply { setDim(3, "cr1", v) }) } }
        blankIsZero("라운드 5 모서리 R(2)") { v -> @Composable { RoundScreen(roundExample(5, open = true).apply { setDim(5, "cr2", v) }) } }
        blankIsZero("내경 라운드 Z 여유") { v -> @Composable { RoundScreen(roundInnerExample(3).apply { setShared("zClear", v) }) } }
        blankIsZero("3점호 Z 여유") { v -> @Composable { RoundScreen(roundExample(MANUAL, open = true).apply { setShared("zClear", v) }) } }
    }

    // ---------------------------------------------------------------- 4) 키패드

    /** 다음 칸이 있는 첫 칸에서 연 키패드: 오른쪽 열 '확인' 하나(두 칸 높이 124dp), '다음' · '닫기' · "n / m" 없음. 확인 = 닫기만 */
    private fun confirmOnly(name: String, open: () -> Unit, read: () -> String, next: () -> String, content: @Composable () -> Unit) {
        open()
        Screen { content() }.use { sc ->
            assertTrue(sc.keypadOpen, "$name 키패드")
            val t = sc.texts()
            assertTrue("확인" in t && "취소" in t, "$name $t")
            assertTrue(t.none { it == "다음" || it == "닫기" || it == "다음 ›" || Regex("""\d+ / \d+""").matches(it) }, "$name $t")
            val ok = sc.clickable("확인").single()
            assertEquals(124f, ok.boundsInRoot.height, 1.5f, "$name 확인 키 높이")
            assertTrue(ok.boundsInRoot.left > 280f, "$name 확인 키는 오른쪽 열 ${ok.boundsInRoot}")
            val nextBefore = next()
            sc.key(Key.Seven)
            assertEquals("7", read(), name)
            sc.tap("확인")
            assertFalse(sc.keypadOpen, "$name 확인 뒤 키패드가 남았다")
            assertTrue("확인" !in sc.texts() && "취소" !in sc.texts(), name)
            assertEquals("7", read(), name)
            sc.key(Key.Five) // 닫힌 뒤 키는 아무 칸에도 들어가지 않는다(다음 칸이 열려 있지 않다)
            assertEquals("7", read(), name)
            assertEquals(nextBefore, next(), "$name 다음 칸이 바뀌었다")
            assertFalse(sc.keypadOpen)
        }
    }

    @Test
    fun confirmClosesOnEveryScreen() {
        TaperState(type = 1, open = true).let { s -> confirmOnly("테이퍼", { s.editing = "x1" }, { s.point("x1") }, { s.point("z1") }) { TaperScreen(s) } }
        TaperState(TaperKind.INNER_DOUBLE, type = 2, open = true).let { s -> confirmOnly("2중", { s.editing = "x1" }, { s.point("x1") }, { s.point("z1") }) { TaperScreen(s) } }
        TaperState(TaperKind.GROOVE, type = 1, open = true).let { s -> confirmOnly("홈", { s.editing = "zStart" }, { s.shared("zStart") }, { s.shared("width") }) { TaperScreen(s) } }
        RoundState(3, open = true).let { s -> confirmOnly("라운드 3", { s.editing = "cr1" }, { s.dim(3, "cr1") }, { s.dim(3, "mainR") }) { RoundScreen(s) } }
        RoundState(3, open = true).apply { typeInner = true }.let { s ->
            confirmOnly("내경 라운드 보링바", { s.editing = "h/dia" }, { s.holder("dia") }, { s.holder("edge") }) { RoundScreen(s) }
        }
        RoundState(MANUAL, open = true).let { s -> confirmOnly("3점호", { s.editing = "x1" }, { s.point("x1") }, { s.point("z1") }) { RoundScreen(s) } }
        TriangleState().let { s -> confirmOnly("삼각형", {}, { s.value(TriField.A_SIDE) }, { s.value(TriField.B_SIDE) }) { TriangleScreen(s, TriField.A_SIDE) } }
    }

    /** "-" 만 남기고 닫으면 연 때 값으로 — 확인 · Enter · Esc · 바깥 누름 모두 같다. "5." 는 "5" */
    @Test
    fun confirmSettlesLikeClose() {
        fun run(close: (Screen) -> Unit, keys: List<Key>): String {
            val s = TaperState(type = 1, open = true).apply { setPoint("x1", "40"); editing = "x1" }
            Screen { TaperScreen(s) }.use { sc ->
                keys.forEach { sc.key(it) }
                close(sc)
                assertFalse(sc.keypadOpen)
                return s.point("x1")
            }
        }
        val dash = listOf(Key.Seven, Key.Backspace, Key.Minus)
        val closes = mapOf<String, (Screen) -> Unit>(
            "확인" to { it.tap("확인") },
            "Enter" to { it.key(Key.Enter) },
            "NumPadEnter" to { it.key(Key.NumPadEnter) },
            "Esc" to { it.key(Key.Escape) },
        )
        for ((n, c) in closes) {
            assertEquals("40", run(c, dash), "$n: '-' 만이면 연 때 값")
            assertEquals("40", run(c, listOf(Key.Minus)), "$n: '-' 하나")
            assertEquals("5", run(c, listOf(Key.Five, Key.Period)), "$n: '5.'")
            assertEquals("", run(c, listOf(Key.Delete)), "$n: C = 빈칸")
        }
        // 취소는 연 때 값으로
        assertEquals("40", run({ it.tap("취소") }, listOf(Key.Nine)))
    }

    /** PC: Tab = 다음 칸(키패드 그대로), Shift+Tab = 앞 칸, Enter = 닫기(넘어가지 않음), 마지막 칸 Tab = 닫기, Esc = 닫기 */
    @Test
    fun pcKeysOnScreen() {
        val s = TaperState(type = 1, open = true).apply { editing = "x1" }
        Screen { TaperScreen(s) }.use { sc ->
            sc.key(Key.Tab)
            assertTrue(sc.keypadOpen)
            sc.key(Key.Nine)
            assertEquals("" to "9", s.point("x1") to s.point("z1"))
            sc.key(Key.Tab, shift = true)
            sc.key(Key.Three)
            assertEquals("3" to "9", s.point("x1") to s.point("z1"))
            sc.key(Key.Enter)
            assertFalse(sc.keypadOpen, "Enter 는 닫는다")
            sc.key(Key.One)
            assertEquals("3" to "9", s.point("x1") to s.point("z1"))
        }
        val e = TaperState(type = 1, open = true).apply { editing = "z1" }
        Screen { TaperScreen(e) }.use { sc ->
            sc.key(Key.Two)
            sc.key(Key.Escape)
            assertFalse(sc.keypadOpen, "Esc 는 닫는다")
            assertEquals("2", e.point("z1"))
            assertTrue(e.open, "Esc 한 번은 키패드만 닫는다(입력 화면 그대로)")
        }
        // 마지막 칸(각도 B)의 Tab 은 닫기
        val tri = TriangleState()
        Screen { TriangleScreen(tri, TriField.ANGLE_B) }.use { sc ->
            sc.key(Key.Four)
            sc.key(Key.Tab)
            assertFalse(sc.keypadOpen, "마지막 칸 Tab")
            assertEquals("4", tri.value(TriField.ANGLE_B))
        }
    }

    // ---------------------------------------------------------------- 5) 되살린 0

    @Test
    fun savedZerosStayZero() {
        val scope = SaverScope { true }
        val t = oldTaper(TaperKind.OUTER, 1, false)
        val tBack = TaperState.Saver.restore(with(TaperState.Saver) { scope.save(t) }!!)!!
        assertEquals(listOf("0", "0", "0", "0", "0"), listOf("x1", "z1", "x2", "z2").map(tBack::point) + tBack.shared("zClear"))
        val g = TaperState(TaperKind.GROOVE, type = 1, open = true).apply { listOf("zStart", "width", "od", "bottom", "xClear").forEach { setShared(it, "0") } }
        val gBack = TaperState.Saver.restore(with(TaperState.Saver) { scope.save(g) }!!)!!
        assertEquals(List(5) { "0" }, listOf("zStart", "width", "od", "bottom", "xClear").map(gBack::shared))
        val r = RoundState(3, open = true).apply {
            dims(3).forEach { setDim(3, it.key, "0") }
            listOf("x1", "z1", "x2", "z2", "x3", "z3").forEach { setPoint(it, "0") }
            setShared("zClear", "0")
        }
        val rBack = RoundState.Saver.restore(with(RoundState.Saver) { scope.save(r) }!!)!!
        assertEquals(List(4) { "0" }, dims(3).map { rBack.dim(3, it.key) })
        assertEquals("0", rBack.point("z3"))
        assertEquals("0", rBack.shared("zClear"))
        val tri = TriangleState(mapOf("a" to "0"))
        val triBack = TriangleState.Saver.restore(with(TriangleState.Saver) { scope.save(tri) }!!)!!
        assertEquals("0", triBack.value(TriField.A_SIDE))
        // 되살린 0 화면: 칸에 0 이 보이고 빨강 없이 파란 안내
        for (dark in listOf(false, true)) {
            val cases = listOf<Pair<String, @Composable () -> Unit>>(
                "테이퍼 0" to @Composable { TaperScreen(tBack) },
                "2중 0" to @Composable { TaperScreen(oldTaper(TaperKind.OUTER_DOUBLE, 2, false)) },
                "역 내경 0" to @Composable { TaperScreen(oldTaper(TaperKind.INNER, 3, true)) },
                "홈 0" to @Composable { TaperScreen(gBack) },
                "라운드 0" to @Composable { RoundScreen(rBack) },
                "3점호 0" to @Composable { RoundScreen(RoundState.Saver.restore(with(RoundState.Saver) { scope.save(r) }!!)!!.apply { type = MANUAL }) },
            )
            for ((name, c) in cases) Screen(dark) { c() }.use { sc ->
                val txt = sc.texts()
                assertEquals(0, sc.redPixels(), "$name 빨강: ${txt.joinToString(" | ")}")
                assertTrue(alarmTexts(txt).isEmpty(), "$name ${alarmTexts(txt)}")
                assertTrue(txt.count { it == "0" } >= 3, "$name 0 이 칸에 보인다: $txt")
            }
        }
    }

    /** 화면에 남은 '다음' 글: 키패드를 연 화면들에서 '다음' 이 들어간 글을 모아 본다(도움말은 소스 grep 으로 따로) */
    @Test
    fun noNextWordOnKeypads() {
        val shots = listOf<Pair<String, @Composable () -> Unit>>(
            "테이퍼" to @Composable { TaperScreen(TaperState(type = 1, open = true).example().apply { editing = "z1" }) },
            "홈" to @Composable { TaperScreen(TaperState(TaperKind.GROOVE, type = 1, open = true).example().apply { editing = "od" }) },
            "라운드" to @Composable { RoundScreen(roundExample(3, open = true).apply { editing = "mainR" }) },
            "3점호" to @Composable { RoundScreen(roundExample(MANUAL, open = true).apply { editing = "x2" }) },
            "삼각형" to @Composable { TriangleScreen(TriangleState(mapOf("a" to "10", "angA" to "25")), TriField.C_SIDE) },
        )
        for ((name, c) in shots) Screen { c() }.use { sc ->
            assertTrue(sc.keypadOpen)
            val hits = sc.texts().filter { "다음" in it }
            assertTrue(hits.isEmpty(), "$name 화면에 '다음': $hits")
        }
    }
}
