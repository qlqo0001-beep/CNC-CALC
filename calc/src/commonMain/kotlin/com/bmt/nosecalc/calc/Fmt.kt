package com.bmt.nosecalc.calc

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/** 숫자를 글로. 계산은 Double 그대로 두고 반올림은 여기(보여 줄 때)서만 한다. 단위 mm, 0.001 까지 */
object Fmt {
    /**
     * 화면 값 표기(끝 0 을 지우고 소수점은 꼭 붙인다): 34 → "34.", −2.186 → "-2.186", 0.5 → "0.5". 앱이 내는 NC 글의 숫자는 [word]
     * (앞 0 을 뺀 회사 표기) — 이것은 칸 값 · 결과 칸 · 이름(M10×0.75) 같은 화면 글에 쓴다
     */
    fun nc(v: Double): String = text(v, keepZeros = false)

    /**
     * NC 글의 숫자(회사 표기 — `G0X38.Z1.` · `.5`): [nc] 에서 앞 0 을 뺀다. 34 → "34.", 0.5 → ".5", −0.234 → "-.234", 0 → "0."
     * 되읽으면 같은 값이다(FANUC 은 `.5` 를 0.5 로 읽는다)
     */
    fun word(v: Double): String = shortLead(nc(v))

    /** 숫자 글의 앞 0 을 뺀다: "0.5" → ".5", "-0.25" → "-.25", "0." · "12.5" 는 그대로. 자리 수가 다른 숫자 글(나사 리드 0.0001)을 NC 글로 */
    fun shortLead(t: String): String {
        val neg = t.startsWith("-")
        val body = if (neg) t.substring(1) else t
        val short = if (body.length > 2 && body.startsWith("0.")) body.substring(1) else body
        return if (neg) "-$short" else short
    }

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

/**
 * NC 줄을 낱말로 쪼갠다 — 빈칸이 있든(옛 꼴 `G01 X40. Z0.`) 없든(회사 꼴 `G1X40.Z0.`) 같다. 낱말 = 주소 글자 + 숫자 글(`G12.1` · `X-.5` · `,R1.`),
 * 괄호 주석은 통째 한 낱말. 빈칸은 버린다 — 회사 꼴 줄(빈칸 없음)은 낱말을 그대로 이으면 줄 글이다
 */
fun ncWords(line: String): List<String> = NC_WORD.findAll(line).map { it.value }.toList()

private val NC_WORD = Regex("""\([^)]*\)?|,?[A-Za-z][-+]?[0-9.]*|[^\sA-Za-z(,]+|,""")

/**
 * NC 줄 모으기 — 회사 표기(`G1X40.Z-5.` · `.5` · 낱말 붙여 씀). 앞 줄과 같은 축 값은 빼고 이동 G(G0 · G1 · G2 · G3)도 바뀔 때만 쓴다(모달),
 * 움직임이 없는 줄은 버린다(그 줄의 G 도 안 쓴 것으로 친다). 같은지는 찍힌 글(0.001)로 본다
 */
internal class NcLines {
    val lines = mutableListOf<String>()

    /** 줄마다 [element] 와 원호 조각 번호 */
    val refs = mutableListOf<NcRef>()

    /** 지금 쓰는 요소 번호(부르는 쪽이 정한다) */
    var element = -3
    private var x: String? = null
    private var z: String? = null

    /** 마지막으로 쓴 이동 G(처음은 없음 — 첫 이동 줄은 늘 G 를 쓴다) */
    private var motion: String? = null

    /** 마지막으로 쓴 이송 F 글(처음은 없음) */
    private var feed: String? = null

    /**
     * [g] = 이동 G(G0 · G1), [comp] = 앞에 붙일 보정 G(G40 · G41 · G42 — 이동 G 와 그룹이 달라 이 줄에 늘 쓴다),
     * [f] = 이송(mm/rev) — 바뀔 때만 줄 끝에 `F.1` 처럼(회사 꼴). null 이면 쓰지 않는다
     */
    fun move(g: String, p: Pt, comp: String = "", f: Double? = null) = put(g, p, "", 0, 1, comp, f)

    /** 이동 없는 한 줄(회사 꼴의 `G42` · `G40` 혼자 줄). 이동 G · 축 값은 그대로 이어진다 */
    fun alone(word: String) {
        lines += word
        refs += NcRef(element, 0, 1)
    }

    /**
     * G2/G3 끝점 R. 90° 넘는 원호는 같은 각으로 나눠(조각마다 90° 이하) 여러 줄로 쓴다.
     * 제어기는 R 지령의 중심을 현의 가운데에서 √(R² − (현/2)²) 떨어진 곳으로 다시 구하는데, 그 위치는 R·끝점의
     * 0.001 반올림을 1/cos(각/2) 배로 키운다(170° 면 약 11배). 90° 이하로 나누면 다시 그린 원호가 0.001 mm 안에 든다.
     * 180° 넘는 원호는 R 의 뜻이 둘이라 어차피 나눠야 한다.
     */
    fun arc(a: Arc) {
        // 90° 를 0.09° 까지 넘는 것은 한 줄: 3점호를 CAD 넷째 자리로 넣으면 보통 1/4 원이 90.0003° 로 나온다
        val k = max(1, ceil(a.sweepDeg / 90 - 1e-3).toInt())
        val a0 = angleOf(a.start - a.center)
        val step = (if (a.cw) -1 else 1) * a.sweepRad() / k
        for (q in 1..k) {
            val p = if (q == k) a.end else a.center + Pt(cos(a0 + step * q), sin(a0 + step * q)) * a.radius
            put(if (a.cw) "G2" else "G3", p, "R" + Fmt.word(a.radius), q - 1, k)
        }
    }

    /**
     * [a] 를 원호 위 점 [p] 에서 끊어 쓴다(보링바 안전 끝점). 조각은 [arc] 가 전체 원호를 나눈 그대로 두고 p 앞에서 끝나는 것은
     * 그 글 그대로, 마지막 조각만 p 까지 — 앞 블록이 원래 프로그램과 같은 글이다. p 가 원호 끝을 조금 넘어도 된다(볼록 모서리에서
     * 늘인 보정선). p 는 진행 방향으로 시작 뒤여야 한다
     */
    fun arcUntil(a: Arc, p: Pt) {
        val k = max(1, ceil(a.sweepDeg / 90 - 1e-3).toInt())
        val a0 = angleOf(a.start - a.center)
        val sw = a.sweepRad()
        val sign = if (a.cw) -1 else 1
        // 조각 끝은 [arc] 와 같은 식(같은 글)
        val step = sign * sw / k
        val along = wrap2Pi(sign * (angleOf(p - a.center) - a0))
        val g = if (a.cw) "G2" else "G3"
        val tail = "R" + Fmt.word(a.radius)
        var q = 1
        while (q < k && sw * q / k < along - 1e-12) {
            put(g, a.center + Pt(cos(a0 + step * q), sin(a0 + step * q)) * a.radius, tail, q - 1, k)
            q++
        }
        put(g, p, tail, q - 1, k)
    }

    private fun put(g: String, p: Pt, tail: String, piece: Int, pieces: Int, comp: String = "", f: Double? = null) {
        val nx = Fmt.word(p.xDia)
        val nz = Fmt.word(p.z)
        val words = (if (nx != x) "X$nx" else "") + (if (nz != z) "Z$nz" else "")
        if (words.isNotEmpty()) {
            val fw = f?.let { Fmt.word(it) }?.takeIf { it != feed }
            lines += comp + (if (g != motion) g else "") + words + tail + (fw?.let { "F$it" } ?: "")
            refs += NcRef(element, piece, pieces)
            motion = g
            if (fw != null) feed = fw
        }
        x = nx
        z = nz
    }
}
