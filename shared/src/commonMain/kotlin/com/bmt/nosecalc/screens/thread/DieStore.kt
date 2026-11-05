package com.bmt.nosecalc.screens.thread

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import com.bmt.nosecalc.KeyValueStore
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.IsoGroup
import com.bmt.nosecalc.calc.MAX_MM
import kotlin.math.abs

/**
 * 다이스 조건 칸 — 회사 값(사용자 결정 2026-11-05: 다이스 헤드 모델을 몰라 카탈로그 일반 값과 따로, 화면에서 고쳐 저장).
 * key = 저장 열쇠 끝(`thread/die/…`) · 키패드 칸 id 끝. 비율 · 멈춤 · 물러남은 빈칸 = 사용자 결정 처음 값(0.96 · 1 초 · Z150),
 * Vc · 플로트 · 목 산수는 빈칸 = 없음(Vc 는 처음 빈칸 — 카탈로그 값만 보인다)
 */
internal enum class DieField(val key: String, val label: String, val unit: String) {
    VC("vc", "절삭 속도 Vc", "m/min"),
    RATIO("ratio", "이송 비율 F/P", ""),
    DWELL("dwell", "끝 멈춤", "초"),
    RETRACT("retract", "물러남 Z", "mm"),
    FLOAT("float", "플로트", "mm"),
    THROAT("throat", "체이서 목 산수", "산"),
}

/**
 * 다이스 조건 저장소([KeyValueStore] — 안드로이드 SharedPreferences · PC 설정 파일, `LocalAppState.current.store`). 화면을 열 때 읽고, 칸을 고치면
 * 바로 쓴다(키패드가 누를 때마다 값을 넘긴다). 열쇠: `thread/die/ratio` · `dwell` · `retract` · `float` · `throat`, Vc 는 가공 소재마다
 * `thread/die/vc/<ISO 군>`(소재를 안 고르면 `thread/die/vc`). 값은 쓴 그대로의 글(빈칸 = 처음 값 · 없음)
 */
@Stable
internal class DieStore(private val kv: KeyValueStore) {
    private val values = mutableStateMapOf<String, String>().apply {
        KEYS.forEach { k -> kv.get(k)?.let { put(k, it) } }
    }

    /** 칸 글(빈칸 = 처음 값 · 없음). material 은 Vc 칸만 쓴다 */
    fun value(f: DieField, material: IsoGroup?): String = values[key(f, material)] ?: ""

    fun num(f: DieField, material: IsoGroup?): Double? = value(f, material).toDoubleOrNull()

    /** 고치면 바로 저장 */
    fun set(f: DieField, material: IsoGroup?, v: String) {
        val k = key(f, material)
        values[k] = v
        kv.put(k, v)
    }

    companion object {
        private const val ROOT = "thread/die/"

        fun key(f: DieField, material: IsoGroup?): String =
            if (f == DieField.VC && material != null) "${ROOT}vc/${material.name}" else ROOT + f.key

        /** 읽을 열쇠 전부(칸 여섯 + 소재별 Vc 여섯) */
        private val KEYS = DieField.entries.map { key(it, null) } + IsoGroup.entries.map { key(DieField.VC, it) }
    }
}

/**
 * 범위 밖 다이스 조건 칸 — 계산기(DieHeadCalc)의 BAD_INPUT 과 같은 규칙. 사이즈를 골랐으면 피치와 엮인 것(찍힐 F 0 · 목 길이 한도)도 그 칸에서 본다.
 * 회전수(S)가 한도를 넘는 것처럼 지름 · 회전수와 엮인 것은 칸으로 짚지 않는다(글이 '범위 밖' 만 쓴다)
 */
internal fun dieBad(s: ThreadState, d: DieStore): Set<DieField> = DieField.entries.filter { f ->
    val v = d.num(f, s.material) ?: return@filter false
    if (!v.isFinite()) return@filter true
    val p = s.spec?.pitch
    when (f) {
        DieField.VC -> v <= 0 || v > Cutting.MAX_SPEED
        DieField.RATIO -> v <= 0 || (p != null && (um(v * p) < 1 || v * p > MAX_MM))
        DieField.DWELL, DieField.FLOAT -> v < 0 || v > MAX_MM
        DieField.RETRACT -> abs(v) > MAX_MM
        DieField.THROAT -> v < 0 || (p != null && v * p > MAX_MM)
    }
}.toSet()
