package com.bmt.nosecalc.screens.taper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.CompMode
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.screens.TaperScreen
import com.bmt.nosecalc.ui.theme.DarkColors
import com.bmt.nosecalc.ui.theme.LightColors
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 독립 검증(사용자 피드백 2026-11-14 "z값이 자동으로 안나타나")의 화면 쪽. 구현 도우미의 판정을 쓰지 않고 실제 화면(ImageComposeScene)의 글 · 누름 · 픽셀 색으로 본다.
 * 사용자 화면(내경 형상 3 · T1 ⇄ T2 · X1 6.3 · Z1 빈칸 · X2 7.5 · Z2 −10.1 · 오목 R3.75)에서 Z1 칸에 청록 자동 값 −12.135 · 결과 · NC,
 * 원호에서 X · Z 칸 키패드의 '비우기 · 자동 계산', 멈춤 띠(ARC_NO_TANGENT · ARC_R_TOO_SMALL), 직선 ↔ 원호 · T1 ⇄ T2 · 화면 역방향
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class Verify1114ArcAutoUiTest {
    /** 화면 하나(밀도 1, 412 폭). 상태는 밖에서 바꾸고 frames 로 다시 그린다 — VerifyKeypadBlankTest 의 Screen 과 같은 꼴 */
    private class Screen(comp: CompMode = CompMode.MACHINE, dark: Boolean = false, height: Int = 4600, content: @Composable () -> Unit) : AutoCloseable {
        val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() })).apply {
            setTheme(dark)
            compMode = comp
        }
        private val scene = ImageComposeScene(412, height, Density(1f)) { AppHost(app) { content() } }
        private var t = 0L
        val colors = if (dark) DarkColors else LightColors
        private val bad: Set<Int> = colors.let { c -> setOf(c.error.bg, c.error.border, c.error.label, c.red).map { it.toArgb() }.toSet() }

        init {
            frames(8)
        }

        fun frames(n: Int = 3) = repeat(n) {
            Snapshot.sendApplyNotifications()
            scene.render(t).close()
            t += 100_000_000L
        }

        private fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        private fun nodes() = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
        private fun textOf(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text.replace("⁠", "").replace(' ', ' ') }

        fun texts(): List<String> = nodes().flatMap(::textOf)

        fun clickable(text: String): List<SemanticsNode> = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.filter { n ->
            n.config.getOrNull(SemanticsActions.OnClick) != null && n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
        }

        fun clickableContaining(part: String): List<String> = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.filter { n ->
            n.config.getOrNull(SemanticsActions.OnClick) != null
        }.flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }.filter { part in it }

        fun tap(text: String) {
            clickable(text).single().config[SemanticsActions.OnClick].action!!.invoke()
            frames(4)
        }

        val keypadOpen: Boolean get() = app.overlays.isNotEmpty()

        private fun image(): java.awt.image.BufferedImage {
            Snapshot.sendApplyNotifications()
            val img = scene.render(t)
            t += 100_000_000L
            val bytes = img.encodeToData()!!.bytes
            img.close()
            return ImageIO.read(ByteArrayInputStream(bytes))
        }

        fun redPixels(): Int {
            val bi = image()
            return bi.getRGB(0, 0, bi.width, bi.height, null, 0, bi.width).count { it in bad }
        }

        /** 글이 꼭 text 인 마디들의 자리 안에서 색 c 에 가까운(채널마다 24 안) 픽셀 수 — 마디마다 */
        fun colorIn(text: String, c: Color): List<Int> {
            val want = c.toArgb()
            val bi = image()
            return nodes().filter { text in textOf(it) }.map { n ->
                val b = n.boundsInRoot
                var k = 0
                for (y in b.top.toInt().coerceAtLeast(0) until b.bottom.toInt().coerceAtMost(bi.height)) for (x in b.left.toInt().coerceAtLeast(0) until b.right.toInt().coerceAtMost(bi.width)) {
                    val p = bi.getRGB(x, y)
                    if ((0..2).all { sh -> abs(((p shr (sh * 8)) and 0xFF) - ((want shr (sh * 8)) and 0xFF)) <= 24 }) k++
                }
                k
            }
        }

        override fun close() = scene.close()
    }

    /** 멈춤(빨강 띠) · 못 푼 칸에 나오는 말 — 노랑 '확인 필요'(AGAINST_TOOL 등 숫자와 함께 나오는 경고)는 넣지 않는다 */
    private val alarms = listOf("계산 멈춤", "입력이 잘못됐습니다", "맞지 않습니다", "계산할 수 없습니다", "너무 작습니다", "닿지 못합니다", "계산 안 됨")
    private fun alarmTexts(t: List<String>) = t.filter { s -> alarms.any { it in s } }

    /** 사용자 화면 그대로: 내경 형상 3 · T1 ⇄ T2 켬 · X1 6.3 · Z1 빈칸 · X2 7.5 · Z2 −10.1 · 원호 R 3.75 · 오목 · 시작 · 끝 빈칸 */
    private fun userState(reverse: Boolean = false, z2: String = "-10.1", editing: String? = null) = TaperState(TaperKind.INNER, reverse, 3, open = true).apply {
        toggleFlip()
        setArc(true)
        setPoint("x1", "6.3")
        setPoint("x2", "7.5")
        setPoint("z2", z2)
        setPoint("arcR", "3.75")
        setArcConcave(true)
        if (editing != null) this.editing = editing
    }

    private val userZ1 = -10.1 - sqrt(0.6 * 6.9)

    private val obs = mutableListOf<String>()

    private fun report(name: String) {
        File("build/verify1114arcauto").mkdirs()
        File("build/verify1114arcauto/ui-$name.txt").writeText(obs.joinToString("\n"))
    }

    /** B-1: Z1 칸 청록 자동 값 −12.135 · 결과 · NC(기계 보정 R3.75 · 직접 보정 R3.35) · 빨강 없음, Z1 키패드에 '자동 -12.135' 와 '비우기 · 자동 계산' */
    @Test
    fun userScreen() {
        val bad = mutableListOf<String>()
        assertEquals("-12.135", Fmt.fixed3(userZ1))
        for (comp in CompMode.entries) for (dark in listOf(false, true)) {
            val s = userState()
            Screen(comp, dark) { TaperScreen(s) }.use { sc ->
                val t = sc.texts()
                val tag = "$comp 다크 $dark"
                if ("-12.135" !in t) bad += "$tag: 자동 값 -12.135 가 화면에 없음"
                if ("자동" !in t) bad += "$tag: '자동' 표가 없음"
                if (t.any { "계산 안 됨" in it }) bad += "$tag: '계산 안 됨' 표"
                alarmTexts(t).takeIf { it.isNotEmpty() }?.let { bad += "$tag: 멈춤 글 $it" }
                obs += "$tag: 노랑 경고 ${t.filter { "확인 필요" in it || "바이트" in it }.map { it.take(60) }}"
                // 오류색 픽셀은 그림 · 점 표에도 쓰일 수 있어 적어만 둔다(멈춤은 위 글로 본다)
                obs += "$tag: 오류색 픽셀 ${sc.redPixels()}"
                val blue = sc.colorIn("-12.135", sc.colors.valueComp)
                obs += "$tag: -12.135 마디 ${blue.size} 개 · valueComp 색 픽셀 $blue"
                if (blue.isEmpty() || blue.max() < 5) bad += "$tag: -12.135 글이 자동 값 색(valueComp)이 아님 $blue"
                val nc = t.filter { it.contains("G") && (it.contains("R3.75") || it.contains("R3.35")) }
                obs += "$tag: NC 마디 ${nc.size} — ${nc.firstOrNull()?.take(200)}"
                val want = if (comp == CompMode.MACHINE) "R3.75" else "R3.35"
                if (t.none { want in it }) bad += "$tag: NC 에 $want 이 없음"
                // 기계 보정 글은 도면 좌표 그대로(직접 보정은 보정된 좌표라 다르다)
                if (comp == CompMode.MACHINE && t.none { "Z-12.135" in it }) bad += "$tag: NC 에 Z-12.135 가 없음"
                if (comp == CompMode.MACHINE && t.none { it.lines().any { l -> l.trim() == "G41" || l.trim() == "G42" } || it == "G41" || it == "G42" }) bad += "$tag: G41/G42 혼자 줄이 없음"
            }
        }
        // Z1 키패드: 자동 값 · 비우기 단추
        Screen { TaperScreen(userState(editing = "z1")) }.use { sc ->
            val t = sc.texts()
            if (!sc.keypadOpen) bad += "Z1 키패드가 안 열림"
            if ("자동 -12.135" !in t) bad += "Z1 키패드에 '자동 -12.135' 가 없음: ${t.filter { "자동" in it }}"
            if (sc.clickable("비우기 · 자동 계산").size != 1) bad += "Z1 키패드에 '비우기 · 자동 계산' 단추가 없음"
        }
        report("user")
        assertEquals(emptyList(), bad)
    }

    /** B-2: 원호에서 X1 · Z1 · X2 · Z2 키패드마다 '비우기 · 자동 계산' — 누르면 닫히고 그 칸이 빈칸 + 따로 계산한 자동 값. R 칸에는 없다 */
    @Test
    fun blankButtonInArc() {
        val bad = mutableListOf<String>()
        val r = 3.75
        // 네 좌표: X1 6.3 Z1 −12.135(사용자가 자동 값을 받아 적은 것) · X2 7.5 Z2 −10.1. 접하는 끝은 늘 그림 T1(칸 T2 = Ø7.5) — 수평
        val z1 = -12.135
        val want = mapOf(
            // Z1: 넣은 반경 차 0.6 → Δz = √(0.6 × (2R − 0.6))
            "z1" to Fmt.fixed3(-10.1 - sqrt(0.6 * (2 * r - 0.6))),
            // X1: 넣은 Z 거리 2.035 → Δr = R − √(R² − Δz²)
            "x1" to Fmt.fixed3(7.5 - 2 * (r - sqrt(r * r - 2.035 * 2.035))),
            "z2" to Fmt.fixed3(z1 + sqrt(0.6 * (2 * r - 0.6))),
            "x2" to Fmt.fixed3(6.3 + 2 * (r - sqrt(r * r - 2.035 * 2.035))),
        )
        for ((field, auto) in want) {
            val s = userState().apply {
                setPoint("z1", z1.toString())
                editing = field
            }
            Screen { TaperScreen(s) }.use { sc ->
                if (!sc.keypadOpen) bad += "$field: 키패드가 안 열림"
                if (sc.clickable("비우기 · 자동 계산").size != 1) {
                    bad += "$field: '비우기 · 자동 계산' 단추가 없음"
                    return@use
                }
                // 네 좌표가 다 찬 상태(사용자가 자동 값 −12.135 를 적어 넣은 꼴)는 멈추지 않는다
                if (alarmTexts(sc.texts()).isNotEmpty()) bad += "$field: 네 좌표(Z1 −12.135)에서 경고 ${alarmTexts(sc.texts())}"
                sc.tap("비우기 · 자동 계산")
                if (sc.keypadOpen) bad += "$field: 누른 뒤에도 키패드가 열림"
                if (s.point(field) != "") bad += "$field: 칸이 비지 않음 '${s.point(field)}'"
                val t = sc.texts()
                obs += "$field 비움 → 자동 기대 $auto · 화면에 ${t.contains(auto)}"
                if (auto !in t) bad += "$field: 자동 값 $auto 가 화면에 없음"
                if (alarmTexts(t).isNotEmpty()) bad += "$field: 비운 뒤 경고 ${alarmTexts(t)}"
                obs += "$field: 비운 뒤 오류색 픽셀 ${sc.redPixels()}"
            }
        }
        // R 칸은 비울 수 없다(비우기 단추 없음)
        Screen { TaperScreen(userState(editing = "arcR")) }.use { sc ->
            if (!sc.keypadOpen) bad += "R 키패드가 안 열림"
            if (sc.clickable("비우기 · 자동 계산").isNotEmpty()) bad += "R 칸 키패드에 '비우기 · 자동 계산' 이 있음"
        }
        report("blank")
        assertEquals(emptyList(), bad)
    }

    /** B-3: 형상 1 오목(외경 · 내경)에서 Z2 를 비우면 빨간 ARC_NO_TANGENT 띠('오목' · '네 좌표'), 형상 4 볼록은 '볼록'. NC 는 없다 */
    @Test
    fun noTangentBanner() {
        val bad = mutableListOf<String>()
        for ((kind, type, concave) in listOf(Triple(TaperKind.OUTER, 1, true), Triple(TaperKind.INNER, 1, true), Triple(TaperKind.OUTER, 4, false), Triple(TaperKind.INNER, 4, false))) {
            for (flip in listOf(false, true)) {
                val s = TaperState(kind, false, type, open = true).example().apply {
                    setArc(true)
                    setPoint("arcR", "10")
                    setArcConcave(concave)
                    if (flip) toggleFlip()
                    setPoint(if (flip) "z1" else "z2", "")
                }
                Screen { TaperScreen(s) }.use { sc ->
                    val t = sc.texts()
                    val tag = "$kind 형상 $type ${if (concave) "오목" else "볼록"} 뒤집기 $flip"
                    val banner = t.filter { "빈 좌표를 계산할 수 없습니다" in it }
                    obs += "$tag: ${banner.firstOrNull()}"
                    if (banner.size != 1) bad += "$tag: ARC_NO_TANGENT 띠가 ${banner.size} 개 — 글 ${alarmTexts(t)}"
                    else {
                        val b = banner.single()
                        if ((if (concave) "오목" else "볼록") !in b || "네 좌표" !in b) bad += "$tag: 띠 글 '$b'"
                    }
                    if (sc.redPixels() == 0) bad += "$tag: 빨강이 없음"
                    if (t.any { "G40" in it }) bad += "$tag: 멈췄는데 NC 가 보임"
                    // 빈 칸은 '자동 · 계산 안 됨'
                    if (t.none { "계산 안 됨" in it }) bad += "$tag: 빈 칸에 '계산 안 됨' 표가 없음"
                }
            }
        }
        report("notangent")
        assertEquals(emptyList(), bad)
    }

    /** B-4: 넣은 차가 R 보다 크면 ARC_R_TOO_SMALL 띠에 그 차(빈 칸이 Z 면 반경 차, X 면 Z 거리) */
    @Test
    fun rTooSmallBanner() {
        val bad = mutableListOf<String>()
        // 외경 형상 1 볼록 X1 40 Z1 0 · X2 50 · Z2 빈칸 · R4 → 반경 차 5 > 4
        for ((blank, text) in listOf("z2" to "반경 차 5.000", "x2" to "Z 거리 5.000", "z1" to "반경 차 5.000", "x1" to "Z 거리 5.000")) {
            val s = TaperState(TaperKind.OUTER, false, 1, open = true).example().apply {
                setArc(true)
                setPoint("arcR", "4")
                setPoint(blank, "")
            }
            Screen { TaperScreen(s) }.use { sc ->
                val t = sc.texts()
                val msg = t.filter { "R 이 너무 작습니다" in it }
                obs += "$blank: ${msg.firstOrNull()}"
                if (msg.size != 1 || text !in msg.single()) bad += "$blank: R 과소 띠에 '$text' 없음 — ${msg}"
                if (sc.redPixels() == 0) bad += "$blank: 빨강이 없음"
            }
        }
        report("rsmall")
        assertEquals(emptyList(), bad)
    }

    /**
     * B-5: 직선 ↔ 원호를 오가도 칸 값 · 자동 칸 기억(lastAuto)이 그대로이고, 직선의 CONTRADICTION '… 비우고 계산' 단추가 원호에서는 없으며 돌아오면 다시 있다.
     * 직선의 각도 자동 값도 원호를 다녀와서 그대로
     */
    @Test
    fun straightArcToggle() {
        val bad = mutableListOf<String>()
        // 직선 다섯 값이 어긋남: X1 40 Z1 0 X2 50 Z2 −5(빈칸에서 넣음 → lastAuto z2) · 각도 30(X축 기준 — 실제는 45)
        val s = TaperState(TaperKind.OUTER, false, 1, open = true).apply {
            setPoint("x1", "40")
            setPoint("z1", "0")
            setPoint("x2", "50")
            setPoint("ang", "30")
            setPoint("z2", "-5")
        }
        val before = listOf("x1", "z1", "x2", "z2", "ang", "arcR").associateWith { s.point(it) }
        Screen { TaperScreen(s) }.use { sc ->
            val fixes0 = sc.clickableContaining("비우고 계산")
            obs += "직선: 단추 $fixes0 · lastAuto ${s.lastAuto}"
            if (fixes0.toSet() != setOf("Z2 비우고 계산", "각도 비우고 계산")) bad += "직선 CONTRADICTION 단추 $fixes0"
            s.setArc(true)
            s.setPoint("arcR", "10")
            sc.frames(4)
            val fixes1 = sc.clickableContaining("비우고 계산")
            if (fixes1.isNotEmpty()) bad += "원호에서 '비우고 계산' 단추가 남음 $fixes1"
            val t1 = sc.texts()
            if (alarmTexts(t1).isNotEmpty()) bad += "원호(네 좌표 · R10)에서 경고 ${alarmTexts(t1)}"
            if (t1.none { "R10." in it }) bad += "원호 NC 에 R10. 이 없음"
            // 원호에서 Z2 를 비웠다 다시 넣고(lastAuto z2) 직선으로
            s.setPoint("z2", "")
            sc.frames(3)
            val autoZ2 = sc.texts()
            obs += "원호 Z2 비움: 화면 자동 값 후보 ${autoZ2.filter { it.startsWith("-") && it.length <= 8 }.take(6)}"
            s.setPoint("z2", "-5")
            s.setArc(false)
            sc.frames(4)
            val fixes2 = sc.clickableContaining("비우고 계산")
            if (fixes2.toSet() != setOf("Z2 비우고 계산", "각도 비우고 계산")) bad += "직선으로 돌아와 단추 $fixes2"
            val after = listOf("x1", "z1", "x2", "z2", "ang", "arcR").associateWith { s.point(it) }
            if (after - "arcR" != before - "arcR" || after["arcR"] != "10") bad += "칸 값이 바뀜 $before → $after"
            if (s.lastAuto != "z2") bad += "lastAuto ${s.lastAuto}"
        }
        // 직선의 각도 자동 값(X1 40 Z1 0 X2 46 Z2 −5 → X축 기준 59.036)이 원호를 다녀와도 그대로
        val a = TaperState(TaperKind.OUTER, false, 1, open = true).apply {
            setPoint("x1", "40")
            setPoint("z1", "0")
            setPoint("x2", "46")
            setPoint("z2", "-5")
        }
        Screen { TaperScreen(a) }.use { sc ->
            if ("59.036" !in sc.texts()) bad += "직선 각도 자동 값 59.036 이 없음"
            a.setArc(true)
            a.setPoint("arcR", "20")
            sc.frames(4)
            val t = sc.texts()
            if ("59.036" in t) bad += "원호에서 각도 자동 값이 보임"
            if (alarmTexts(t).isNotEmpty()) bad += "원호(X40 Z0 X46 Z−5 R20) 경고 ${alarmTexts(t)}"
            a.setArc(false)
            sc.frames(4)
            if ("59.036" !in sc.texts()) bad += "직선으로 돌아와 각도 자동 값이 없음"
            if (a.lastAuto != "z2") bad += "원호를 다녀와 lastAuto 가 바뀜 — ${a.lastAuto}"
        }
        report("toggle")
        assertEquals(emptyList(), bad)
    }

    /** B-6: T1 ⇄ T2 를 눌러도 빈 칸이 같은 실제 점에 남는다 — 사용자 화면에서 바꾸면 Z2 칸이 비고 자동 값 −12.135, 키패드도 */
    @Test
    fun flipKeepsBlank() {
        val bad = mutableListOf<String>()
        val s = userState()
        Screen { TaperScreen(s) }.use { sc ->
            s.toggleFlip()
            sc.frames(4)
            if (s.point("z2") != "" || s.point("z1") != "-10.1" || s.point("x1") != "7.5" || s.point("x2") != "6.3") {
                bad += "바꾼 뒤 칸 x1 ${s.point("x1")} z1 ${s.point("z1")} x2 ${s.point("x2")} z2 ${s.point("z2")}"
            }
            val t = sc.texts()
            if ("-12.135" !in t) bad += "바꾼 뒤 자동 값 -12.135 가 없음"
            if (alarmTexts(t).isNotEmpty()) bad += "바꾼 뒤 경고 ${alarmTexts(t)}"
            s.toggleFlip()
            sc.frames(4)
            if (s.point("z1") != "" || "-12.135" !in sc.texts()) bad += "다시 바꾼 뒤 Z1 '${s.point("z1")}'"
        }
        Screen { TaperScreen(userState().apply { toggleFlip(); editing = "z2" }) }.use { sc ->
            if ("자동 -12.135" !in sc.texts()) bad += "바꾼 뒤 Z2 키패드에 '자동 -12.135' 가 없음"
        }
        // 계산 결과도 같은 실제 점
        val f0 = TaperSolve.solve(slopeFormOf(userState()))
        val f1 = TaperSolve.solve(slopeFormOf(userState().apply { toggleFlip() }))
        val p0 = f0.solution?.points?.map { it.vertex }?.toSet()
        val p1 = f1.solution?.points?.map { it.vertex }?.toSet()
        if (p0 == null || p0 != p1) bad += "뒤집기 전후 점이 다름 $p0 / $p1"
        report("flip")
        assertEquals(emptyList(), bad)
    }

    /** B-7: 화면 역방향(거울)에서도 같은 값 — Z 부호만 바꿔 넣으면(Z2 10.1) 자동 Z1 12.135, 척 쪽에 둔 거울(Z2 −19.9 = −30 − (−10.1))이면 −17.865 */
    @Test
    fun mirrorSameValue() {
        val bad = mutableListOf<String>()
        for ((z2, want) in listOf("10.1" to "12.135", "-19.9" to "-17.865")) {
            val s = userState(reverse = true, z2 = z2)
            Screen { TaperScreen(s) }.use { sc ->
                val t = sc.texts()
                obs += "역방향 Z2 $z2: 자동 $want ${want in t} · 경고 ${alarmTexts(t)}"
                if (want !in t) bad += "역방향 Z2 $z2: 자동 값 $want 가 없음"
                if (alarmTexts(t).isNotEmpty()) bad += "역방향 Z2 $z2: 경고 ${alarmTexts(t)}"
            }
        }
        report("mirror")
        assertEquals(emptyList(), bad)
    }

    /** 원호에서 빈 칸 하나 · R 글 · 종류 · 형상 · 방향 · 뒤집기 · 볼록/오목을 바꿔 가며 그려도 던지지 않는다(두 보정 방식을 번갈아) */
    @Test
    fun drawBlankStates() {
        val bad = mutableListOf<String>()
        var n = 0
        for (kind in listOf(TaperKind.OUTER, TaperKind.INNER)) for (type in 1..5) for (reverse in listOf(false, true)) for (flip in listOf(false, true))
            for (concave in listOf(false, true)) for (blank in listOf("x1", "z1", "x2", "z2")) for (r in listOf("", "0.5", "10", "99999")) {
                n++
                if (n % 2 == 0 && r == "99999") continue
                try {
                    Screen(if (n % 2 == 0) CompMode.MACHINE else CompMode.DIRECT, height = 2600) {
                        TaperScreen(
                            TaperState(kind, reverse, type, open = true).example().apply {
                                setArc(true)
                                setPoint("arcR", r)
                                setArcConcave(concave)
                                if (flip) toggleFlip()
                                setPoint(blank, "")
                                if (n % 3 == 0) {
                                    setPoint("r1", "0.5")
                                    setPoint("r2", "0.5")
                                    setPoint("start", "")
                                }
                            },
                        )
                    }.use { it.frames(2) }
                } catch (e: Throwable) {
                    bad += "$kind 형상 $type 역 $reverse 뒤집기 $flip 오목 $concave 빈 $blank R '$r' → $e"
                }
            }
        assertEquals(emptyList(), bad.take(10), "그리다 던진 것 ${bad.size} 건 / $n")
    }

    /** 화면 상태 → SlopeForm(화면이 하는 것과 같은 바꿈 — 빈칸 null) */
    private fun slopeFormOf(s: TaperState) = SlopeForm(
        kind = s.kind, type = s.type!!, reverse = s.reverse, noseR = 0.4, tip = null,
        x = listOf(s.point("x1").toDoubleOrNull(), s.point("x2").toDoubleOrNull()), z = listOf(s.point("z1").toDoubleOrNull(), s.point("z2").toDoubleOrNull()),
        angle = null, zClear = 0.0, corners = listOf(0.0, 0.0), flip = s.flip, angleRef = com.bmt.nosecalc.AngleRef.X,
        arc = s.arc, arcR = s.point("arcR").toDoubleOrNull(), arcConcave = s.arcConcave,
    )
}
