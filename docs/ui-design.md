# 화면 기초 (`:shared`)

기능 화면을 채우는 사람이 볼 문서. 원본 화면 명세는 `screens.md`, 계산 API 는 `calc-design.md`.
확인: `gw.bat :shared:jvmTest` → `build/shots/<이름>-light.png`·`-dark.png` 를 `docs/store` 원본과 나란히 본다.

## 파일 지도 (`shared/src/commonMain/kotlin/com/bmt/nosecalc`)
| 파일 | 내용 |
|---|---|
| `App.kt` | `App(state)` — 7기능 라우팅, `AppHost`(테마 + 위층 + 뒤로) |
| `AppState.kt` | 화면 쌓임(`open`·`home`·`back`), `menuOpen`·`helpOpen`, 테마 저장, `toast(글)`, PC 키(`handleKey`), `OnBack` |
| `Feature.kt` | 7기능 이름(메뉴용·헤더용)·헤더 제목 크기, 메뉴 순서 |
| `KeyValueStore.kt` | expect/actual 설정 저장(안드로이드 SharedPreferences, PC `~/.bmt-nosecalc/settings.properties`) |
| `screens/*Screen.kt` | 기능마다 한 파일 + 도움말 글 `XxxHelp`. 아직 안 만든 기능은 `ComingSoon` 자리표시 |
| `screens/TaperScreen.kt`, `screens/taper/` | 테이퍼·홈 화면(아래 절) |
| `screens/RoundScreen.kt`, `screens/round/` | 라운드 R 화면(아래 절) |
| `screens/TriangleScreen.kt`, `screens/triangle/` | 직각삼각형 화면(아래 절) |
| `ui/theme/AppColors.kt` | 색 토큰 라이트·다크 |
| `ui/theme/AppTheme.kt` | `AppTheme(dark)`, `AppTheme.colors / sans / mono` (CompositionLocal 하나) |
| `ui/FeatureScaffold.kt` | 기능 화면 틀(헤더 + 본문), APP MENU 패널, 도움말 시트 |
| `ui/Header.kt` | `FeatureHeader`, `CircleIconButton`, `AppsButton`, `PillButton` |
| `ui/Components.kt` | `AppCard`, `TypeGrid`, `Segmented`, `OutlineTabs`, `ResultCard`, `WarningBanner`, `ComingSoon` |
| `ui/NumberInput.kt` | `NumberField`, 숫자 키패드(`NumberPad`), 글 고치기 `NumEdit` |
| `ui/ShapeViewer.kt` | `ShapeViewer(lines, marks)` — calc 의 `Contour` 를 그린다 |
| `ui/Overlays.kt` | `BottomSheet`, `Modal`, 토스트 층 |
| `ui/AppIcons.kt`, `ui/FeatureIcon.kt`, `ui/BmtLogo.kt` | 선 아이콘, 기능 아이콘(Canvas), BMT 표식·글자·로고 |

## 기능 화면 채우기
- `screens/XxxScreen.kt` 의 `ComingSoon(...)` 자리를 바꾼다. `FeatureScaffold(Feature.X, XxxHelp) { … }` 의 본문은 `BoxScope`(화면 크기),
  스크롤은 화면이 `Column(Modifier.verticalScroll(…))` 로 정한다. 헤더 버튼(메뉴/테마/도움말/홈)·뒤로는 틀이 처리한다.
- 입력값은 `rememberSaveable` 로 — `App` 이 화면마다 `SaveableStateHolder` 로 감싸 다른 기능에 다녀와도 남는다.
- 화면 안 단계(예: TYPE 고르기 → 입력)를 뒤로 키·Esc 로 되돌리려면 `OnBack(enabled = 단계 > 0) { 단계-- }`.
  나중에 그려진 `OnBack` 이 먼저 받는다(키패드·모달 → 메뉴 → 화면 안 단계 → 앞 화면). 첫 화면에서 갈 곳이 없으면 안드로이드는 앱을 닫는다.
- 도움말 글은 같은 파일의 `XxxHelp`(문단 목록). 화면을 만들면 실제 순서에 맞춰 고친다.
- 다른 기능으로 보내기: `LocalAppState.current.open(Feature.X)`, 처음 화면: `.home()`, 안내: `.toast("…")`.
- **화면 글은 되도록 한글**(10-17 사용자 요청). 그대로 두는 것은 NC·치수 기호(X · Z · T1 · R · D · L · G02/G03 · G41 …)와 단위(mm, °).
  모든 기능의 헤더 제목(`Feature.title`·`sub`)과 메뉴 카드(`menuTitle`·`menuSub`), 메뉴·닫기 버튼, 테이퍼·라운드·직각삼각형 화면을 맞췄다.
  바꾼 표는 `screens.md` 첫머리 — 3·4단계 화면도 그 표를 따른다.

## 부품
- `NumberField(label, value: String, onValueChange, modifier, unit = "", role = FieldRole.Normal|Start|End|Dim, placeholder = "", autoValue = null)`
  - 값은 **쓴 그대로의 글**("5.0", "-25", "" = 빈칸). 계산에는 `value.toDoubleOrNull()` — calc 입력의 `Double?`(빈칸 = 풀 값)과 바로 맞는다.
  - `autoValue` = 빈칸일 때 계산된 값. 칸에 흐리게 보이고 "자동" 표는 **라벨 줄 오른쪽 작은 배지**로 간다(칸 안에 "값 자동" 을 쓰면
    412dp 에서 단위 "mm" 와 겹쳤다). 값 글은 단위가 있으면 양쪽 22dp 를 비워 가운데에 두고, 길면 18sp → 11sp 로 줄인다.
  - 누르면 키패드 바텀시트(0~9 · . · −(부호 바꾸기) · ⌫ · C · 확인). 열 때 값이 통째 선택돼 처음 누른 숫자가 새 값이 된다.
    PC 는 실제 키보드(숫자·.·−·Backspace·Delete=C·Enter=확인·Esc=닫기). 확인 때 "5." → "5", "-0" → "0".
- `TypeGrid(count, selected: Int?, onSelect, chip = ChipPlace.BottomStart|TopStart|BottomEnd|TopEnd, aspectRatio = 1.08f, chipColor = null, chipTextSize = 14f) { type -> 그림 }`
  — type 은 1부터. End 는 그림을 좌우로 뒤집을 때, 칩 글자는 04 라운드 14 · 02 테이퍼 11.5.
- `OutlineTabs(rows: List<List<String>>, selected, onSelect)` — 번호는 줄을 이어 센다(02: 0~4).
- `Segmented(options, selected, onSelect, color = blue, track = segTrack, height = 48.dp, fontSize = 16)`.
  고른 칸 글자색은 `c.onFill(color)` — 바탕이 밝으면(휘도 > 0.3) 짙은 글자. 다크의 청록 #2DD4BF·보라 #A78BFA 위 흰 글자는
  대비가 2:1 아래였다(2단계 점검). 노즈R 칩·팁 번호 원도 같은 함수를 쓴다.
- `ResultCard(label, listOf("X" to Fmt.fixed3(..), "Z" to ...), color = valueComp)` — 고정폭(Courier Prime) 큰 숫자.
- `WarningBanner(text, level = WarnLevel.Warn|Error)` — Warn = 숫자는 나왔지만 볼 것, Error = 계산 멈춤. 경고 enum → 한국어 글은 화면이 고른다.
- `ShapeViewer(listOf(ViewerLine(contour, color, width = 2.dp, dashed = false)), modifier, marks = listOf(ViewerMark(pt, color, "T1")))`
  - Z 오른쪽 +, X(반경) 위 + — calc 와 같은 그림. 처음엔 자동 맞춤, 두 손가락 확대·끌기, PC 휠, 두 번 누르면 다시 맞춤.
  - 격자 1·2·5 간격(왼쪽 아래 "격자 n mm"), 중심선 X0 은 1점 쇄선. 원호는 `Arc.cw` 그대로(G02 = 화면에서 시계).
  - 점 라벨은 오른쪽 위. 앞서 둔 라벨과 겹치면 오른쪽 아래 · 왼쪽 위 · 왼쪽 아래 순으로 옮긴다("T1" 과 "진입" 이 겹치던 것).
- `BottomSheet(onDismiss) { … }`, `Modal(title, onDismiss) { … }` — 부른 자리와 상관없이 화면 전체 위에 뜬다(`if (열림) BottomSheet(...)` 식으로 쓴다).
- `AppCard`, `SheetTitle(title, trailing)`, `PillButton(text, icon, onClick, danger)`, `CircleIconButton(icon, 설명, onClick, tint, glow)`.
- `FeatureScaffold(feature, help, helpExtra = null) { … }` — helpExtra 는 도움말 문단 뒤에 붙일 그림(테이퍼의 팁 번호 그림).

## 테마·색
- 쓰는 법: `val c = AppTheme.colors` → `c.page`, `c.card`, `c.textBody`, `c.drawT1` …. 숫자는 `fontFamily = AppTheme.mono`.
- 라이트 = `screens.md` 1.1 값(11.png 의 Tailwind slate 계열 우선). 메뉴 카드 제목색은 JPEG 값을 가장 가까운 Tailwind 700 으로 맞췄다
  (재료 rose-700, 라운드 teal-700, 시뮬 indigo-700, 삼각 violet-700, 극좌표 amber-700, 나사 emerald-700).
- 다크("딥 다크", 우리가 정함): 바탕 #0D1624 · 카드 #142032 · 입력칸 #1B2A3F · 3D 뷰포트 #09111D. 강조색은 같은 색상의 300~400 단계.
  다크에선 그림자 대신 1dp 테두리. 새 색이 필요하면 `AppColors` 에 라이트·다크 둘 다 넣는다.
- 테마 버튼은 **지금 테마**를 보인다(라이트 = 해, 02 와 같음 · 다크 = 달). 누르면 바뀌고 저장된다. 저장 전엔 시스템 설정을 따른다.

## 글꼴·크기
- Noto Sans KR 가변 글꼴(google/fonts `ofl/notosanskr`, wght 100~900) 하나에서 400~900 을 꺼내 쓴다. 고정폭은 Courier Prime(`ofl/courierprime`,
  원본의 Courier 계열 결과 숫자와 닮음). OFL 전문은 `composeResources/files/licenses/` 에 같이 들어간다. 폰·PC 가 같은 글꼴로 그린다.
- "BMT" 글자는 글꼴 대신 Noto Serif Black 윤곽을 벡터로 넣었다(`BmtWordmark`) — 폰과 PC 의 세리프 글꼴이 달라서.
- **크기 주의**: 원본은 Tailwind px 크기(제목 20 · 카드 제목 16 · 부제 12~13)로 보인다. `screens.md` 1.2 의 추정 sp 는 라틴 글자 기준
  약 15% 크게 잡혀 있다 — 스크린샷을 원본과 나란히 놓고 폭으로 맞춘다(헤더·메뉴는 그렇게 맞췄다).
- 헤더 제목은 기능마다 최대 크기를 두고 폭이 모자라면 자동으로 줄인다(버튼 3개면 제목 칸이 약 224dp).

## 스크린샷 도구 (`shared/src/jvmTest`)
- `Shots.both(이름, heightDp = 915, frames = 40, setup = { state -> … }) { state -> App(state) }` — 412×915dp, 밀도 2.625(1082×2402px).
  부품만 볼 때는 `AppHost(state) { … }` 로 감싼다. 프레임마다 0.1초를 흘려 글꼴 로딩·애니메이션을 끝낸다.
- 지금 찍는 것: `01-menu`, `02-taper`, `feature-<기능>`, `help`, `keypad`, `modal`, `components`(부품 모아 보기 + 테이퍼 계산 결과 뷰어),
  `taper-*`(TaperShotsTest: A 화면 종류·방향별, B 화면 — 외경 형상 1·모서리 R(직접 보정 탭)·형상 3·내경 형상 1·자동 칸·역방향·2중 테이퍼 —
  홈·오류·팁 시트), `round-*`(RoundShotsTest: 고르기, 형상 1~6·접점 모드·오류·수동),
  `triangle-*`(TriangleShotsTest: 처음 값·반대 배치·가파름·각도만). 긴 화면은 heightDp 를 늘려 한 장에 찍는다.
- 프레임마다 나오는 그림은 바로 닫는다(네이티브 메모리). 안 닫으면 1700dp 넘는 스크린샷이 `Surface.makeRasterN32Premul` 에서 실패했다.
- 견고성 시험 `InputRobustnessTest`: 화면 처음 값에서 칸을 흔들어(키패드로 칠 수 있는 글 — 빈칸·0·음수·12자리·아주 작은 수)
  테이퍼·2중 테이퍼 6만, 라운드·3점호 3만, 홈·삼각형 2만 가지를 풀고, 예외가 없고 화면에 찍는 숫자가 모두 유한한지 본다.
  극단 값 화면도 실제로 그려 본다. 이 시험이 찾은 것: 홈 단 수 Int 넘침(빈 목록으로 죽음), 큰 수에서 NaN·끊긴 윤곽(→ `MAX_MM`).

## 테이퍼·홈 화면 (`screens/TaperScreen.kt`, `screens/taper/`)
| 파일 | 내용 |
|---|---|
| `TaperScreen.kt` | `TaperScreen()` — A 화면(가공 종류 탭 · 방향 바 · 형상 그림 2열), 도움말 `TaperHelp`. 형상을 누르면 B, 뒤로 키·Esc 는 B → A |
| `taper/TaperModel.kt` | `TaperKind`(탭 순서 = 02), `TaperState`(화면 상태 + Saver), `TaperDefaults`(처음 값), `TaperSolve`·`Mirror`(역방향), 경고 글 |
| `taper/TypeArt.kt` | `TypeArt(kind, type, mirrored)` — TYPE 그림 Canvas(외경 기준, 내경은 위아래·역방향은 좌우 반전, 바이트도 반대쪽) |
| `taper/SlopeScreen.kt` | B 화면(외경·내경 · 2중 테이퍼): 요약 카드 → 입력 → 경고 → 보정 좌표 → 형상 뷰어 → NC(두 프로그램 탭) |
| `taper/GrooveScreen.kt` | B 화면(외경 홈): 기준 인선 → 치수 → 침범 경고 → 다단 진입 표·정삭 → 형상 → NC |
| `taper/Parts.kt` | 요약 카드, 점 이름표, 노즈R 칩, 팁 번호 칸·시트·그림(`TipDiagram`, 도움말 `TipHelp`), NC 카드(복사·프로그램 탭), 뷰어 카드(범례) |

- **역방향 = 그림을 좌우로 뒤집은 형상**(역바이트로 Z+ 쪽으로 깎음, T1 = 처음 깎는 왼쪽 점, 표준 팁 외경 4 · 내경 1, 보정 쪽 반대).
  계산기 `TaperCalc` 의 `reverse = true` 는 "형상은 그대로, 방향만 Z+" 라 화면 그림과 뜻이 다르다(그대로 쓰면 TYPE 1~4 가 모두 바이트 반대).
  그래서 화면은 Z 를 뒤집어 계산기의 **정방향**으로 풀고 결과(점·경로·진입점·NC·팁 3↔4 2↔1·G41↔G42)를 다시 뒤집는다(`TaperSolve`).
  노즈R 보정은 거울에 대해 그대로 옮겨지는 기하라 숫자는 같다. `TaperSolveTest` 가 같은 실물 형상(화면 역방향 TYPE 3 = 계산기 TYPE 5 역방향,
  TYPE 5 = 계산기 TYPE 3 역방향, 겹테이퍼는 점 순서만 뒤집음)을 5°~85° × r 0.2~1.2 에서 계산기 reverse 와 맞춰 보고(NC 글자까지 같음),
  역방향 외경 TYPE 1 을 닫힌 식(ΔX = −2r(1 − tan((90° − θ)/2)), ΔZ = +r(1 − tan(θ/2)))으로 본다.
- NC 뒤집기(`Mirror.nc`)는 글에서 Z 부호를 바꾸고 G02↔G03 · G41↔G42 · 주석 `(R0.4 T3)` 의 팁 번호를 바꾼다 — `Fmt.nc` 가 절댓값을
  반올림하고 부호를 붙이므로 뒤집은 점을 다시 쓴 것과 같다. 모서리 R 은 점을 바꿔 넣고(화면 T1 = 계산기 T2), 시작·끝점이 Z 면 같이 뒤집는다
  (단면 X 는 그대로). `TaperSolveTest` 가 모서리 R·시작·끝점을 넣은 경우도 계산기 reverse 와 두 NC 글자까지 맞춰 본다.
- 겹테이퍼 TYPE(그림에서 정함, 외경 기준 T1 → T3 지름): 1 두 번 커짐 · 2 가운데가 큼(산) · 3 가운데가 작음(골) · 4 두 번 작아짐. 내경은 지름 방향이 반대.
  계산기는 세 점이면 무엇이든 받으므로, 점의 오르내림이 고른 그림과 다르면 화면이 `SHAPE_MISMATCH` 로 멈춘다.
- 홈은 방향이 없다(방향 칸 대신 11 모양 안내 띠). TYPE 1 = 왼쪽 인선(팁 3번, 기본·첫 칸), TYPE 2 = 오른쪽 인선(팁 4번) — B 화면의 세그먼트와 같은 값.
- 입력값: 점 칸(X·Z·각도·점마다 모서리 R `r1`~`r3`·시작점 `start`·끝점 `end`)은 (종류·형상·방향)마다 따로(방향을 바꾸면 Z 순서가 바뀌고
  형상마다 X 오르내림과 시작·끝점의 뜻이 달라서), 노즈R·Z 여유·홈 치수는 함께. NC 탭 선택(`ncTab`)도 상태에 있다(스크린샷이 정할 수 있게).
  처음 값(사용자 결정 2026-10-17): 도면 치수(점 X·Z, 홈 시작 Z·폭·외경·바닥)와 Z 여유·X 여유는 0 — 예시 값이 도면 값처럼 남지 않게.
  공구 값(노즈 R 0.4·인서트 폭 3·겹침 0.5)은 그대로, 모서리 R·시작·끝점·각도는 비워 둔다. 치수가 아직 0·빈칸이면 빨간 멈춤 대신 파란 안내
  ("값을 넣으면 바로 계산합니다 …"). 스크린샷·골든은 2단계 처음 값(외경 X40 Z0 / X50 Z-5, 역방향은 Z -30·-25 쪽, 홈은 calc-design.md 의 예,
  여유 1.0)을 넣어 찍는다(jvmTest `TaperExample`).
  빈칸인 X·Z·각도·끝점은 계산된 값을 `autoValue` 로 보인다 — 끝점은 깎기가 끝나는 마지막 T 점(모서리 R 이면 그 끝). **시작점은 빈칸이면 "안 깎음"** —
  그 요소는 깎지 않고 연장선에서 들어가므로 그림의 5 mm 값을 "자동" 으로 보이면 그 좌표부터 깎는 것처럼 읽혔다(2단계 점검).
  모서리 R·Z 여유·X 여유는 비우면 0, 겹침은 비우면 기본값.
- 시작·끝점 칸: 그 요소가 단면이면 "시작점 X (T1 앞)", 수평이면 "시작점 Z (T1 앞)" 처럼 라벨이 형상·종류에 따라 바뀐다(`legsVertical`:
  형상 1·2 는 앞이 단면, 2·4 는 뒤가 단면, 2중 테이퍼는 둘 다 수평). 옛 "앞뒤 요소 길이" 칸은 없앴다.
- 보정 좌표 카드: 날카로운 점은 X·Z(직접 보정 좌표) + ΔX·ΔZ, 모서리 R 점은 R 시작·끝 접점마다 보정 X·Z 와 "도면 X… Z…", 그 아래
  "G03 반시계 · 도면 R1.000 → 보정 R1.400"(오목 R ≤ 노즈R 이면 "보정 R 없음"). 형상 뷰어는 보정점마다 노즈 원을 그린다.
- NC 카드 탭: **"G41/G42 보정 프로그램"(기본)** = 도면 좌표 + 기계 보정(첫 줄 주석 `(R0.4 T3)` 을 공구 보정 화면 R·T 칸에),
  **"직접 보정 (G40)"** = 앱이 계산한 보정 좌표. 카드 아래 글이 둘의 차이(이중 보정 금지, 끝 블록에서 인선 위치가 다름)를 적는다.
  홈은 보정량이 0 이라 NC 가 하나(카드 아래에 이유).
- 노즈R 칸 라벨은 "노즈 R (공구 반경, mm)"(라운드도 같은 부품). 도움말에 "노즈R = 인서트 날끝 반경 = 기계 공구 보정 화면 R 칸의 공구 반경
  (밀링의 공구 반경과 같은 역할), 팁 번호는 T 칸" 한 줄.
- 그림 치수: 02 의 점 자리를 잰 값(카드 폭·높이 비율), 글자·선 굵기는 02 카드 폭 199dp 기준 배율이라 B 화면 요약 칸(132dp)에서도 같은 모양.
  탭 글자 15.5sp·자간 −0.03em, 방향 바 글자 13.5/12.5sp 는 02 와 글자 폭을 재서 맞췄다(원본 라틴 글자가 좁다).
- 복사는 `LocalClipboardManager`(경고 숨김) — 새 `LocalClipboard` 는 ClipEntry 를 플랫폼마다 따로 만들어야 해서.

## 라운드 R 화면 (`screens/RoundScreen.kt`, `screens/round/`)
| 파일 | 내용 |
|---|---|
| `RoundScreen.kt` | `RoundScreen()` — 고르기(04: "가공 형태 선택" 줄 + 형상 카드 2열 + 수동(3점호) 카드), 도움말 `RoundHelp`. 카드를 누르면 입력 화면, 뒤로 키·Esc 는 입력 → 고르기 |
| `round/RoundModel.kt` | `RoundState`(화면 상태 + Saver), `dims(type)`(형상마다 칸 이름·순서 = 그림), `blankable`, `RoundDefaults`(처음 값), 경고 글 `roundWarningText` |
| `round/RoundArt.kt` | `RoundTypeArt(type)` — 형상 그림 Canvas, `sampleInput(type)`(그림용 견본 치수) |
| `round/RoundParts.kt` | `TypeCard`(04 카드: 머리 칩 + 몸통), `ManualArt`, `RoundSummary`, `ModeCard`(접점/합성 R), `NodeTable`, `RoundViewer`, `ArcCard` |
| `round/RoundInputScreen.kt` | `RoundTypeScreen`(형상 1~6) · `ManualScreen`(수동 3점호): 요약 → 모드 → 치수·공구 → 경고 → (원호) → 노드 표 → 형상 확인 → NC |

- 이름: 04 의 영어는 한글로 — "CNC ROUND CALC" → 헤더 "라운드 R 계산", "SELECT MACHINING TYPE" → "가공 형태 선택", "TYPE n" → "형상 n",
  Main R → **주 R**, Corner R → **모서리 R**(Corner R(1)·(2) → 모서리 R(1)·(2)). D · D1 · D2 · L · L1 · L2 · L2(A)/L2(B) 는 그대로. 그림·칸·경고·도움말이 같은 이름을 쓴다.
- 그림: 원본은 남의 3D 렌더라 쓰지 않고, **계산기가 견본 치수로 만든 도면 윤곽**(`RoundCalc.calc(sampleInput(t)).drawing`)을 축 아래로 접어
  금속 그라데이션으로 칠한 측면도 + 1점 쇄선 중심선 + 치수선(보조선·화살·90° 돌린 지름 이름) + 밑줄 지시선 이름으로 그린다 — 그림이 계산의 해석과 같다.
  견본 비율·이름 자리·치수선 높이는 04 의 카드 몸통(193 × 189dp)을 잰 비율이라 입력 화면 요약 칸(132dp)에서도 같은 모양이다.
  형상 2 의 주 R 은 초록, L2(B) 의 B 는 파랑, L2(A) 의 A 는 빨강(04 와 같음). `RoundStateTest.artSamplesSolve` 가 견본이 늘 풀리는지 본다(안 풀리면 카드가 빈다).
- 칩: 홀수 하늘 · 짝수 민트(`chipSky`·`chipMint`), 카드 테두리 2dp(고른 카드는 청록 3dp — 원본 사진에 없음). 섹션 줄 오른쪽 흰 네모 버튼은 용도가 사진에 없어 수동(3점호) 바로 가기로 쓴다.
- 모드 두 개는 **같은 계산의 두 보기**다(calc-design.md): 접점 = 도면 점·`drawing`, NC 는 같은 입력을 노즈R 0 으로 푼 것(= 도면 좌표, 기계 G42 보정과 함께 씀).
  합성 R = 보정점·`center`·`program`·`progR`·`nc`(G40, 가상 인선). 노드 표는 점마다 지금 모드의 X · Z 를 크게(numLarge), 다른 모드 값을 그 아래 작게('도면 …' / '보정 …') 보인다.
  치수 · 공구 칸은 키패드 묶음("다음", 키패드 그림은 그 치수 하나만 초점색), 노드 표가 화면 밖이면 아래 결과 띠(첫 점 · 끝 점 · 외 n점).
- 입력값: 치수 칸은 형상마다 따로, 노즈R·Z 여유·끝 요소 길이·팁은 함께, 수동 칸은 따로. 처음 값은 그림 모양대로 풀리는 값(형상 3 은 calc-design.md 의 손 계산,
  비울 수 있는 칸 하나는 비워 계산값이 자리표시로 보인다). 형상 1~6 은 외경 정방향(팁 3, G42) 하나, 수동은 외경/내경 + 끝 Z 로 방향을 정한다.

## 직각삼각형 화면 (`screens/TriangleScreen.kt`, `screens/triangle/`)
| 파일 | 내용 |
|---|---|
| `TriangleScreen.kt` | `TriangleScreen()` — 그림 → 아는 값 다섯 칸 → 경고 → 결과 표 → X · Z 연동, 도움말 `TriangleHelp` |
| `triangle/TriangleModel.kt` | `TriField`(칸 다섯: 밑변 a · 높이 b · 빗변 c · 각도 A · 각도 B), `TriangleState`(값 + `baseIsZ` + Saver), 경고 글 |
| `triangle/TriangleArt.kt` | `TriangleFigure(s, sol)` — 삼각형 Canvas, 값을 제자리 칸에(넣은 값 = 보라 칸, 계산값 = 흰 칸, 모르면 ?), 왼쪽 위 Z · X 화살 |

- 원본 사진이 메뉴 카드뿐이라 화면 전체를 우리가 정했다. 헤더 "직각삼각형 계산"(보라 그라데이션, 메뉴 #6430C5 계열 = violet-700).
- 다섯 칸 가운데 아는 둘(빈칸은 계산값을 "값 자동" 자리표시로). 셋 이상이면 계산기가 맞는지 본다. 경고는 모두 계산을 멈춘 것이라 빨강.
- 그림 모양은 각도 A 를 따르되 22°~58° 로 눌러 그린다(칸이 겹치지 않게). 그림 높이는 삼각형에 맞춰 정한다.
- X · Z 연동: "밑변 = Z · 높이 = X" / "밑변 = X · 높이 = Z" 세그먼트(`baseIsZ`), X 는 직경값(반경 × 2). 값은 크기만 보이고 부호는 가공 방향에 맞게 붙인다고 적는다.
- 처음 값: 밑변 10 · 각도 A 25°(A = 30° 면 X 직경 = 빗변이 되어 헷갈려서 피했다).
