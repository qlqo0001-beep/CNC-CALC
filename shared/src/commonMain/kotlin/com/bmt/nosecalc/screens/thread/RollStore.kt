package com.bmt.nosecalc.screens.thread

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.MAX_MM
import kotlin.math.abs

/**
 * 전조 조건 칸 — 회사 값(화면에서 고쳐 저장, 다이스 조건 [DieField] 와 따로). key = 저장 열쇠 끝(`thread/roll/…`) · 키패드 칸 id 끝.
 * 빈칸 = 처음 값: 비율은 사슬(20산 회사 F1.22 → Fette 0.95 — [com.bmt.nosecalc.calc.RollHeadCalc]), 멈춤 · 물러남은 1 초 · Z150(다이스와 같은 사용자 결정 값)
 */
internal enum class RollField(val key: String, val label: String, val unit: String) {
    RATIO("ratio", "이송 비율 F/P", ""),
    DWELL("dwell", "끝 멈춤", "초"),
    RETRACT("retract", "물러남 Z", "mm"),
}

/**
 * 전조 조건 저장소([KeyValueStore] — 안드로이드 SharedPreferences · PC 설정 파일, `LocalAppState.current.store`). 화면을 열 때 읽고, 칸을 고치면
 * 바로 쓴다(키패드가 누를 때마다 값을 넘긴다). 열쇠: `thread/roll/ratio` · `dwell` · `retract` — 다이스 조건 `thread/die/…` 와 섞지 않는다.
 * 값은 쓴 그대로의 글(빈칸 = 처음 값)
 */
@Stable
internal class RollStore(private val kv: KeyValueStore) {
    private val values = mutableStateMapOf<String, String>().apply {
        KEYS.forEach { k -> kv.get(k)?.let { put(k, it) } }
    }

    /** 칸 글(빈칸 = 처음 값) */
    fun value(f: RollField): String = values[key(f)] ?: ""

    fun num(f: RollField): Double? = value(f).toDoubleOrNull()

    /** 고치면 바로 저장 */
    fun set(f: RollField, v: String) {
        val k = key(f)
        values[k] = v
        kv.put(k, v)
    }

    companion object {
        private const val ROOT = "thread/roll/"

        fun key(f: RollField): String = ROOT + f.key

        /** 읽을 열쇠 전부(칸 셋) */
        private val KEYS = RollField.entries.map(::key)
    }
}

/**
 * 범위 밖 전조 조건 칸 — 계산기(RollHeadCalc)의 BAD_INPUT 과 같은 규칙: 숫자 아님 · 비율 ≤ 0 · 멈춤 < 0 · 크기 한도(±[MAX_MM]).
 * 사이즈를 골랐으면 피치와 엮인 것(넣은 비율의 찍힐 F 가 0 · 한도 밖)도 그 칸에서 본다
 */
internal fun rollBad(s: ThreadState, r: RollStore): Set<RollField> = RollField.entries.filter { f ->
    val v = r.num(f) ?: return@filter false
    if (!v.isFinite()) return@filter true
    val p = s.spec?.pitch
    when (f) {
        RollField.RATIO -> v <= 0 || (p != null && (um(v * p) < 1 || v * p > MAX_MM))
        RollField.DWELL -> v < 0 || v > MAX_MM
        RollField.RETRACT -> abs(v) > MAX_MM
    }
}.toSet()
