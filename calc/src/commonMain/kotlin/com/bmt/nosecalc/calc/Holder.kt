package com.bmt.nosecalc.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 인서트 모양(ISO 1832 첫 글자)과 코너 각 ε(도) */
enum class InsertShape(val epsDeg: Double) { C(80.0), D(55.0), V(35.0), T(60.0) }

/**
 * 보링바(바 + 인서트). 길이 mm, 각 도.
 * [dia] 바 지름 D, [f] 바 중심 ~ 인선(카탈로그 WF — 가상 인선의 x 까지), [edge] 인서트 날 길이 l(품번 크기 숫자),
 * [kappaDeg] 절입각 κr(카탈로그 KAPR), [overhang] 인선 ~ 공구대 면 Z 거리(null = 공구대 검사 안 함),
 * [dMin] 최소 가공경(카탈로그 DMIN, null = 검사 안 함)
 */
data class BoringBar(
    val dia: Double,
    val f: Double,
    val shape: InsertShape,
    val edge: Double,
    val kappaDeg: Double,
    val overhang: Double? = null,
    val dMin: Double? = null,
)

/** 소재에 닿는 부분: 인서트, 바(헤드 · 자루), 공구대(돌출 길이가 모자람), 최소 가공경(헤드가 DMIN 보다 좁은 곳에 들어감) */
enum class HolderPart { INSERT, BAR, TURRET, MIN_BORE }

/** 검사를 안 한 까닭: 보링바 값이 틀림, 윤곽이 Z 로 되돌아감 */
enum class HolderSkip { BAD_INPUT, NOT_MONOTONE }

/** 틀린 보링바 칸 */
enum class BarField { DIA, F, EDGE, KAPPA, OVERHANG, DMIN }

/** 공구 자세. [tip] = G40 프로그램 좌표(가상 인선), [nose] = 노즈 중심, [drawn] = 노즈가 닿는 도면 점(기계 보정 프로그램 좌표) */
data class HolderPose(val tip: Pt, val nose: Pt, val drawn: Pt)

/**
 * 요소 하나에서 한 부분이 소재에 닿는 구간. [element] −2 = G00 진입점에 선 자세, −1 = 진입점 → 시작점 G01, k = program.segs[k](= drawing.segs[k]).
 * [fromT] · [toT] = 처음 · 마지막 닿는 곳의 요소 길이 비율(사이에 떨어진 곳이 있어도 하나로 묶는다). [worst] = 가장 깊은 자세.
 * [depth] = 겹침(mm): 인서트 · 바는 반경, 공구대는 Z, 최소 가공경은 DMIN/2 − 가장 좁은 반경.
 * [opposite] = 가장 깊은 점이 맞은편 벽(x < 0), [behind] = 그 점 z − 가상 인선 z
 */
data class HolderHit(
    val element: Int,
    val part: HolderPart,
    val first: HolderPose,
    val last: HolderPose,
    val worst: HolderPose,
    val fromT: Double,
    val toT: Double,
    val depth: Double,
    val opposite: Boolean,
    val behind: Double,
)

/**
 * 검사 결과. [hits] 는 요소 순, 한 요소 안은 처음 닿는 곳 순. [barClear] = 경로 전체에서 바와 구멍 벽 사이 가장 좁은 반경 여유
 * (닿으면 음수, 같으면 앞의 것)와 그 자세 · 요소. 검사를 안 했으면 [skip] 이고 나머지는 비었다
 */
data class HolderResult(
    val skip: HolderSkip?,
    val hits: List<HolderHit>,
    val barClear: Double?,
    val barClearAt: HolderPose?,
    val barClearElement: Int?,
)

/**
 * 보링바 간섭 검사. 축을 지나는 ZX 단면에서 바 · 인서트 단면([BarShape])이 소재 {|x| ≥ ρ(z)}([Bore] — 깎은 벽과 축 너머 맞은편 벽)에
 * 들어가는지 G40 경로를 따라 본다. 둥근 바 자루는 이 단면으로 정확하다: 바 원판에서 축과 가장 먼 점이 y = 0 에 있어 원판이 덮는 반경이
 * 단면과 같다. 인서트 아래쪽 · 헤드 앞 모양 · 인서트 기울기는 단면으로 어림하고 최소 가공경(DMIN)으로만 본다.
 * 진입점까지 오는 길 · 가공 뒤 빠지는 길, 덜 깎은 소재(황삭)는 보지 않는다
 */
object HolderCheck {
    /** 이만큼(mm)까지 겹친 것은 닿음으로 보지 않는다 */
    const val CONTACT_TOL = 0.005

    /** 경로를 따라 보는 간격(mm). 경로가 길면 20000 점이 되게 늘린다 — 이 간격보다 긴 닿는 구간은 놓치지 않는다 */
    const val STEP = 0.02

    /** 바 윗면이 노즈 접점 T1 보다 낮으려면 f ≥ D/2 + r(1 − cos κr) */
    fun fMin(dia: Double, kappaDeg: Double, r: Double): Double = dia / 2 + r * (1 - cos(rad(kappaDeg)))

    /** 날 길이는 꼭짓점에서 노즈 접점까지(r / tan(ε/2))보다 길어야 한다 */
    fun edgeMin(shape: InsertShape, r: Double): Double = r / tan(rad(shape.epsDeg) / 2)

    /** 헤드 길이 = 가상 인선에서 인서트 뒤 끝 · 바 앞 끝(주절삭날 연장선이 바 아래에 닿는 곳)까지 Z. 돌출 길이는 이보다 길어야 한다 */
    fun overhangMin(bar: BoringBar, r: Double): Double = BarShape(bar, r, false).head

    /** 틀린 칸. 하나라도 있으면 [check] 는 BAD_INPUT */
    fun problems(bar: BoringBar, r: Double): Set<BarField> {
        fun ok(v: Double) = v.isFinite() && abs(v) <= MAX_MM
        val bad = mutableSetOf<BarField>()
        if (!(ok(bar.dia) && bar.dia > 0)) bad += BarField.DIA
        if (!(ok(bar.kappaDeg) && bar.kappaDeg >= 90 && bar.kappaDeg < 180 - bar.shape.epsDeg)) bad += BarField.KAPPA
        if (!(ok(bar.edge) && bar.edge > edgeMin(bar.shape, r))) bad += BarField.EDGE
        if (!ok(bar.f) || (BarField.DIA !in bad && BarField.KAPPA !in bad && bar.f < fMin(bar.dia, bar.kappaDeg, r))) bad += BarField.F
        // 돌출 길이 하한(헤드 길이)은 나머지 칸이 맞을 때만 잴 수 있다
        bar.overhang?.let { if (!(ok(it) && it > 0) || (bad.isEmpty() && it <= overhangMin(bar, r))) bad += BarField.OVERHANG }
        bar.dMin?.let { if (!(ok(it) && it > 0)) bad += BarField.DMIN }
        return bad
    }

    /**
     * [program] = G40 프로그램 경로(가상 인선 [tip]), [entry] = G00 진입점, [drawing] = 도면(요소 번호가 같다), [r] = 노즈R.
     * [faceAtStart] = 도면 시작이 구멍 입구 끝면 위(TYPE 화면). 도면이 +Z 로 가면 뒤보링으로 본다.
     * 진입점에 선 자세, 진입 이동, 프로그램 요소마다 [STEP] 간격으로 여유를 재고, 닿는 구간의 처음 · 끝은 이분법으로 1e-4 mm 쯤까지 좁힌다.
     * 요소 첫 점에서 이미 닿아 있으면(앞 요소에서 이어짐) 처음은 0
     */
    fun check(drawing: Contour, program: Contour, entry: Pt, tip: Int, r: Double, faceAtStart: Boolean, bar: BoringBar): HolderResult {
        if (!(r >= 0 && r.isFinite()) || problems(bar, r).isNotEmpty()) return skipped(HolderSkip.BAD_INPUT)
        val bore = Bore(drawing, faceAtStart)
        if (!bore.monotone) return skipped(HolderSkip.NOT_MONOTONE)
        val tool = BarShape(bar, r, bore.reverse)
        val tv = NoseComp.tipVector(tip, r)
        val moves = (listOf<Pair<Int, Seg>>(-1 to Line(entry, program.segs.first().start)) + program.segs.withIndex().map { it.index to it.value })
            .filter { lengthOf(it.second) > EPS }
        val h = max(STEP, moves.sumOf { lengthOf(it.second) } / 20000)
        val hits = mutableListOf<HolderHit>()
        var clear = Double.POSITIVE_INFINITY
        var clearAt: HolderPose? = null
        var clearEl: Int? = null
        fun pose(p: Pt, k: Int): HolderPose = (p - tv).let { HolderPose(p, it, drawnOf(drawing, k, it)) }
        // seg = null 은 진입점에 선 자세
        fun scan(k: Int, seg: Seg?) {
            val n = if (seg == null) 0 else max(1, ceil(lengthOf(seg) / h).toInt())
            val ts = DoubleArray(n + 1) { if (n == 0) 0.0 else it.toDouble() / n }
            fun at(t: Double) = if (seg == null) entry else pointOn(seg, t)
            val poses = ts.map { pose(at(it), k) }
            val ms = poses.map { margins(bore, tool, it.nose) }
            for ((j, m) in ms.withIndex()) {
                if (m.bar.m < clear) {
                    clear = m.bar.m
                    clearAt = poses[j]
                    clearEl = k
                }
            }
            val found = mutableListOf<HolderHit>()
            for (part in HolderPart.entries) {
                fun touch(m: Margins) = m[part].m < -CONTACT_TOL
                val jf = ms.indexOfFirst(::touch)
                if (jf < 0) continue
                val jl = ms.indexOfLast(::touch)
                // 떨어진 t 와 닿은 t 사이를 좁혀 닿은 쪽 끝
                fun edge(off: Double, on: Double): Double {
                    var a = off
                    var b = on
                    repeat(40) {
                        val mid = (a + b) / 2
                        if (touch(margins(bore, tool, at(mid) - tv))) b = mid else a = mid
                    }
                    return b
                }
                val from = if (jf == 0) 0.0 else edge(ts[jf - 1], ts[jf])
                val to = if (jl == n) ts[n] else edge(ts[jl + 1], ts[jl])
                val jw = ms.indices.minBy { ms[it][part].m }
                val g = ms[jw][part]
                val w = poses[jw]
                found += HolderHit(k, part, pose(at(from), k), pose(at(to), k), w, from, to, -g.m, g.x < 0, g.z - (w.nose + tool.ref).z)
            }
            hits += found.sortedBy { it.fromT }
        }
        scan(-2, null)
        for ((k, s) in moves) scan(k, s)
        return HolderResult(null, hits, clear.takeIf { it.isFinite() }, clearAt, clearEl)
    }

    /** [nose] 에 놓은 단면: [바(maxZ 에서 자름), 인서트(노즈 원호 포함)]. [reverse] = 뒤보링 */
    fun outline(bar: BoringBar, r: Double, nose: Pt, maxZ: Double, reverse: Boolean = false): List<Contour> {
        if (!(r >= 0 && r.isFinite()) || problems(bar, r).isNotEmpty()) return emptyList()
        val t = BarShape(bar, r, reverse)
        val body = clipZ(t.body.pts.map { it + nose }, maxZ)
        val ins = distinct(t.full).map { it + nose }
        val close: Seg = if (r > 0) Arc(ins.last(), ins.first(), nose, cw = false) else Line(ins.last(), ins.first())
        return listOf(closed(body), Contour(ins.zipWithNext { a, b -> Line(a, b) } + close))
    }

    /**
     * 노즈 중심 [nose] 자세에서 공구 조각이 소재와 겹치는 곳(깎는 쪽 벽 · 맞은편 벽)을 닫힌 꺾은선으로. [maxZ] 너머는 자른다 —
     * 그린 바([outline] 의 maxZ)와 같은 곳에서. 뒤보링은 입구 반직선이 끝없이 가 띠가 넓어지면 원호가 현으로 그려졌다
     */
    fun overlap(drawing: Contour, faceAtStart: Boolean, bar: BoringBar, r: Double, nose: Pt, maxZ: Double = Double.POSITIVE_INFINITY): List<Contour> {
        if (!(r >= 0 && r.isFinite()) || problems(bar, r).isNotEmpty()) return emptyList()
        val bore = Bore(drawing, faceAtStart)
        if (!bore.monotone) return emptyList()
        val t = BarShape(bar, r, bore.reverse)
        return (t.inserts + t.body).flatMap { bore.overlap(it, nose, maxZ) }
    }

    /** 한 자세(노즈 중심 [nose])의 부분별 여유 — 검사 고리 */
    internal fun margins(bore: Bore, tool: BarShape, nose: Pt): Margins {
        val ref = nose + tool.ref
        val insert = tool.inserts.map { bore.gap(it, nose) }.minByOrNull { it.m } ?: Gap.CLEAR
        val turret = tool.bar.overhang?.let { o -> bore.face?.let { Gap(ref.z + o - it, it, 0.0) } } ?: Gap.CLEAR
        // 헤드 구간: 인선에서 인서트 끝까지(뒤보링은 −Z 쪽)
        val minBore = tool.bar.dMin?.let { d ->
            val z0 = if (tool.reverse) ref.z - tool.insZ else ref.z
            bore.narrowest(z0, z0 + tool.insZ).let { Gap(it.m - d / 2, it.z, it.x) }
        } ?: Gap.CLEAR
        return Margins(insert, bore.gap(tool.body, nose), turret, minBore)
    }

    private fun skipped(s: HolderSkip) = HolderResult(s, emptyList(), null, null, null)

    /** 요소 k 위에서 노즈 중심 c 와 가장 가까운 도면 점(원호는 원호 안, 중심이면 가운데). −2 · −1 은 도면 첫 점 */
    private fun drawnOf(drawing: Contour, k: Int, c: Pt): Pt = when (val s = drawing.segs.getOrNull(k)) {
        null -> drawing.segs.first().start
        is Line -> {
            val d = s.end - s.start
            val l2 = d dot d
            if (l2 <= 0) s.start else s.start + d * (((c - s.start) dot d) / l2).coerceIn(0.0, 1.0)
        }
        is Arc -> {
            val v = c - s.center
            val along = wrap2Pi((if (s.cw) -1 else 1) * (angleOf(v) - angleOf(s.start - s.center)))
            when {
                v.length <= EPS -> pointOn(s, 0.5)
                along <= s.sweepRad() -> s.center + v.unit() * s.radius
                (c - s.start).length <= (c - s.end).length -> s.start
                else -> s.end
            }
        }
    }
}

/** 바 뒤 · 반직선이 끝없이 갈 때의 길이(mm) — 공작물보다 한참 길게 */
private const val REACH = 1e5

/** Z 로 이만큼(mm)까지 되돌아가는 것은 반올림으로 본다 — 3점호를 CAD 점 셋째 자리로 넣으면 입구 R 의 끝이 면을 조금 넘는다 */
private const val Z_BACK = 1e-3

/** 반직선 방향의 z 성분이 이보다 작으면(0.11° 안) 수직 = 입구 면 */
private const val UPRIGHT = 2e-3

/** 겹침 그림: 조각마다 띠 수와 띠 폭(mm) */
private const val STRIPS = 400
private const val STRIP_W = 0.02

/** 한 자세 · 한 부분의 여유(mm, 음수 = 겹침)와 가장 좁은 점 (z, x). x < 0 이면 맞은편 벽 */
internal class Gap(val m: Double, val z: Double, val x: Double) {
    companion object {
        val CLEAR = Gap(Double.POSITIVE_INFINITY, 0.0, 0.0)
    }
}

/** 한 자세의 부분별 여유 */
internal class Margins(val insert: Gap, val bar: Gap, val turret: Gap, val minBore: Gap) {
    operator fun get(p: HolderPart): Gap = when (p) {
        HolderPart.INSERT -> insert
        HolderPart.BAR -> bar
        HolderPart.TURRET -> turret
        HolderPart.MIN_BORE -> minBore
    }
}

/** 볼록 다각형 조각(노즈 중심 기준, 반시계). 꼭짓점 z 사이마다 위 · 아래 변을 x = m·z + b 로 둔다 */
internal class Piece(pts: List<Pt>) {
    val pts = distinct(pts)
    val zs: DoubleArray
    val uM: DoubleArray
    val uB: DoubleArray
    val lM: DoubleArray
    val lB: DoubleArray

    init {
        val z = this.pts.map { it.z }.sorted()
        zs = z.filterIndexed { i, v -> i == 0 || v - z[i - 1] > 1e-12 }.toDoubleArray()
        val n = max(0, zs.size - 1)
        uM = DoubleArray(n)
        uB = DoubleArray(n)
        lM = DoubleArray(n)
        lB = DoubleArray(n)
        val edges = this.pts.indices.map { this.pts[it] to this.pts[(it + 1) % this.pts.size] }.filter { (p, q) -> abs(q.z - p.z) > 1e-12 }
        for (i in 0 until n) {
            val mid = (zs[i] + zs[i + 1]) / 2
            var top = Double.NEGATIVE_INFINITY
            var bottom = Double.POSITIVE_INFINITY
            for ((p, q) in edges) {
                if (mid < min(p.z, q.z) || mid > max(p.z, q.z)) continue
                val m = (q.x - p.x) / (q.z - p.z)
                val b = p.x - m * p.z
                val x = m * mid + b
                if (x > top) {
                    top = x
                    uM[i] = m
                    uB[i] = b
                }
                if (x < bottom) {
                    bottom = x
                    lM[i] = m
                    lB[i] = b
                }
            }
        }
    }
}

/**
 * 보링바 단면(노즈 중심 기준, x 는 반경). 주절삭날 e1 = (−cos κr, −sin κr), 끝날 e2 = (cos κr′, −sin κr′), κr′ = 180° − κr − ε.
 * 꼭짓점 N = −r / sin(ε/2) · (e1 + e2 방향), 노즈 접점 T1 = r·n1 · T2 = r·n2 (n = 날의 바깥 법선).
 * 인서트 = [T1, N + l·e1, N + l·(e1 + e2), N + l·e2, T2](T 는 셋째 꼭짓점 없이, 노즈는 현 T1–T2 — 노즈 원은 보정으로 이미 소재에 안 닿는다).
 * 가상 인선 [ref] = (−r, r) = 팁 2 — 카탈로그 f 의 기준. 바 = 중심 x_c = r − f, 반지름 D/2 의 띠: 앞은 N 을 지나는 주절삭날 연장선,
 * 뒤는 공구대 면(ref.z + 돌출 길이, 없으면 아주 멀리). 인서트는 띠 위 · 아래로 나온 곳만 조각으로 — 띠 안은 바가 덮는다.
 * 뒤보링([reverse])은 인서트를 노즈 중심에서 Z 로 뒤집고(날이 +Z, [ref] = (r, r) = 팁 1) 바는 그대로 +Z 로 나간다 —
 * 바 앞(−Z) 끝은 인서트 끝에서 수직(인서트를 받치는 가장 짧은 헤드)
 */
internal class BarShape(val bar: BoringBar, r: Double, val reverse: Boolean) {
    val ref = Pt(if (reverse) r else -r, r)

    /** 자르지 않은 인서트(반시계) */
    val full: List<Pt>

    /** 띠 밖으로 나온 인서트 조각 */
    val inserts: List<Piece>
    val body: Piece

    /** 인선에서 인서트 끝까지 Z 길이(DMIN 구간) */
    val insZ: Double

    /** 정방향 헤드 길이: 가상 인선에서 인서트 뒤 끝 · 바 앞 끝까지 Z */
    val head: Double

    init {
        val k = rad(bar.kappaDeg)
        val eps = rad(bar.shape.epsDeg)
        val kp = PI - k - eps
        val e1 = Pt(-cos(k), -sin(k))
        val e2 = Pt(cos(kp), -sin(kp))
        val corner = (e1 + e2).unit() * (-r / sin(eps / 2))
        val l = bar.edge
        val fwd = listOf(Pt(-sin(k), cos(k)) * r, corner + e1 * l) +
            (if (bar.shape == InsertShape.T) emptyList() else listOf(corner + (e1 + e2) * l)) +
            listOf(corner + e2 * l, Pt(sin(kp), cos(kp)) * r)
        full = if (reverse) fwd.map { Pt(-it.z, it.x) }.asReversed() else fwd
        insZ = fwd.maxOf { it.z } + r
        val top = r - bar.f + bar.dia / 2
        val bottom = r - bar.f - bar.dia / 2
        // 바 앞 끝(아래, 위)
        val front = if (reverse) {
            val zf = full.minOf { it.z }
            listOf(Pt(zf, bottom), Pt(zf, top))
        } else {
            listOf(bottom, top).map { x -> corner + e1 * ((corner.x - x) / sin(k)) }
        }
        val back = ref.z + (bar.overhang ?: REACH)
        body = Piece(listOf(front[0], Pt(back, bottom), Pt(back, top), front[1]))
        inserts = listOf(clipX(full, top, above = true), clipX(full, bottom, above = false)).map(::Piece).filter { it.pts.size >= 3 }
        head = max(fwd.maxOf { it.z }, front.maxOf { it.z }) + r
    }
}

/**
 * 구멍 단면의 소재 {(z, x): |x| ≥ ρ(z)} — 깎은 벽과 축 너머 맞은편 벽. 도면은 Z 로 한쪽으로만 가야 한다(정방향 Z−, 뒤보링 Z+).
 * 양 끝은 반직선으로 늘인다: 입구 면이 없으면([faceAtStart] false, 3점호) 시작에서 −시작 접선, 끝이 축 위가 아니면 끝 접선 방향.
 * 반직선은 축에서 멈추고 거의 수직이면 수직(입구 면). 늘인 윤곽의 가장 큰 z([zTop]) 너머는 공기(입구 면 앞),
 * 깊은 끝이 축 위면 그 너머는 소재(ρ = 0). 수직 요소(면)는 직접 보지 않고 이웃 토막의 한쪽 극한으로만 본다 —
 * 면에 모서리로만 붙는 것은 닿음이 아니다
 */
internal class Bore(drawing: Contour, faceAtStart: Boolean) {
    /** 뒤보링: 도면이 +Z 로 간다 */
    val reverse = drawing.segs.last().end.z > drawing.segs.first().start.z

    /** Z 로 1 µm 넘게 되돌아가지 않는다 */
    val monotone: Boolean
    val zTop: Double

    /** 공구대가 닿는 입구 면 z. 입구 쪽 반직선이 축에 닿지 않고 +Z 로 비스듬히 멀어지면 면이 어디인지 몰라 null */
    val face: Double?
    private val zMin: Double
    private val closed: Boolean
    private val spans: List<Span>
    private val breaks: DoubleArray

    init {
        val first = drawing.segs.first()
        val last = drawing.segs.last()
        val head = if (faceAtStart) null else ray(first.start, tangent(first, first.start) * -1.0)?.let { Line(it, first.start) }
        val tail = if (last.end.x > EPS) ray(last.end, tangent(last, last.end))?.let { Line(last.end, it) } else null
        val ext = listOfNotNull(head) + drawing.segs + listOfNotNull(tail)
        monotone = ext.all { oneWay(it, if (reverse) 1.0 else -1.0) }
        val a = ext.first().start
        val b = ext.last().end
        val mouth = if (a.z >= b.z) a else b
        val deep = if (mouth === a) b else a
        zTop = mouth.z
        zMin = deep.z
        closed = deep.x <= EPS
        val mouthRay = if (mouth === a) head else tail
        face = if (faceAtStart || mouth.x <= EPS || mouthRay == null || mouthRay.start.z == mouthRay.end.z) zTop else null
        spans = ext.mapNotNull(::spanOf).sortedBy { it.z0 }
        breaks = (spans.flatMap { listOf(it.z0, it.z1) } + zTop + zMin).sorted().distinct().toDoubleArray()
    }

    /** ρ(z), 공기면 +∞ — 검사 고리 */
    internal fun rho(z: Double): Double = spanAt(z)?.rho(z) ?: Double.POSITIVE_INFINITY

    /** 노즈 중심 [nose] 에 놓은 조각의 여유 min(ρ − 위, ρ + 아래): 위는 깎는 쪽 벽, 아래는 맞은편 벽 */
    fun gap(p: Piece, nose: Pt): Gap {
        var g = Gap.CLEAR
        for (i in 0 until p.zs.size - 1) {
            val uB = p.uB[i] + nose.x - p.uM[i] * nose.z
            val lB = p.lB[i] + nose.x - p.lM[i] * nose.z
            g = band(p.zs[i] + nose.z, p.zs[i + 1] + nose.z, p.uM[i], uB, p.lM[i], lB, g)
        }
        return g
    }

    /** z ∈ [z0, z1] 에서 가장 좁은 반경 */
    fun narrowest(z0: Double, z1: Double): Gap = band(z0, z1, 0.0, 0.0, 0.0, 0.0, Gap.CLEAR)

    /**
     * z ∈ [za, zb] 에서 위 x = uM·z + uB, 아래 x = lM·z + lB 인 띠. 소재 꺾임점으로 더 나눠 토막마다 양 끝(한쪽 극한)과
     * 원호에서 ρ′ 가 변의 기울기와 같은 곳(ρ − 위 · ρ + 아래의 극값)을 본다 — 이 값들의 최솟값이 정확한 최솟값이다
     */
    private fun band(za: Double, zb: Double, uM: Double, uB: Double, lM: Double, lB: Double, best: Gap): Gap {
        var g = best
        fun look(sp: Span, z: Double) {
            val rho = sp.rho(z)
            val u = uM * z + uB
            val l = lM * z + lB
            if (rho - u < g.m) g = Gap(rho - u, z, u)
            if (rho + l < g.m) g = Gap(rho + l, z, l)
        }
        var lo = za
        var i = breaks.indexOfFirst { it > za }.let { if (it < 0) breaks.size else it }
        while (lo < zb) {
            val hi = if (i < breaks.size && breaks[i] < zb) breaks[i++] else zb
            val sp = if (hi - lo > 1e-12) spanAt((lo + hi) / 2) else null
            if (sp != null) {
                look(sp, lo)
                look(sp, hi)
                if (sp is Round) for (z in listOf(sp.flat(uM), sp.flat(-lM))) if (z > lo && z < hi) look(sp, z)
            }
            lo = hi
        }
        return g
    }

    /**
     * 조각이 소재와 겹치는 곳을 z 띠로 잘라 닫힌 꺾은선으로(깎는 쪽 벽 [max(아래, ρ), 위] · 맞은편 벽 [아래, min(위, −ρ)]).
     * 띠는 조각마다 400 개 이하(폭 0.02 mm 이하가 되게), 조각 꼭짓점과 소재 꺾임점 z 는 띠 경계. 겹침이 띠 안에서 끝나면 그 z 를 이분법으로 찾는다.
     * [maxZ] 너머는 보지 않는다(띠가 그만큼 좁아진다)
     */
    fun overlap(p: Piece, nose: Pt, maxZ: Double = Double.POSITIVE_INFINITY): List<Contour> {
        val za = p.zs.first() + nose.z
        val zb = minOf(p.zs.last() + nose.z, zTop, maxZ)
        if (zb - za <= 1e-12) return emptyList()
        val n = min(STRIPS, ceil((zb - za) / STRIP_W).toInt())
        val cuts = ((0..n).map { za + (zb - za) * it / n } + p.zs.map { it + nose.z } + breaks.toList()).filter { it in za..zb }.sorted()
        fun chain(z: Double, up: Boolean): Pair<Double, Double> {
            val i = p.zs.indexOfLast { it + nose.z <= z }.coerceIn(0, p.zs.size - 2)
            return if (up) p.uM[i] to p.uB[i] + nose.x - p.uM[i] * nose.z else p.lM[i] to p.lB[i] + nose.x - p.lM[i] * nose.z
        }
        val out = mutableListOf<Contour>()
        for (wallUp in listOf(true, false)) {
            val bottom = mutableListOf<Pt>()
            val top = mutableListOf<Pt>()
            fun close() {
                val ring = distinct(bottom + top.asReversed())
                if (ring.size >= 3) out += closed(ring)
                bottom.clear()
                top.clear()
            }
            for ((z0, z1) in cuts.zipWithNext()) {
                if (z1 - z0 <= 1e-12) continue
                val sp = spanAt((z0 + z1) / 2)
                if (sp == null) {
                    close()
                    continue
                }
                val (uM, uB) = chain((z0 + z1) / 2, true)
                val (lM, lB) = chain((z0 + z1) / 2, false)
                fun g(z: Double) = if (wallUp) uM * z + uB - sp.rho(z) else -sp.rho(z) - (lM * z + lB)
                fun add(z: Double) {
                    val u = uM * z + uB
                    val l = lM * z + lB
                    val rho = sp.rho(z)
                    bottom += Pt(z, if (wallUp) max(l, rho) else l)
                    top += Pt(z, if (wallUp) u else min(u, -rho))
                }
                // 겹친 쪽(g > 0) 끝
                fun root(off: Double, on: Double): Double {
                    var a = off
                    var b = on
                    repeat(50) {
                        val mid = (a + b) / 2
                        if (g(mid) > 0) b = mid else a = mid
                    }
                    return b
                }
                val g0 = g(z0)
                val g1 = g(z1)
                if (g0 <= 0 && g1 <= 0) {
                    close()
                    continue
                }
                if (g0 <= 0) close()
                add(if (g0 > 0) z0 else root(z0, z1))
                add(if (g1 > 0) z1 else root(z1, z0))
                if (g1 <= 0) close()
            }
            close()
        }
        return out
    }

    private fun spanAt(z: Double): Span? = when {
        z > zTop -> null
        z < zMin -> if (closed) Solid else null
        else -> {
            var lo = 0
            var hi = spans.size - 1
            while (lo < hi) {
                val m = (lo + hi) / 2
                if (spans[m].z1 < z) lo = m + 1 else hi = m
            }
            spans.getOrNull(lo)?.takeIf { it.z0 <= z && z <= it.z1 }
        }
    }

    /** ρ(z) 한 토막 [z0, z1] */
    private sealed interface Span {
        val z0: Double
        val z1: Double
        fun rho(z: Double): Double
    }

    /** 직선: 두 끝을 잇는다 */
    private class Straight(val a: Pt, val b: Pt) : Span {
        override val z0 = min(a.z, b.z)
        override val z1 = max(a.z, b.z)
        override fun rho(z: Double) = a.x + (b.x - a.x) * (z - a.z) / (b.z - a.z)
    }

    /** 원호: c.x + s√(R² − (z − c.z)²), s = 위 반원 +1 */
    private class Round(override val z0: Double, override val z1: Double, val c: Pt, val r: Double, val s: Double) : Span {
        override fun rho(z: Double) = c.x + s * sqrt(max(0.0, r * r - (z - c.z) * (z - c.z)))

        /** ρ′ = m 인 z */
        fun flat(m: Double) = c.z - s * m * r / sqrt(1 + m * m)
    }

    /** 축 위로 막힌 바닥 너머 */
    private object Solid : Span {
        override val z0 = Double.NEGATIVE_INFINITY
        override val z1 = Double.NEGATIVE_INFINITY
        override fun rho(z: Double) = 0.0
    }

    private companion object {
        /** 수직(면)이면 null */
        fun spanOf(s: Seg): Span? {
            if (abs(s.end.z - s.start.z) <= EPS) return null
            return when (s) {
                is Line -> Straight(s.start, s.end)
                is Arc -> {
                    val mid = pointOn(s, 0.5)
                    Round(min(s.start.z, s.end.z), max(s.start.z, s.end.z), s.center, s.radius, if (mid.x >= s.center.x) 1.0 else -1.0)
                }
            }
        }

        /** p 에서 d(단위) 방향 반직선의 끝. 축(x = 0)에서 멈추고, 거의 수직이면 수직. 길이 0 이면 null */
        fun ray(p: Pt, d0: Pt): Pt? {
            val d = if (abs(d0.z) <= UPRIGHT) Pt(0.0, if (d0.x >= 0) 1.0 else -1.0) else d0
            if (d.x >= 0) return p + d * REACH
            val len = min(REACH, p.x / -d.x)
            return when {
                len <= EPS -> null
                len < REACH -> Pt(p.z + d.z * len, 0.0)
                else -> p + d * len
            }
        }

        /** z 로 dir 쪽으로만 가나(되돌아감 [Z_BACK] 까지는 받는다). 원호는 안에 있는 z 극점(중심에서 0 · 180°)까지 본다 */
        fun oneWay(s: Seg, dir: Double): Boolean {
            if ((s.end.z - s.start.z) * dir < -Z_BACK) return false
            if (s !is Arc) return true
            val lo = min(s.start.z, s.end.z) - Z_BACK
            val hi = max(s.start.z, s.end.z) + Z_BACK
            val a0 = angleOf(s.start - s.center)
            return listOf(0.0, PI).all { t ->
                wrap2Pi((if (s.cw) -1 else 1) * (t - a0)) >= s.sweepRad() || (s.center.z + s.radius * cos(t)) in lo..hi
            }
        }
    }
}

private fun rad(deg: Double) = deg * PI / 180

private fun lengthOf(s: Seg): Double = when (s) {
    is Line -> (s.end - s.start).length
    is Arc -> s.sweepRad() * s.radius
}

private fun tangent(s: Seg, p: Pt): Pt = when (s) {
    is Line -> (s.end - s.start).unit()
    is Arc -> (p - s.center).unit().left() * (if (s.cw) -1.0 else 1.0)
}

/** 요소 위 길이 비율 t 의 점(t = 1 은 끝점 그대로) */
private fun pointOn(s: Seg, t: Double): Pt {
    if (t >= 1) return s.end
    return when (s) {
        is Line -> s.start + (s.end - s.start) * t
        is Arc -> {
            val a = angleOf(s.start - s.center) + (if (s.cw) -1 else 1) * s.sweepRad() * t
            s.center + Pt(cos(a), sin(a)) * s.radius
        }
    }
}

/** 이웃(처음 · 끝 포함)과 겹치는 점을 뺀다 */
private fun distinct(pts: List<Pt>): List<Pt> {
    val q = pts.filterIndexed { i, p -> i == 0 || (p - pts[i - 1]).length > 1e-12 }
    return if (q.size > 1 && (q.last() - q.first()).length <= 1e-12) q.dropLast(1) else q
}

private fun closed(pts: List<Pt>): Contour = if (pts.isEmpty()) Contour(emptyList()) else Contour.polyline(*(pts + pts.first()).toTypedArray())

/** 볼록 다각형에서 x ≥ level(above) 또는 x ≤ level 쪽 */
private fun clipX(poly: List<Pt>, level: Double, above: Boolean): List<Pt> = clip(poly) { if (above) it.x - level else level - it.x }

/** 볼록 다각형에서 z ≤ maxZ 쪽 */
private fun clipZ(poly: List<Pt>, maxZ: Double): List<Pt> = clip(poly) { maxZ - it.z }

/** 반평면 {side ≥ 0} 으로 자르기(Sutherland–Hodgman) */
private fun clip(poly: List<Pt>, side: (Pt) -> Double): List<Pt> {
    val out = mutableListOf<Pt>()
    for (i in poly.indices) {
        val p = poly[i]
        val q = poly[(i + 1) % poly.size]
        val sp = side(p)
        val sq = side(q)
        if (sp >= 0) out += p
        if ((sp >= 0) != (sq >= 0)) out += p + (q - p) * (sp / (sp - sq))
    }
    return out
}
