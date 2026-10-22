package com.bmt.nosecalc.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.AppHost
import com.bmt.nosecalc.AppState
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.Contour
import com.bmt.nosecalc.calc.Pt
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 형상 뷰어의 보기(맞춤 배수 · 가운데 점)와 손짓: 한 손가락은 페이지로, 두 손가락 · Ctrl + 휠만 뷰어로 */
class ViewerStateTest {
    // 그림 상자(Z −20..0, X 10..20) — left/right = Z, top/bottom = X
    private val box = Rect(-20f, 10f, 0f, 20f)

    @Test
    fun clampKeepsZoomAndCentreNearTheDrawing() {
        val u = UserView(1000f, Pt(100.0, -50.0)).clampedTo(box)
        assertEquals(200f, u.zoom)
        assertEquals(Pt(10.0, 5.0), u.center)
        assertEquals(0.5f, UserView(0.1f, Pt(-10.0, 15.0)).clampedTo(box).zoom)
        // 상자 안이면 그대로
        assertEquals(UserView(3f, Pt(-10.0, 15.0)), UserView(3f, Pt(-10.0, 15.0)).clampedTo(box))
    }

    @Test
    fun editKeepsViewWhileTheNewDrawingIsInSight() {
        val u = UserView(4f, Pt(-2.0, 11.0))
        val seen = Rect(-4f, 9f, 0f, 13f)
        // 치수를 조금 고쳐 그림이 커졌다 — 보이던 곳에 걸치니 그대로
        assertEquals(u, keptView(u, seen, Rect(-25f, 10f, 0f, 22f)))
        // 보이던 곳과 겹치지 않는 그림 — 맞춤으로
        assertNull(keptView(u, seen, Rect(-60f, 30f, -40f, 40f)))
        assertNull(keptView(u, seen, null))
        // 걸치지만 가운데가 새 상자 반 폭 밖이면 안으로 당긴다
        assertEquals(Pt(1.0, 11.0), keptView(UserView(4f, Pt(5.0, 11.0)), Rect(-2f, 9f, 8f, 13f), Rect(-2f, 10f, 0f, 12f))?.center)
    }

    @Test
    fun sameViewOnAnyWidth() {
        val u = UserView(1.6f, Pt(-10.0, 15.0))
        for (w in listOf(1018f, 1617f)) {
            val area = Rect(36f, 36f, w - 52f, 600f)
            val v = ViewXf.of(u, box, area)
            // 가운데 점은 늘 맞춤 곳 가운데, 배율은 그 폭의 맞춤 × 1.6
            assertEquals(area.center.x, v.toScreen(u.center).x, 1e-3f)
            assertEquals(area.center.y, v.toScreen(u.center).y, 1e-3f)
            assertEquals(ViewXf.fit(box, area).s * 1.6f, v.s, 1e-3f)
        }
        // zoom 1 · 상자 가운데 = 맞춤 그대로
        val area = Rect(36f, 36f, 900f, 500f)
        assertEquals(ViewXf.fit(box, area), ViewXf.of(UserView(1f, Pt(-10.0, 15.0)), box, area))
    }

    @Test
    fun buttonsZoomAboutTheCentreAndFitResets() {
        val s = ViewerState()
        s.zoomBy(1.5f)
        assertNull(s.userView, "그림을 그리기 전에는 단추가 아무 일도 안 한다")
        val area = Rect(36f, 36f, 900f, 500f)
        s.follow(box, area, IntSize(952, 540))
        s.zoomBy(1.5f)
        s.zoomBy(1.5f)
        assertEquals(UserView(2.25f, Pt(-10.0, 15.0)), s.userView)
        assertTrue(s.isCustom)
        // 조금 고친 그림 — 보기를 둔다
        s.follow(Rect(-22f, 10f, 0f, 21f), area, IntSize(952, 540))
        assertEquals(2.25f, s.userView?.zoom)
        // 전혀 다른 곳의 그림 — 맞춤으로
        s.follow(Rect(-300f, 200f, -280f, 210f), area, IntSize(952, 540))
        assertNull(s.userView)
        s.zoomBy(1.5f)
        s.fit()
        assertFalse(s.isCustom)
    }

    @Test
    fun oneFingerSwipeScrollsThePage() = page { scene, scroll, st ->
        fun touch(type: PointerEventType, y: Float, t: Long) = scene.sendPointerEvent(type, Offset(200f, y), timeMillis = t, type = PointerType.Touch)
        touch(PointerEventType.Press, 300f, 0)
        for (i in 1..10) touch(PointerEventType.Move, 300f - 15 * i, i * 16L)
        touch(PointerEventType.Release, 150f, 200L)
        scene.render(1_000_000_000)
        assertTrue(scroll.value > 0, "뷰어 위에서 시작한 한 손가락 끌기가 페이지를 내린다")
        assertNull(st.userView)
        assertEquals(false, st.hint?.mouse)
    }

    // 손가락 여럿은 ComposeScenePointer 로만 보낼 수 있다(내부 API — 컴포즈를 올리면 여기만 고친다)
    @OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun twoFingersMoveTheDrawingNotThePage() = page { scene, scroll, st ->
        fun finger(id: Long, x: Float, y: Float, pressed: Boolean) = ComposeScenePointer(PointerId(id), Offset(x, y), pressed, PointerType.Touch)
        fun two(type: PointerEventType, y: Float, t: Long, pressed: Boolean = true) = scene.sendPointerEvent(
            type,
            listOf(finger(0, 150f, y, pressed), finger(1, 250f, y, pressed)),
            timeMillis = t,
        )
        scene.sendPointerEvent(PointerEventType.Press, listOf(finger(0, 150f, 300f, true)), timeMillis = 0)
        two(PointerEventType.Press, 300f, 1)
        for (i in 1..10) two(PointerEventType.Move, 300f - 15 * i, i * 16L)
        two(PointerEventType.Release, 150f, 200L, pressed = false)
        scene.render(1_000_000_000)
        assertEquals(0, scroll.value)
        assertTrue(st.isCustom)
    }

    @Test
    fun ctrlWheelZoomsAndDoubleTapFits() = page { scene, _, st ->
        scene.sendPointerEvent(PointerEventType.Scroll, Offset(200f, 250f), scrollDelta = Offset(0f, -1f))
        assertNull(st.userView)
        assertEquals(true, st.hint?.mouse)
        scene.sendPointerEvent(
            PointerEventType.Scroll, Offset(200f, 250f), scrollDelta = Offset(0f, -1f),
            keyboardModifiers = PointerKeyboardModifiers(isCtrlPressed = true),
        )
        assertEquals(1.15f, st.userView?.zoom ?: 0f, 1e-4f)
        // 두 번 누르면 다시 맞춤
        for (t in listOf(1000L, 1150L)) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(200f, 250f), timeMillis = t, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, Offset(200f, 250f), timeMillis = t + 50, type = PointerType.Touch)
        }
        assertNull(st.userView)
    }

    /** 400 × 800 화면(밀도 1): 위 100dp 빈칸 · 뷰어 300dp · 아래 1500dp — 페이지가 스크롤된다 */
    private fun page(block: (ImageComposeScene, ScrollState, ViewerState) -> Unit) {
        val app = AppState(KeyValueStore(File.createTempFile("bmt-kv", ".properties").apply { delete() }))
        val scroll = ScrollState(0)
        val st = ViewerState()
        val lines = listOf(ViewerLine(Contour.polyline(Pt(5.0, 10.0), Pt(0.0, 10.0), Pt(-10.0, 15.0), Pt(-20.0, 15.0)), Color.Gray))
        val scene = ImageComposeScene(400, 800, Density(1f)) {
            AppHost(app) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Spacer(Modifier.height(100.dp))
                    ShapeViewer(lines, Modifier.fillMaxWidth().height(300.dp), state = st)
                    Spacer(Modifier.height(1500.dp))
                }
            }
        }
        try {
            scene.render(0)
            block(scene, scroll, st)
        } finally {
            scene.close()
        }
    }
}
