package com.bmt.nosecalc.screens.triangle

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.bmt.nosecalc.calc.TriangleInput
import com.bmt.nosecalc.calc.TriangleSolution
import com.bmt.nosecalc.calc.TriangleWarning

/** 다섯 칸: key = 상태 열쇠, label = 칸 위 이름(그림 칸의 윗줄도) */
internal enum class TriField(val key: String, val label: String, val unit: String) {
    A_SIDE("a", "밑변 a", "mm"),
    B_SIDE("b", "높이 b", "mm"),
    C_SIDE("c", "빗변 c", "mm"),
    ANGLE_A("angA", "각도 A", "°"),
    ANGLE_B("angB", "각도 B", "°"),
    ;

    fun of(s: TriangleSolution): Double = when (this) {
        A_SIDE -> s.a
        B_SIDE -> s.b
        C_SIDE -> s.c
        ANGLE_A -> s.angleA
        ANGLE_B -> s.angleB
    }
}

/**
 * 직각삼각형 화면 상태. 값은 쓴 그대로의 글(빈칸 = 모르는 값).
 * 처음엔 다섯 칸 모두 빈칸이다 — 파랑 안내 띠가 둘을 넣으라고 한다.
 */
@Stable
internal class TriangleState(
    values: Map<String, String> = emptyMap(),
    baseIsZ: Boolean = true,
) {
    private val values = mutableStateMapOf<String, String>().apply { putAll(values) }

    /** true = 밑변이 Z · 높이가 X(반경), false = 반대 */
    var baseIsZ by mutableStateOf(baseIsZ)

    fun value(f: TriField): String = values[f.key] ?: ""
    fun set(f: TriField, v: String) { values[f.key] = v }
    fun clear() = TriField.entries.forEach { values[it.key] = "" }

    /** 넣은 칸 */
    fun given(f: TriField): Boolean = value(f).isNotEmpty()

    /** 모자란 칸(그림에 점선 칸): 각도 둘뿐 = 변 셋, 값 부족 = 넣지 않은 칸 모두 */
    fun need(w: TriangleWarning?): Set<TriField> = when (w) {
        TriangleWarning.ANGLES_ONLY -> setOf(TriField.A_SIDE, TriField.B_SIDE, TriField.C_SIDE)
        TriangleWarning.MISSING_INPUT -> TriField.entries.filterNot { given(it) }.toSet()
        else -> emptySet()
    }

    /** 빨강으로 짚을 칸(그림 칸 테 · 숫자칸 error): 각도 둘뿐 = 변 셋, 서로 안 맞음 = 넣은 칸. 값 부족은 안내라 짚지 않는다 */
    fun wrong(w: TriangleWarning?): Set<TriField> = when (w) {
        TriangleWarning.ANGLES_ONLY -> need(w)
        TriangleWarning.CONTRADICTION -> TriField.entries.filter { given(it) }.toSet()
        else -> emptySet()
    }

    fun input(): TriangleInput {
        fun n(f: TriField) = value(f).toDoubleOrNull()
        return TriangleInput(n(TriField.A_SIDE), n(TriField.B_SIDE), n(TriField.C_SIDE), n(TriField.ANGLE_A), n(TriField.ANGLE_B), baseIsZ)
    }

    companion object {
        val Saver = listSaver<TriangleState, String>(
            save = { s -> listOf(s.baseIsZ.toString()) + TriField.entries.map { s.value(it) } },
            restore = { l -> TriangleState(TriField.entries.mapIndexed { i, f -> f.key to l[i + 1] }.toMap(), l[0].toBoolean()) },
        )
    }
}

/** 경고 글. 값 부족은 파랑 안내(처음 빈 화면), 나머지는 계산을 멈춘 것 = 빨강. 수식은 줄바꿈 없는 공백(\u00A0)으로 한 줄에 */
internal fun triangleWarningText(w: TriangleWarning): String = when (w) {
    TriangleWarning.MISSING_INPUT -> "값 두 개를 넣으세요 — 변 하나는 꼭 포함."
    TriangleWarning.ANGLES_ONLY -> "각도 둘만으로는 크기를 알 수 없습니다(A\u00A0+\u00A0B\u00A0=\u00A090° 라 사실상 하나). 밑변 · 높이 · 빗변 가운데 하나를 넣으세요."
    TriangleWarning.BAD_INPUT -> "입력이 잘못됐습니다. 밑변 · 높이 · 빗변은 0 보다 커야 합니다."
    TriangleWarning.BAD_ANGLE -> "각도는 0° 보다 크고 90° 보다 작아야 합니다."
    TriangleWarning.HYPOTENUSE_TOO_SHORT -> "빗변은 밑변 · 높이보다 길어야 합니다."
    TriangleWarning.CONTRADICTION -> "넣은 값이 서로 맞지 않습니다(길이 0.001 mm · 각도 0.001° 넘게). 셋 이상 넣었다면 하나를 비우세요."
}
