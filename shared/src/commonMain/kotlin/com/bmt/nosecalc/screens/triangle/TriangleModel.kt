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

/** 다섯 칸: key = 상태 열쇠, sym = 그림의 짧은 이름, label = 칸 위 이름 */
internal enum class TriField(val key: String, val sym: String, val label: String, val unit: String) {
    A_SIDE("a", "a", "밑변 a", "mm"),
    B_SIDE("b", "b", "높이 b", "mm"),
    C_SIDE("c", "c", "빗변 c", "mm"),
    ANGLE_A("angA", "A", "각도 A", "°"),
    ANGLE_B("angB", "B", "각도 B", "°"),
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
 * 처음 값: 밑변 10 · 각도 A 25° — 넣자마자 결과가 보이게.
 */
@Stable
internal class TriangleState(
    values: Map<String, String> = DEFAULTS,
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

    fun input(): TriangleInput {
        fun n(f: TriField) = value(f).toDoubleOrNull()
        return TriangleInput(n(TriField.A_SIDE), n(TriField.B_SIDE), n(TriField.C_SIDE), n(TriField.ANGLE_A), n(TriField.ANGLE_B), baseIsZ)
    }

    companion object {
        val DEFAULTS = mapOf("a" to "10", "angA" to "25")

        val Saver = listSaver<TriangleState, String>(
            save = { s -> listOf(s.baseIsZ.toString()) + TriField.entries.map { s.value(it) } },
            restore = { l -> TriangleState(TriField.entries.mapIndexed { i, f -> f.key to l[i + 1] }.toMap(), l[0].toBoolean()) },
        )
    }
}

/** 경고 글(모두 계산을 멈춘 것 = 빨강) */
internal fun triangleWarningText(w: TriangleWarning): String = when (w) {
    TriangleWarning.MISSING_INPUT -> "값이 모자랍니다. 다섯 칸 가운데 아는 값 둘을 넣으세요."
    TriangleWarning.ANGLES_ONLY -> "각도 둘만으로는 크기를 알 수 없습니다(A + B = 90° 라 사실상 하나). 밑변 · 높이 · 빗변 가운데 하나를 넣으세요."
    TriangleWarning.BAD_INPUT -> "입력이 잘못됐습니다. 밑변 · 높이 · 빗변은 0 보다 커야 합니다."
    TriangleWarning.BAD_ANGLE -> "각도는 0° 보다 크고 90° 보다 작아야 합니다."
    TriangleWarning.HYPOTENUSE_TOO_SHORT -> "빗변은 밑변 · 높이보다 길어야 합니다."
    TriangleWarning.CONTRADICTION -> "넣은 값이 서로 맞지 않습니다(길이 0.001 mm · 각도 0.001° 넘게). 셋 이상 넣었다면 하나를 비우세요."
}
