# 계산 엔진 설계 (`:calc`)

화면(`:shared`)은 이 모듈의 공개 API 만 부른다. 계산은 전부 여기서, 그림도 여기서 만든 경로를 그린다.
테스트: `gw.bat :calc:jvmTest` (`calc/src/commonTest`).

## 좌표 약속
- 선반, 후방 공구대 기준 화면: **Z 오른쪽 +, X 위 +**. 척 쪽이 Z−.
- 내부 기하는 **반경값**(`Pt(z, x)` 의 x 는 반경). 화면 입출력의 X 는 **직경값** — 바꾸는 곳은 API 경계 한 군데.
- 각도는 입력·출력 모두 **도(°)**. 테이퍼 각은 **Z 축에서 잰 각**(0° = Z 평행, 90° = 단면).
- 길이 mm. 결과 표시는 소수 셋째 자리(0.001) 반올림 — 반올림은 화면에 보여 줄 때만(`Fmt`), 계산은 Double 그대로.
- **크기 한도** `MAX_MM` = 10 000 mm(10 m, 절댓값). 받는 좌표·치수와 계산기가 채운 점(아주 작은 각도로 푼 Z 등)이 넘으면
  각 계산기가 `BAD_INPUT` 으로 멈춘다. 키패드는 12자리까지 받아, 잘못 친 큰 수에서 부동소수 오차가 `EPS`(1e-9)를 넘어
  NaN·끊긴 윤곽이 나와 앱이 죽던 것을 막는다(2단계 점검의 견고성 시험 `InputRobustnessTest`). 3점호는 중심이 한도 밖이면
  (세 점이 거의 한 줄) `SHAPE_MISMATCH`.

## 가상 인선(imaginary tool tip)과 노즈R
- 프로그램 좌표는 **가상 인선점**(노즈 원에 Z·X 방향으로 접하는 두 접선의 교점) 기준. G41/G42 를 안 쓰고
  가상 인선으로 직접 프로그램할 때 필요한 보정 좌표를 내는 것이 이 앱의 핵심.
- **팁 번호(FANUC 1~9, 0)** → 노즈 중심 C 에서 가상 인선 P 로 가는 벡터 `tipVector(T, r)` (Δz, Δx):
  - T3 = (−r, −r) : 외경 일반 바이트(척 쪽으로 깎음) — 기본값
  - T4 = (+r, −r) : 외경 역바이트
  - T2 = (−r, +r) : 내경 보링
  - T1 = (+r, +r) : 내경 역보링
  - T5 = (+r, 0) Z+ · T6 = (0, +r) X+ · T7 = (−r, 0) Z− · T8 = (0, −r) X− (변 가운데), T0/T9 = (0, 0) 중심.
  - **출처(확인함)**: Haas ST/TL 프로그래밍 워크북 69~70쪽 "TOOL TIP DIRECTION" 표, 방향 정의는 59쪽
    ("a vector originating from the center of the tool radius and extending to the imaginary tool tip").
    https://haascnc.com/content/dam/haascnc/videos/bonus-content/g71-essential-tips-tod/lathe---programming-workbook.pdf
    · 같은 표: Haas Factory Outlet 교재 74~75쪽 https://www.productivity.com/wp-content/uploads/2022/08/Haas-Lathe-Programming-8-2022.pdf
    · FANUC 공식 매뉴얼은 웹에 공개본이 없어 못 봤다. 방향은 기계 좌표(X+, Z+)로 적혀 있어 그림 방향과 무관하다.
- 역바이트(Rev. Nose-R) = 진행 방향과 팁을 Z 로 뒤집음(T3↔T4, T2↔T1). `NoseComp.standardTip(inner, reverse)`.
- **보정 쪽 G41/G42** = 진행 방향에서 본 공구의 왼쪽/오른쪽. 외경 정방향(척 쪽) G42 · 외경 역방향 G41 ·
  내경 정방향 G41 · 내경 역방향 G42 (`Side.of(inner, reverse)`, Haas 워크북 59쪽). 단면처럼 표에 없는 것은 부르는 쪽이 정한다
  (예: 외경 바이트로 단면을 중심 쪽(X−)으로 깎으면 G41).
- **원호 방향**: `Arc.cw = true` 가 G02. 이 그림(Z 오른쪽, X 위)에서 **G02 = 시계, G03 = 반시계**로 보인다 —
  G18 평면은 +Y 에서 내려다보고 정하는데 Y = Z × X 라 이 그림이 곧 +Y 에서 본 모습. Haas 워크북 43~44쪽 예제
  (`X1.5 Z0 → G03 X2. Z-0.25 R0.25`, `G02 X5. Z-2.5 I1.5 K0.`)로 맞춰 봤다(`GeomTest`).

## 일반 해법(모든 보정의 바탕)
1. 도면 윤곽 = 선분·원호의 연속 `Contour(List<Seg>)`
2. 공구 쪽(G41 왼쪽 / G42 오른쪽)으로 r 만큼 **오프셋**. 원호는 같은 중심, 반지름 R±r(볼록 +, 오목 −).
3. 오프셋 요소마다 **노즈 원이 재료와 겹치지 않는 구간**만 남긴다. 재료 = 도면 윤곽 + 양 끝을 접선 방향으로 늘인 반직선
   (도면 밖으로도 소재가 이어진다고 본다). 겹침은 노즈 중심이 "요소를 양쪽으로 r 민 직선·원, 끝점 둘레 r 원,
   연장선을 양쪽으로 민 직선" 을 넘을 때만 바뀌므로, 그 교점들로 오프셋을 나누고 토막 가운데 점을 재 보면 정확한 구간이 나온다
   (허용 1e-7 mm). 이웃만 보지 않으므로 짧은 요소의 끝없는 연장선이 벽처럼 이웃을 자르는 일이 없다.
4. 남은 구간끼리 잇는다 → 노즈 중심 경로.
   - 두 구간이 한 점에서 만나면(오목 모서리, 접해서 이어지는 곳) 그 점. 허용치 때문에 두 구간이 교점을 조금 넘어 겹치면
     교점에서 자른다(거의 일직선으로 꺾일 때 넘는 길이가 커진다).
   - 둘 다 꼭짓점까지 깎이면(볼록 모서리) **원호를 끼우지 않고 두 오프셋을 늘인 교점**(날카로운 모서리). 늘인 토막도
     재료와 겹치지 않는지 본다. 교점이 둘이면 두 오프셋 끝점의 가운데에 가까운 쪽.
   - 빠진 요소를 사이에 두고 공구가 볼록 꼭짓점을 돌아 넘어가야 하면 그 **꼭짓점 둘레 반지름 r 원호를 빠진 요소 자리에** 넣는다
     (예: 볼록 T1 과 오목 골 사이의 짧은 경사가 빠지면 앞 직선 끝에서 T1 을 돌아 둘째 경사에 닿는다).
5. 중심 경로의 모든 점에 `tipVector` 를 더함 → 가상 인선 경로 = 프로그램 좌표.
- 결과 경로의 요소 수·순서는 **원래 윤곽과 같다**(i번 = 원래 i번). 사라진 요소는 길이 0 의 `Line`
  (4의 셋째 경우만 그 자리가 꼭짓점 둘레 `Arc`). 그래서 "T1 보정점" = `tipPath(...).path.segs[i].end` 처럼 번호로 꺼내면 된다.
- 공구가 못 따라가는 경우:
  - 오목 원호 R < r → `CompWarning.ArcTooSmall`, 그 원호는 빼고 이웃끼리 잇는다(그 자리는 R 대신 r 로 남음).
    **R = r 은 경고하지 않는다**(공구가 꼭 맞게 들어가 정확하다; 중심 경로의 그 원호는 한 점).
  - 남는 구간이 없는 요소 → `CompWarning.Unreachable`. "닿을 수 있다" = 그 요소의 한 점에서 공구 쪽으로 접한 반지름 r 원이
    다른 재료와 겹치지 않는다. 노즈보다 좁은 V 홈 바닥, 폭 < 2r 인 홈의 벽·바닥(공구는 홈 위를 지나간다),
    **한쪽 끝이 볼록이어도** 다른 끝 오목 모서리에 접한 원이 요소 밖에서 닿는 짧은 요소가 그렇다. 한 점에서만 닿아도
    (폭이 꼭 2r 인 홈 바닥) 닿는 것이다.
  - 이 꼴(요소마다 한 자리, 볼록 모서리는 날카롭게)로 낼 수 없으면 틀린 숫자 대신 `IllegalArgumentException`:
    이웃한 두 요소의 볼록 모서리에서 늘인 오프셋이 만나지 않을 때(제자리로 되돌아가는 윤곽, 단면에서 날카롭게 오목 원호로
    넘어가는 모서리 — 꼭짓점을 도는 원호가 필요한데 넣을 자리가 없다), 빠진 요소보다 돌아야 할 꼭짓점이 많을 때,
    한 요소에서 닿는 곳이 둘로 나뉠 때, 닿는 요소가 없을 때. 지금 계산기들은 이런 모양을 만들지 않는다
    (모서리 R 은 접해서 붙고 볼록 모서리는 직선끼리이거나 볼록 원호). 계산기는 잡아서 `NOSE_UNREACHABLE` 로 바꾼다.
- 닫힌 식(검증용, `NoseR`): 테이퍼 θ, T3, 외경, 척 쪽으로 커지는 테이퍼에서
  ΔZ = r(1 − tan(θ/2)), ΔX(반경) = r(1 − tan((90° − θ)/2)) → 0.4R·45° 에서 0.234 / 직경 0.468.
  - ΔZ 는 이웃이 수평선일 때(꺾인 점이 Z 로만 밀림), ΔX 는 이웃이 단면일 때(X 로만 밀림) 쓰는 값.
  - 척 쪽으로 작아지는 테이퍼(테이퍼 TYPE 5 꼴)는 식이 다르다: ΔZ = r(1 + tan(θ/2)), ΔX = r(tan(45° + θ/2) − 1).
  - `NoseCompTest` 가 일반 해법을 5°~85°, r 0.2~1.6, 외경/내경 × 정/역방향에서 이 식들과 1e-9 안으로 맞춰 본다.
  - 원본 앱 06.jpg 의 보정좌표(`G00 X40. Z0.` → `G01 X34. Z-2.` → X34.000 Z-2.186)도 내경·0.4R·T2·G41 로 같은 값이 나온다.

## 공개 API (구현됨 — 바꾸면 이 문서도 같이)
패키지 `com.bmt.nosecalc.calc`. 파일: `Geom.kt`, `NoseComp.kt`, `Fmt.kt`, `NoseR.kt`(닫힌 식).
계산기 `TaperCalc.kt`·`GrooveCalc.kt`·`RoundCalc.kt`·`Holder.kt`·`RightTriangle.kt` 는 아래 따로 절이 있다.
```kotlin
// Geom.kt
data class Pt(val z: Double, val x: Double) {               // x = 반경. 점이자 벡터
    operator fun plus(o: Pt): Pt; operator fun minus(o: Pt): Pt; operator fun times(k: Double): Pt
    infix fun dot(o: Pt): Double; infix fun cross(o: Pt): Double   // cross > 0 이면 o 가 반시계 쪽
    val length: Double
    val xDia: Double                                        // 직경값 X
    companion object { fun ofDia(z: Double, xDia: Double): Pt }    // 직경값으로 만들기(인자 순서 z, x)
}
sealed interface Seg { val start: Pt; val end: Pt }
data class Line(override val start: Pt, override val end: Pt) : Seg
data class Arc(override val start: Pt, override val end: Pt, val center: Pt, val cw: Boolean) : Seg { // cw = G02
    val radius: Double                                      // |start − center|
    val sweepDeg: Double                                    // 진행 방향으로 돈 각 [0, 360). 전체 원은 안 다룸
}
data class Contour(val segs: List<Seg>) {                   // 끊기면(1e-6 넘게) IllegalArgumentException
    companion object { fun polyline(vararg pts: Pt): Contour }
}
object Intersect {                                          // 직선 = 지나는 점 p + 방향 d(무한 직선)
    fun lineLine(p1: Pt, d1: Pt, p2: Pt, d2: Pt): Pt?       // 평행이면 null
    fun lineCircle(p: Pt, d: Pt, c: Pt, r: Double): List<Pt>          // 0~2개, 접하면 1개
    fun circleCircle(c1: Pt, r1: Double, c2: Pt, r2: Double): List<Pt> // 0~2개, 같은 중심이면 0개
}

// NoseComp.kt
enum class Side { G41, G42; companion object { fun of(inner: Boolean, reverse: Boolean): Side } }
sealed interface CompWarning {
    val index: Int                                          // 원래 윤곽의 요소 번호
    data class ArcTooSmall(override val index: Int, val arcR: Double, val noseR: Double) : CompWarning
    data class Unreachable(override val index: Int) : CompWarning
}
data class CompPath(val path: Contour, val warnings: List<CompWarning>)  // segs[i] = 원래 i번, 빠진 자리는 길이 0 Line 또는 꼭짓점 둘레 Arc
object NoseComp {
    fun tipVector(tip: Int, r: Double): Pt                  // 0~9 밖이면 IllegalArgumentException
    fun standardTip(inner: Boolean, reverse: Boolean): Int  // 3 / 4 / 2 / 1
    fun centerPath(c: Contour, r: Double, side: Side): CompPath
    fun tipPath(c: Contour, r: Double, side: Side, tip: Int): CompPath  // = 프로그램 경로
}

// Fmt.kt — 반올림은 여기서만(0.001, 5 는 올림, "-0" 안 냄, NaN·무한대는 IllegalArgumentException)
object Fmt {
    fun nc(v: Double): String       // FANUC 표기: 34 → "34.", -2.186 → "-2.186", 0.5 → "0.5"
    fun fixed3(v: Double): String   // 결과 칸: 34 → "34.000"
}

// NoseR.kt — 닫힌 식(검증·간단 표시용)
object NoseR {
    fun taperDeltaZ(r: Double, angleDeg: Double): Double     // r(1 − tan(θ/2))
    fun taperDeltaXDia(r: Double, angleDeg: Double): Double  // 2r(1 − tan((90° − θ)/2)), 직경값
}
```
- 예: 외경 45° 면취를 0.4R 로 → `NoseComp.tipPath(Contour.polyline(...), 0.4, Side.of(false, false), NoseComp.standardTip(false, false))`,
  보정된 T1 = `path.segs[0].end`, 화면엔 `Fmt.fixed3(p.xDia)`, G코드엔 `"X" + Fmt.nc(p.xDia)`.
- 기능별 계산기(`TaperCalc`, `GrooveCalc`, `RoundCalc`, `RightTriangle`, `G76`, `PolarG12`, `Cutting`)는
  입력 data class → 결과 data class(숫자 + 그릴 `Contour` 들 + 경고 목록)를 돌려주는 순수 함수.
- 경고는 문자열이 아니라 `enum`/sealed 로(화면이 한국어 문구를 고름).
- NC 줄은 `Fmt.kt` 의 `NcLines`(internal)로 만든다: 앞 줄과 같은 축 값은 빼고(모달), 안 움직이는 줄은 버린다.
  원본 06.jpg 처럼 줄마다 `G00`/`G01` 을 쓴다. 이송 F 는 넣지 않는다.
  테이퍼·겹테이퍼는 프로그램을 둘 낸다 — 도면 좌표에 G41/G42 를 거는 프로그램(기본)과 보정 좌표를 G40 으로 쓰는 직접 보정 프로그램
  (테이퍼 절의 "진입점과 NC"). 홈은 보정량이 0 이라 하나(홈 절).
  원호는 `arc(a)` → `G02`/`G03 X Z R` (원본 09.jpg 처럼 R 지령). **90° 넘는 원호는 같은 각으로 나눠 줄마다 90° 이하**로 쓴다:
  제어기는 R 지령의 중심을 현의 가운데에서 √(R² − (현/2)²) 로 다시 구해, 끝점·R 의 0.001 반올림이 중심 위치에서 1/cos(각/2) 배로
  커진다(150° 약 4배, 170° 약 11배 → 0.002~0.004 mm). 모의 계산(R 0.1~60, 무작위 2만 개)으로 다시 그린 원호의 최대 벗어남이
  나누지 않으면 0.16, 120° 조각 0.0014, **90° 조각 0.0007 mm**. 180° 넘는 원호는 R 의 뜻이 둘이라 어차피 나눠야 한다.
  나누면 줄은 늘지만 R 값은 그대로 합성 R(`progR`)이다.
  줄마다 어디서 나왔는지 `NcRef(element, piece, pieces)` 를 함께 남긴다(부르는 쪽이 `element` 를 정한다, 나눈 원호는 조각 번호) —
  라운드의 `ncRefs` · `lineOf` 가 이것으로 요소 → NC 줄 번호를 찾는다(보링바 경고의 줄 번호). 글은 그대로라 테이퍼 NC 는 바뀌지 않는다.

## 테이퍼 계산 (`TaperCalc`, 구현됨)
가공 종류 Outer Dia · Inner Dia (`TaperInput`, TYPE 1~5) 와 Outer/Inner Double Taper (`DoubleTaperInput`).

### TYPE 표 — 경사 앞뒤 요소
T1 = 그림 오른쪽(Z 큰 쪽) 점, T2 = 왼쪽 점. 점 사이는 경사, 앞(T1 쪽)·뒤(T2 쪽)에 요소가 하나씩 붙는다.
단면(수직)은 경사의 오르내림을 이어 간다. 모든 TYPE 에서 단면은 재료가 왼쪽(척 쪽)이다.

| TYPE | 외경 (재료 아래, T2 가 높음 — 5 만 낮음) | 내경 (재료 위, T2 가 낮음 — 5 만 높음) | T1 쪽 | T2 쪽 |
|---|---|---|---|---|
| 1 | 단면 → 면취 → 외경 | 단면 → 입구 면취 → 내경 (원본 06.jpg) | 단면 | 수평 |
| 2 | 단면 → 경사 → 단면 | 단면 → 경사 → 단면 | 단면 | 단면 |
| 3 | 작은 외경 → 테이퍼 → 큰 외경 | 큰 내경 → 테이퍼 → 작은 내경 | 수평 | 수평 |
| 4 | 작은 외경 → 테이퍼 → 어깨 단면 | 큰 내경 → 테이퍼 → 단면(안쪽 바닥) | 수평 | 단면 |
| 5 | 역테이퍼: 척 쪽으로 지름이 작아짐 | 역테이퍼: 안쪽으로 지름이 커짐(외경 5 와 대칭, 원본엔 그림 없음) | 수평 | 수평 |

- 앞뒤 요소의 바깥 끝 = 시작점·끝점(`start`·`end`, 아래 절). 비우면 그림은 5 mm(중심 쪽으로 내려가는 단면은 X0 에서 멈춤)이고 그 요소는 깎지 않는다.
- T 점마다 모서리 R(`cornerR1`…, 아래 절).
- 겹테이퍼: T1 오른쪽, T2 가운데, T3 왼쪽, 경사 둘, 앞뒤 요소는 수평. 세 점의 X·Z 를 모두 넣고 각도는 결과로 나온다
  (빈 X 를 각도로 풀면 오르내림 방향을 알 수 없어서).

### 방향
- 정방향(Forward) = 척 쪽(Z−)으로, 가공 순서는 앞 요소 → T1 → T2 → 뒤 요소. 역방향(Rev.) = 역바이트로 Z+, 순서가 거꾸로.
- 팁 기본 = `NoseComp.standardTip(inner, reverse)` (외경 3·역 4, 내경 2·역 1), `tip` 으로 바꿀 수 있다. 보정 쪽 = `Side.of(inner, reverse)`.
- 형상은 방향을 바꿔도 그대로다(그림을 뒤집지 않음). 그래서 역방향에 맞는 것은 TYPE 5(역테이퍼)이고
  정방향의 TYPE 5, 역방향의 TYPE 1~4 는 `AGAINST_TOOL` 경고가 난다(아래).
  - **화면의 역방향은 이 `reverse` 를 쓰지 않는다**: 화면은 그림을 좌우로 뒤집고(T1 = 왼쪽 점), Z 를 뒤집어 정방향으로 푼 뒤 결과를 되돌린다
    (`ui-design.md` 의 테이퍼·홈 화면 절, `TaperSolveTest` 가 이 `reverse` 와 같은 실물 형상에서 숫자·NC 를 맞춰 본다).

### 빈 값 풀기 — X1·Z1·X2·Z2·각도 가운데 넷
- 하나를 비우면 나머지로 푼다: ΔX(직경) = 2·ΔZ·tanθ, 오르내림 방향은 TYPE 이 정한다. 둘 이상 비면 `MISSING_INPUT`.
- 다섯 다 넣으면 점에서 구한 각도와 0.001° 안이어야 한다(소수 셋째 자리 반올림 허용), 아니면 `CONTRADICTION`.
- 결과의 각도는 늘 점에서 다시 구한 값.

### 보정점
- `NoseComp.tipPath` 의 일반 해법 그대로(앞뒤 요소 오프셋의 교점 + 팁 벡터). T1′·T2′ 는 꺾임점의 보정점.
- 닫힌 식(테스트로 맞춤, θ = 경사각, A = r(1 − tan(θ/2)), B = r(1 + tan(θ/2)), C = r(1 − tan((90° − θ)/2)) 반경):

| 꺾임점 이웃 | 정방향 TYPE 1~4 | 정방향 TYPE 5 | 역방향 TYPE 1~4 | 역방향 TYPE 5 |
|---|---|---|---|---|
| 수평 | Z −A | Z −B | Z +B | Z +A |
| 단면 | X ∓C (외경 −, 내경 +) | — | Z +2r, X ∓C | — |

  - 역방향 TYPE 5 = 정방향 TYPE 3 을 Z 로 뒤집은 것. 역방향 TYPE 1~4 는 T4 인선이 +Z 단면에서 2r 떨어지는 데서 손으로 유도.
  - 아무 꺾임에나 맞는 식: 노즈 중심 = 꼭짓점 + r(n₁ + n₂)/(1 + n₁·n₂) (n = 공구 쪽 단위 법선). 겹테이퍼 가운데 점 검증에 쓴다.
- `TaperCalcTest` 가 외경/내경 × TYPE 1~5 × 정/역 × 5°~85° × r 0.2~1.2 와 겹테이퍼 5모양 × 정/역에서
  ① 따로 짠 오프셋 직선 교점 ② 이등분선 식 ③ 위 표와 1e-9 안으로 맞춰 본다.
  손 계산: 외경 TYPE 1 X40 Z0 / X50 Z-5 / 0.4R → T1′ X39.531 Z0, T2′ X50 Z-5.234,
  원본 06.jpg(내경 TYPE 1 X40 Z0 / X34 Z-2) → T2′ X34.000 Z-2.186.
- 일부러 틀린 식 8가지(진입 볼록/오목 뒤집기, TYPE 5 무시, TYPE 표 틀림, 바이트 반대 판정 느슨, 역방향 팁·점 번호,
  홈 마지막 단·나눗셈 오차)를 테스트가 모두 잡는 것을 확인했다.

### 모서리 R (`cornerR1`·`cornerR2`(·`cornerR3`), 기본 0)
- T 점마다 모서리 R. 0 이면 날카로운 꼭짓점(전과 같은 결과 — 테스트 `zeroCornerRIsSharp`). 넣으면 그 꼭짓점에
  앞뒤 요소와 **접하는 원호**를 끼워 윤곽이 선–원호–선이 된다. 라운드R 계산과 같은 함수 `Corners.insert`(internal)를 쓴다:
  모서리 원 중심 = 꺾이는 쪽에서 두 요소를 R 만큼 민 선·원의 교점, 접점은 중심에서 각 요소에 내린 발.
- 보정은 `NoseComp` 일반 해법 그대로 — 볼록 R 은 보정 R = R + r, 오목 R 은 R − r. 오목 R ≤ r 이면 원호가 없어지고(`progR` = null)
  `CONCAVE_R_LE_NOSE`(R = r 은 꼭 맞아 정확, R < r 은 그 자리에 r 이 남는다. G41/G42 프로그램은 기계가 간섭 알람을 낼 수 있다).
- 안 들어가면 `CORNER_R_TOO_BIG` 로 멈춘다: 접점까지 거리가 경사나 앞뒤 요소(시작·끝점까지)보다 길 때, 두 R 의 접점이 경사 위에서 겹칠 때.
- 결과 `TaperPoint`: `vertex`(이론 꼭짓점), `nodes`(R 없으면 하나, 있으면 R 시작·끝 접점 둘 — 가공 순서, 도면·보정 좌표),
  `corner`(`CornerArc`: 도면 R, 보정 R, 가공 순서의 방향 cw = G02, 중심).
- 손 계산(`convexCornerR`): 외경 TYPE 1 X40 Z0 / X50 Z-5, T1 에 R1, 0.4R → 접선 길이 R·tan22.5° = 0.414,
  R 시작 X39.172 Z0 · 끝 X40.586 Z-0.293, G03, 보정 R 1.4 → 보정 접점 X38.372 Z0 · X40.351 Z-0.41.
  오목(`concaveCornerR`, TYPE 3 T1 R1): G02, 보정 R 0.6, 보정 접점 X40 Z0.014 · X40.351 Z-0.41.
- `GeometricVerifyTest` 의 무작위 기하 검사(테이퍼·겹테이퍼)에 모서리 R(볼록·오목, R < r · R = r · R > r)을 넣어
  파고듦·덜깎음이 없음을 본다.

### 시작점·끝점 (`start`·`end`, 앞뒤 요소의 바깥 끝 좌표)
- 앞(T1 앞)·뒤(T2 뒤, 겹테이퍼는 T3 뒤) 요소가 **단면이면 X(직경), 수평이면 Z** 를 받는다. 예: 내경 TYPE 1 의 끝점 = 구멍 깊이 Z-20.
- 비우면 T 점에서 5 mm(`LEG_DEFAULT`, 단면은 반경 5 — 중심 쪽 단면은 X0 에서 멈춤) 떨어진 곳으로 그린다. **그 요소는 넣었을 때만 깎는다**
  (현장 요청 2026-10-20): 끝점을 비우면 마지막 꺾임점(모서리 R 이면 그 끝)′ 에서 끝나고(`toEnd` = false), 화면 끝점 칸의 자동 값은 그 도면 점이다.
- 잘못된 값은 `BAD_LEG` 로 멈춘다: T 점의 반대쪽이거나 같은 자리, X < 0, 그리고 노즈R 보정 때문에 생기는 두 경우 —
  ① 보정점이 이미 그 좌표를 지나 공구가 되돌아가야 할 때, ② 인선을 그 좌표에 두면 노즈가 그 요소에 한 번도 닿지 않을 때
  (요소가 노즈R 쯤보다 짧다. 예: 외경 TYPE 3 의 끝 Z 가 T2 에서 0.3, 0.4R → 노즈 중심이 Z-4.9 라 외경에 안 닿는다).
- **끝점에서는 인선이 끝좌표에 멈춘다**(Haas ST/TL 워크북 56쪽 O00055 `N14 Z-2.375`, 53쪽 O00052 처럼 — 척·턱 가까운
  실제 끝좌표를 넣으므로 r 만큼 더 들어가면 안 된다). 직접 보정 프로그램의 끝 점 = 끝 요소의 보정선 위에서 끝점에 내린 발.
  표준 팁이면 노즈가 끝좌표를 넘지 않는다(대신 끝좌표 앞 r 은 노즈 둥근 부분이 지나가 그 요소를 덜 깎는다).
- 시작점을 넣으면 그 요소부터 깎는다(인선을 시작 좌표에 두고, 수평이면 그 연장선, 단면이면 공구 쪽에서 들어감).
  비우면 그 요소는 깎지 않고(그림·재료로만) 진입점에서 첫 꺾임점′ 으로 바로 간다(`fromStart` = false).
- 역방향(`reverse`)은 가공이 끝점 쪽에서 시작하므로 시작점·끝점의 구실이 바뀐다.

### 진입점과 NC — 두 프로그램
**기본 = G41/G42 프로그램**(`ncG41G42`): 좌표는 **도면 좌표**, 기계가 보정한다. 보정 좌표에 G41/G42 를 다시 걸면 r 만큼
두 번 밀리므로(이중 보정) 절대 섞지 않는다. **두 번째 = 직접 보정 프로그램**(`ncG40`): 앱이 계산한 보정 좌표(가상 인선)를
G40 상태로 쓴다(모서리 원호는 보정 R = R ± r).
- G41/G42 프로그램 순서(Haas ST/TL 워크북 62쪽 APPROACH·DEPARTURE MOVES, 66쪽, 60쪽 보정 쪽):
  1. 첫 줄 주석 `(R0.4 T3)` = 기계 공구 보정 화면의 R 칸(노즈R = 공구 반경)·T 칸(팁 번호)에 넣을 값.
  2. `G00` 진입점(G40 상태 — 인선이 그 점에 있다).
  3. `G42 G01 …`(외경 정방향 G42 · 역방향 G41, 내경 정방향 G41 · 역방향 G42 = `Side.of`) — **보정 시작은 직선 이동에서만**,
     이 이동 끝에서 보정이 다 걸린다. 길이 ≥ c = max(zClear, r).
  4. 형상(도면 좌표, 모서리 R 은 `G02`/`G03 X Z R`(도면 R)).
  5. `G40 G00` 후퇴 — 보정 취소도 직선 이동에서(원호 블록 안 됨), 길이 ≥ r(66쪽 "twice the tool radius" 는 직경값).
- 시작 이동은 **첫 형상 요소와 한 직선**(수평 시작 요소의 연장선, 비운 시작의 경사 연장선·시작 요소선)이거나,
  **단면에 공구 쪽에서 직각으로** 들어간다(안쪽 모서리 = 두 보정선의 교점). 그래서 시작 이동 끝의 보정점이 직접 보정 프로그램과 같다.
- **G00 점의 노즈**: G40 상태에서 인선이 선 위에 있으면 노즈 중심 = 점 − 팁 벡터라 선에서 −(n·팁 벡터) 떨어진다(n = 공구 쪽 법선).
  표준 팁이 제 쪽 요소를 따라가면 r 이상이지만, 바이트 반대쪽 요소(`AGAINST_TOOL` — 예: 역바이트 T4 로 +Z 를 보는 단면)나
  다른 팁이면 노즈가 선을 넘어 재료에 박힌다. 그때(lift = r + n·팁 벡터 > 0)는 G00 점을 공구 쪽으로 c + lift 띄우고,
  그 선에 직각으로 `G4x G01` 한 뒤 선을 따라 `G01` 로 간다. 직각 진입 끝의 노즈는 교점 규칙대로 선을 따라 r 앞에 서므로
  선 위 점을 r 뒤에 두어 그때의 인선이 직접 보정 진입점과 같게 한다. 후퇴점도 같은 lift 만큼 공구 쪽으로.
  볼록 첫 꺾임의 경사 연장선은 재료가 아니라 띄우지 않는다.
- 직접 보정 프로그램: `G00` 진입점 → (단면 시작이면 시작점′) → 보정점들(원호는 보정 R) → 끝점′ → `G00` 후퇴.
  진입점은 첫 꺾임점(정방향 T1′, 역방향 T2′ — 겹테이퍼는 T3′)에서 바깥으로(시작점을 비웠을 때):
  - 그 꺾임이 **볼록**(재료 모서리가 공구 쪽으로 나옴: 정방향 TYPE 1·2·5, 역방향 TYPE 1·3)이고 모서리 R 이 없으면
    **보정 경사선을 연장**한 위, Z 로 c 바깥. 경사각을 따라가므로 X 가 정확하다.
  - **오목**이거나 모서리 R 이 있으면 수평 시작 요소는 **그 보정선**을 따라 c 바깥, 단면은 **공구 쪽에서 Z 로** c(G41/G42 는 단면 선 위
    lead 에 직각으로 들어가 선을 따라 첫 T 점으로 — 노즈가 교점 규칙대로 lead 보다 r 앞, 곧 직접 보정의 첫 깎는 점에 선다).
  - 그래서 진입은 늘 첫 깎는 점(직접 보정은 첫 보정점·시작점′, G41/G42 는 첫 도면점)에서 **Z 로 c = max(Z 여유, r)** 떨어진다
    (현장: "Z 여유를 바꿔도 G00 이 따라오지 않는다" — 테스트 `entryFollowsZClearance`). Z 여유 0 도 된다(진입 거리 = r), 노즈R 과 Z 여유가
    둘 다 0 이면 진입 이동이 없어 `BAD_INPUT`.
  - 진입점이나 후퇴점이 X0 아래로 가면 `ENTRY_PAST_AXIS`.
  - 후퇴(끝점을 넣었을 때): 끝 요소가 수평이면 공구 쪽으로 직각, 단면이면 그대로 이어서 c(직각으로 빼면 G41/G42 제어기가 안쪽 모서리 교점을 잡아 단면 끝을 덜 깎는다).
  - 후퇴(끝점을 비웠을 때): 직접 보정은 마지막 보정점에서 끝 요소의 공구 쪽으로 c. G41/G42 는 시작과 거울로 마지막 꺾임이 날카로운 볼록이면
    경사를 Z 로 c 더 늘인 점, 아니면 끝 요소 선을 따라 c(제어기가 끝 요소와의 안쪽 모서리 교점을 잡는다). 바이트 반대쪽 모양이면 노즈가
    꺾임을 벗어나게 경사 쪽은 lift, 끝 요소 쪽은 직접 보정의 마지막 점이 꺾임에서 끝 요소를 따라 간 만큼 더 간다.
- 두 프로그램의 끝 블록은 일부러 다르다: G41/G42 는 마지막 블록 끝에서 제어기가 그 요소의 법선으로 보정해 **노즈 접점**이 끝좌표에
  오고(표준 팁이 수평 끝 요소를 따라가면 인선은 r 더 감 — 뒤에 어깨가 있으면 깎아 들어간다), 직접 보정은 **인선**을 끝좌표에 멈춘다.
  화면 도움말·NC 카드 아래에 한 줄로 알린다.
- 테스트 `g41g42ProgramCompensatesToDirectProgram`(외경/내경 × 정/역 × TYPE 1~5 × 3각 × r 2가지 × 모서리 R 4가지 × 시작점 유무,
  겹테이퍼 3모양): G41/G42 글이 도면과 0.001 안이고, 그 도면을 `NoseComp` 로 보정(시작 이동 끝에서 보정이 다 걸림)한
  점들이 직접 보정 글과 0.001 안에서 같다. 끝 점만 (G41/G42) − (직접 보정) = u·(팁 벡터·u).
  (보정은 반올림 전 도면으로 한다 — 글을 다시 읽은 원호는 접선이 0.001 만큼 틀어져 거의 접한 이음이 흔들린다.)
  `GeometricVerifyTest` 는 G41/G42 글의 형상이 도면 위에 있고 도면 꼭짓점을 모두 지나는지, 시작·취소 이동 ≥ r, 표준 팁이면
  진입점·후퇴점(G40 상태)에서 노즈가 재료 밖인지 무작위로 본다.
- 손 계산(`outerType1Numbers`, 외경 TYPE 1 X40 Z0 / X50 Z-5 / 0.4R, 시작·끝점 비움):
  - G41/G42: `(R0.4 T3)` `G00 X38. Z1.` `G42 G01 X40. Z0.` `G01 X50. Z-5.` `G40 G00 X52. Z-6.`
  - 직접 보정: `G00 X37.531 Z1.` `G01 X39.531 Z0.` `G01 X50. Z-5.234` `G00 X52.`
  - 끝점 Z-10: … `G01 X50. Z-5.` `G01 Z-10.` `G40 G00 X52.` / 직접 보정 … `G01 X50. Z-5.234` `G01 Z-10.` `G00 X52.`
  - 시작점 X30(단면부터): `G00 X30. Z1.` `G42 G01 Z0.` `G01 X40.` … / 직접 보정 `G00 X30. Z1.` `G01 Z0.` `G01 X39.531` …
  - 띄운 진입 예(외경 TYPE 2 역방향 X60 Z0 / X64.368 Z-6, 0.8R, `AGAINST_TOOL`): `G00 X69.088 Z-3.4` `G41 G01 Z-6.`
    `G01 X64.368` `G01 X60. Z0.` `G01 X50.` `G40 G00 X48. Z1.6`.

### 경고 `TaperWarning`
- 계산을 멈춤(solution = null, 경고 하나): `MISSING_INPUT`, `CONTRADICTION`, `BAD_ANGLE`(0° 이하·90° 이상, 점이 수평·수직),
  `SHAPE_MISMATCH`(T2 가 T1 왼쪽이 아님, 오르내림이 TYPE 과 반대), `BAD_INPUT`(지름 ≤ 0, r·모서리 R < 0, 여유 ≤ 0, 팁 0~9 밖, NaN, 크기 한도 밖),
  `BAD_LEG`(시작·끝점, 위 절), `CORNER_R_TOO_BIG`(모서리 R, 위 절),
  `NOSE_UNREACHABLE`(경사나 앞뒤 요소의 어느 점에도 노즈 원이 닿지 못함 — NoseComp 의 Unreachable·이을 수 없음.
  한쪽 끝이 볼록이어도 다른 끝 오목 모서리에 접한 원이 요소 밖에서 닿으면 그렇다. 예: 외경 TYPE 5 역방향 경사 0.627 에
  1.2R — 오목 T2(101.7°)에 접한 원이 경사 위 0.976 에서 닿는다. 겹테이퍼 T1 볼록–T2 골(74.3°) 경사 1.221 에 1.2R — 1.58 에서 닿는다).
- 숫자와 함께: `NOSE_GT_SLOPE`(r > 경사 길이. 공구는 경사에 닿고 숫자는 맞다 — 못 닿으면 이 경고가 아니라 `NOSE_UNREACHABLE`.
  오목 모서리 쪽엔 r 이 남아 경사가 거의 안 남을 수 있어 도면 확인용. 예: C0.3 면취에 0.8R 은 양 끝이 볼록이라 다 깎인다),
  `CONCAVE_R_LE_NOSE`, `AGAINST_TOOL`, `ENTRY_PAST_AXIS`.
- `AGAINST_TOOL` 판정: 가상 인선은 Z·X 축에 평행한 두 접선이 날이라는 가정이다. 도면 요소의 노즈 접점이 팁 벡터 ±45°
  사분면 밖이면 실제 바이트의 끝날·여유각이 맞는지 따로 봐야 한다(역테이퍼는 끝날 여유각 > 경사각이어야 깎인다).
  팁 1~4 일 때만 본다.

### API
```kotlin
enum class TaperWarning { MISSING_INPUT, CONTRADICTION, BAD_ANGLE, SHAPE_MISMATCH, BAD_INPUT, BAD_LEG, CORNER_R_TOO_BIG,
                          NOSE_UNREACHABLE, /* ↑ 멈춤 · ↓ 숫자와 함께 */ NOSE_GT_SLOPE, CONCAVE_R_LE_NOSE, AGAINST_TOOL, ENTRY_PAST_AXIS }
data class TaperInput(val inner: Boolean = false, val type: Int = 1 /*1~5*/, val reverse: Boolean = false,
    val noseR: Double = 0.4, val tip: Int? = null /*null = 표준*/,
    val x1: Double? = null, val z1: Double? = null, val x2: Double? = null, val z2: Double? = null, // X 직경
    val angleDeg: Double? = null, val zClear: Double = 1.0,
    val cornerR1: Double = 0.0, val cornerR2: Double = 0.0,
    val start: Double? = null, val end: Double? = null)  // 앞뒤 요소 바깥 끝: 단면이면 X(직경), 수평이면 Z. null = 안 깎음(그림 5 mm)
data class DoubleTaperInput(val inner: Boolean = false, val reverse: Boolean = false, val noseR: Double = 0.4,
    val tip: Int? = null, val x1/z1/x2/z2/x3/z3: Double? = null, val zClear: Double = 1.0,
    val cornerR1/cornerR2/cornerR3: Double = 0.0, val start: Double? = null, val end: Double? = null)  // start·end 는 Z
data class CompPoint(val drawn: Pt, val prog: Pt) { val dXDia: Double; val dZ: Double }  // 보정 − 도면
data class CornerArc(val r: Double, val progR: Double? /*R ± r, 없어지면 null*/, val cw: Boolean /*G02*/, val center: Pt)
data class TaperPoint(val vertex: Pt, val nodes: List<CompPoint> /*1 또는 R 시작·끝 2*/, val corner: CornerArc?)
data class TaperSolution(
    val points: List<TaperPoint>,    // T1, T2(, T3) 그림 오른쪽부터. 비웠던 값도 vertex 에 채워짐
    val anglesDeg: List<Double>,     // 경사마다
    val tip: Int, val side: Side, val entry: Pt, val exit: Pt /*직접 보정의 G00 점*/, val fromStart: Boolean, val toEnd: Boolean,
    val ncG41G42: List<String>, val ncG40: List<String>,
    val drawing: Contour, val center: Contour, val program: Contour)  // 가공 순서, segs[k] 끼리 대응
data class TaperResult(val solution: TaperSolution?, val warnings: List<TaperWarning>)
object TaperCalc {
    val NOSE_R_CHOICES: List<Double>   // 0.2, 0.4, 0.8, 1.2
    const val LEG_DEFAULT = 5.0
    fun calc(i: TaperInput): TaperResult           // type 이 1~5 밖이면 IllegalArgumentException(프로그램 실수)
    fun calcDouble(i: DoubleTaperInput): TaperResult
}
```

## 홈 계산 (`GrooveCalc`, 구현됨)
Outer Groove. 홈 바이트(폭 T)로 오른쪽 벽 `zStart` 에서 왼쪽으로 폭 W, 외경 D → 바닥 지름 d.
- **기준 인선**(`GrooveRef`): `LEFT`(왼쪽 = 척 쪽 모서리, 기본 — 소재 끝면에 Z 를 맞추면 닿는 쪽) / `RIGHT`.
  공구 보정 화면의 팁 번호는 LEFT 3, RIGHT 4 (Haas 워크북 76쪽: 홈 바이트 오른쪽 모서리 = "Tip Direction 4").
  **팁 8(아래 가운데)은 노즈 중심 바로 아래 점**이라 폭 = 2r 인 풀R 인서트에서만 날 가운데와 맞는다 — 보통 홈 바이트엔 안 맞다.
- **노즈R 보정량은 0**: 벽·바닥이 Z·X 축에 평행해 기준 모서리의 가상 인선이 도면선 위에 그대로 놓인다.
  오른쪽 벽은 오른쪽 모서리가 깎으므로 LEFT 기준이면 Z = 벽 − T. 바닥 두 모서리에는 인서트 노즈R 만큼 R 이 남는다.
  그래서 입력에 노즈R 이 없다.
- **단 나누기**: 피치 = T − 겹침(기본 겹침 0.5). 첫 단은 날 오른쪽이 오른쪽 벽에, 그다음 피치씩 왼쪽으로,
  **마지막 단은 날 왼쪽이 왼쪽 벽에 꼭 맞춘다**(마지막 간격만 짧다 — G75 의 K 와 같은 방식, Haas 워크북 106쪽).
  단 수 = 1 + ⌈(W − T)/피치⌉ (딱 나눠떨어질 때 단이 하나 더 생기지 않게 1e-9 를 뺀다). 모든 단의 목표 X = d.
  단 수는 Double 로 `MAX_STEPS`(1000) 와 견준 뒤 Int 로 바꾼다 — 겹침이 T 에 아주 가까우면 Int 를 넘쳐 음수가 되어 죽었다.
- **벽·바닥 정삭**(`finish`): 오른쪽 벽 위 → 바닥(X d) → 왼쪽 벽 → 위. 다단 진입 사이 바닥에 남는 작은 턱
  (겹침 < 2r 일 때)을 이것으로 깎는다.
- NC: 단마다 `G00 X(D+2·xClear) Z` → `G01 X d` → `G00 X(D+2·xClear)`, 끝에 정삭 `G00 Z` → `G01 X` → `G01 Z` → `G01 X`.
- **NC 는 하나**(G40, 기준 인선 좌표)이고 G41/G42 프로그램은 내지 않는다: 벽·바닥이 축에 평행해 기준 모서리의 보정량이 0 이라
  G41/G42 를 걸어도 좌표가 같고, 반대쪽 벽은 다른 모서리(폭 T 떨어짐)가 깎는데 공구 보정 하나(R·팁)로는 두 모서리를 맡을 수 없어
  도면 좌표로 쓰면 그 벽이 T 만큼 틀린다. 화면 NC 카드 아래에 이 이유를 한 줄로 적는다.
  예: W8 T3 겹침 0.5 D40 d30 zStart −10 LEFT → Z −13, −15.5, −18.
- 경고 `GrooveWarning`(모두 계산을 멈춤, 침범 경고는 한꺼번에): `MISSING_INPUT`, `BAD_INPUT`,
  `TOOL_WIDER_THAN_GROOVE`(T > W), `BOTTOM_NOT_BELOW_OD`(d ≥ D), `OVERLAP_TOO_BIG`(겹침 ≥ T), `TOO_MANY_STEPS`(1000 단 넘음).

```kotlin
enum class GrooveRef(val tip: Int) { LEFT(3), RIGHT(4) }
enum class GrooveWarning { MISSING_INPUT, BAD_INPUT, TOOL_WIDER_THAN_GROOVE, BOTTOM_NOT_BELOW_OD, OVERLAP_TOO_BIG, TOO_MANY_STEPS }
data class GrooveInput(val zStart: Double? = null, val width: Double? = null, val od: Double? = null,
    val bottom: Double? = null, val insert: Double? = null,   // X 는 직경
    val overlap: Double = 0.5, val ref: GrooveRef = GrooveRef.LEFT, val xClear: Double = 1.0 /*반경*/)
data class GrooveStep(val z: Double, val xDia: Double)        // 기준 인선의 프로그램 Z, 목표 X
data class GrooveSolution(val steps: List<GrooveStep>, val pitch: Double, val retractXDia: Double,
    val nc: List<String>, val drawing: Contour /*홈 모양*/, val finish: Contour /*정삭 경로(프로그램 점)*/)
data class GrooveResult(val solution: GrooveSolution?, val warnings: List<GrooveWarning>)
object GrooveCalc { const val MAX_STEPS = 1000; fun calc(i: GrooveInput): GrooveResult }
```

## 라운드R 계산 (`RoundCalc`, 구현됨)
원본 04.jpg 의 TYPE 1~6(외경, 척 쪽으로 깎음 = G42, 팁 기본 3), 같은 치수의 구멍인 내경 TYPE 2~6(`inner`, 보링 G41 · 팁 2 — 아래 "내경" 절)과
수동 모드(CAD 3점호, `arc3`, 외경 · 내경 · 정/역방향). 내경 경로를 따라 보링바가 소재에 닿는지는 `HolderCheck`(아래 "보링바 간섭 검사" 절).
**접점 모드와 합성 R 궤적은 같은 결과의 두 보기**다(화면의 보정 방식 스위치 — 기계 보정 = 접점, 직접 보정 = 합성 R): 접점 = `drawing`·`nodes[].drawn`,
합성 R = `center`·`program`·`nodes[].prog`·`arcs[].progR`·`nc`(화면은 첫 줄에 주석 `(G40 DIRECT R… T…)` 을 붙인다).

### 치수 해석 (그림을 보고 정함)
- Z0 = 부품 오른쪽 끝, X 는 직경값. 모든 TYPE 이 축 위 (Z0, X0) 에서 시작해 왼쪽으로 간다(끝 면 또는 돔 끝). 내경은 Z0 = 구멍 입구 끝면(아래 "내경" 절).
- **Main R = 중심이 축 위인 구**. 공·돔·구면을 돌려 깎으니 중심이 축에 있어야 끝이 매끈하고, TYPE 1(지름 칸 없음)·
  TYPE 6(돔 밑동 지름 칸 없음)은 이렇게 봐야만 치수가 맞는다. 그래서 TYPE 2~5 는 R·지름·길이 가운데 하나가
  나머지로 정해진다 — 그 묶음은 **하나를 비워도 되고**(채운 값은 `filled`), 다 넣으면 0.001 mm 안에서 맞아야 한다
  (`CONTRADICTION`, 도면 치수를 0.001 로 반올림해 넣어도 되게). 다 넣으면 R 과 지름을 믿고 길이를 다시 구한다.
- 길이(L·L1·L2)는 모서리 R 을 넣기 **전** 이론 꼭짓점 기준. 그림에 R 이 없는 모서리(끝 면–축, 플랜지 모서리)는 날카롭다.
  Corner R = 0 이면 그 모서리도 날카롭다(빈칸은 `MISSING_INPUT` — 빠뜨린 R 을 날카로운 모서리로 깎지 않게).
  접해서 이어지는 곳(반구 R = D/2, TYPE 5 의 R = D2/2)은 모서리가 없어 Corner R 을 무시한다.
- 끝의 원통·축·플랜지 윗면 길이는 `legLen`(기본 5) — 그림과 NC 마지막 줄에만 쓰인다. 내경 3~6 은 이것이 입구 구멍 깊이(실제 치수)다.

| TYPE | 윤곽(가공 순서) | 칸 → 필드 | 비울 수 있는 묶음 | 식 (a = D1/2, b = D2/2, h = D/2) |
|---|---|---|---|---|
| 1 술통 | 오른쪽 면 → CR → Main R → CR → 왼쪽 면(X0 까지) | Main R, Corner R(양쪽 같음) → `cornerR1`, L(두 면 사이) → `l1` | 없음 | 중심 (−L/2, 0), 면 높이 √(R² − (L/2)²), L < 2R |
| 2 아령 | 끝 면 → 축 D1·L1 → CR(1) 오목 → 공 → CR(1) 오목 → 축 D1 | Main R, Corner R(1)(두 이음점 같음), D1, L1, L2 + `l2Ref` | R·D1·L2 | L2(B) = 이음점 사이 = 2√(R² − a²), L2(A) = 왼쪽 이음점 ~ 오른쪽 끝 = L1 + L2(B), 중심 (−L1 − L2(B)/2, 0) |
| 3 돔 | 돔 끝 → Main R → CR 볼록 → 원통 D | Corner R → `cornerR1`, Main R, D → `d1`, L(이음점 ~ 끝) → `l1` | R·D·L | 중심 (−R, 0), L = R − √(R² − h²), L ≤ h |
| 4 공 | 공 끝 → Main R(꼭대기 넘어) → CR 오목 → 축 D | 3 과 같음 | R·D·L | L = R + √(R² − h²), L ≥ h |
| 5 | 끝 면 → 축 D1·L1 → CR(1) 오목 → Main R 볼록 → CR(2) 볼록 → 원통 D2 | CR(1) → `cornerR1`, CR(2) → `cornerR2`, Main R, D1, D2, L1, L2(이음점 사이) | R·D2·D1·L2 | u = √(R² − a²), v = √(R² − b²), 중심 (−L1 − u, 0), L2 = u − v (중심이 큰 원통 이음점 왼쪽) |
| 6 | 끝 면 → 축 D1·L1 → CR(1) 오목 → 돔 → CR(2) 오목 → 플랜지 면 → 플랜지 윗면 D2 | 5 와 같음, L2 = 플랜지 면 ~ 축 이음점 | 없음 | 중심 (−L1 − u, 0), 면 z = −L1 − L2, 돔 밑동 = 구가 면을 자르는 높이(입력 아님) |

- TYPE 5 에서 R 을 비우면: u + v = (b² − a²)/L2, u − v = L2 → R = √(u² + a²). L2 > √(b² − a²) 이면 v < 0 이라 모양이 아니다.
- TYPE 6 은 중심이 플랜지 면 오른쪽이어도(돔이 꼭대기를 넘어 면에 닿음) 받는다. 돔의 가장 높은 곳이 플랜지보다 낮아야 한다.

### 접점 모드 (도면 윤곽)
- 이론 윤곽(직선 + 주 원호)의 꼭짓점마다 모서리 R(rc)을 접하게 끼운다. 모서리 원 중심은 꺾이는 쪽에서 직선과 rc,
  원과는 R − rc(그 원 안쪽) 또는 R + rc(바깥) 떨어진 두 자취의 교점 — 직선·경사선·원 어느 조합이든 같은 방법이다.
  접점은 직선 위 수선의 발, 원 위에서는 원 중심과 모서리 원 중심을 잇는 선 위. 모서리 원호 방향은 꺾이는 쪽(왼쪽 = G03).
- 교점이 둘이면 꼭짓점에 가까운 것부터, 접점이 두 요소 안에 있고 접선 방향이 같은 것을 고른다. 없거나 이웃 요소를
  다 먹으면 `CORNER_R_TOO_BIG`.
  이 끼우기는 테이퍼와 같이 쓰는 `Corners.insert`(internal, `Corners.kt`)다.
- `RoundCalcTest` 가 TYPE 마다 닫힌 식(모서리 원 중심 = 직선 높이 h ∓ c, 구 중심에서 √((R ∓ c)² − …))으로 따로 구한
  접점과 1e-9 안으로 맞춰 보고, 원호가 낀 이음점마다 접선 방향이 이어지는지 본다.

### 합성 R 궤적 (보정)
- `NoseComp` 일반 해법 그대로: 원호는 같은 중심, 볼록 R + r · 오목 R − r. 프로그램 경로 = 노즈 중심 궤적 + 팁 벡터라
  프로그램 원호의 R 도 R ± r(`RoundArc.progR`, NC 의 R).
- 오목 R − r ≤ 0 → `CONCAVE_R_LE_NOSE`(숫자는 낸다, `progR = null`). R = r 은 정확하다(원호가 점이 된다).
  R < r 이면 그 자리에 도면보다 큰 R(= r)이 남는다.
  R < r 로 빠진 원호 다음의 짧은 요소까지 못 닿으면(예: TYPE 6 의 CR2 < r 뒤 짧은 플랜지 면) `NOSE_UNREACHABLE` 로 멈춘다.
- `RoundCalcTest` 는 노즈 중심 궤적을 촘촘히 찍어 도면 어디에도 r 보다 가까이 가지 않는지(파고듦), 요소마다 그 직선·원에서
  정확히 r 인지 따로 짠 거리 계산으로 본다(TYPE 1~6 대표 18모양 × r 0~1.2).
- 첫 점이 축 위라 첫 보정점은 X = −2r(팁 3) — 노즈 중심이 축까지 가야 끝 면·돔 끝이 다 깎인다.
- 손 계산(TYPE 3, Ø20, SR12.5 → L = 12.5 − √(12.5² − 10²) = 5, CR1, 0.4R, 팁 3):
  `G00 X-0.8 Z1.` `G01 Z0.` `G03 X19.391 Z-4.87 R12.9` `G03 X20. Z-5.741 R1.4` `G01 Z-10.4`.
- 일부러 틀린 식 12가지(모서리 원 안/밖, 모서리 원호 방향, 돔/공 근, L2 A/B, R = r 경고, 원호 90° 검사, 3점 방향,
  TYPE 6 면 높이 부호, 모서리 R 무시, 180° 나누기, 삼각형 식 둘)를 테스트가 모두 잡는 것을 확인했다.

### 수동 모드 (CAD 3점호, `arc3`)
- 세 점(시작 → 가운데 → 끝, 가공 순서) → 중심 = 두 현의 수직이등분선 교점, 방향 = 세 점의 돌림(시계면 G02).
- 끝 Z < 시작 Z 면 정방향, 크면 역방향(같으면 방향을 몰라 `SHAPE_MISMATCH`). 보정 쪽 `Side.of(inner, reverse)`,
  팁 기본 `standardTip(inner, reverse)`. 진입은 첫 보정점에서 Z 로 zClear 바깥(역방향이면 −Z 쪽).
  그 방향이 시작점의 공구 쪽 법선과 반대면(n·e < 0: 시작 접선이 재료로 가파르게 내려가는 오목 홈 등) Z 로 물러난 노즈 원이
  시작점 옆을 판다(X40 Z0 → X35 Z−3 → X40 Z−6, 0.4R 에서 0.33 mm). 그때는 **시작 접선을 거꾸로 따라 zClear 만큼** 진입한다 —
  노즈 원이 시작 접선에 접한 채 들어온다.
- 세 점이 한 줄·겹침 → `SHAPE_MISMATCH`. 오목 R < r → `NOSE_UNREACHABLE`(원호 하나뿐이라 남는 요소가 없다).

### 내경 (`inner`, TYPE 2~6)
같은 치수의 구멍을 보링바(팁 2 · G41 = `standardTip(true, false)` · `Side.of(true, false)`)로 입구에서 안쪽(Z−)으로 깎는다.
Z0 = 구멍 입구 끝면, X 는 직경값. TYPE 1 은 없고(내경이면 `IllegalArgumentException`), 입구 R 처럼 TYPE 에 없는 구멍은 3점호 내경으로 넣는다
(사용자 결정 2026-10-20 D1 · D9 — 결정 표는 `stage2-report.md` 8절).
- **도면 = 외경 도면을 Z 로 뒤집은 것**(`innerOf`). 외경 TYPE 의 이론 윤곽에 모서리 R 까지 끼운 도면(`Corners.insert`)에서 만들므로
  치수 해석 · 비울 수 있는 묶음 · `filled` · `CORNER_R_TOO_BIG` 은 외경과 같다.
  - TYPE 3~6: K = 외경 도면 끝의 깊이(= −마지막 점 z, 외경의 끝 원통 · 축 끝), m(z, x) = (−z − K, x). 요소를 거꾸로 가며
    `Line(m(끝), m(시작))` · `Arc(m(끝), m(시작), m(중심), cw)` — Z 뒤집기와 거꾸로 가기가 각각 원호 방향을 한 번씩 바꿔 cw 는 그대로다.
    외경의 끝 원통 · 축(`legLen`)이 구멍 입구가 되고, 외경의 끝 면 · 돔 끝이 구멍 바닥(축 위)이다.
  - TYPE 2: 외경 도면에서 첫 요소(끝 면)만 뺀다 — 구멍은 입구 (Z0, D1/2) 에서 시작하고 z 는 그대로(L2 A/B 도 입구에서 잰다).
    마지막 축 D1 이 공 뒤 구멍(`legLen`, 그림 · 마지막 NC 줄만).
  - 관통(`through`, 5 · 6 만 — 사용자 결정 D3 '막힘 + 관통 스위치'): 마지막 요소(바닥 면)를 뺀다 — 작은 구멍 D1 이 L1 끝에서 그대로 끝난다.
    외경이나 다른 TYPE 에 주면 `IllegalArgumentException`.
  - 재료가 구멍 바깥이라 모서리 R 의 볼록 · 오목은 저절로 바뀐다(보정 R − 도면 R 의 부호가 외경과 반대 — `convexityFlips`).
    그래서 내경 4 의 CR 0.3 < r 0.4 는 볼록이라 `CONCAVE_R_LE_NOSE` 가 없고(외경 4 는 있다), 내경 3 의 CR = r 은 오목이라 있다.
- **`legLen`**: 내경 3~6 은 **입구 구멍 깊이** — Z0 에서 첫 이론 꼭짓점까지, 실제 치수다(사용자 결정 D2). 내경 2 는 공 뒤 구멍 길이(그림 · 마지막 줄만).
- **진입 · NC**는 외경과 같다: 첫 보정점에서 Z+ 로 max(zClear, r) 인 `G00` → `G01` 첫 점 → 요소마다. 끝에 빠지는 블록은 넣지 않는다
  (사용자 결정 D4, 외경과 같다).
- **축 넘음**: 노즈 중심 경로의 가장 낮은 x(원호가 맨 아래 −90° 를 지나면 중심 x − R)가 −1e-9 보다 낮으면 `NOSE_UNREACHABLE` —
  구멍 지름이 2r 보다 작아 노즈가 맞은편 벽을 판다(예: TYPE 3 R0.5 · Ø0.6 · 0.4R). 3점호 내경도 같다.
- **AGAINST_TOOL 은 외경과 같다**: 외경 요소의 바깥(공구 쪽) 법선을 n = (nz, nx) 라 하면 노즈 중심 → 접점 방향은 외경 (−nz, −nx),
  내경(Z 로 뒤집고 거꾸로 감) (−nz, nx) 이고, 팁 3 (−1, −1)/√2 · 팁 2 (−1, 1)/√2 와의 내적이 둘 다 (nz + nx)/√2 라 판정이 같다. 그래서 내경 3 · 5 는 늘 없고, 2 · 4 는 늘 있고
  (입구 쪽을 향한 면 — 언더컷, 화면은 파란 한 줄로 접는다), 6 은 돔이 꼭대기를 넘을 때만 있다(`againstToolSameAsOuter`, 관통도 같다).
- **NC 줄 ↔ 요소**: `RoundSolution.ncRefs`(nc 와 같은 길이) — −2 = G00 진입, −1 = G01 시작점, k = drawing.segs[k](90° 넘게 나눈 원호는 조각 번호).
  `lineOf(k, p)` = 요소 k 의 줄(0부터). 나눈 원호는 p 의 원호 각 비율로 조각을 고르고(원호 밖 — 반올림 — 이면 가까운 끝), 움직임이 없어 줄을 버린
  요소는 그 앞 줄. p 는 그 풀이의 프로그램 좌표 — G40 풀이는 가상 인선, 노즈R 0 으로 푼 도면 좌표 풀이는 도면 점.
- 손 계산(0.4R, Z 여유 1, 노즈R 0 은 도면 좌표 프로그램):
  - TYPE 3 돔 바닥(입구 Ø20 · 깊이 10, SR12.5, CR1): L = 5, 도면 (0, 10) → (−9.659, 10) 직선, CR 중심 (−9.659, 9) → (−10.281, 9.783),
    구 중심 (−2.5, 0) → (−15, 0). `G00 X20. Z0.6` `G01 Z-0.4` `G01 Z-10.059` `G03 X19.739 Z-10.432 R0.6` `G03 X0.8 Z-15. R12.1`
    (노즈R 0: `G00 X20. Z1.` `G01 Z0.` `G01 Z-9.659` `G03 X19.565 Z-10.281 R1.` `G03 X0. Z-15. R12.5`). ncRefs (−2, −1, 0, 1, 2) 각 한 줄.
  - TYPE 5 막힘(입구 Ø40 · 깊이 5, SR25, Ø30 · L1 6, 모서리 R 0): L2 = 5, `G00 X40. Z0.6` `G01 Z-0.4` `G01 Z-5.266` `G03 X30. Z-10.199 R24.6`
    `G01 Z-16.` `G01 X0.8`. 관통이면 바닥 면이 없어 마지막 `G01 X0.8` 이 없다.
  - TYPE 4 소켓(입구 Ø10 · 깊이 5, SR8, CR1): `G01 Z-4.937` `G02 X10.933 Z-5.98 R1.4` `G03 X14.999 Z-14.357 R7.6` `G03 X0.8 Z-19.245 R7.6`
    — 공은 꼭대기를 넘는 원호라 두 줄(ncRefs (2, 0, 2) · (2, 1, 2), 원호 25 % 점은 5번째 줄 · 75 % 는 6번째), `AGAINST_TOOL`.
- 테스트: `RoundInnerTest`(테스트가 따로 뒤집은 도면과 1e-9, 위 손 계산, 볼록/오목 뒤집힘, AGAINST_TOOL 이 외경과 같음, 오목 CR ≤ r, 막는 경우,
  ncRefs 가 NC 끝점 · 요소와 맞음), `GeometricVerifyTest.roundInner`(그 파일에서 따로 뒤집은 고정 11모양 × r 4 + 무작위 600 —
  5 · 6 은 넷에 하나 관통, 맞은편 벽(축 대칭)까지 파고듦을 본다).

### NC
- `G00` 진입(첫 보정점 + Z zClear, 3점 모드는 위 진입 규칙) → `G01` 첫 보정점 → 요소마다 `G01` / `G02`·`G03 X Z R`(바뀐 축만). 숫자는 `Fmt.nc`.
- TYPE 1~6 의 원호는 모두 180° 미만이다. 90° 넘는 원호(TYPE 2 의 공, TYPE 4 의 꼭대기를 넘는 원호, 3점 모드)는
  같은 각으로 나눠 줄마다 90° 이하(`NcLines.arc`, 위 "공개 API" 의 NC 줄 설명).

### 경고 `RoundWarning`
- 계산을 멈춤(solution = null, 경고 하나): `MISSING_INPUT`(묶음에서 둘 이상 비움 포함), `BAD_INPUT`, `CONTRADICTION`,
  `SHAPE_MISMATCH`, `CORNER_R_TOO_BIG`, `NOSE_UNREACHABLE`(NoseComp 의 Unreachable·이을 수 없음, 내경에서 노즈 중심이 축을 넘음).
- 숫자와 함께: `CONCAVE_R_LE_NOSE`, `AGAINST_TOOL`(TaperCalc 와 같은 기준 — 노즈 접점이 팁 벡터 ±45° 밖. 원호는 접점 방향이
  원호 각만큼 돌므로 90° 넘거나 양 끝이 밖이면. 팁 1~4 만. 경계는 내적·각으로 1e-3(약 0.06~0.08°) 넘을 때부터 밖 —
  3점호는 CAD 점을 넷째 자리로 넣어 중심이 흔들려, 단면→외경의 보통 볼록 R 이 경계를 1e-6 넘어 경고가 나던 것). TYPE 1(왼쪽 면)·2·4(공의 뒤쪽)는 늘 나온다 — 그쪽은 35° 인서트·
  역바이트·절단 등 따로 확인할 것.

### API
```kotlin
enum class RoundWarning { MISSING_INPUT, BAD_INPUT, CONTRADICTION, SHAPE_MISMATCH, CORNER_R_TOO_BIG, NOSE_UNREACHABLE,
                          CONCAVE_R_LE_NOSE, AGAINST_TOOL }
enum class L2Ref { A, B }   // TYPE 2: A = 왼쪽 이음점 ~ 오른쪽 끝(= L1 + B), B = 이음점 사이
data class RoundInput(val type: Int = 1 /*1~6*/, val mainR: Double? = null,
    val cornerR1: Double? = null /*Corner R, Corner R(1)*/, val cornerR2: Double? = null,
    val d1: Double? = null /*D, D1 (직경)*/, val d2: Double? = null, val l1: Double? = null /*L, L1*/, val l2: Double? = null,
    val l2Ref: L2Ref = L2Ref.B, val noseR: Double = 0.4, val tip: Int? = null /*null = 표준(외경 3, 내경 2)*/, val zClear: Double = 1.0,
    val legLen: Double = 5.0 /*내경 3~6 = 입구 구멍 깊이*/, val inner: Boolean = false /*TYPE 2~6*/, val through: Boolean = false /*내경 5·6*/)
data class Arc3Input(val inner: Boolean = false, val noseR: Double = 0.4, val tip: Int? = null /*null = 표준*/,
    val x1/z1/x2/z2/x3/z3: Double? = null /*X 직경*/, val zClear: Double = 1.0)
data class RoundArc(val index: Int /*drawing.segs 번호*/, val cw: Boolean, val center: Pt, val r: Double,
    val progR: Double? /*R ± r, 없어졌으면 null*/)
data class RoundSolution(
    val filled: RoundInput?,          // 빈 값을 채운 입력(3점 모드는 null)
    val nodes: List<CompPoint>,       // 요소 끝점마다 도면점·보정점, 가공 순서
    val arcs: List<RoundArc>,
    val tip: Int, val side: Side, val entry: Pt, val nc: List<String>,
    val drawing: Contour, val center: Contour, val program: Contour,  // segs[k] 끼리 대응
    val ncRefs: List<NcRef>) {        // nc 줄마다 하나
    fun lineOf(element: Int, p: Pt): Int  // element 의 NC 줄 번호(0부터), 나눈 원호는 p 의 각 비율로 조각. 줄이 없으면 그 앞 줄
}
data class NcRef(val element: Int /*−2 = G00 진입, −1 = G01 시작점, k = drawing.segs[k]*/, val piece: Int, val pieces: Int)
data class RoundResult(val solution: RoundSolution?, val warnings: List<RoundWarning>)
object RoundCalc {
    fun calc(i: RoundInput): RoundResult   // type 이 1~6 밖, 내경 1, 외경·내경 2~4 의 through 면 IllegalArgumentException(프로그램 실수)
    fun arc3(i: Arc3Input): RoundResult
}
```

## 보링바 간섭 검사 (`HolderCheck`, 구현됨)
내경 라운드(TYPE 2~6 · 3점호 내경)의 G40 경로를 따라 보링바(바 + 인서트)가 소재에 닿는지 본다(사용자 요청 2026-10-20 "내경 라운드는 홀더
크기를 감안해서 공구가 소재에 닿는 걸 확인"). 화면은 닿으면 노란 띠 + 빨간 표시(사용자 결정 D5 — 숫자는 그대로 나온다), 결정 표는 `stage2-report.md` 8절.
축을 지나는 ZX 단면에서 소재 = {|x| ≥ ρ(z)}(깎은 벽과 축 너머 맞은편 벽), 공구 = 인서트 조각 + 바 띠.

### 공구 단면 (`BarShape`, 노즈 중심 C = 0 기준)
- r = 노즈 R, κr = 절입각(카탈로그 KAPR — 사용자 결정 D6, 받는 값), ε = 인서트 코너 각(C 80° · D 55° · V 35° · T 60°),
  κr′ = 180° − κr − ε(끝날각, 계산해 보이기만).
- 날 방향 e1 = (−cos κr, −sin κr)(주절삭날), e2 = (cos κr′, −sin κr′)(끝날). 바깥 법선 n1 = (−sin κr, cos κr) · n2 = (sin κr′, cos κr′),
  노즈 접점 T1 = r·n1 · T2 = r·n2. 꼭짓점 N = −r / sin(ε/2) · unit(e1 + e2)(노즈 원이 두 날에 접하는 가상 꼭짓점). r = 0 이면 T1 = T2 = N = C.
- 인서트 = [T1, N + l·e1, N + l·(e1 + e2), N + l·e2, T2] 반시계(T 는 셋째 꼭짓점 없이 삼각형), 노즈는 현 T1–T2 — 노즈 원은 보정으로
  이미 소재에 안 닿는다. l = 날 길이 = 품번 크기 숫자(DCMT 07 → 7, 사용자 결정 D7 — 실제 날은 0~1 mm 더 길다).
- 기준점 ref = (−r, r) = 팁 2 가상 인선 — 카탈로그 f(WF)를 여기서 잰다. 바 = 중심 x_c = r − f, 반지름 a = D/2 의 띠.
  앞은 N 을 지나는 주절삭날 연장선 E(x) = N + ((N.x − x) / sin κr)·e1, 뒤는 공구대 면 z = ref.z + 돌출 길이(없으면 1e5 mm).
  바 조각 = [E(x_c − a), (뒤, x_c − a), (뒤, x_c + a), E(x_c + a)].
- 인서트는 띠 **위(x ≥ x_c + a) · 아래(x ≤ x_c − a)로 나온 조각**만 따로 본다(띠 안은 바가 덮는다). 설계안은 위 조각만이었으나 바보다 큰
  인서트의 아래 끝이 맞은편 벽에 닿는 것(아래 F)을 놓치고 독립 검증과 어긋나 둘 다 본다.
- 헤드(DMIN) 구간 길이 insZ = 자르지 않은 인서트의 가장 큰 z + r(인선에서 인서트 뒤 끝까지), 헤드 길이 `overhangMin` = 인선에서 인서트 뒤 끝 ·
  바 앞 끝(E 가 바 아래에 닿는 곳) 가운데 먼 쪽까지 Z.
- 확인 값(C, κr 95°, l 9.67, r 0.4, D 16, f 11 — `barSectionCheckValues`): N = (−0.44003, 0.44003), 위 조각
  [(−0.3985, −0.0349), (−0.1741, −2.6), (9.3854, −2.6), (9.1932, −0.4028), (0.0349, 0.3985)], E 아래 (1.2258, −18.6) · 위 (−0.1741, −2.6),
  insZ = 헤드 길이 = 10.436.
- **뒤보링**(도면이 +Z 로 감 — 3점호 역방향 내경. 사용자 결정 D8 로 검사한다): 인서트를 노즈 중심에서 Z 로 뒤집고(날이 +Z, ref = (r, r) = 팁 1)
  바는 그대로 +Z 로 입구를 지나 나간다. 바 앞(−Z) 끝은 인서트 끝에서 수직(인서트를 받치는 가장 짧은 헤드), DMIN 구간은 인선에서 −Z 쪽으로 insZ.
  κr 은 뒤보링 바의 카탈로그 KAPR. 뒤보링 바 프리셋은 D · f · DMIN · κr 을 모두 낸 출처를 찾지 못해 넣지 않았다(사용자가 넣는다).
- **값 검사** `problems(bar, r)` — 하나라도 틀리면 `check` 는 `BAD_INPUT`(화면은 칸 아래 빨간 글, 띠 없음): 모든 값 유한 · ±`MAX_MM` 안,
  D > 0, 90° ≤ κr < 180° − ε, l > `edgeMin` = r / tan(ε/2), f ≥ `fMin` = D/2 + r(1 − cos κr)(바 윗면이 노즈 접점 T1 아래 — f = fMin 이면
  E 위 = T1), 돌출 길이 > 0 이고 > 헤드 길이(다른 칸이 맞을 때만 잰다), DMIN > 0.

### 소재 (`Bore`)
- 도면을 반직선(1e5 mm, 축에서 멈춤)으로 늘인다: 입구 면이 없으면(`faceAtStart` false — 3점호) 시작에서 −시작 접선, 끝이 축 위가 아니면 끝 접선.
  반직선 방향의 z 성분이 0.002 미만이면 수직(입구 면 — CAD 점을 셋째 자리로 넣은 입구 R 은 시작 접선이 0.0x° 기운다).
- 늘인 윤곽은 Z 로 한쪽으로만 가야 한다(정방향 Z−, 뒤보링 Z+, 원호는 안의 z 극점까지 본다). 1 µm 넘게 되돌아가면 `NOT_MONOTONE`(검사 안 함),
  1 µm 까지는 반올림으로 받는다(입구 R 의 끝이 면을 조금 넘는다).
- zTop = 늘인 윤곽의 가장 큰 z(입구), 그 너머는 공기(ρ = ∞). 깊은 끝이 축 위면 그 너머는 소재(ρ = 0, 막힌 바닥).
  공구대가 닿는 입구 면 = `faceAtStart` 이거나 입구 쪽 반직선이 수직 · 축에 닿을 때 zTop, 아니면 모름(공구대 검사 안 함).
- ρ: 직선은 두 끝을 잇는 1차식, 원호는 c.x + s√(R² − (z − c.z)²)(s = 위 반원 +1). **수직 요소(면)는 직접 보지 않고** 이웃 토막의
  한쪽 극한으로만 본다 — 면에 모서리로만 붙는 것은 닿음이 아니다(κr 90° 바 앞이 바닥 면에 붙어 깎는 것).

### 여유(한 자세, 정확한 최솟값)
- 볼록 조각마다 꼭짓점 z 사이의 위 · 아래 변 U(z) · L(z)(1차식). 소재 꺾임점(요소 끝 z · zTop · 바닥)으로 더 나눈 토막마다 양 끝(한쪽 극한)과,
  원호 토막이면 ρ′ 가 변의 기울기와 같은 곳 z* = c.z − s·m·R / √(1 + m²)(ρ − U 는 m = U′, ρ + L 은 m = −L′)에서 min(ρ − U, ρ + L).
  토막 안에서 ρ − 1차식은 볼록이거나 오목이라 이 후보들의 최솟값이 정확한 최솟값이다. ρ − U < 0 은 깎는 쪽 벽, ρ + L < 0 은 맞은편 벽에 들어간 것.
- 부분: 인서트(위 · 아래 조각) · 바 = 반경 여유, 공구대 = ref.z + 돌출 길이 − 입구 면 z, 최소 가공경 = 헤드 구간(ref.z ~ ref.z + insZ)의
  가장 좁은 ρ − DMIN/2(같은 후보 규칙, U ≡ 0).
- 닿음 = 여유 < −0.005 mm(`CONTACT_TOL`).

### 경로 훑기 (`check`)
- 요소: −2 = G00 진입점에 선 자세, −1 = 진입점 → 첫 점 G01, k = program.segs[k](= drawing.segs[k]). 길이 1e-9 이하는 건너뛴다.
- 간격 h = max(0.02 mm, 전체 길이 / 20000), 요소마다 ⌈길이 / h⌉ 토막의 끝점. 부분마다 처음 · 마지막 닿는 곳은 이분법 40번(약 1e-4 mm),
  요소 첫 점에서 이미 닿아 있으면(앞 요소에서 이어짐) 처음 = 0. 요소 · 부분마다 `HolderHit` 하나(사이에 떨어진 곳이 있어도 묶는다),
  가장 깊은 자세 = 여유가 가장 작은 표본. h 보다 긴 닿는 구간은 놓치지 않는다.
- `HolderHit`: 요소, 부분, 처음 · 마지막 · 가장 깊은 자세(`HolderPose` = 가상 인선 · 노즈 중심 · 노즈가 닿는 도면 점 — 요소 k 에서 노즈 중심과
  가장 가까운 점, −2 · −1 은 도면 첫 점), 요소 길이 비율 fromT · toT, 깊이(인서트 · 바 = 반경, 공구대 = Z, 최소 가공경 = DMIN/2 − 가장 좁은 ρ),
  `opposite`(가장 깊은 점 x < 0 = 맞은편 벽), `behind` = 그 점 z − 인선 z. 요소 순, 한 요소 안은 처음 순.
- `barClear` = 경로 전체에서 바 여유의 최솟값(같으면 앞의 것)과 그 자세 · 요소 — 안 닿을 때 화면의 '가장 좁은 곳'.
- 그림용: `outline`(바는 maxZ 에서 자름, 인서트는 노즈 원호 포함), `overlap`(조각마다 z 띠 400 개 이하 · 폭 0.02 mm 이하로 깎는 쪽
  [max(L, ρ), U] · 맞은편 [L, min(U, −ρ)] 를 닫힌 꺾은선으로, 겹침이 띠 안에서 끝나면 이분법).

### 단면이 다루는 것 · 어림 · 보지 않는 것
- 정확: 둥근 바 자루 — 바 원판에서 축과 가장 먼 점이 y = 0 에 있어 원판이 덮는 반경 [max(0, |x_c| − a), |x_c| + a] 가 단면과 같다
  (독립 검증 V5 가 원기둥 표면 표본과 0.003 안에서 본다).
- 어림: 인서트의 중심 높이 아래쪽 · 헤드 앞 모양 · 인서트 기울기 같은 입체 효과 → 최소 가공경(DMIN)으로만 본다.
- 보지 않는 것: 진입점까지 오는 길과 가공 뒤 빠지는 길, 덜 깎은 소재(황삭 · 예비 구멍), 헤드 · 목 길이 입력, 제조사 램프 각(RMPX),
  W · S 인서트와 κr < 90° 바, 3D 헤드 모양.

### 테스트
- `HolderCheckTest` — 설계 때 따로 짠 표본 검사(파이썬) · 손 식, 좌표는 G40 인선 ±0.003 mm(X 직경), 바 A = Ø16 · f 11 · C · l 9.67 · κr 95°,
  0.4R · Z 여유 1:

| 예 | 형상 · 바 | 결과 |
|---|---|---|
| A | 내경 3 돔 바닥(Ø20 · 깊이 10, SR12.5, CR1), 바 A | 요소 2(구)에서 바 · 인서트가 맞은편 벽. 바 처음 Z−11.409 X18.006(여유 0 은 Z−11.404 X18.014), 가장 깊은 곳 Z−15 X0.8 12.4355, behind 1.626 · 인서트 처음 Z−14.998 X1.238, 0.2341. 요소 0 바 여유 2ρ − f − D/2 = 1, 요소 0~1 가장 좁은 곳 0.8696. 줄은 G40 · 도면 좌표 둘 다 5번째 |
| B | A 를 Ø18 | 바가 요소 0 Z−1.539 X18 부터 맞은편 벽: 깊이 1 · 1.1826 · 12.4355 |
| C | 내경 5 막힘(Ø40 · 깊이 5, SR25, Ø30 · L1 6), 바 A | 바닥 면(요소 3)에서 바가 맞은편 벽: 처음 X7.99(반경 f + D/2 − D1/2 − 0.005), 가장 깊은 곳 X0.8 3.6. 관통이면 안 닿고 가장 좁은 곳 2.9698 |
| C38 | Ø50 → Ø38 막힘, 바 A · κr 90° | 안 닿음, 가장 좁은 곳 D1/2 + r − f − D/2 = 0.4 — 바닥을 중심까지 깎으려면 구멍 Ø ≥ 2f + D. κr 90° 바 앞이 바닥 면에 붙어도 닿음이 아니다 |
| D | A + 돌출 길이 12 | 공구대: 처음 Z−12.005 X16.739, 3.0 |
| E | A + DMIN 20 | 최소 가공경: 요소 0 처음 Z−9.759 X20 0.0835, 요소 1 처음부터 0.3394, 요소 2 10 |
| F | 내경 4 소켓(Ø10 · 깊이 5, SR8, CR1), Ø8 · f 4.5 · D · l 7.75 · κr 93° | 위 인서트 조각이 깎는 쪽 벽(요소 1 처음 Z−5.769 X10.548, 0.0655 · 요소 2 처음부터 0.2559), 아래 조각이 맞은편 벽(요소 0~2), 바가 입구 턱(처음 Z−6.015 X11.010 — 반경 입구 + f − D/2 + 0.005, 5.5303). 바 처음은 5번째 줄, 가장 깊은 곳은 6번째 줄 |
| 문턱 | 내경 5 관통, Ø20 · C 9.67 · κr 95° | f 20.01 이면 요소 2 바 0.01 닿음, 19.99 면 0.01 남음 |
| 입구 R | 3점호 X30 Z0 → X22.929 Z−1.464 → X20 Z−5, A16R-SCLCR 09 | 입구 면 Z0, 안 닿음, 가장 좁은 곳 1.152(원호 끝에서 바 아래 앞 끝이 아직 R 위) |
| 뒤보링 | 3점호 X30 Z−20 → X24 Z−19 → X20 Z−15(팁 1 · G42), Ø12 · κr 95° | f 11 은 바가 Ø20 을 지나며 0.4 닿음(원호 4.195° 까지), f 11.5 면 0.1 남음. 원뿔 뒤보링은 α > κr′ 이면 끝날이 판다(깊이 l·sin(α − κr′)/cos α). 뒤집은 인서트 여유 = 정방향 인서트를 Z 로 뒤집은 것 |

  그 밖에 270° 원호 `NOT_MONOTONE`, 칸마다 `BAD_INPUT`(f = fMin − 1e-6 · κr = 180° − ε 포함), 겹침 그림(꼭짓점이 소재 안 · 조각 안,
  가장 깊은 곳 = 바 깊이 ±0.02), outline.
- `verify/HolderVerifyTest` — 독립 표본 검증: 이 절의 식을 쓰지 않고 따로 만든 인서트(마름모 · 삼각형을 돌리고 노즈 원으로 둥글게) · 바 띠 ·
  ρ(z)(면 · 반직선 · 막힌 바닥 규칙도 따로) 위에 0.005 mm 마다 점을 찍고 NC 글을 0.05 mm 마다 따라간다. 무작위 내경 TYPE 300(5 · 6 은 넷에 하나 관통) ·
  3점호 정방향 150 · 뒤보링 150 · 되돌아가는 원호 30 + 위 손 계산 · 화면 프리셋 바. V1 표준 팁 인선 = 둥근 인서트 끝 ±1e-6, V2 구현 여유 ≤ 표본 + 1e-6,
  V3 표본 ≤ 구현 + 0.02(대상 자세 70 % 이상), V4 닿는 요소가 서로 같고 처음 닿는 곳 0.1 mm 안, V5 바 자루 단면 = 원기둥 표본 ±0.003,
  V6 `lineOf` 의 NC 블록이 첫 인선점을 지남. 일부러 틀린 식 7가지(f 부호, 맞은편 벽 빼기, |x_c| 대신 x_c, κr′ 뒤집기, lineOf 한 줄 밀림,
  바 앞 경사 빼기, 뒤보링 인서트 안 뒤집기)를 모두 잡는 것을 확인했다.

```kotlin
enum class InsertShape(val epsDeg: Double) { C(80.0), D(55.0), V(35.0), T(60.0) }
data class BoringBar(val dia: Double, val f: Double, val shape: InsertShape, val edge: Double, val kappaDeg: Double,
    val overhang: Double? = null /*null = 공구대 검사 안 함*/, val dMin: Double? = null /*null = 검사 안 함*/)
enum class HolderPart { INSERT, BAR, TURRET, MIN_BORE }
enum class HolderSkip { BAD_INPUT, NOT_MONOTONE }
enum class BarField { DIA, F, EDGE, KAPPA, OVERHANG, DMIN }
data class HolderPose(val tip: Pt /*G40 프로그램 점*/, val nose: Pt, val drawn: Pt /*노즈가 닿는 도면 점*/)
data class HolderHit(val element: Int /*−2 진입점, −1 진입 이동, k = program.segs[k]*/, val part: HolderPart,
    val first: HolderPose, val last: HolderPose, val worst: HolderPose, val fromT: Double, val toT: Double /*요소 길이 비율*/,
    val depth: Double, val opposite: Boolean /*맞은편 벽*/, val behind: Double /*닿는 점 z − 인선 z*/)
data class HolderResult(val skip: HolderSkip?, val hits: List<HolderHit> /*요소 순, 처음 닿는 곳 순*/,
    val barClear: Double?, val barClearAt: HolderPose?, val barClearElement: Int?)
object HolderCheck {
    const val CONTACT_TOL = 0.005; const val STEP = 0.02
    fun fMin(dia: Double, kappaDeg: Double, r: Double): Double; fun edgeMin(shape: InsertShape, r: Double): Double
    fun overhangMin(bar: BoringBar, r: Double): Double
    fun problems(bar: BoringBar, r: Double): Set<BarField>
    fun check(drawing: Contour, program: Contour, entry: Pt, tip: Int, r: Double, faceAtStart: Boolean, bar: BoringBar): HolderResult
    fun outline(bar: BoringBar, r: Double, nose: Pt, maxZ: Double, reverse: Boolean = false): List<Contour> // [바, 인서트]
    fun overlap(drawing: Contour, faceAtStart: Boolean, bar: BoringBar, r: Double, nose: Pt): List<Contour>  // 겹친 곳
}
```

## 직각삼각형 (`RightTriangle`, 구현됨)
- 밑변 a, 높이 b, 빗변 c, 각 A(밑변–빗변), 각 B(높이–빗변, = 90° − A) 가운데 **아는 둘** → 나머지 전부. 각도 둘만이면 `ANGLES_ONLY`.
- 변 둘이 있으면 변으로(빗변과 변이면 √(c² − 변²)), 아니면 변 하나와 각으로 푼다. 각은 늘 atan2(b, a) 로 다시 구한다.
  셋 이상 넣으면 맞는지 본다(길이 0.001 mm, 각 0.001°) → `CONTRADICTION`.
- 좌표 연동: 밑변 = Z, 높이 = X 반경으로 보고 `dz` = a, `dxDia` = 2b. `baseIsZ = false` 면 반대(dz = b, dxDia = 2a).
- 경고(모두 계산을 멈춤): `MISSING_INPUT`(둘 미만), `ANGLES_ONLY`, `BAD_INPUT`(길이 ≤ 0, 숫자 아님, 넣은 변이나 계산한 변이 `MAX_MM` 넘음 —
  0° · 90° 에 아주 가까운 각. 넣은 빗변은 다시 구한 값이 아니라 넣은 값으로 본다: 한도 그대로인 빗변이 부동소수 오차로 막히지 않게), `BAD_ANGLE`(0° 이하·90° 이상),
  `HYPOTENUSE_TOO_SHORT`(빗변 ≤ 변), `CONTRADICTION`.
- `RightTriangleTest` 가 다섯 값의 모든 둘 조합 × 크기 3 × 각 7(1°~89°)을 sin·cos 로 만든 정답과 맞춰 본다.

```kotlin
enum class TriangleWarning { MISSING_INPUT, ANGLES_ONLY, BAD_INPUT, BAD_ANGLE, HYPOTENUSE_TOO_SHORT, CONTRADICTION }
data class TriangleInput(val a: Double? = null, val b: Double? = null, val c: Double? = null,
    val angleA: Double? = null, val angleB: Double? = null, val baseIsZ: Boolean = true)
data class TriangleSolution(val a: Double, val b: Double, val c: Double, val angleA: Double, val angleB: Double,
    val dz: Double, val dxDia: Double)
data class TriangleResult(val solution: TriangleSolution?, val warnings: List<TriangleWarning>)
object RightTriangle { fun calc(i: TriangleInput): TriangleResult }
```
