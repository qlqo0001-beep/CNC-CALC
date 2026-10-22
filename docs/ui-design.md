# 화면 기초 (`:shared`)

기능 화면을 채우는 사람이 볼 문서. 원본 화면 명세는 `screens.md`, 계산 API 는 `calc-design.md`.
확인: `gw.bat :shared:jvmTest` → `build/shots/<이름>-light.png`·`-dark.png` 를 `docs/store` 원본과 나란히 본다(아래 '스크린샷 도구').
3단계 다듬기(그림 · 입력 · 결과 · 헤더)의 원칙은 아래 '원칙', 원본과 일부러 다르게 한 것은 `screens.md` 의 '원본과 다르게 한 것'.

## 파일 지도 (`shared/src/commonMain/kotlin/com/bmt/nosecalc`)
| 파일 | 내용 |
|---|---|
| `App.kt` | `App(state)` — 7기능 라우팅, `AppHost`(테마 + 위층 + 뒤로) |
| `AppState.kt` | 화면 쌓임(`open`·`back`·`hasBackStep`), `menuOpen`·`helpOpen`, 테마 저장(`setTheme(null\|false\|true)`), 보정 방식 `compMode`, `toast(글)`, PC 키(`handleKey`), `OnBack` |
| `Feature.kt` | 7기능 이름(메뉴용·헤더용)·`ready`(다 만든 기능), 메뉴 순서 |
| `KeyValueStore.kt` | expect/actual 설정 저장(안드로이드 SharedPreferences, PC `~/.bmt-nosecalc/settings.properties`) — 테마 · 보정 방식 · PC 창 크기 |
| `screens/*Screen.kt` | 기능마다 한 파일 + 도움말 `XxxHelp`(`HelpSection` 목록). 아직 안 만든 기능은 `ComingSoonPage` |
| `screens/TaperScreen.kt`, `screens/taper/` | 테이퍼·홈 화면(아래 절) |
| `screens/RoundScreen.kt`, `screens/round/` | 라운드 R 화면(아래 절) |
| `screens/TriangleScreen.kt`, `screens/triangle/` | 직각삼각형 화면(아래 절) |
| `ui/theme/AppColors.kt` | 색 토큰 라이트·다크(`RoleColors` · `FeatureColors` · `onFill`), KDoc 에 대비표 |
| `ui/theme/AppTheme.kt` | `AppTheme(dark)`, `AppTheme.colors / sans / mono` (CompositionLocal 하나) |
| `ui/theme/AppType.kt` | 글자 단계 `AppType`, 여백 `Space`, 모서리 `Radius` |
| `ui/FeatureScaffold.kt` | 기능 화면 틀(헤더 + 본문 640dp), APP MENU 패널(화면 테마 · 준비 중 묶음), 도움말 시트(`HelpSection`) |
| `ui/Header.kt` | `FeatureHeader`, `CircleIconButton`, `AppsButton`, `PillButton` |
| `ui/Components.kt` | `AppCard`, `TypeCard`, `CardHeader`, `CoordPair`, `LabeledValue`, `ExpandableHint`, `Segmented`, `OutlineTabs`, `WarningBanner`(`WarnLevel`) |
| `ui/NumberInput.kt` | `NumberField`, 숫자 키패드(`NumberPad`), 글 고치기 `NumEdit` |
| `ui/FieldChain.kt` | 키패드 칸 묶음 `FieldChain` · `rememberFieldChain` · `FieldChainHost`("다음") |
| `ui/DrawKit.kt` | 그림 한 벌의 선 규칙 `DrawSpec` · `artScale` · `centerLine` · `arrowHead` · `haloText` · `linearDim` · `hatchFill` |
| `ui/ShapeViewer.kt` | `ShapeViewer(lines, marks, state, stock, fills)` — calc 의 `Contour` 를 그린다. `ViewerLine` · `ViewerMark`(`MarkKind`) · `ViewerStock` · `ViewerFill` · `Arrow` · `ViewerState`(`UserView`) |
| `ui/ViewerPanel.kt` | 형상 확인 카드 `ShapeViewerCard`(−/+/맞춤 · 크게 보기 · 범례 `LegendItem` · `LegendGlyph`) |
| `ui/ResultDock.kt` | 아래 결과 띠 `ResultDock`(`DockStatus` · `DockRow` · `oneLine`) |
| `ui/Overlays.kt` | `BottomSheet`, `Modal`, `SheetTitle`, 토스트 층 |
| `ui/ComingSoonPage.kt` | 준비 중 화면 `ComingSoonPage` · 도움말 `ComingSoonHelp` |
| `ui/AppIcons.kt`, `ui/FeatureIcon.kt`, `ui/BmtLogo.kt` | 선 아이콘, 기능 아이콘(Canvas), BMT 표식·글자·로고 |

## 원칙 (3단계 다듬기)
- **숫자는 그대로**: 화면 숫자 · NC 는 `UiGoldenTest` 골든(`jvmTest/resources/golden/*.txt`)이 지킨다. 바꾸는 것은 사용자가 승인한 것뿐이고, 그때는
  `-DupdateGolden=true` 로 다시 쓰고 바뀐 줄을 커밋 글에 적는다.
- **원본의 순서는 그대로**: 종류 → 방향 → 형상 → 노즈R → T1/T2 → 결과 → 형상 확인 → NC. 칸을 옮기거나 빼거나 처음 값을 바꾸는 것은 사용자 결정(`stage2-report.md` · `screens.md`).
- **화면 글은 한글**, NC · 치수 기호(X Z T R D L Ø G02 G41 …)와 단위(mm, °)는 라틴 그대로.
- **이름 규칙**: 화면의 T 는 **도면 점**(T1 · T2 · T3)이다. 공구 팁 번호는 늘 **'팁 N번'**(칸 · 시트 · 요약 · 도움말). NC 주석 `(R0.4 T3)` ·
  `(G40 DIRECT R0.4 T3)` 은 기계 공구 보정 화면의 T 칸 이름이라 그대로. 3점호의 점은 P1 · P2 · P3, 라운드 치수는 주 R · 모서리 R(사용자 결정 — 바꾸지 않음).
- **숫자는 잘못 읽을 수 없게**: 숫자 글자는 바탕 대비 4.5:1 이상(결과 좌표 약 7:1), 뜻 있는 그림 선은 3:1 이상 — 라이트 · 다크 모두.
  넣은 값과 계산값은 색 + 표("자동") + 굵기로 가른다(흐리게 하지 않는다). 값은 움직이지(애니메이션) 않는다. 고정폭 글꼴은 0 안에 점이 있는 JetBrains Mono.
- **색의 뜻은 고정**(ISA-101): 빨강 = 계산 멈춤 · 되돌릴 수 없는 일, 노랑 = 확인할 것, 파랑 = 안내. 기준선 · 시작/끝점 · 각도 같은 보통 요소에는
  빨강 · 노랑을 쓰지 않는다. 색만으로 가르지 않는다 — 아이콘 · 선 모양 · 글을 같이.
- **장갑 · 한 손**: 누르는 것은 48dp 이상(보이는 40dp 이상 + `Modifier.minimumInteractiveComponentSize()`), 이웃과 8dp. 손짓(두 손가락 · 끌기 · 두 번 누름 · 휠)마다
  보이는 단추 하나가 있다. 자주 누르는 것은 아래쪽.
- **움직임은 짧게**: 시트 250ms 이하, 단추 반응 100ms 이하, 토스트 2.5초, 결과에는 꾸밈 움직임 없음.
- **폰은 한 줄 배치**(412dp, 360dp 도 본다). PC 창은 본문을 가운데 640dp 로 묶는다(두 칸 배치는 미룸).

## 기능 화면 채우기
- `screens/XxxScreen.kt` 의 `ComingSoonPage(Feature.X)` 자리를 바꾼다. `FeatureScaffold(Feature.X, XxxHelp, helpStart = 0) { … }` 의 본문은
  `BoxScope`(화면 크기), 스크롤은 화면이 `Column(Modifier.verticalScroll(…))` 로 정한다. 헤더(← 뒤로 · 제목 · 메뉴 · 도움말)·뒤로 키는 틀이 처리한다.
- 화면 Column 은 `.padding(horizontal = Space.gutter)` + `spacedBy(Space.cardGap)`, 카드 안 여백 `Space.cardPad`, 카드 머리는 `CardHeader`.
- 입력값은 `rememberSaveable` 로 — `App` 이 화면마다 `SaveableStateHolder` 로 감싸 다른 기능에 다녀와도 남는다.
- 화면 안 단계(예: TYPE 고르기 → 입력)를 뒤로 키·Esc 로 되돌리려면 `OnBack(enabled = 단계 > 0) { 단계-- }`.
  나중에 그려진 `OnBack` 이 먼저 받는다(키패드·모달 → 메뉴 → 화면 안 단계 → 앞 화면). 첫 화면에서 갈 곳이 없으면 안드로이드는 앱을 닫는다.
- 도움말은 같은 파일의 `XxxHelp = listOf(HelpSection(제목, 문단들, 그림))`. 그림(`extra`)은 제목 바로 아래(그림 먼저), 시트 끝에는 늘 '장갑을 끼고 쓸 때'.
  `helpStart` = 시트를 열면 먼저 보일 묶음(입력 화면이면 '값 넣기' 등). 문단 목록만 주면(`List<String>` 꼴) 제목 없는 한 묶음이 된다.
- 다른 기능으로 보내기: `LocalAppState.current.open(Feature.X)`(이미 쌓인 화면이면 그 위를 걷어 낸다), 안내: `.toast("…")`.
- **화면 글은 되도록 한글**(10-17 사용자 요청). 그대로 두는 것은 NC·치수 기호(X · Z · T1 · R · D · L · G02/G03 · G41 …)와 단위(mm, °).
  모든 기능의 헤더 제목(`Feature.title`·`sub`)과 메뉴 카드(`menuTitle`·`menuSub`), 메뉴·닫기 버튼, 테이퍼·라운드·직각삼각형 화면을 맞췄다.
  바꾼 표는 `screens.md` 첫머리 — 3·4단계 화면도 그 표를 따른다.

## 부품
### 입력: `NumberField` · `FieldChain` · 키패드
- `NumberField(label, value: String, onValueChange, modifier, unit = "", placeholder = "", autoValue = null, error = false, tag = null, hint = null,
  blankable = false, id = label, figure = null, preview = null)` — 위 라벨 줄(`AppType.label`) + 52dp 칸, 값 20sp 굵게(넘치면 11sp 까지 줄이고 그래도 넘치면 "…").
  - 값은 **쓴 그대로의 글**("5.0", "-25", "" = 빈칸). 계산에는 `value.toDoubleOrNull()` — calc 입력의 `Double?`(빈칸 = 풀 값)과 바로 맞는다.
  - 칸 모양(색 + 표 + 굵기 — 색만으로 가르지 않는다): 넣은 값 = 진한 굵은 글 · `autoValue` = 빈칸일 때 계산값(청록 옅은 바탕 · 테두리 + `valueComp` 글 +
    라벨 줄 "자동" 표), `autoValue = ""` = 못 풂("—" + "자동 · 계산 안 됨") · `error` = 빨강 2dp 테두리 · 옅은 빨강 바탕 · 라벨 앞 멈춤 아이콘 ·
    `tag` = 라벨 뒤 회색 표("Ø직경" · "반경") · `placeholder` = 빈칸 자리표시("0", 흐리게).
  - `hint` · `blankable` · `figure` · `preview` 는 키패드로 간다(안내 줄 · "비우기 · 자동 계산" 단추 · 작은 그림 · 결과 한 줄).
- `FieldChainHost(rememberFieldChain(처음 열 칸 id)) { 칸들 }` — 안의 `NumberField` 가 `id` 로 스스로 들어오고, 순서는 화면 자리(위 → 아래, 같은 줄은 왼 → 오른).
  키패드는 묶음이 하나만 띄운다: 오른쪽 열 C · ⌫ · 닫기 · **"다음"**, 제목 오른쪽 "n / m". 연 칸은 시트 위로 올라오고(`chain.sheetOpen` 이면 화면 끝에
  440dp 빈자리), 다음 · 앞 칸으로 가도 시트는 그대로다. 모든 화면의 칸이 묶음 안에 있다 — 묶음 밖 `NumberField` 는 모양만 보이는 견본(누를 수 없음).
- 키패드(`NumberPad`): 열면 지금 값이 통째 선택돼 처음 누른 숫자 · . · − 가 새 값이 된다(− 로 새 음수, ⌫ 는 원래 값을 고친다). **확인 단추는 없다** — 누를 때마다
  칸 · 결과 · 그림 · NC 에 바로 들어간다. 닫기(닫기 키 · 바깥 누름 · 뒤로 · Esc · 손잡이 끌어내림)는 넣은 값을 그대로 두고, "-" 뿐이면 연 때 값으로. 제목 오른쪽
  "취소" 는 연 때 값으로 되돌리고 닫는다. 위에 결과 한 줄(`preview` — 넘치면 9sp 까지 줄여 끝 점 좌표가 잘리지 않게), 시트 자리가 넉넉하면(780dp 이상) 제목 아래 작은 그림(`figure`).
  PC 는 실제 키보드: 숫자 · . · − · Backspace · Delete = C · Enter · Tab = 다음(마지막 칸은 닫기) · Shift+Tab = 앞 칸 · Esc = 닫기.

### 카드 · 글
- `AppCard(modifier, padding = 16.dp)` — 흰 카드(라이트 1dp 테두리 + 옅은 그림자, 다크는 테두리만).
- `CardHeader(title, subtitle, modifier, trailing)` — 카드 머리: 제목(`cardTitle`) + 부제(`caption`) + 오른쪽 단추 자리. 아래로 12dp.
- `CoordPair(x, z, color, modifier, size = 24.sp, dx, dz)` — X | Z 두 칸, 축 글자가 제 값 바로 옆, 오른쪽 맞춤 고정폭, 아래 "Δ …". 칸보다 길면 12sp 까지 줄여 한 줄.
- `LabeledValue(label, value, modifier, color = textValue, size = 17.sp)` — 이름(12sp 흐림) 위, 고정폭 굵은 값 아래(각도 · G00 진입점 · 단 수 …).
- `ExpandableHint(short, long)` — 한 줄 설명 + '자세히/접기'(48dp). 긴 규칙 문단은 이렇게 접는다.
- `keepWords(s)` — 한글 음절 사이 · 한글과 붙은 영문 · 숫자 · 닫는 괄호 사이에 낱말 이음(\u2060)을 넣어 줄이 띄어쓰기에서만 바뀌게 한다
  (Compose 는 한글 음절 사이 어디서나 줄을 바꿔 '확 / 인하세요' 처럼 어절이 쪼개졌다). `ExpandableHint` 의 두 글 · `WarningBanner` 본문 · 도움말 문단이 쓴다.
- `TypeCard(chip, caption, chipColors, selected, onClick, modifier, aspectRatio) { 그림 }` — 형상 카드: 36dp 머리 띠(칩 "형상 N" + 짧은 이름) + 몸통.
  고르면 2dp 청록 테두리 + 옅은 청록 바탕 + 오른쪽 위 체크 원. 테이퍼(민트 칩) · 라운드(홀수 하늘 · 짝수 민트) 고르기 화면이 쓴다.
- `Segmented(options, selected, onSelect, color = blue, track = segTrack, height = 48.dp, fontSize = 16)` — 고른 칸 글자색은 `c.onFill(color)`
  (바탕 휘도 > 0.3 이면 짙은 글자 — 다크의 밝은 청록 위 흰 글자가 2:1 아래였다). 방향 두 칸 · 외경/내경 · 화면 테마 · L2 기준 등.
- `OutlineTabs(rows: List<List<String>>, selected, onSelect)` — 테이퍼 가공 종류 탭(02), 번호는 줄을 이어 센다(0~4).
- `WarningBanner(text, modifier, level = WarnLevel.Warn|Error|Info, collapsedText = null, actions = emptyList())` — 왼쪽 4dp 색 띠 + 모양이 다른 아이콘 + 머리말:
  - `Warn` 노랑 삼각형 "확인 필요 — 결과는 나왔습니다"(숫자와 함께 나온 경고 — 결과 카드 위),
  - `Error` 빨강 팔각형 "계산 멈춤 — 결과·NC 없음"(요약 바로 아래, 고칠 칸은 `NumberField(error = true)`),
  - `Info` 파랑 원, 머리말 없음(값 부족 "값을 넣으면 바로 계산합니다 …", 늘 뜨는 바이트 반대쪽 면 등).
  `collapsedText` 가 있으면 한 줄(44dp, "… ⌄")로 접혀 시작하고 누르면 편다. `actions` = 글 아래 알약 단추(예 "각도 비우고 계산" · "L2 비우기").
  경고 enum → 한국어 글은 화면이 고른다(`taperWarningText` · `grooveWarningText` · `roundWarningText` · `triangleWarningText`).
- `ResultDock(status: DockStatus, visible, onJump)`(`BoxScope` 안) — 결과 카드가 화면 밖일 때 아래에 붙는 띠. `DockStatus.Ok(rows)` · `Warn(n, rows)` ·
  `Error(글)`, `DockRow(이름, x, z, 색, value 색)`(점이 둘을 넘으면 처음 · 끝 + "외 n점"). 오른쪽 48dp "결과"/"칸 보기" 단추. 띠만 150ms 로 오르내린다.
  `DockStatus.oneLine()` 은 키패드 미리보기 한 줄.

### 형상 확인: `ShapeViewer` · `ShapeViewerCard`
- `ShapeViewerCard(lines, marks, legend, modifier, title = "형상 확인", subtitle, stock, fills, state = rememberViewerState(), footer)` — 화면은 이것을 쓴다.
  머리(제목 · 부제 · 크게 보기) → 뷰어(높이는 그림 상자 비율: 폭 × 세로/가로(0.45~1.1) + 76dp 를 220~400dp, 넓은 창 520dp) → 단추 줄 **− · + · 맞춤**
  (직접 정한 보기면 맞춤이 청록) → `footer`(라운드 내경의 보링바 자리 알약) → 범례(`LegendItem(색, 이름, LegendGlyph)`, 줄을 바꿔 잘리지 않는다).
  **크게 보기**는 화면 전체(머리 · 닫기 · 범례 · 뷰어 · 아래 단추 줄과 footer), 한 손가락 이동 · Ctrl 없는 휠 확대, 키 + · − · 0, Esc · 뒤로 키로 닫기.
  카드와 크게 보기는 같은 `ViewerState` 를 써 확대가 이어진다.
- `ShapeViewer(lines, modifier, marks, state, oneFingerPan = false, stock, fills)` — 그림 약속은 calc 와 같다(Z 오른쪽 +, X(반경) 위 +, 원호는 `Arc.cw` 그대로 —
  G02 = 화면에서 시계).
  - 손짓: **한 손가락 끌기와 Ctrl 없는 휠은 페이지 스크롤**(위 가운데에 "두 손가락으로 확대·이동" / "Ctrl + 휠로 확대 · 끌어서 이동" 안내 1.5초),
    두 손가락 확대 · 이동, PC 는 Ctrl + 휠 확대 · 마우스 끌기 이동. 두 번 누르기도 맞춤이지만 늘 보이는 '맞춤' 단추가 같은 일을 한다.
  - `ViewerState`: 보기 = 맞춤의 배수(0.5~200) + 가운데 점(`UserView`). 폰 회전 · 창 크기가 바뀌어도 같은 곳, 치수를 고쳐도 보이던 곳에 새 그림이 걸치면 그대로.
  - 그리는 순서: 격자(1·2·5 간격, 다섯째 줄 굵게) → 소재(`ViewerStock`: 외경은 윤곽과 중심선 사이, 내경은 위, `mirror` 면 맞은편 벽까지 — 면 + 45° 빗금,
    "◀ 척 쪽") → 면(`ViewerFill`) → 중심선 X0 · Z0(1점 쇄선) → 윤곽 → 원호 중심 지시선 → 점 → 뷰어 알약(척 쪽 · Z0 · 중심 · 왼쪽 아래 **축척 막대** "2 mm · X 반경")
    → 점 라벨.
  - `ViewerLine(contour, color, width = 2.dp, dashed, dotted, arrows = Arrow.None|Filled|Hollow, fill)` — 끊은 선(노즈 중심) · 점선(급송) · 진행 화살(경로 = 채움,
    급송 = 빈 화살, 40dp 넘는 토막 가운데).
  - `ViewerMark(pt, color, label, kind = MarkKind.Ring|Dot|Square|Cross, info, arc)` — T 점 · 노드 · 진입/후퇴 · 원호 중심. `arc` 가 있으면 중심에서 원호 가운데까지
    끊은 지시선, 화면 반지름 14dp 미만이면 뺀다. 라벨은 점 둘레 **알약**: 8곳 × 4겹 자리에 점수(다른 라벨 · 점 · 알약과 겹침, 지나는 선, 거리)를 매겨
    고르고(같은 꼴 점 — 노드 둘 — 이 제 점만큼 가까운 자리는 무겁게, 지시선이 앞 라벨을 지나는 자리도), 뷰어 4dp 안쪽만, 16dp 넘게 떨어지거나
    제 점보다 같은 꼴 다른 점에 더 가까우면 지시선, 가장 나은 자리도 앞서 둔 라벨과 겹치면 라벨은 빼고 점 표시만. 라벨 글(13sp)은 글꼴 1.1배까지(`artFontCap`).
    T 점 → 노드 → 진입 · 후퇴 → 원호 중심 순으로 자리를 잡는다. '중심' 알약이 점 표시(내경 깊은 끝 노드)를 덮으면 중심선 오른쪽 끝으로.
    짧은 토막뿐인 선의 화살은 끝에서 9dp 앞에서 멈춘다(끝 표시 □ 에 붙지 않게, 그만큼도 안 되면 화살 없음).

### 틀 · 위층
- `FeatureScaffold(feature, help: List<HelpSection>, helpStart = 0) { 본문 }` — 헤더 `FeatureHeader`(48dp 단추: [← 뒤로] 제목 · 메뉴 · ? 도움말 — 모든 화면 같은 꼴,
  ← 는 돌아갈 곳이 있을 때만), 본문은 가운데 최대 640dp. APP MENU 는 헤더 아래로 펼쳐지고(지금 쓸 수 있는 계산 → '준비 중' 묶음, 맨 위 '화면 테마: 자동 · 밝게 ·
  어둡게'), 준비 중 카드는 토스트만 띄운다.
- `HelpSection(title, items, extra)` — 도움말 시트 한 묶음. 시트는 창 높이 85% 까지, 머리 오른쪽 '닫기' 알약, 머리 아래 1dp 선.
  `helpStart` 묶음부터 열 때는 끝에 빈 자리(목록 높이 60 %)를 두어 그 묶음이 맨 위까지 올라온다.
- `BottomSheet(onDismiss, onKey, scrimAlpha, maxHeightFraction, onHeight) { … }`(위 모서리 20dp · 220ms · PC 넓은 창은 가운데 560dp · 손잡이 띠 24dp 를
  80dp 넘게 끌어내리면 닫힘), `Modal(title, onDismiss) { … }`(가운데 카드, 최대 480dp), `SheetTitle(title, trailing, action)` — 부른 자리와 상관없이 화면 전체 위에 뜬다
  (`if (열림) BottomSheet(...)` 식으로 쓴다). 토스트는 아래(바닥에서 96dp — 결과 띠 위) 2.5초.
- `PillButton(text, icon, onClick, danger)`(보이는 40dp · 누르는 자리 48dp, danger 는 되돌릴 수 없는 일에만), `CircleIconButton(icon, 설명, onClick, tint, outlined, size = 44.dp)`,
  `AppsButton(open, onClick)`.
- `ComingSoonPage(feature)` — 준비 중 안내 + 지금 쓸 수 있는 계산 셋으로 바로 가기(56dp), 도움말 `ComingSoonHelp`.
- PC 창(`desktopApp`): 처음 480 × 960dp, 옮기거나 크기를 바꾸면 0.5초 뒤 `win.w · win.h · win.x · win.y` 를 저장해 다음에 그 자리로(최대화 · 최소화 때는 저장 안 함).

## 테마·색
- 쓰는 법: `val c = AppTheme.colors` → `c.page`, `c.card`, `c.textBody`, `c.drawT1` …. 숫자는 `AppType.num*`(고정폭 굵게) 또는 `fontFamily = AppTheme.mono`.
- 라이트 = `screens.md` 1.1 값(11.png 의 Tailwind slate 계열 우선) — 다만 화면 바탕은 원본 slate-300 보다 밝은 #E8EDF3(카드와 글이 더 또렷하게, `screens.md` '원본과 다르게 한 것').
- 다크("딥 다크", 우리가 정함): 바탕 #0D1624 · 카드 #142032 · 입력칸 #1B2A3F · 뷰어 #0B1421. 강조색은 같은 색상의 300~400 단계. 다크에선 그림자 대신 1dp 테두리.
  새 색이 필요하면 `AppColors` 에 라이트·다크 둘 다 넣고, 숫자 · 선이면 아래 대비표에 더한다.
- 화면 테마는 메뉴 안 '화면 테마: 자동 · 밝게 · 어둡게'(`AppState.setTheme(null|false|true)`, 저장된다). 자동 = 시스템 설정을 따른다.

**토큰** (라이트 / 다크)

| 묶음 | 토큰 | 뜻 · 값 |
|---|---|---|
| 바탕 · 면 | `page` · `card` · `subtle` · `field` · `segTrack` · `headerDim` · `menuTop` · `menuBottom` · `scrim` | 화면 #E8EDF3 / #0D1624, 카드 #FFFFFF / #142032, 입력칸 #F1F5F9 / #1B2A3F, 메뉴 열림 헤더 · 메뉴 패널 · 뒤 덮개 |
| 뷰어 | `viewport` · `viewportGrid` · `viewGridMajor` · `viewportAxis` · `viewport3d` | 뷰어 바탕 #EFF2F7 / #0B1421, 격자 · 굵은 격자 · 축, 3D 뷰포트(시뮬레이터 자리) |
| 선 | `lineCard` · `lineField` · `lineStrong` · `lineTabIdle` · `lineFocus` | 카드 테두리, 입력칸 테두리, 진한 선, 탭 테두리, 고치는 칸 · 그림 초점(#617FC5 / #7896E6) |
| 글 · 아이콘 | `textStrong` · `textValue` · `textBody` · `textMuted` · `textHint` · `icon` · `onAccent` | 제목, 넣은 값, 본문, 설명, 자리표시, 아이콘, 채운 강조 위 글 |
| 강조 | `teal` · `tealDeep` · `blue` · `green` · `amber` · `red` · `indigo` · `orange` · `purple` · `appsFill` · `appsBorder` · `appsOpenBg` · `circleBg` · `bmtBlue` | 청록(고른 것 · 켜진 것), 기능색 · 메뉴 단추 · 헤더 원형 단추 바탕 · BMT |
| 결과 숫자 | `valueComp` · `valuePos` | 계산값 · 보정 좌표 #0F5E8C / #7DD3FC(7:1), 위치 값 |
| 역할 한 벌(`RoleColors` bg · border · label · value) | `fieldNormal` · `warn` · `error` · `info` · `chipSky` · `chipMint` | 입력칸, 노랑 확인 띠, 빨강 멈춤 띠 · 칸, 파랑 안내 띠, 라운드 형상 칩(홀수 하늘 · 짝수 민트) |
| 토스트 | `toast` · `onToast` | 짙은 띠(다크는 밝은 띠) + 글 |
| 그림 | `drawProfile` · `drawRef` · `drawT1` · `drawT2` · `drawAngle` · `drawToolBody` · `drawToolLine` · `drawInsert` · `drawMetal` · `drawMetalEdge` · `drawStock` · `drawHatch` | 윤곽, 기준 · 보조 · 중심선(회색 — 빨강 아님), T1 주황 · T2 청록, 치수 · 각도(파랑), 바이트 · 인서트, 라운드 금속 몸통, 소재 면 · 45° 빗금 |
| 형상 확인 | `viewNose` · `viewPath` · `viewRapid` · `viewLabelT1` · `viewLabelT2` · `drawHolder` · `drawHolderFill` · `simProgram` · `simArc` | 노즈 원 · 노즈 중심 경로, 프로그램 경로(보라), 급송, 뷰어 T 이름표, 보링바 단면(초록 — 간섭 빨강과 갈림), 시뮬레이터 자리 |

**대비** (WCAG, 라이트 / 다크 — `AppColors` KDoc 과 같은 표. 바탕: 카드 #FFFFFF / #142032, 뷰어 #EFF2F7 / #0B1421)

| 토큰 | 라이트 | 다크 | 바탕 |
|---|---|---|---|
| valueComp | 7.0 | 9.8 | 카드 |
| drawRef | 4.8 | 6.4 | 카드 |
| drawAngle | 5.9 | 9.1 | 카드 |
| drawToolLine | 7.6 | 11.0 | 카드 |
| drawMetalEdge | 10.4 | 11.0 | 카드 |
| drawToolBody · drawStock · drawHatch (면 · 빗금 — 글 · 선 아님) | 1.5 · 1.2 · 1.5 | 2.4 · 1.2 · 1.6 | 카드 |
| viewportAxis | 4.2 | 3.9 | 뷰어 |
| viewNose · viewPath · viewRapid | 5.3 · 5.6 · 4.2 | 8.6 · 7.5 · 7.2 | 뷰어 |
| viewGridMajor (보조선) | 1.4 | 1.5 | 뷰어 |
| viewLabelT1 · viewLabelT2 | 4.5 · 4.9 | 11.1 · 9.9 | 뷰어 |
| drawHolder | 4.5 | 10.6 | 뷰어 |
| onToast | 12.8 | 13.1 | 토스트 |
| info.label · info.value | 6.2 · 9.5 | 6.6 · 11.8 | info.bg |
| warn.label · error.label (띠 머리말) | 4.8 · 5.7 | 9.4 · 6.3 | 각 띠 bg |

## 글꼴·크기
- Noto Sans KR 가변 글꼴(google/fonts `ofl/notosanskr`, wght 100~900) 하나에서 400~900 을 꺼내 쓴다. 고정폭(결과 숫자 · NC · 좌표)은 **JetBrains Mono** Regular/Bold
  (`jetbrains_mono_*.ttf`, JetBrains/JetBrainsMono v2.304): 0 안에 점이 있어 G00 이 GOO 로 읽히지 않고, − 가 길어 −5.234 와 5.234 가 갈린다. 글자 폭은 예전
  Courier Prime 과 같은 0.6em 이라 자리는 그대로다. 한글은 고정폭에 없어 시스템 글꼴로 그린다(노드 표의 "없음" 등). OFL 전문은
  `composeResources/files/licenses/`(`NotoSansKR-OFL.txt` · `JetBrainsMono-OFL.txt`)에 같이 들어간다. 폰·PC 가 같은 글꼴로 그린다.
- "BMT" 글자는 글꼴 대신 Noto Serif Black 윤곽을 벡터로 넣었다(`BmtWordmark`) — 폰과 PC 의 세리프 글꼴이 달라서.
- `AppType`(색은 넣지 않는다 — `Text(style = AppType.numHero, color = c.valueComp)`):

| 단계 | 크기 · 굵기 | 쓰는 곳 |
|---|---|---|
| `numHero` | 고정폭 24sp 굵게 | 기계 앞에서 읽는 결과 좌표 |
| `numLarge` | 고정폭 18sp 굵게 | 노드 표 · 보조 좌표 |
| `numBody` | 고정폭 15sp 굵게 | NC 줄 · 표 |
| `numSmall` | 고정폭 12.5sp 굵게 | Δ · 작은 숫자 |
| `screenTitle` | 20sp 굵게 | 헤더 제목(넘치면 16sp 까지 줄이고 …) |
| `sheetTitle` · `cardTitle` | 18 · 16sp 굵게 | 시트 · 카드 제목 |
| `label` | 14sp SemiBold | 칸 이름 |
| `body` · `caption` | 14sp / 21 · 12.5sp / 18 | 본문 · 설명 · 힌트 |

- `Space`: 화면 양옆 `gutter` 12dp · 카드 안 `cardPad` 16dp · 카드 사이 `cardGap` 12dp · 입력칸 사이 `fieldGap` 12dp.
- `Radius`: 시트 20dp · 카드 14dp · 단추 · 칸 10dp · 표 6dp.
- **크기 주의**: 원본은 Tailwind px 크기(제목 20 · 카드 제목 16 · 부제 12~13)로 보인다. `screens.md` 1.2 의 추정 sp 는 라틴 글자 기준
  약 15% 크게 잡혀 있다 — 스크린샷을 원본과 나란히 놓고 폭으로 맞춘다(헤더·메뉴는 그렇게 맞췄다).

## 그림 도구 (`ui/DrawKit.kt`)
형상 그림(테이퍼 `TypeArt` · 라운드 `RoundTypeArt` · 삼각형 · 팁 그림 · 보링바 스케치)은 한 벌의 선 규칙(ISO 128 꼴)을 쓴다.
| 역할 | 규칙 |
|---|---|
| 굵은 윤곽(`DrawSpec.profile`) | 2.4dp, `drawProfile` — 깎는 윤곽 · 부품 |
| 치수선 · 보조선 · 지시선(`dim` · `ext` · `leader`) | 1.0 · 0.8 · 0.8dp, 보조선은 점에서 2.5dp 띄우고 치수선을 3dp 넘게. 치수 · 각도 색 `drawAngle` |
| 화살촉(`arrowHead`) | 채운 촉 6dp(반폭 0.3). 짧은 치수는 보조선 바깥에서 안으로 겨누고 이름은 끝 너머(`linearDim`) |
| 글(`haloText`) | 바탕색 테(halo 3dp, 둥근 이음)를 먼저 — 흐린 그림자 없음. `pill` 이면 알약(초점 표시) |
| 중심선(`centerLine`) | 1점 쇄선(9 · 2.5 · 2 · 2.5dp), 1dp, `drawRef`(회색) |
| 소재(`hatchFill`) | 면 `drawStock` + 그 안 45° 빗금(6dp 간격 · 0.75dp, `drawHatch`) |
| 배율(`artScale(w, base)`) | 그림 폭 / 기준 폭 → 선 0.9~1.3 · 글 0.95~1.25 배로 묶는다 — 360dp 폰에서 글 10.5sp 아래로, 넓은 PC 창에서 너무 크게 가지 않게. 자리는 폭 비율 그대로. 글은 시스템 글꼴 배율을 1.1 까지만 따른다(그 위는 나눔 — 큰 글꼴에서 이름끼리 겹치지 않게) |

## 스크린샷 도구 (`shared/src/jvmTest`)
- `Shots.both(이름, heightDp = 915, frames = 40, setup = { state -> … }, widthDp = 412, fontScale = 1f, frameMs = 100, tapDp = null, scrollToEnd = false) { state -> App(state) }`
  — 412×915dp, 밀도 2.625(1082×2402px). 부품만 볼 때는 `AppHost(state) { … }` 로 감싼다. 프레임마다 `frameMs` 를 흘려 글꼴 로딩·애니메이션을 끝낸다.
  키패드가 칸을 시트 위로 끌어올리는 장면은 0.1초 걸음이면 출렁여 `frames = 150, frameMs = 16`(폰 프레임)으로 찍는다. `tapDp` = 몇 프레임 뒤 한 번 누를 자리
  (칸을 눌러 연 키패드 · 크게 보기), `scrollToEnd` = 휠로 끝까지 내림(아래 결과 띠).
- 화면 숫자 골든 `UiGoldenTest`: 스크린샷 상태마다 경고 · 좌표 · NC 를 `golden/<이름>.txt` 로 고정 — 바뀌면 실패, `-DupdateGolden=true` 로만 다시 쓴다.
- 찍는 것(테스트 파일마다):
  - `ScreenShotTest`: `01-menu`, `02-taper`, `feature-<기능>`, `help`, `keypad` · `keypad-long`(13자), `modal`;
    **큰 글꼴 · 좁은 폰 묶음** — 장면마다 `-fs130`(글꼴 1.3배) · `-w360`(360dp): `01-menu`, `taper-b`(첫 화면 — 360dp 는 TaperShotsTest 의 `taper-b-w360`), `taper-b-keypad`, `round-b2`, `round-b2-keypad`,
    `triangle`, `help`, 내경 라운드 `round-pick-inner`(고르기) · `round-inner-b3-holder`(보링바 · 빨간 닿음) · `round-manual-inner-rev-holder`(뒤보링 + 보링바, 보통 판도);
    **깨지기 쉬운 상태** — `taper-b-big`(Z −1500) · `taper-b-max`(한도 끝) · `taper-b-warn-gt` · `taper-b-warn-concave` · `taper-b-against` · `taper-b-inner-double-rev` ·
    `taper-b-longnum` · `taper-groove-many`(80단) · `taper-groove-errors`(빨간 띠 셋) · `round-b2-concave` · `round-manual-error` · `triangle-huge` · `triangle-three`;
    경고 글 모아 보기 `texts-warn-taper` · `texts-warn-round`; PC 창 `pc-wide-a` · `pc-wide-keypad`(1280 × 720dp) · `pc-short-help` · `pc-short-keypad`(412 × 640dp).
  - `FoundationShotsTest`(`foundation-*`: 아이콘 · 경고 띠 · 카드 부품 · 결과 띠 · 그림 도구, -w360), `KeypadShotsTest`(`field-states` · `keypad-*`),
    `ViewerShotsTest`(`viewer-*`), `ShellShotsTest`(헤더 단추 48dp 재기 · 메뉴 · 아이콘 · 도움말 · `taper-b-wide` 1280dp),
    `TaperArtShotsTest` · `RoundArtShotsTest`(`art-*` 그림만, 폭마다), `TaperShotsTest`(`taper-*`), `RoundShotsTest`(`round-*` — 내경 · 보링바 카드 · 크게 보기 포함),
    `TriangleShotsTest`(`triangle-*`). 긴 화면은 heightDp 를 늘려 한 장에 찍는다.
- 프레임마다 나오는 그림은 바로 닫는다(네이티브 메모리). 안 닫으면 1700dp 넘는 스크린샷이 `Surface.makeRasterN32Premul` 에서 실패했다.
- 견고성 시험 `InputRobustnessTest`: 화면 처음 값에서 칸을 흔들어(키패드로 칠 수 있는 글 — 빈칸·0·음수·12자리·아주 작은 수)
  테이퍼·2중 테이퍼 6만, 라운드·3점호 3만, 홈·삼각형 2만 가지를 풀고, 예외가 없고 화면에 찍는 숫자가 모두 유한한지 본다.
  극단 값 화면도 실제로 그려 본다. 이 시험이 찾은 것: 홈 단 수 Int 넘침(빈 목록으로 죽음), 큰 수에서 NaN·끊긴 윤곽(→ `MAX_MM`).
  유한하지만 틀린 숫자(직각삼각형의 아주 큰 값이 `Fmt` 에서 Long 끝에 붙는 것)는 잡지 못한다 — `stage2-report.md` 6절.

## 테이퍼·홈 화면 (`screens/TaperScreen.kt`, `screens/taper/`)
| 파일 | 내용 |
|---|---|
| `TaperScreen.kt` | `TaperScreen()` — A 화면(가공 종류 탭 · 방향 두 칸 · 형상 카드 2열, 본문 600dp 넘으면 3열), 도움말 `TaperHelp`. 형상을 누르면 B, 뒤로 키·Esc 는 B → A |
| `taper/TaperModel.kt` | `TaperKind`(탭 순서 = 02), `TaperState`(화면 상태 + Saver), `TaperDefaults`(처음 값), `TaperSolve`·`Mirror`(역방향), 경고 글 |
| `taper/TypeArt.kt` | `TypeArt(kind, type, mirrored, modifier, compact, focus)` — TYPE 그림 Canvas(외경 기준, 내경은 위아래·역방향은 좌우 반전, 바이트도 반대쪽 — 단 내경 뒤보링 바는 인서트만 뒤집고 자루는 +Z 구멍 입구로, +Z 가 막힌 홑 2 · 4 만 통째로). compact = 요약 칸 · 키패드 꼴, focus = 고치는 칸만 초점색 |
| `taper/SlopeScreen.kt` | B 화면(외경·내경 · 2중 테이퍼): 요약 카드 → 입력(도면 치수 · 공구) → 보정 방식 스위치 → 경고 → 결과 카드 → 형상 뷰어 → NC |
| `taper/GrooveScreen.kt` | B 화면(외경 홈): (침범 경고) → 입력(홈 치수 · 공구 — 기준 인선은 공구 묶음) → 다단 진입 표·정삭 → 형상 → NC |
| `taper/Parts.kt` | 요약 카드, 점 이름표, 입력 묶음 구분선(`GroupDivider`), 노즈R 칩, 팁 번호 칸·시트·그림(`TipDiagram`, 도움말 `TipHelp`), 보정 방식 스위치, NC 카드(복사) |

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
  형상마다 X 오르내림과 시작·끝점의 뜻이 달라서), 노즈R·Z 여유·홈 치수는 함께. 보정 방식은 화면 상태가 아니라 앱 전체(`AppState.compMode`)다.
  처음 값(사용자 결정 2026-10-17): 도면 치수(점 X·Z, 홈 시작 Z·폭·외경·바닥)와 Z 여유·X 여유는 0 — 예시 값이 도면 값처럼 남지 않게.
  공구 값(노즈 R 0.4·인서트 폭 3·겹침 0.5)은 그대로, 모서리 R·시작·끝점·각도는 비워 둔다. 치수가 아직 0·빈칸이면 빨간 멈춤 대신 파란 안내
  ("값을 넣으면 바로 계산합니다 …"). 스크린샷·골든은 2단계 처음 값(외경 X40 Z0 / X50 Z-5, 역방향은 Z -30·-25 쪽, 홈은 calc-design.md 의 예,
  여유 1.0)을 넣어 찍는다(jvmTest `TaperExample`).
  빈칸인 X·Z·각도·끝점은 계산된 값을 `autoValue` 로 보인다 — 끝점은 깎기가 끝나는 마지막 T 점(모서리 R 이면 그 끝). **시작점은 빈칸이면 "안 깎음"** —
  그 요소는 깎지 않고 연장선에서 들어가므로 그림의 5 mm 값을 "자동" 으로 보이면 그 좌표부터 깎는 것처럼 읽혔다(2단계 점검).
  모서리 R·Z 여유·X 여유는 비우면 0, 겹침은 비우면 기본값.
- 입력 카드(사용자 결정 2026-10-17, E19 '두 묶음으로 정리'): **'도면 치수'** — 머리 오른쪽에 '단위 mm · X 는 직경' 을 한 번 적고 칸 안의 mm 는 뺐다.
  점마다 한 줄 [T 표 38dp][X][Z][모서리 R(폭 0.8)](옛 따로 줄 'T1 모서리 R' 은 없앴다 — 칸 이름 '모서리 R', 점 이름은 키패드 안내 줄에),
  단일 테이퍼는 각도 반 줄, 시작 · 끝 → 구분선(1dp, 위아래 14dp) → **'공구'** — 노즈 R 칩 → [Z 여유 (mm)][팁 번호](라운드의 공구 칸과 같은 순서).
  칸 묶음 '다음' 은 X1 → Z1 → 모서리 R → X2 … 시작 → 끝 → Z 여유. 노즈 R 은 요약 카드 정보 줄에도 있다.
  홈은 **'홈 치수'** [시작 Z][홈 폭 W] / [외경 ØD][바닥 Ød] → **'공구'** 기준 인선 → [인서트 폭 T][단 겹침][X 여유](한 줄 셋).
- 시작·끝점 칸: 그 요소가 단면이면 "시작점 X (T1 앞)", 수평이면 "시작점 Z (T1 앞)" 처럼 라벨이 형상·종류에 따라 바뀐다(`legsVertical`:
  형상 1·2 는 앞이 단면, 2·4 는 뒤가 단면, 2중 테이퍼는 둘 다 수평). 옛 "앞뒤 요소 길이" 칸은 없앴다.
- 보정 방식(사용자 결정 2026-10-17, E21 · E20): 결과 맨 위 스위치 하나 **"기계 보정 (G41/G42 · 도면 좌표)" / "직접 보정 (G40 · 보정 좌표)"** —
  테이퍼 · 라운드가 함께 쓰는 `AppState.compMode`(키 `compMode`, 처음은 기계 보정)라 한쪽에서 바꾸면 다른 쪽도 바뀌고 다음에 켜도 그대로다.
  옛 NC 카드 탭과 라운드의 '접점 모드 / 합성 R 궤적' 카드를 대신한다. 결과 카드 · 형상 확인 · NC · 결과 띠가 모두 이것을 따른다.
  - 기계 보정 카드: 공구 보정 화면에 넣을 "R 0.400 · T 3"(크게) → 점마다 도면 좌표(모서리 R 은 접점 둘 · G02/G03 · 도면 R) → 각도.
    보정 좌표는 접힌 '직접 보정 좌표도 보기' 를 눌러야 흐리게 나온다(G41/G42 프로그램에 넣으면 노즈R 이 두 번 들어가서). 뷰어는 도면의 깎는 토막을
    경로로, NC 는 도면 좌표 + G41/G42(첫 줄 주석 `(R0.4 T3)` 을 공구 보정 화면 R·T 칸에), 결과 띠는 T1 · T2 도면 좌표.
  - 직접 보정 카드("보정 좌표"): 날카로운 점은 X·Z(직접 보정 좌표) + ΔX·ΔZ, 모서리 R 점은 R 시작·끝 접점마다 보정 X·Z 와 "도면 X… Z…", 그 아래
    "G03 반시계 · 도면 R1.000 → 보정 R1.400"(오목 R ≤ 노즈R 이면 "보정 R 없음"), 각도와 G00 진입점. 형상 뷰어는 보정점마다 노즈 원을 그린다.
    NC 첫 줄은 주석 `(G40 DIRECT R0.4 T3)`(입력의 노즈 R · 팁 — 이 좌표에 G41/G42 를 또 걸지 않게, 기계는 주석을 건너뜀). 결과 띠는 T1′ · T2′ 보정 좌표.
  카드 아래 글이 둘의 차이(이중 보정 금지, 끝 블록에서 인선 위치가 다름)를 적는다. 홈은 보정량이 0 이라 NC 가 하나(카드 아래에 이유).
- 노즈R 칸 라벨은 "노즈 R (공구 반경, mm)"(라운드도 같은 부품). 도움말에 "노즈R = 인서트 날끝 반경 = 기계 공구 보정 화면 R 칸의 공구 반경
  (밀링의 공구 반경과 같은 역할), 팁 번호는 T 칸" 한 줄.
- 그림 치수: 02 의 점 자리를 잰 값(카드 폭·높이 비율), 글자·선 굵기는 02 카드 폭 199dp 기준 배율이라 B 화면 요약 칸(132dp)에서도 같은 모양.
  탭 글자 15.5sp·자간 −0.03em 은 02 와 글자 폭을 재서 맞췄다(원본 라틴 글자가 좁다). 방향은 02 의 방향 바 대신 48dp 두 칸(정방향 Z− · 역방향 Z+).
- 복사는 `LocalClipboardManager`(경고 숨김) — 새 `LocalClipboard` 는 ClipEntry 를 플랫폼마다 따로 만들어야 해서.

## 라운드 R 화면 (`screens/RoundScreen.kt`, `screens/round/`)
| 파일 | 내용 |
|---|---|
| `RoundScreen.kt` | `RoundScreen()` — 고르기(04: 외경/내경 스위치 + "가공 형태 선택" 줄 + 형상 카드 2열 + 수동(3점호) 카드), 도움말 `RoundHelp`. 카드를 누르면 입력 화면, 뒤로 키·Esc 는 입력 → 고르기 |
| `round/RoundModel.kt` | `RoundState`(화면 상태 + Saver — 내경 · 관통 · 보링바 칸 · 프리셋), `dims(type)`(형상마다 칸 이름·순서 = 그림), `blankable`, `RoundDefaults`(처음 값), 경고 글 `roundWarningText`, 보링바 띠 `holderNote` · `holderHitText` · 칸 오류 `barFieldText` |
| `round/RoundArt.kt` | `RoundTypeArt(type, inner, through)` — 형상 그림 Canvas(외경 측면도 · 내경 구멍 단면), `sampleInput(type, inner, through)`(그림용 견본 치수) |
| `round/RoundParts.kt` | `ManualArt`(3점호 그림 — 내경 · 역방향을 따라 뒤집힘, focus), `RoundSummary`, `NodeTable`, `RoundViewer`(내경 보링바 단면 · 겹침 · 자리 고르기), `ArcCard` |
| `round/RoundInputScreen.kt` | `RoundTypeScreen`(형상 1~6 · 내경 2~6) · `ManualScreen`(수동 3점호): 요약 → 치수·공구 → (내경) 보링바 → 보정 방식 스위치 → (원호) → 경고 → (내경) 보링바 띠 → 노드 표 → 형상 확인 → NC |
| `round/HolderCard.kt` | 보링바 카드(단면 스케치 · 프리셋 시트 · 칸 · 칸 아래 오류 글) |
| `round/HolderPresets.kt` | 출처를 확인한 보링바 프리셋 28 줄(제조사 · 품번 · D · f · DMIN · κr · 인서트 · 날 길이 · URL) |

- 이름: 04 의 영어는 한글로 — "CNC ROUND CALC" → 헤더 "라운드 R 계산", "SELECT MACHINING TYPE" → "가공 형태 선택", "TYPE n" → "형상 n",
  Main R → **주 R**, Corner R → **모서리 R**(Corner R(1)·(2) → 모서리 R(1)·(2)). D · D1 · D2 · L · L1 · L2 · L2(A)/L2(B) 는 그대로. 그림·칸·경고·도움말이 같은 이름을 쓴다.
- 그림: 원본은 남의 3D 렌더라 쓰지 않고, **계산기가 견본 치수로 만든 도면 윤곽**(`RoundCalc.calc(sampleInput(t)).drawing`)을 축 아래로 접어
  금속 그라데이션으로 칠한 측면도 + 깎는 윤곽(굵게) + 1점 쇄선 중심선 + DrawKit 치수선(지름 이름도 가로) + 밑줄 지시선 이름으로 그린다 — 그림이 계산의 해석과 같다.
  입력 화면 그림(`showDatum`)은 Z0 ▲, 이론 꼭짓점 ○, 끝 직선 길이(점선 치수), 칸 값(넣은 값 · 자동 · 빈칸 '?')을 그림에 적는다.
  견본 비율·이름 자리·치수선 높이는 04 의 카드 몸통(193 × 189dp)을 잰 비율이라 입력 화면 요약 칸(132dp)에서도 같은 모양이다.
  04 의 색 글자(형상 2 의 초록 주 R, L2(A) 의 빨강 A · L2(B) 의 파랑 B)는 색의 뜻 규칙에 맞지 않아 보통 글 'L2 (A)' · 'L2 (B)' 로 바꾸고, 고른 L2 기준만 굵게
  (다른 쪽은 흐리게). `RoundStateTest.artSamplesSolve` 가 견본이 늘 풀리는지 본다(안 풀리면 카드가 빈다).
- 고르기 카드는 `TypeCard`(머리 띠 칩 "형상 N" + 이름 — 술통 · 아령 …): 칩은 홀수 하늘 · 짝수 민트(`chipSky`·`chipMint`), 고른 카드는 청록 2dp + 체크.
  섹션 줄 오른쪽 흰 네모 버튼(04, 용도가 사진에 없음) 자리에는 글이 있는 '3점호 입력' 단추(누르는 자리 48dp).
- 보정 방식 두 개는 **같은 계산의 두 보기**다(calc-design.md): 기계 보정 = 접점 · 도면 점 · `drawing`, NC 는 같은 입력을 노즈R 0 으로 푼 것(= 도면 좌표, 기계 G42 보정과 함께 씀).
  직접 보정 = 합성 R 보정점 · `center` · `program` · `progR` · `nc`(G40, 가상 인선, 첫 줄 `(G40 DIRECT R0.4 T3)`). 고르는 곳은 테이퍼와 같은 앱 전체 스위치(아래 '보정 방식').
  노드 표는 점마다 지금 보정 방식의 X · Z 를 크게(numLarge), 다른 쪽 값을 그 아래 작게('도면 …' / '보정 …') 보인다.
  치수 · 공구 칸은 키패드 묶음("다음", 키패드 그림은 그 치수 하나만 초점색), 노드 표가 화면 밖이면 아래 결과 띠(첫 점 · 끝 점 · 외 n점).
- 입력값: 치수 칸은 형상마다 따로, 노즈R·Z 여유·끝 요소 길이는 함께, 팁은 외경 · 내경 따로(표준이 3 · 2 라 외경에서 고른 팁이 보링으로 넘어가지 않게 —
  테이퍼도 가공 종류마다 따로), 수동 칸은 따로. 처음 값은 그림 모양대로 풀리는 값(형상 3 은 calc-design.md 의 손 계산,
  비울 수 있는 칸 하나는 비워 계산값이 자리표시로 보인다). 형상 1~6 은 외경 정방향(팁 3, G42), 내경 2~6 은 정방향 보링(팁 2, G41),
  수동은 외경/내경 + 끝 Z 로 방향을 정한다.

### 내경 라운드 · 보링바 간섭 (3단계, 사용자 요청 2026-10-20)
결정(D1~D9)은 `stage2-report.md` 8절, 계산은 calc-design.md 의 "내경" · "보링바 간섭 검사" 절.
- **고르기**: 맨 위 외경/내경 스위치(`RoundState.typeInner`, 키 `inner`). 내경은 형상 2~6(공 모양 홈 · 돔 바닥 · 공 바닥 · 구면 단차 · 돔 단차 —
  `roundTypeShort(type, inner)`), 2 · 4 는 입구보다 안이 넓어 카드 아래 구석에 노란 '언더컷' 표. 3점호 카드 · '3점호 입력' 단추는 스위치 값으로 수동 화면의
  외경/내경을 정해 연다.
- **내경 그림**(`RoundTypeArt(inner = true)`): 금속 덩어리(구멍 깊이 + 8 %, 반경 × 1.25)에서 구멍(깎는 윤곽 + 축 아래로 뒤집은 것)을 뺀 단면,
  구멍은 옅은 바탕, 깎는 벽은 굵게, Z0 ▲ 는 오른쪽 입구, 금속 그라데이션은 축에 대칭(아래 벽도 구멍과 갈린다). 지름 치수는 구멍 안 — 이름이 구멍에
  들어가면 가운데(화살은 바깥), 못 들어가면 위 끝 너머 금속 안(덩어리 위 끝 2dp 아래까지). 형상 5 · 6 의 D2 는 입구 구멍 안쪽(이음점 쪽 0.8)에 두고
  주 R 이름은 그 6dp 오른쪽부터. R 이름은 덩어리 위 한 줄(겹치지 않게 좌우로 민다), 길이는 입구에서 안쪽으로 덩어리 아래 줄마다(입구 구멍 깊이가
  첫 줄). 이름이 치수보다 길면 왼쪽 보조선 4dp 오른쪽에서 놓고, 그 아래로 지나는 보조선은 이름 위에서 끊는다(글자 사이로 선이 보이지 않게).
  관통(`through`)은 덩어리 왼쪽 끝까지 뚫린다. 견본은 입구 구멍을 길게(`sampleInput`).
- **입력 화면**: 요약 '내경 · 정방향 가공 (Z−) · 노즈 R0.4 · 팁 2번', 그림 아래 '▲ Z0 = 구멍 입구 끝면 · X 는 지름 · 보링바는 Z− (왼쪽) 으로'.
  끝 직선 칸은 내경 3~6 이면 **'입구 구멍 깊이'**(키패드 안내 'Z0(입구 끝면)에서 첫 이론 꼭짓점까지 · 실제 치수', `legLabel`), 형상 5 · 6 은
  '구멍 바닥' 세그먼트 '막힘 (바닥까지) / 관통'. 내경 바이트 반대쪽 면 글은 '구멍 입구 쪽을 향한 면 — 언더컷', 형상 2 · 4 는 파란 한 줄
  '바이트 반대쪽 면 있음 (입구 쪽 면) — 눌러서 자세히'.
- **보링바 카드**(`HolderCard`, 내경 TYPE · 3점호 내경 화면에서 도면 · 공구 카드 다음 — 칸은 같은 키패드 묶음):
  - 단면 스케치: 구멍(깎는 벽 · 맞은편 벽 빗금)에 넣은 견본 바(계산기 `HolderCheck.outline` 그대로)와 공구대, 치수 D · f · 돌출 길이, 각 κr · κr′.
    3점호 뒤보링(역방향)이면 계산기처럼 인서트를 Z 로 뒤집어(날이 +Z, 팁 1) 그리고 κr 은 +Z 에서 · κr′ 은 −Z 에서 잰다. 벽 이름은 본문색(빗금 위 대비).
  - '프리셋 (선택)' 칸 → 시트(직접 입력 + 제조사별 품번 — Ø · f · Dmin · κr · 인서트). 고르면 D · f · DMIN · κr · 모양 · 날 길이를 채운다
    (돌출 길이 · 노즈 R 은 그대로). 고친 뒤엔 품번 옆 '수정됨', 아래 '출처: <사이트>'.
  - 칸: 바 지름 D · 날 길이 l(안내 '인서트 크기 숫자 (DCMT 07 → 7)') · f(바 중심 ~ 인선, 카탈로그 WF) · 인서트 모양 C 80° · D 55° · V 35° · T 60° ·
    절입각 κr(카탈로그 KAPR) 과 그 아래 '끝날각 κr′ = …°' · 돌출 길이 · 최소 가공경 Dmin(둘은 비우면 그 검사 안 함). 틀리면 칸 빨간 테두리 +
    아래 빨간 글(`barFieldText`, 예 'f 는 8.435 이상이어야 합니다 (바 윗면이 노즈보다 낮게)') 이고 띠는 없다.
  - 값은 앱을 켠 동안만(Saver, 키 `h/*`) — 공구 값을 기억하는 것은 승인되지 않았다(C10).
- **결과 띠**(`holderNote`, 노드 표 위): 바 값이 없으면 파란 '보링바 값을 넣으면 공구가 소재에 닿는지 봅니다.', 닿으면 노란
  '보링바가 소재에 닿습니다 — n곳' + 곳마다 '· a~b줄 NC 블록 — 바가 맞은편 벽에 닿음: 처음 X… Z…, 반경 겹침 최대 … mm (인선에서 … mm 뒤)'
  (인서트 · 공구대 '돌출 길이가 … mm 모자람' · 최소 가공경 'Ø… 보다 좁은 곳에 헤드가 들어감' — 최소 가공경은 입체 어림이라 뒤에), 4 줄 + '외 n곳'.
  앞 · 뒤는 이송 방향 기준(뒤보링은 +Z 가 앞). 안 닿으면 파란 '… 가장 좁은 곳 2.970 mm (n줄).', 되돌아가는 원호는 '보링바 간섭 검사를 하지 않습니다'.
  줄 번호 · 좌표는 보정 방식 스위치를 따른다 — 직접 보정은 G40 프로그램의 가상 인선(첫 줄 주석을 센 카드 줄), 기계 보정은 도면 좌표 프로그램의
  노즈가 닿는 도면 점(진입점 · 진입 이동은 도면 좌표 프로그램의 진입 이동 위 — 뒤보링은 진입점이 이미 구멍 안이라 G00 블록과 같은 Z, `hitPoint`).
  결과 띠(아래 독)의 노란 수에 하나 더한다.
- **형상 확인**(`RoundViewer` + 보링바): 지름 전체 단면(`ViewerStock(mirror = true)` — 맞은편 벽도 빗금, 1dp 뒤집은 도면), 고른 자리의 바 · 인서트
  단면(초록 `drawHolder` · 면 `drawHolderFill`, 입구 끝면 너머 max(3, 깊이 15 %) 에서 자름), 겹친 곳 빨강 α .5(바와 같은 곳에서 자름), 닿는 경로 구간 빨강 3dp
  (직접 보정은 가상 인선, 기계 보정은 노즈가 닿는 도면 점 사이 — ✕ 표와 같은 곳), 처음 닿는 곳 ✕ '간섭 n줄'(최소 가공경만이면 'Dmin n줄').
  카드 아래(크게 보기에서도) '보링바를 그릴 자리' 알약: 첫 간섭 · 가장 깊은 곳 · 가장 좁은 곳 · P1…(처음은 첫 간섭, 없으면 가장 좁은 곳).
  범례 '보링바 단면' · '간섭'. 색 대비(뷰어 바탕): drawHolder 라이트 4.5 · 다크 10.6.
- **NC 카드**: 닿는 줄은 줄 번호 왼쪽 3dp 빨간 띠 + 빨간 굵은 번호(`NcCard(flagged)`).
- 도움말 `RoundHelp`: '형상 고르기' 에 외경/내경 스위치, '치수 넣기' 에 입구 구멍 깊이 · 막힘/관통 · 입구 R 은 3점호, 새 묶음 '보링바 간섭 (내경)'
  (칸 · 검사 방법 · 단면의 한계(DMIN 어림 · 진입/빠지는 길 안 봄) · 프리셋은 제조사마다 다름) — 내경 입력 화면은 이 묶음부터 연다. '경고' 에 노란 띠의 보링바 닿음.

## 직각삼각형 화면 (`screens/TriangleScreen.kt`, `screens/triangle/`)
| 파일 | 내용 |
|---|---|
| `TriangleScreen.kt` | `TriangleScreen()` — 그림 → 경고 → 아는 값 다섯 칸([a][b][c] / [A][B], 한 키패드 묶음) → 결과 표 → X · Z 연동, 도움말 `TriangleHelp` |
| `triangle/TriangleModel.kt` | `TriField`(칸 다섯: 밑변 a · 높이 b · 빗변 c · 각도 A · 각도 B), `TriangleState`(값 + `baseIsZ` + Saver), 경고 글 |
| `triangle/TriangleArt.kt` | `TriangleFigure(s, result, focus, onPick)` — 삼각형 Canvas, 변 a · b · c 는 DrawKit 치수선, 값은 두 줄 칩(이름 · 축 / 값 — 넣은 값 = 보라 칩, 계산값 = 흰 칩, 모르면 ?). 칩을 누르면 그 칸 키패드 |

- 원본 사진이 메뉴 카드뿐이라 화면 전체를 우리가 정했다. 헤더 "직각삼각형 계산"(기능색 한 색, 메뉴 #6430C5 계열 = violet-700).
- 다섯 칸 가운데 아는 둘(빈칸은 계산값을 "자동" 표와 함께). 셋 이상이면 계산기가 맞는지 본다(사용자 결정: 셋째 값을 자동으로 놓아 주지 않고, 결과 표도 그대로 — H8 승인 안 됨).
  값 부족은 파란 안내 '값 두 개를 넣으세요 — 변 하나는 꼭 포함.', 나머지 경고는 계산을 멈춘 것이라 빨강(그림 카드 바로 아래, 그림에서도 그 칸을 짚는다).
- 그림 모양은 각도 A 를 따르되 22°~58° 로 눌러 그리고, 그 밖이면 왼쪽 위에 '그림 비율 아님 (실제 A …°)'. 그림 높이는 290dp 고정(각을 바꿔도 아래 칸이 들썩이지 않게).
- X · Z 연동: 좌표가 아니라 바뀐 양 — 'ΔX 지름 변화 (직경)' · 'ΔZ Z 이동' 두 칸(24sp), 세그먼트 '밑변 Z · 높이 X(반경)' / '밑변 X(반경) · 높이 Z'(`baseIsZ`).
  X 는 직경값(반경 × 2). 값은 크기만 보이고 부호는 가공 방향에 맞게 붙인다고 적는다.
- 처음 값(사용자 결정 2026-10-17): 다섯 칸 모두 빈칸(자리표시 '0'). 스크린샷 · 골든은 예전 처음 값 밑변 10 · 각도 A 25° 로 찍는다
  (A = 30° 면 X 직경 = 빗변이 되어 헷갈려서 피했다).
