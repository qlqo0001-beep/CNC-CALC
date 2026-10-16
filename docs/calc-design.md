# 계산 엔진 설계 (`:calc`)

화면(`:shared`)은 이 모듈의 공개 API 만 부른다. 계산은 전부 여기서, 그림도 여기서 만든 경로를 그린다.
테스트: `gw.bat :calc:jvmTest` (`calc/src/commonTest`).

## 좌표 약속
- 선반, 후방 공구대 기준 화면: **Z 오른쪽 +, X 위 +**. 척 쪽이 Z−.
- 내부 기하는 **반경값**(`Pt(z, x)` 의 x 는 반경). 화면 입출력의 X 는 **직경값** — 바꾸는 곳은 API 경계 한 군데.
- 각도는 입력·출력 모두 **도(°)**. 테이퍼 각은 **Z 축에서 잰 각**(0° = Z 평행, 90° = 단면).
- 길이 mm. 결과 표시는 소수 셋째 자리(0.001) 반올림 — 반올림은 화면에서만, 계산은 Double 그대로.

## 가상 인선(imaginary tool tip)과 노즈R
- 프로그램 좌표는 **가상 인선점**(노즈 원에 Z·X 방향으로 접하는 두 접선의 교점) 기준. G41/G42 를 안 쓰고
  가상 인선으로 직접 프로그램할 때 필요한 보정 좌표를 내는 것이 이 앱의 핵심.
- **팁 번호(FANUC 1~9, 0)** → 노즈 중심 C 에서 가상 인선 P 로 가는 벡터 `tipVector(T, r)` (Δz, Δx):
  - T3 = (−r, −r) : 외경 일반 바이트(척 쪽으로 깎음) — 기본값
  - T4 = (+r, −r) : 외경 역바이트
  - T2 = (−r, +r) : 내경 보링
  - T1 = (+r, +r) : 내경 역보링
  - T5~T8 은 한 축만 r(변 가운데), T0/T9 = (0, 0) 중심. **5~8 의 방향은 FANUC/Haas 매뉴얼로 확인하고 출처를 주석에 남길 것**
- 역바이트(Rev. Nose-R) = 진행 방향과 팁을 Z 로 뒤집음(T3↔T4, T2↔T1).

## 일반 해법(모든 보정의 바탕)
1. 도면 윤곽 = 선분·원호의 연속 `Contour(List<Seg>)`
2. 재료 반대쪽으로 r 만큼 **오프셋** → 노즈 중심 경로. 이웃 오프셋 요소는 **교점**으로 잇는다(볼록·오목 모두).
   원호는 같은 중심, 반지름 R±r(볼록 +, 오목 −). R−r ≤ 0 이면 경고(공구가 못 들어감).
3. 중심 경로의 모든 점에 `tipVector` 를 더함 → 가상 인선 경로 = 프로그램 좌표.
- 닫힌 식(검증용): 테이퍼 θ, T3, 외경에서
  ΔZ = r(1 − tan(θ/2)), ΔX(반경) = r(1 − tan((90° − θ)/2)) → 0.4R·45° 에서 0.234 / 직경 0.468.
  일반 해법의 결과와 0.001 안에서 같아야 한다.

## 공개 API (초안 — 구현하며 고쳐도 되지만 바꾸면 이 문서도 같이)
```kotlin
data class Pt(val z: Double, val x: Double)                 // x = 반경
sealed interface Seg { val start: Pt; val end: Pt }
data class Line(override val start: Pt, override val end: Pt) : Seg
data class Arc(override val start: Pt, override val end: Pt, val center: Pt, val cw: Boolean) : Seg  // cw = G02
data class Contour(val segs: List<Seg>)

enum class Side { MATERIAL_BELOW, MATERIAL_ABOVE }           // 외경 = 재료가 경로 아래(X−), 내경 = 위
object NoseComp {
    fun tipVector(tip: Int, r: Double): Pt
    fun centerPath(c: Contour, r: Double, side: Side): Contour
    fun tipPath(c: Contour, r: Double, side: Side, tip: Int): Contour    // = 프로그램 경로
}
```
- 기능별 계산기(`TaperCalc`, `GrooveCalc`, `RoundCalc`, `RightTriangle`, `G76`, `PolarG12`, `Cutting`)는
  입력 data class → 결과 data class(숫자 + 그릴 `Contour` 들 + 경고 목록)를 돌려주는 순수 함수.
- 경고는 문자열이 아니라 `enum`/sealed 로(화면이 한국어 문구를 고름).
