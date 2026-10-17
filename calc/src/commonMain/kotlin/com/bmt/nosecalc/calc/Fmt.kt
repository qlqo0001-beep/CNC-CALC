package com.bmt.nosecalc.calc

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/** 숫자를 글로. 계산은 Double 그대로 두고 반올림은 여기(보여 줄 때)서만 한다. 단위 mm, 0.001 까지 */
object Fmt {
    /** FANUC 프로그램 표기: 끝 0 을 지우고 소수점은 꼭 붙인다. 34 → "34.", −2.186 → "-2.186", 0.5 → "0.5" */
    fun nc(v: Double): String = text(v, keepZeros = false)

    /** 결과 칸 표기: 소수 셋째 자리까지 0 을 채운다. 34 → "34.000" */
    fun fixed3(v: Double): String = text(v, keepZeros = true)

    private fun text(v: Double, keepZeros: Boolean): String {
        require(v.isFinite()) { "숫자가 아니다: $v" }
        // 5 는 올림(0 에서 먼 쪽). 10진으로 친 2.1855 가 2진에서 2.18549999… 로 저장돼도 올라가게 아주 조금 더한다
        val m = floor(abs(v) * 1000 + 0.5 + 1e-6).toLong()
        val sign = if (v < 0 && m != 0L) "-" else ""
        val frac = (m % 1000).toString().padStart(3, '0')
        return "$sign${m / 1000}.${if (keepZeros) frac else frac.trimEnd('0')}"
    }
}

/** NC 줄 모으기. 앞 줄과 같은 축 값은 빼고(모달), 움직임이 없는 줄은 버린다. 같은지는 찍힌 글(0.001)로 본다 */
internal class NcLines {
    val lines = mutableListOf<String>()
    private var x: String? = null
    private var z: String? = null

    fun move(g: String, p: Pt) = put(g, p, "")

    /**
     * G02/G03 끝점 R. 90° 넘는 원호는 같은 각으로 나눠(조각마다 90° 이하) 여러 줄로 쓴다.
     * 제어기는 R 지령의 중심을 현의 가운데에서 √(R² − (현/2)²) 떨어진 곳으로 다시 구하는데, 그 위치는 R·끝점의
     * 0.001 반올림을 1/cos(각/2) 배로 키운다(170° 면 약 11배). 90° 이하로 나누면 다시 그린 원호가 0.001 mm 안에 든다.
     * 180° 넘는 원호는 R 의 뜻이 둘이라 어차피 나눠야 한다.
     */
    fun arc(a: Arc) {
        val k = max(1, ceil(a.sweepDeg / 90 - 1e-6).toInt())
        val a0 = angleOf(a.start - a.center)
        val step = (if (a.cw) -1 else 1) * a.sweepRad() / k
        for (q in 1..k) {
            val p = if (q == k) a.end else a.center + Pt(cos(a0 + step * q), sin(a0 + step * q)) * a.radius
            put(if (a.cw) "G02" else "G03", p, " R" + Fmt.nc(a.radius))
        }
    }

    private fun put(g: String, p: Pt, tail: String) {
        val nx = Fmt.nc(p.xDia)
        val nz = Fmt.nc(p.z)
        val words = (if (nx != x) " X$nx" else "") + (if (nz != z) " Z$nz" else "")
        if (words.isNotEmpty()) lines += g + words + tail
        x = nx
        z = nz
    }
}
