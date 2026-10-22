package com.bmt.nosecalc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.bmt.nosecalc.ui.OverlayEntry

/**
 * 앱 전체 상태: 화면 쌓임, 테마, 보정 방식, 뒤로 처리, 위에 뜨는 것(바텀시트·모달), 토스트.
 * 안드로이드는 MainActivity, PC 는 main() 이 하나 만들어 [App] 에 넘긴다.
 */
@Stable
class AppState(private val store: KeyValueStore) {
    /** 화면 쌓임. 맨 아래는 늘 첫 화면(테이퍼) */
    var stack: List<Feature> by mutableStateOf(listOf(Feature.TAPER))
        private set
    val current: Feature get() = stack.last()

    /** 헤더 메뉴 버튼으로 여는 APP MENU(모든 화면) */
    var menuOpen by mutableStateOf(false)

    /** 헤더 도움말 버튼의 사용법 시트 */
    var helpOpen by mutableStateOf(false)

    /** 저장된 테마. null 이면 시스템 설정을 따른다(메뉴의 '자동') */
    var darkSaved: Boolean? by mutableStateOf(
        when (store.get(KEY_THEME)) {
            "dark" -> true
            "light" -> false
            else -> null
        },
    )
        private set

    fun isDark(systemDark: Boolean) = darkSaved ?: systemDark

    /** 메뉴의 화면 테마: null = 자동(시스템 따라), false = 밝게, true = 어둡게 */
    fun setTheme(dark: Boolean?) {
        darkSaved = dark
        store.put(
            KEY_THEME,
            when (dark) {
                null -> "system"
                true -> "dark"
                false -> "light"
            },
        )
    }

    private var comp by mutableStateOf(if (store.get(KEY_COMP) == CompMode.DIRECT.name) CompMode.DIRECT else CompMode.MACHINE)

    /** 보정 방식(결과 맨 위 스위치) — 테이퍼 · 라운드가 함께 쓰고 다음에 켜도 그대로. 처음은 기계 보정 */
    var compMode: CompMode
        get() = comp
        set(v) {
            comp = v
            store.put(KEY_COMP, v.name)
        }

    /** 기능 화면으로. 이미 쌓인 화면이면 그 위를 걷어 내고 돌아간다(같은 화면이 두 번 쌓이지 않게) */
    fun open(f: Feature) {
        menuOpen = false
        helpOpen = false
        val i = stack.indexOf(f)
        stack = if (i >= 0) stack.take(i + 1) else stack + f
    }

    internal val backs = mutableStateListOf<BackEntry>()
    internal val overlays = mutableStateListOf<OverlayEntry>()

    /** 뒤로 갈 곳이 있나(없으면 안드로이드는 앱을 닫는다) */
    val canGoBack: Boolean get() = backs.any { it.enabled.value } || stack.size > 1

    /** 헤더 ← 를 보일까: 앞 화면이나 화면 안 앞 단계가 있을 때. 메뉴 · 시트 · 키패드처럼 위에 뜬 층은 제 닫기가 있어 세지 않는다 */
    val hasBackStep: Boolean get() = stack.size > 1 || backs.any { it.enabled.value && !it.layer }

    /**
     * 뒤로(안드로이드 뒤로 키·PC Esc). 나중에 등록된 [OnBack] 부터 — 키패드·모달 → 메뉴 → 화면 안 단계 → 앞 화면.
     * 처리했으면 true.
     */
    fun back(): Boolean {
        backs.lastOrNull { it.enabled.value }?.let { it.onBack.value(); return true }
        if (stack.size > 1) {
            stack = stack.dropLast(1)
            return true
        }
        return false
    }

    var toast: Toast? by mutableStateOf(null)
        private set
    private var toastSeq = 0

    /** 화면 아래쪽에 잠깐 뜨는 안내 */
    fun toast(text: String) {
        toast = Toast(++toastSeq, text)
    }

    internal fun clearToast(t: Toast) {
        if (toast == t) toast = null
    }

    /** PC 창의 키. Esc = 뒤로, 나머지는 맨 위 [OverlayEntry.onKey](숫자 키패드)에 */
    fun handleKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        if (e.key == Key.Escape) return back()
        return overlays.lastOrNull()?.onKey?.invoke(e) ?: false
    }

    data class Toast(val id: Int, val text: String)

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_COMP = "compMode"
    }
}

/** 노즈R 보정 방식: 기계 보정(G41/G42 · 도면 좌표, 기계가 보정) · 직접 보정(G40 · 앱이 계산한 보정 좌표 그대로) */
enum class CompMode { MACHINE, DIRECT }

val LocalAppState = staticCompositionLocalOf<AppState> { error("App 안에서만 쓴다") }

internal class BackEntry(val enabled: State<Boolean>, val onBack: State<() -> Unit>, val layer: Boolean)

/**
 * 뒤로를 가로챈다(화면 안 단계 되돌리기 등). 나중에 그려진 것이 먼저 받는다.
 * enabled 가 false 면 건너뛴다. layer = 위에 뜬 층(메뉴 · 시트 · 모달) — 헤더 ← 는 이것만 남았을 때 안 보인다.
 */
@Composable
fun OnBack(enabled: Boolean = true, layer: Boolean = false, onBack: () -> Unit) {
    val state = LocalAppState.current
    val e = rememberUpdatedState(enabled)
    val f = rememberUpdatedState(onBack)
    val entry = remember { BackEntry(e, f, layer) }
    DisposableEffect(state) {
        state.backs.add(entry)
        onDispose { state.backs.remove(entry) }
    }
}
