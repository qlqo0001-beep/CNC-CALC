# 화면 기초 (`:shared`)

기능 화면을 채우는 사람이 볼 문서. 원본 화면 명세는 `screens.md`, 계산 API 는 `calc-design.md`.
확인: `gw.bat :shared:jvmTest` → `build/shots/<이름>-light.png`·`-dark.png` 를 `docs/store` 원본과 나란히 본다(아래 '스크린샷 도구').
3단계 다듬기(그림 · 입력 · 결과 · 헤더)의 원칙은 아래 '원칙', 원본과 일부러 다르게 한 것은 `screens.md` 의 '원본과 다르게 한 것'.

## 파일 지도 (`shared/src/commonMain/kotlin/com/bmt/nosecalc`)
| 파일 | 내용 |
|---|---|
| `App.kt` | `App(state)` — 7기능 라우팅, `AppHost`(테마 + 위층 + 뒤로) |
| `AppState.kt` | 화면 쌓임(`open`·`back`·`hasBackStep`), `menuOpen`·`helpOpen`, 테마 저장(`setTheme(null\|false\|true)`), 보정 방식 `compMode`, 테이퍼 각도 기준 `angleRef`(`AngleRef` X · Z), `toast(글)`, PC 키(`handleKey`), `OnBack` |
| `Feature.kt` | 7기능 이름(메뉴용·헤더용)·`ready`(다 만든 기능), 메뉴 순서 |
| `KeyValueStore.kt` | expect/actual 설정 저장(안드로이드 SharedPreferences, PC `~/.bmt-nosecalc/settings.properties`) — 테마 · 보정 방식 · 테이퍼 각도 기준 · PC 창 크기 |
| `screens/*Screen.kt` | 기능마다 한 파일 + 도움말 `XxxHelp`(`HelpSection` 목록). 아직 안 만든 기능은 `ComingSoonPage` |
| `screens/TaperScreen.kt`, `screens/taper/` | 테이퍼·홈 화면(아래 절) |
| `screens/RoundScreen.kt`, `screens/round/` | 라운드 R 화면(아래 절) |
| `screens/TriangleScreen.kt`, `screens/triangle/` | 직각삼각형 화면(아래 절) |
| `screens/ThreadScreen.kt`, `screens/thread/` | 나사 G76 화면(아래 절) |
| `screens/PolarScreen.kt`, `screens/polar/` | 턴밀 극좌표 다각가공 화면 · 시뮬레이션 모달(아래 절) |
| `screens/CuttingScreen.kt`, `screens/cutting/` | 재료·절삭조건 화면 — 기본계산기 · 재료분석 · 드릴링 탭(아래 절) |
| `screens/SimulatorScreen.kt`, `screens/sim/` | G코드 시뮬레이터 — 화면 A 편집기 · CNC 키패드 · 프로그램 저장, 화면 B 궤적 창(2D · 3D · 재생 · 재료 제거 · 설정 창)(아래 절, 설계는 `simulator-design.md` 6 · 7 · 14 · 15 · 16절) |
| `ui/theme/AppColors.kt` | 색 토큰 라이트·다크(`RoleColors` · `FeatureColors` · `onFill`), KDoc 에 대비표 |
| `ui/theme/AppTheme.kt` | `AppTheme(dark)`, `AppTheme.colors / sans / mono` (CompositionLocal 하나) |
| `ui/theme/AppType.kt` | 글자 단계 `AppType`, 여백 `Space`, 모서리 `Radius` |
| `ui/FeatureScaffold.kt` | 기능 화면 틀(헤더 + 본문 640dp), APP MENU 패널(화면 테마 · 준비 중 묶음), 도움말 시트(`HelpSection`) |
| `ui/Header.kt` | `FeatureHeader`, `CircleIconButton`, `AppsButton`, `PillButton` |
| `ui/Components.kt` | `AppCard`, `TypeCard`, `CardHeader`, `CoordPair`, `LabeledValue`, `ExpandableHint`, `Segmented`, `OutlineTabs`, `WarningBanner`(`WarnLevel`) |
| `ui/NumberInput.kt` | `NumberField`, 숫자 키패드(`NumberPad`), 글 고치기 `NumEdit` |
| `ui/FieldChain.kt` | 키패드 칸 묶음 `FieldChain` · `rememberFieldChain` · `FieldChainHost`(키패드 하나 · PC Tab 순서) |
| `ui/DrawKit.kt` | 그림 한 벌의 선 규칙 `DrawSpec` · `artScale` · `centerLine` · `arrowHead` · `haloText` · `linearDim` · `hatchFill` |
| `ui/ShapeViewer.kt` | `ShapeViewer(lines, marks, state, stock, fills)` — calc 의 `Contour` 를 그린다. `ViewerLine` · `ViewerMark`(`MarkKind`) · `ViewerStock` · `ViewerFill` · `Arrow` · `ViewerState`(`UserView` · `focus(점, 배)`) |
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
    `tag` = 라벨 뒤 회색 표("Ø직경" · "반경") · `placeholder` = 빈칸 자리표시(흐리게 — 빈칸이 0 인 여유 · 모서리 R 칸은 "비우면 0", 도면 치수는 없음).
  - `hint` · `blankable` · `figure` · `preview` 는 키패드로 간다(안내 줄 · "비우기 · 자동 계산" 단추 · 작은 그림 · 결과 한 줄).
- `FieldChainHost(rememberFieldChain(처음 열 칸 id)) { 칸들 }` — 안의 `NumberField` 가 `id` 로 스스로 들어오고, 순서는 화면 자리(위 → 아래, 같은 줄은 왼 → 오른).
  키패드는 묶음이 하나만 띄운다: 오른쪽 열 C · ⌫ · 두 칸 높이의 채운 **"확인"**(닫기만 — 다음 칸으로 넘어가지 않는다, 사용자 요청 2026-10-28
  "다음 을 확인으로 변경하고 넘어가게 하지마". 예전의 테두리 '닫기' + 채운 '다음 ›' 과 제목 옆 "n / m" 은 없앴다 — 화면 키로는 넘기지 않으니 순번이 뜻이 없다).
  연 칸은 시트 위로 올라오고(`chain.sheetOpen` 이면 화면 끝에 440dp 빈자리), PC Tab 으로 다음 · 앞 칸으로 가도 시트는 그대로다.
  모든 화면의 칸이 묶음 안에 있다 — 묶음 밖 `NumberField` 는 모양만 보이는 견본(누를 수 없음).
- 키패드(`NumberPad`): 열면 지금 값이 통째 선택돼 처음 누른 숫자 · . · − 가 새 값이 된다(− 로 새 음수, ⌫ 는 원래 값을 고친다). 누를 때마다
  칸 · 결과 · 그림 · NC 에 바로 들어가고, '확인'은 닫기와 같다. 닫기(확인 · 바깥 누름 · 뒤로 · Esc · 손잡이 끌어내림)는 넣은 값을 그대로 두고, "-" 뿐이면 연 때 값으로. 제목 오른쪽
  "취소" 는 연 때 값으로 되돌리고 닫는다. 위에 결과 한 줄(`preview` — 넘치면 9sp 까지 줄여 끝 점 좌표가 잘리지 않게), 시트 자리가 넉넉하면(780dp 이상) 제목 아래 작은 그림(`figure`
  — 둥근 `subtle` 바탕은 figure 가 받은 Modifier 에: 테이퍼 비율 그림은 제 폭만이라 소재 면이 칸 끝까지, 라운드 형상 그림 · 삼각형 · 3점호는 시트 폭 바탕).
  PC 는 실제 키보드: 숫자 · . · − · Backspace · Delete = C · **Enter = 확인(닫기)** · Tab = 다음 칸(마지막 칸은 닫기) · Shift+Tab = 앞 칸 · Esc = 닫기.
  시험 `NumEditTest.confirmClosesWithoutNext`(다음 칸이 있어도 확인 = 닫기 · '다음' · '닫기' · "n / m" 글 없음) · `chainPcKeys`(Enter 닫기 · Tab 넘기기).

### 카드 · 글
- `AppCard(modifier, padding = 16.dp)` — 흰 카드(라이트 1dp 테두리 + 옅은 그림자, 다크는 테두리만).
- `CardHeader(title, subtitle, modifier, trailing)` — 카드 머리: 제목(`cardTitle`) + 부제(`caption`) + 오른쪽 단추 자리. 아래로 12dp.
- `CoordPair(x, z, color, modifier, size = 24.sp, dx, dz)` — X | Z 두 칸, 축 글자가 제 값 바로 옆, 오른쪽 맞춤 고정폭, 아래 "Δ …". 칸보다 길면 12sp 까지 줄여 한 줄.
- `LabeledValue(label, value, modifier, color = textValue, size = 17.sp)` — 이름(12sp 흐림) 위, 고정폭 굵은 값 아래(각도 · G00 진입점 · 단 수 …).
- `ExpandableHint(short, long)` — 한 줄 설명 + '자세히/접기'(48dp). 긴 규칙 문단은 이렇게 접는다.
- `keepWords(s)` — 한글 음절 사이 · 한글과 붙은 영문 · 숫자 · 닫는 괄호 사이에 낱말 이음(\u2060)을 넣어 줄이 띄어쓰기에서만 바뀌게 한다
  (Compose 는 한글 음절 사이 어디서나 줄을 바꿔 '확 / 인하세요' 처럼 어절이 쪼개졌다). `ExpandableHint` 의 두 글 · `WarningBanner` 본문 · 도움말 문단 ·
  키패드 안내 줄 · 결과 띠 '계산 멈춤' 글이 쓴다.
- `TypeCard(chip, caption, chipColors, selected, onClick, modifier, aspectRatio) { 그림 }` — 형상 카드: 36dp 머리 띠(칩 "형상 N" + 짧은 이름) + 몸통.
  고르면 2dp 청록 테두리 + 옅은 청록 바탕 + 오른쪽 위 체크 원. 테이퍼(민트 칩) · 라운드(홀수 하늘 · 짝수 민트) 고르기 화면이 쓴다.
- `Segmented(options, selected, onSelect, color = blue, track = segTrack, height = 48.dp, fontSize = 16)` — 고른 칸 글자색은 `c.onFill(color)`
  (바탕 휘도 > 0.3 이면 짙은 글자 — 다크의 밝은 청록 위 흰 글자가 2:1 아래였다). 방향 두 칸 · 외경/내경 · 화면 테마 · L2 기준 등.
  칸 글은 양옆 6dp 안, 넘치면 0.8배까지 줄이고 그래도 넘치면 '…'(소리 없이 잘려 온전한 낱말처럼 보이지 않게).
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
  - `ViewerState`: 보기 = 맞춤의 배수(0.5~200) + 가운데 점(`UserView`). `focus(점, 배)` = 그 점을 가운데에 맞춤의 몇 배로(라운드 간섭 목록에서 누른 자리로 가기). 폰 회전 · 창 크기가 바뀌어도 같은 곳, 치수를 고쳐도 보이던 곳에 새 그림이 걸치면 그대로.
  - 그리는 순서: 격자(1·2·5 간격, 다섯째 줄 굵게) → 소재(`ViewerStock`: 외경은 윤곽과 중심선 사이, 내경은 위, `mirror` 면 맞은편 벽까지 — 면 + 45° 빗금,
    "◀ 척 쪽") → 면(`ViewerFill`) → 중심선 X0 · Z0(1점 쇄선) → 윤곽 → 원호 중심 지시선 → 점 → 뷰어 알약(척 쪽 · Z0 · 중심 · 왼쪽 아래 **축척 막대** "2 mm · X 반경")
    → 점 라벨.
  - `ViewerLine(contour, color, width = 2.dp, dashed, dotted, arrows = Arrow.None|Filled|Hollow, fill)` — 끊은 선(노즈 중심) · 점선(급송) · 진행 화살(경로 = 채움,
    급송 = 빈 화살, 40dp 넘는 토막 가운데).
  - `ViewerMark(pt, color, label, kind = MarkKind.Ring|Dot|Square|Flag|Cross, info, arc)` — T 점 · 노드 · 진입/후퇴 · 깃발(보링바 안전 끝점 — 점에서 위로 14dp 깃대 +
    9dp 삼각 깃발, 범례 `LegendGlyph.Flag` 는 0.75 배) · 원호 중심. `arc` 가 있으면 중심에서 원호 가운데까지
    끊은 지시선, 화면 반지름 14dp 미만이면 뺀다. 라벨은 점 둘레 **알약**: 8곳 × 4겹 자리에 점수(다른 라벨 · 점 · 알약과 겹침, 지나는 선, 거리)를 매겨
    고르고(같은 꼴 점 — 노드 둘 — 이 제 점만큼 가까운 자리는 무겁게, 지시선이 앞 라벨을 지나는 자리도, 진행 화살을 덮는 자리는 알약처럼), 뷰어 4dp 안쪽만, 16dp 넘게 떨어지거나
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
- `ComingSoonPage(feature)` — 준비 중 안내 + 지금 쓸 수 있는 기능(`ready` — 4단계부터 넷)으로 바로 가기(56dp), 도움말 `ComingSoonHelp`.
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
| 배율(`artScale(w, base)`) | 그림 폭 / 기준 폭 → 선 0.9~1.3 · 글 0.95~1.25 배로 묶는다 — 360dp 폰에서 글 10.5sp 아래로, 넓은 PC 창에서 너무 크게 가지 않게. 자리는 폭 비율 그대로. 글은 시스템 글꼴 배율을 1.1 까지만 따른다(그 위는 나눔 — 큰 글꼴에서 이름끼리 겹치지 않게). 라운드 그림(`RoundTypeArt`) 글은 글꼴 배율을 따르지 않고 폭만 따른다(dp — 이름 자리가 폭 비율이라 1.1배에서도 'L2 (B)' 가 치수선 칸을 넘었다) |

## 스크린샷 도구 (`shared/src/jvmTest`)
- `Shots.both(이름, heightDp = 915, frames = 40, setup = { state -> … }, widthDp = 412, fontScale = 1f, frameMs = 100, tapDp = null, scrollToEnd = false) { state -> App(state) }`
  — 412×915dp, 밀도 2.625(1082×2402px). 부품만 볼 때는 `AppHost(state) { … }` 로 감싼다. 프레임마다 `frameMs` 를 흘려 글꼴 로딩·애니메이션을 끝낸다.
  키패드가 칸을 시트 위로 끌어올리는 장면은 0.1초 걸음이면 출렁여 `frames = 150, frameMs = 16`(폰 프레임)으로 찍는다. `tapDp` = 몇 프레임 뒤 한 번 누를 자리
  (칸을 눌러 연 키패드 · 크게 보기), `scrollToEnd` = 휠로 끝까지 내림(아래 결과 띠).
- 화면 숫자 골든 `UiGoldenTest`: 스크린샷 상태마다 경고 · 좌표 · NC 를 `golden/<이름>.txt` 로 고정 — 바뀌면 실패, `-DupdateGolden=true` 로만 다시 쓴다.
- 찍는 것(테스트 파일마다):
  - `ScreenShotTest`: `01-menu`, `02-taper`, `feature-<기능>`, `help`, `keypad` · `keypad-long`(13자), `modal`;
    **큰 글꼴 · 좁은 폰 묶음** — 장면마다 `-fs130`(글꼴 1.3배) · `-w360`(360dp): `01-menu`, `taper-b`(첫 화면 — 360dp 는 TaperShotsTest 의 `taper-b-w360`), `taper-b-keypad`, `round-b2`, `round-b2-keypad`,
    `triangle`, `help`, 내경 라운드 `round-pick-inner`(고르기) · `round-inner-b3-holder`(보링바 · 빨간 닿음) · `round-inner-a-stop`(안전 끝점 · 잘린 NC 스위치) · `round-manual-inner-rev-holder`(뒤보링 + 보링바, 보통 판도);
    **깨지기 쉬운 상태** — `taper-b-big`(Z −1500) · `taper-b-max`(한도 끝) · `taper-b-warn-gt` · `taper-b-warn-concave` · `taper-b-against` · `taper-b-inner-double-rev` ·
    `taper-b-longnum` · `taper-groove-many`(80단) · `taper-groove-errors`(빨간 띠 셋) · `round-b2-concave` · `round-manual-error` · `triangle-huge` · `triangle-three`;
    경고 글 모아 보기 `texts-warn-taper` · `texts-warn-round`; PC 창 `pc-wide-a` · `pc-wide-keypad`(1280 × 720dp) · `pc-short-help` · `pc-short-keypad`(412 × 640dp).
  - **빈칸 시작**(10-28): 첫 화면 `taper-b-empty` · `taper-b-double-empty` · `taper-groove-empty` · `round-b3-empty` · `round-inner-b3-empty` · `round-manual-empty` · `triangle-empty`,
    칸 하나만 넣은 상태 `taper-b-one` · `taper-b-double-one` · `taper-groove-one` · `round-b3-one` · `round-inner-b3-one` · `triangle-one`.
  - `FoundationShotsTest`(`foundation-*`: 아이콘 · 경고 띠 · 카드 부품 · 결과 띠 · 그림 도구, -w360), `KeypadShotsTest`(`field-states` · `keypad-*`),
    `ViewerShotsTest`(`viewer-*`), `ShellShotsTest`(헤더 단추 48dp 재기 · 메뉴 · 아이콘 · 도움말 · `taper-b-wide` 1280dp),
    `TaperArtShotsTest` · `RoundArtShotsTest`(`art-*` 그림만, 폭마다), `TaperShotsTest`(`taper-*`), `RoundShotsTest`(`round-*` — 내경 · 보링바 카드 · 크게 보기 · 안전 끝점 `round-inner-a-stop` · `round-inner-f-stop` · `round-manual-back-stop` · 깎는 면 예외 `round-inner-c-stop` · 기계 보정 G00 을 띄운 직각 진입 `round-inner-b3-tip0` · NC 전체 보기 `round-inner-nc-full-focus` · 회사 사용 프리셋 `round-preset-sheet`(`-w360` · `-fs130-w360` · 회사 23 줄이 다 보이는 `-all`) · `round-inner-holder-company` · `round-inner-holder-blank`(둘 다 `-fs130-w360`) 포함),
    `TriangleShotsTest`(`triangle-*`), `screens/sim/SimShotsTest`(`sim-*` · `pc-wide-sim` · `pc-short-sim` — 아래 '시뮬레이터 편집기'), `screens/sim/TrajShotsTest`(`sim-2d*` · `sim-settings` · `sim-tools` · `sim-tools-holder` · `sim-holders` · `sim-legend` · `sim-stop-error` · `pc-wide-traj` · `pc-short-traj` — 궤적 창), `screens/sim/Traj3DShotsTest`(`sim-3d*` · `pc-wide-3d` · `pc-short-3d` — 궤적 창 3D, 백그라운드 그림이라 80 프레임). 긴 화면은 heightDp 를 늘려 한 장에 찍는다.
- 프레임마다 나오는 그림은 바로 닫는다(네이티브 메모리). 안 닫으면 1700dp 넘는 스크린샷이 `Surface.makeRasterN32Premul` 에서 실패했다.
- 견고성 시험 `InputRobustnessTest`: 화면 처음 값에서 칸을 흔들어(키패드로 칠 수 있는 글 — 빈칸·0·음수·12자리·아주 작은 수)
  테이퍼·2중 테이퍼 6만, 라운드·3점호 3만, 홈·삼각형 2만 가지를 풀고, 예외가 없고 화면에 찍는 숫자가 모두 유한한지 본다.
  극단 값 화면도 실제로 그려 본다. 이 시험이 찾은 것: 홈 단 수 Int 넘침(빈 목록으로 죽음), 큰 수에서 NaN·끊긴 윤곽(→ `MAX_MM`).
  유한하지만 틀린 숫자(직각삼각형의 아주 큰 값이 `Fmt` 에서 Long 끝에 붙는 것)는 잡지 못한다 — `stage2-report.md` 6절.
- 3단계 화면 견고성 `Stage3RobustnessTest`: 나사 G76 · 턴밀 극좌표 · 재료·절삭조건을 같은 방식으로 — 칸 글 흔들기에 고르는 칸(사이즈 · 급수 · 도형 · 메이커 · 시리즈 · 행 · 탭)을
  섞어 셋 각 2만 가지로 화면 글 함수(경고 · 결과 칸 · 미리보기 · G76 낱말 · 드릴 표)를 부르고, 무작위 상태 120 × 3 화면(키패드 · 다크 · 직접 보정 · 시뮬레이션 모달 섞음)과
  칸마다 극단 값 화면을 실제로 그린다. 찾은 것: 극좌표 코너 R 0.00000000001 에서 계산기가 죽음(→ `PolarG12` 의 `TINY_R`, calc-design.md).

## 테이퍼·홈 화면 (`screens/TaperScreen.kt`, `screens/taper/`)
| 파일 | 내용 |
|---|---|
| `TaperScreen.kt` | `TaperScreen()` — A 화면(가공 종류 탭 · 방향 두 칸 · 형상 카드 2열, 본문 600dp 넘으면 3열), 도움말 `TaperHelp`. 형상을 누르면 B, 뒤로 키·Esc 는 B → A |
| `taper/TaperModel.kt` | `TaperKind`(탭 순서 = 02), `TaperState`(화면 상태 + Saver, 뒤집기 `flip`·`toggleFlip`), `TaperDefaults`(처음 값), `TaperSolve`·`Mirror`(역방향), `screenSide`·`travelText`(뒤집기 스위치 한 줄), 각도 기준(`AngleRef.toZ`·`fromZ`·`shownAngles`·`complementText`·`switchAngleRef`), 경고 글 |
| `taper/TypeArt.kt` | `TypeArt(kind, type, mirrored, modifier, compact, focus, flip, angleRef)` — TYPE 그림 Canvas(외경 기준, 내경은 위아래·역방향은 좌우 반전, 바이트도 반대쪽 — 단 내경 뒤보링 바는 인서트만 뒤집고 자루는 +Z 구멍 입구로, +Z 가 막힌 홑 2 · 4 만 통째로). compact = 요약 칸 · 키패드 꼴, focus = 고치는 칸만 초점색(점 칸 · 시작 · 끝은 그 점에 테 — 그 점 글은 테 밖으로 6dp 더, 시작 · 끝 테는 글보다 먼저. 홈 요약 칸의 'T' 는 홈 바닥 아래, '시작 Z' 는 ØD 윤곽 위 · 카드 끝 9dp 안), flip = T 이름 · 이송 화살 · 시작/끝을 반대 끝부터(아래 '가공 방향 뒤집기'), angleRef = 각도 호 · 기준 점선이 세로(X축) · 가로(Z축)(아래 '각도 기준') |
| `taper/SlopeScreen.kt` | B 화면(외경·내경 · 2중 테이퍼): 요약 카드 → 입력(도면 치수 · 공구) → 보정 방식 스위치 → 경고 → 결과 카드 → 형상 뷰어 → NC |
| `taper/GrooveScreen.kt` | B 화면(외경 홈): (침범 경고) → 입력(홈 치수 · 공구 — 기준 인선은 공구 묶음) → 다단 진입 표·정삭 → 형상 → NC |
| `taper/Parts.kt` | 요약 카드, 뒤집기 스위치(`FlipSwitch`), 각도 기준 스위치(`AngleRefSwitch`), 점 이름표, 입력 묶음 구분선(`GroupDivider`), 노즈R 칩, 팁 번호 칸·시트·그림(`TipDiagram`, 도움말 `TipHelp`), 보정 방식 스위치, NC 카드(복사) |

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
  형상마다 X 오르내림과 시작·끝점의 뜻이 달라서 — 뒤집기도 같은 열쇠 묶음), 노즈R·Z 여유·홈 치수는 함께. 보정 방식은 화면 상태가 아니라 앱 전체(`AppState.compMode`)다.
  처음 값(사용자 요청 2026-10-28 "0 이 적힌 좌표들을 다 빈칸으로 설정해" · "0 인 칸은 전부" — 예전 2026-10-17 결정 '0 에서 시작' 을 바꿈):
  도면 치수(점 X·Z, 홈 시작 Z·폭·외경·바닥)와 Z 여유·X 여유는 **빈칸**. 도면 치수의 빈칸은 아직 안 넣은 값(단일 테이퍼는 다섯 값 가운데 하나를 자동 계산 —
  예전 규칙 그대로, Z0 인 점도 0 을 넣는다), Z 여유 · X 여유 · 모서리 R 의 빈칸은 0 이고 칸에 흐린 '비우면 0'. 공구 값(노즈 R 0.4·인서트 폭 3·겹침 0.5)은 그대로,
  각도·시작·끝점도 빈칸. 값이 모자라면(계산기 MISSING_INPUT, 또는 예전 처음 값 0 을 그대로 둔 · 되살린 치수 — `SlopeForm.waiting` · `GrooveInput.waiting`)
  빨간 멈춤 대신 파란 안내("값을 넣으면 바로 계산합니다 …")만 — 결과 · 형상 · NC · 결과 띠는 아직 없고 요약 그림은 개념도 그대로. 칸 하나씩 넣는 동안도 그렇다
  (`TaperSolveTest.freshStartSolvesAsYouType`). 넣은 값은 화면 상태(Saver)로 되살아나 이미 0 을 넣은 칸은 0 그대로다(KeyValueStore 는 칸 값을 저장하지 않는다).
  스크린샷·골든은 2단계 처음 값(외경 X40 Z0 / X50 Z-5, 역방향은 Z -30·-25 쪽, 홈은 calc-design.md 의 예, 여유 1.0)을 넣어 찍는다(jvmTest `TaperExample`).
  빈칸인 X·Z·각도·끝점은 계산된 값을 `autoValue` 로 보인다 — 끝점은 깎기가 끝나는 마지막 T 점(모서리 R 이면 그 끝). **시작점은 빈칸이면 "안 깎음"** —
  그 요소는 깎지 않고 연장선에서 들어가므로 그림의 5 mm 값을 "자동" 으로 보이면 그 좌표부터 깎는 것처럼 읽혔다(2단계 점검).
  모서리 R·Z 여유·X 여유는 비우면 0, 겹침은 비우면 기본값.
- 입력 카드(사용자 결정 2026-10-17, E19 '두 묶음으로 정리'): **'도면 치수'** — 머리 오른쪽에 '단위 mm · X 는 직경' 을 한 번 적고 칸 안의 mm 는 뺐다.
  머리 아래 'T1 ⇄ T2 바꾸기' 스위치 한 줄(아래 '가공 방향 뒤집기') → 점마다 한 줄 [T 표 38dp][X][Z][모서리 R(폭 0.8)](옛 따로 줄 'T1 모서리 R' 은 없앴다 — 칸 이름 '모서리 R', 점 이름은 키패드 안내 줄에),
  단일 테이퍼는 [각도][각도 기준 스위치](아래 '각도 기준'), 시작 · 끝 → 구분선(1dp, 위아래 14dp) → **'공구'** — 노즈 R 칩 → [Z 여유 (mm)][팁 번호](라운드의 공구 칸과 같은 순서).
  칸 묶음 순서(PC Tab)는 X1 → Z1 → 모서리 R → X2 … 시작 → 끝 → Z 여유. 노즈 R 은 요약 카드 정보 줄에도 있다.
  홈은 **'홈 치수'** [시작 Z][홈 폭 W] / [외경 ØD][바닥 Ød] → **'공구'** 기준 인선 → [인서트 폭 T][단 겹침][X 여유](한 줄 셋).
- 시작·끝점 칸: 그 요소가 단면이면 "시작점 X (T1 앞)", 수평이면 "시작점 Z (T1 앞)" 처럼 라벨이 형상·종류에 따라 바뀐다(`legsVertical`:
  형상 1·2 는 앞이 단면, 2·4 는 뒤가 단면, 2중 테이퍼는 둘 다 수평). 옛 "앞뒤 요소 길이" 칸은 없앴다.
- **가공 방향 뒤집기 — T1 ⇄ T2**(사용자 요청 2026-10-27, `calc-design.md` '가공 방향 뒤집기'): **T1 = 늘 먼저 깎는 점.** '도면 치수' 머리 바로 아래 한 줄 스위치
  `FlipSwitch`("T1 ⇄ T2 바꾸기", 2중은 "T1 ⇄ T3", 줄 전체를 누름 · 56dp, 켜지면 청록 바탕 + 체크 손잡이) 아래에 지금 방향 한 줄 `travelText` — 홑테이퍼는 그림의 위아래
  ("T1 아래 → T2 위 · G42", 내경은 그림이 위아래로 뒤집혀 반대), 2중은 산 · 골이라 좌우("T1 오른쪽 → T3 왼쪽 · G42"). 보정 쪽은 `screenSide` = `Side.of(inner, reverse != flip)`.
  켜면 같은 형상(그림 좌우 반전 없음)을 같은 바이트(팁 그대로)로 반대 끝부터 깎는다 — 외경 형상 1 이면 외경 쪽 위 점에서 면취를 타고 내려와 단면을 중심 쪽으로, G42 → G41.
  화면 역방향(거울 · 역바이트)과 따로 켜고, 네 조합 모두 `TaperSolve` 가 calc 의 `flip` 에 그대로 넘긴다(역방향은 지금처럼 Z 를 거울로 — calc 의 `reverse` 는 쓰지 않는다).
  - 칸: `TaperState.toggleFlip` 이 넣은 값이 같은 실제 점에 남게 맞바꾼다 — X1·Z1 ↔ X2·Z2(2중 X3·Z3, T2 그대로), 모서리 R1 ↔ R2(R3), 시작점 ↔ 끝점, 빈칸(자동)은 빈칸으로,
    마지막에 채운 자동 칸(`lastAuto`)도 따라간다. 각도는 같은 경사라 그대로. 저장은 점 칸과 같은 (종류 · 형상 · 방향)마다 `flip/<종류>/<형상>/<f|r>` = "1"(열쇠가 없는 옛 저장 값은 꺼짐).
    시작 · 끝 칸 이름도 따라간다: `legsVertical(kind, type, flip)`(뒤집으면 시작이 단면인 형상은 2 · 4, 끝이 단면인 형상은 1 · 2), 척 쪽에서 시작 = `reverse != flip`,
    단면 시작은 외경 '바깥 Ø' · 내경 '안쪽 Ø'(안 뒤집은 것과 반대). Z 칸 이름은 360dp · 큰 글꼴 · '자동' 표에서도 잘리지 않게 짧게(검증 2026-10-27):
    '시작 Z (척 쪽)' · '시작 Z (앞 끝)' · '끝 Z (앞 끝)' · '끝 Z (척 쪽)'(옛 '시작 Z (앞 끝 · 척 쪽)' · '끝 Z (뒤 끝 · 척 반대쪽)' 은 잘렸다).
  - 그림: `TypeArt(…, flip)` — T 이름 · 라벨 자리(점 자리를 따라감) · 이송 화살(T1 → T2) · 시작/끝 표가 반대 끝부터, 기준 점선 · 각도 호는 그대로(2중은 각1 · 각2
    이름만 칸 순서로). 바이트(같은 바이트 · 같은 날 방향)는 시작 끝 곁으로 옮긴다(검증 2026-10-27 — 오른쪽 위에 그대로면 시작 끝과 떨어져 보였다): 외경은 그림마다 잰
    날끝 자리(`Art.flipTool`, 시작 끝 곁 공기 쪽 · T 와 '시작' 글을 피함), 내경은 그 x 에 보링바 날끝을 두고 자루가 오른쪽 꺾은선 밑을 지나며(높이는 그 사이 꺾은선에서
    잰다) 뒤보링처럼 이름보다 먼저 그린다(이름이 바 위에 보이게). 요약 카드(이름 `typeName(…, flip)` "외경 → 면취 → 단면", 정보 줄 "정방향 바이트 · 반대 끝부터 (Z+)"), A 화면 카드(형상마다 저장된 뒤집기), 키패드 그림이
    같은 그림을 쓴다. 결과 카드 · 결과 띠 · 키패드 결과 한 줄 · 뷰어 라벨 · NC 는 calc 결과가 칸 순서(T1 = 먼저 깎는 점)라 따로 손대지 않아도 새 순서다.
  - 경고 글: Z 부호(`zBackward`)는 뒤집으면 반대 쪽(정방향 뒤집기는 Z 가 커짐 — "예: Z1 −5 → Z2 0"), 바이트 반대(`AGAINST_TOOL`) 글 · 접힌 글은 뒤집으면 진행 방향
    (외경 · 내경처럼 누운 요소를 바이트 반대쪽으로 깎음 — 뒤집은 외경 형상 3 에 시작점을 넣으면 그렇다)도 말한다.
    뒤집었을 때 접힌 글은 360dp 한 줄에 들게 "바이트와 반대 (면 · 방향) — 눌러서 자세히".
    `ENTRY_PAST_AXIS` 는 끝 단면을 X0(중심까지)으로 넣었으면 "단면을 중심(X0)까지 깎아 인선이 중심 너머 X−0.8 까지 간 뒤 Z 로 빠집니다(노즈 끝이 중심을 다 깎게 …).
    기계가 X− 로 그만큼 갈 수 있는지 확인하세요"(0.8 = 2 × 노즈R), 아니면 지금 글("… Z 여유를 줄이세요") — 단면을 중심까지 깎으면 일부러 넘는 것이라 Z 여유 글이 맞지 않았다
    (검증 2026-10-27, calc "가공 방향 뒤집기" ②).
  - 접근 줄(검증 2026-10-27, calc "가공 방향 뒤집기" ③): 뒤집어 척 쪽에서 시작하면 calc 의 NC 가 진입 G00 앞에 "앞 끝면 바깥에서 X 를 맞추는 G00" 을 넣고 진입은 Z 로만 간다
    (예: 외경 형상 1 `G00 X52. Z1.` → `G00 Z-6.`, 내경 형상 1 구멍 깊이 Z-20 `G00 X28. Z1.` → `G00 Z-20.`). 직접 보정 뷰어는 그 줄을 급송 점선(빈 화살)으로 그린다
    (`TaperSolution.approach`). 화면 역방향은 Z 를 거울로 풀어 calc 의 +Z 가 실제 척 쪽이라 `TaperInput.approach = false`(역방향 + 뒤집기의 실제 진입은 부품 앞) —
    골든 `taper-b-flip-reverse` 는 그대로. 역방향(뒤집기 없음)은 역바이트가 척 쪽에서 시작해 NC 에 접근 줄이 없으므로 형상 뷰어와 NC 카드 사이에 파란 접힌 띠(Info)
    하나로 알린다 — 내경 "첫 G00 이 구멍 안 — 접근 줄 안내", 외경 "… 척 쪽 …", 펼치면 첫 G00 줄과 "구멍 밖(끝면 바깥 Z)에서 X… 로 먼저 맞추고 Z 로"
    (외경은 "부품의 가장 큰 지름 바깥에서 Z 를 먼저 맞추고 X 로"). 뒤집은 넣은 시작점(구멍 깊이 · 척 쪽 외경)은 calc 가 그 Z 너머로 G00 하지 않는다(calc ①).
  - A 화면 카드: 뒤집어 둔 형상은 이름 앞에 `⇄ `(예: "⇄ 끝 면취") — 그림의 T 자리만으로는 뒤집힌 줄 잘 안 보여서(검증 2026-10-27).
  - 테스트: `TaperSolveTest` — `toggleFlipSwapsFields`(칸 맞바꿈 · 자동 칸 · 저장 · 옛 저장 값), `flipKeepsCompPointsReversesOrder`(모든 종류 · 형상 · 방향 × 노즈 R × 모서리 R ×
    시작 · 끝점: 보정점 같음 · 순서 반대 · 보정 쪽 반대 · 팁 같음), `reverseSingle/DoubleMatchesCalcReverse` 에 뒤집기(화면의 거울 + calc flip = calc reverse + flip, NC 글자까지),
    `flippedOuterType1Numbers`(calc 손 계산 G41 `G00 X52. Z1.` `G00 Z-6.` …), `travelTextMatchesSolution`, `entryFollowsZClearance` 에 뒤집기(진입은 첫 깎는 점에서 Z 로 c —
    쪽은 시작 요소가 정해 단면이면 공구 쪽, 수평 · 경사 연장선이면 진행 반대쪽이라 거리만 본다. 진입 G00 = 처음 G00 줄들의 마지막, 접근 줄은 한 축씩 · 역방향은 없음).
    `TaperWarningsTest.zSignFollowsFlip` · `pastAxisTextForFaceToCenter`(X0 끝: 글과 `G01 X-0.8` → Z 후퇴). 골든(접근 줄이 있으면 "접근 줄 [...]" 한 줄 더) `taper-b-flip`(외경 1) · `-outer3` · `-inner1` ·
    `-double` · `-reverse`(내경 역방향 1) — 안 뒤집은 골든은 그대로. 스크린샷 `taper-b-flip*`(직접 보정 · 키패드 · 360dp 큰 글꼴 포함) · `taper-a-flip` · `art-taper-flip`.
    입력 카드에 스위치 줄이 생겨 각도 칸이 내려갔다 — `taper-b-keypad-ang` 의 누르는 자리 (114, 635)dp.
- **각도 기준 — X축 · Z축**(사용자 결정 2026-10-28, `calc-design.md` '빈 값 풀기'): 현장 질문 "41 − 34.7 = 6.3, ÷2 = 3.15, × tan30 = 1.818, + 23.8 = 25.61 이니까
  Z-25.61 부터인데 왜 29.xxx 부터?" — 앱은 각도를 Z 축(가로선)에서 잰 각으로 받아 3.15 ÷ tan30° = 5.456(→ Z-29.256)을 냈고, 사용자는 도면의 30° 를
  단면(X 축 · 세로선)에서 잰 각으로 읽었다(3.15 × tan30° = 1.819 → Z-25.619). 그래서 **도면의 각이 단면(세로선)과 이루는 각이면 X축, 축(가로선)과 이루는 각이면 Z축**을
  고르게 했다(도움말에도 이 한 줄과 이 예). 처음은 X축, 마지막 선택은 `AppState.angleRef`(키 `taperAngleRef`, 테이퍼 화면 전체 하나)로 다음에 켜도 그대로.
  - 계산은 바꾸지 않는다: `SlopeForm.angleRef` 가 각도 칸의 기준이고 `zAngle`(X 면 90° − 값)을 calc 에 넘긴다. 결과 각은 `shownAngles(ref)` 로 되돌려 보인다.
    `SlopeForm` 의 `angleRef` 는 기본값이 없다 — 테스트도 기준을 밝힌다(옛 테스트의 각도 숫자는 Z축 기준: `TaperSolveTest` · `TaperWarningsTest` · 골든 `taper-b-auto` · `-error`).
  - 스위치 `AngleRefSwitch`: 홑테이퍼는 각도 칸 옆 반 줄(라벨 '각도 기준' + 두 칸 52dp "X축 기준 / 단면" · "Z축 기준 / 수평", 칸을 넘으면 "X축" · "Z축" 만 —
    360dp · 큰 글꼴. 두 줄이 52dp 를 넘는 큰 글꼴이면 줄이 글에 맞춰 높아진다 — 고정 52dp 였을 때 1.3배에서 '단면' · '수평' 아래가 잘렸다),
    2중 테이퍼는 각도가 결과라 결과 카드의 각1 · 각2 바로 아래. 칸이 읽히는 글은 "X축 기준, 단면" 한 번(안 놓는 짧은 글 "X축" 은 의미에서 뺐다). 바꾸면 `switchAngleRef` 가 모든 종류 · 형상 · 방향의 각도 칸을 같은 실제 경사의
    새 기준 값으로 바꾼다(`complementText`: 90 − 값, 넣은 소수 자리 그대로 — 30 → 60, 12.50 → 77.50. 칸 글은 기준을 따로 적지 않고 지금 기준으로 읽기 때문).
    빈칸(자동) 각도는 빈칸이고 새 기준으로 다시 보인다. 결과(점 · 보정점 · NC)는 같은 경사라 그대로.
  - 글: 각도 칸 이름 · 결과 카드 '각도 (X축 기준)' · '각1 (X축 기준)', 키패드 안내 줄은 기준마다(X: "단면(세로선)에서 잰 각 · 축(가로선)에서 잰 각이나 꼭지각 ÷2 는
    옆 스위치를 Z축으로" — 꼭지각의 반은 축에서 잰 각이라 Z축에서만 ÷2 가 그대로 맞다). 결과 띠 · 키패드 결과 한 줄 · 형상 뷰어에는 각도가 없다.
    경고 글(`BAD_ANGLE` "0° 보다 크고 90° 보다 작아야") · `CONTRADICTION` 은 90° − θ 에 대해 같아 그대로. `BAD_INPUT` 의 괄호는 기준과 상관없게
    "각도가 0° 나 90° 에 너무 가까우면 계산한 점이 그 밖으로"(옛 "너무 작으면" 은 X축에서 각도를 넣고 Z 를 비운 흔한 경우에 거꾸로였다).
  - 그림 `TypeArt(…, angleRef)`(A 화면 카드 · 요약 칸 · 키패드 그림): 경사(그림의 오른쪽 점 P → 왼쪽 점 Q)와 두 축 방향 선이 만드는 직각삼각형 P–Q–C 안쪽에 호를 둔다 —
    Z축은 P 에서 가로 점선과 경사 사이(전과 같다), X축은 Q 에서 P 높이 쪽으로 세로 점선과 경사 사이. 두 호가 경사의 같은 쪽이라 이송 화살(반대쪽) 규칙이 그대로다.
    2중 형상 4 는 X축 각1 글이 T1 점에 붙어 T2 자리를 (.47, .55) → (.44, .53) 으로 조금 옮겼다(개념도). 호 옆 '각도' · '각1' 글은 그대로.
  - 테스트 `AngleRefTest`: 사용자 예 그대로(형상 1~4, X축 → Z1 −25.619 · Z축 → −29.256), 모든 종류 · 형상 · 방향 · 뒤집기 × 빈칸 넷 × 7각 × 모서리 R 에서
    같은 경사를 두 기준으로 넣은 결과(경고 · 점 · 보정점 · 진입 · 두 NC)가 글자까지 같음(2중은 보이는 각만 90 − θ), 칸 글 바꾸기 · 스위치 전환(다른 형상의 칸 · 자동 칸) ·
    저장 · 되살리기. `AppStateTest.angleRefSwitchConvertsFieldAndIsRemembered` 는 화면에서 'Z축 기준' 을 눌러 본다. `InputRobustnessTest` 는 기준도 난수로.
    골든 `taper-b-angle-x30` · `-z30`(사용자 예), 나머지 테이퍼 골든은 각도 줄만 "각도 X축 기준 [45.000]"(예가 모두 45°라 숫자 그대로 — 보정점 · NC 는 한 글자도 안 바뀜).
    스크린샷 `taper-b-angle-x30` · `-z30` · `-w360`, `taper-a-angle-z`, `taper-b-double-w360`, 그림 비교 `art-taper-ref`(X · Z 나란히, 요약 칸 포함).
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
| `round/RoundModel.kt` | `RoundState`(화면 상태 + Saver — 내경 · 관통 · 보링바 칸 · 프리셋), `dims(type)`(형상마다 칸 이름·순서 = 그림), `blankable`, `RoundDefaults`(처음 값), 경고 글 `roundWarningText`, 칸 오류 `barFieldText`, 안전 여유 `holderSafe` |
| `round/HolderStopModel.kt` | 보링바 안전 끝점의 글(화면 · 골든이 같이 씀): 간섭 목록 `hitRows`(`HitRow`), 가공 가능 끝점 `stopView`(`StopView` · `StopKind`), 후퇴 길 읽기 `ncMoves` |
| `round/HolderStopCards.kt` | 간섭 목록 카드 `HolderHitsCard`, 가공 가능 끝점 카드 `SafeEndCard`, 내경 NC 카드 `HolderNcCard`(줄 색 `NcTone` · 짚은 줄 · 잘린/전체 `CutSwitch`) |
| `round/RoundArt.kt` | `RoundTypeArt(type, inner, through)` — 형상 그림 Canvas(외경 측면도 · 내경 구멍 단면), `sampleInput(type, inner, through)`(그림용 견본 치수) |
| `round/RoundParts.kt` | `ManualArt`(3점호 그림 — 내경 · 역방향을 따라 뒤집힘, focus), `RoundSummary`, `NodeTable`, `RoundViewer`(내경 보링바 단면 · 겹침 · 자리 고르기, 진입 경로 `entryMoves` · 닿는 구간 `contactBand`), `ArcCard` |
| `round/RoundInputScreen.kt` | `RoundTypeScreen`(형상 1~6 · 내경 2~6) · `ManualScreen`(수동 3점호): 요약 → 치수·공구 → (내경) 보링바 → 보정 방식 스위치 → (원호) → 경고 → (내경) 보링바 띠 → 노드 표 → 형상 확인 → NC |
| `round/HolderCard.kt` | 보링바 카드(단면 스케치 · 프리셋 시트 · 칸 · 칸 아래 오류 글) |
| `round/HolderPresets.kt` | 보링바 프리셋 `HOLDER_PRESETS` 51 줄 = 회사 사용 `COMPANY_PRESETS` 23 줄(아래 '회사 사용 프리셋') + 출처를 확인한 카탈로그 28 줄(제조사 · 품번 · D · f · DMIN · κr · 인서트 · 날 길이 · URL, 회사 줄은 출처 글 · 까닭도) |

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
- 보정 방식 두 개는 **같은 계산의 두 보기**다(calc-design.md): 기계 보정 = 접점 · 도면 점 · `drawing`, NC 는 기계 보정 프로그램 `RoundCalc.machine`(= 도면 좌표, 기계 G42 보정과 함께 씀 —
  노즈R 0 으로 푼 도면 좌표 풀이에서 **G00 만 노즈R · 팁에 맞게 띄운 것**, 사용자 결정 2026-10-28, calc-design.md "기계 보정 프로그램"). 표준 팁 · Z 여유 ≥ r 이면 예전(노즈R 0 풀이)과
  같은 글이고, 비표준 팁 · 3점호 접선 진입처럼 첫 면을 따라 들어가는 진입이 띄워지면 그 면에 직각으로 들어가는 `G01` 한 줄(`RoundSolution.lead`)이 더 붙는다 —
  형상 확인의 진입 경로도 진입점 □ → 직각 진입점 → 첫 점 두 토막(`entryMoves`), NC 카드 설명 '자세히' 와 도움말 '결과와 NC' 가 이를 적는다(예: 내경 돔 팁 0 →
  `G00 X17.2 Z1.4` `G01 X20.` `G01 Z0.` — 골든 `round-inner-b3-tip0`).
  직접 보정 = 합성 R 보정점 · `center` · `program` · `progR` · `nc`(G40, 가상 인선, 첫 줄 `(G40 DIRECT R0.4 T3)`). 고르는 곳은 테이퍼와 같은 앱 전체 스위치(아래 '보정 방식').
  노드 표는 점마다 지금 보정 방식의 X · Z 를 크게(numLarge), 다른 쪽 값을 그 아래 작게('도면 …' / '보정 …') 보인다.
  치수 · 공구 칸은 키패드 묶음(키패드 하나 · PC Tab 순서, 키패드 그림은 그 치수 하나만 초점색), 노드 표가 화면 밖이면 아래 결과 띠(첫 점 · 끝 점 · 외 n점).
- 입력값: 치수 칸은 형상마다 따로, 노즈R·Z 여유·끝 요소 길이는 함께, 팁은 외경 · 내경 따로(표준이 3 · 2 라 외경에서 고른 팁이 보링으로 넘어가지 않게 —
  테이퍼도 가공 종류마다 따로), 수동 칸은 따로. 처음 값(사용자 요청 2026-10-28, 예전엔 0): 치수 · 3점호 세 점 · Z 여유는 **빈칸** —
  치수 · 세 점의 빈칸은 아직 안 넣은 값(비울 수 있는 묶음은 하나를 자동 계산), 모서리 R · Z 여유의 빈칸은 0(칸에 흐린 '비우면 0'), 끝 직선 길이는 5.0 그대로.
  값이 모자라는 동안은 파란 안내만(그림은 이름표만). 빈 모서리 R 은 그림에도 이름만 적는다(예전엔 "모서리 R 0"). 스크린샷 · 골든은 예시 치수(jvmTest `roundExample` — 그림 모양대로 풀리는 값,
  형상 3 은 calc-design.md 의 손 계산)로 찍는다. 형상 1~6 은 외경 정방향(팁 3, G42), 내경 2~6 은 정방향 보링(팁 2, G41),
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
  - '프리셋 (선택)' 칸 → 시트(직접 입력 → **'회사 사용'** 묶음 → 제조사별 품번 — Ø · f · Dmin · κr · 인서트). 고르면 D · f · DMIN · κr · 모양 · 날 길이를 채운다
    (돌출 길이 · 노즈 R 은 그대로). 고친 뒤엔 품번 옆 '수정됨', 아래 '출처: <사이트>'.
  - **회사 사용 프리셋**(사용자 10-29 "회사에서 사용하는 팁홀더도 찾아서 홀더 종류에 추가"): 시트에서 직접 입력 바로 아래 맨 위 묶음. 값 · 출처는
    `docs/research/company-holders.json`(제조사 공식 카탈로그 · 제품 페이지, 출처를 다시 열어 칸마다 대조한 것)의 `presetCandidates` 20 줄 + 소경 3 줄 그대로다 —
    카바이드 자루 SCLCR(C08K · C10K · C12M-06 · C12M-09 · C16R) → 강 자루 STFCR(S10K · S12M-09 · S12M-11 · S16R) → 소경(SH0516-100 · SH0616-100 · PICCO).
    같은 품번도 제조사마다 값이 달라(C08K-SCLCR06 한국야금 f 5 · 대구텍 f 6, STFCR κr 한국야금 90° · 대구텍 91°) **제조사마다 한 줄**이고, 품번은 그 제조사의 표기
    (한국야금 `C08K-SCLCR-06` · 대구텍 `C08K SCLCR 06` · 미쓰비시 `C10KSCLCR06`), 품번 옆에 제조사 이름. 샌드빅 S12M/S16R-STFCR 11 은 단종 표시라 넣지 않았다.
    고르면 아래 줄이 '회사 사용 · 출처: <카탈로그 · 판 · 쪽>'(두 줄까지, 예 'TaeguTec Turning 2026 A175'). 슬리브(교세라 SH 0516/0616-100)와 계열 이름(이스카 PICCO)은
    출처에 바의 D · f · DMIN · κr 이 없어 **여섯 칸을 비우고**(사용자가 넣음 — 지어내지 않음) 시트 줄 · 카드에 까닭 한 줄('보링바 슬리브(구멍 Ø5) — 값은 끼운 바의 카탈로그로 넣으세요').
    '수정됨' 은 프리셋이 채운 칸을 고쳤을 때만(비운 칸에 넣은 값은 아님). 회사 NC 프로그램의 글 · 파일 이름 · 쓴 횟수는 앱 · 저장소에 넣지 않는다(기밀) — 표는 '회사 사용' 뿐.
    시험: jvmTest `CompanyHoldersJsonTest`(드릴의 `DrillCatalogJsonTest` 처럼 — 프리셋 20 줄 = `presetCandidates` 칸마다, 줄마다 `holders` 의 그 제조사 값 · 쪽, 빈 줄은 원본도 `missing`,
    보링바 · 소경이 빠짐없이 다 있음) · `RoundStateTest.companyPresets`(제조사별 값 · 빈 프리셋 · 수정됨 · Saver).
  - 칸: 바 지름 D · 날 길이 l(안내 '인서트 크기 숫자 (DCMT 07 → 7)') · f(바 중심 ~ 인선, 카탈로그 WF) · 인서트 모양 C 80° · D 55° · V 35° · T 60° ·
    절입각 κr(카탈로그 KAPR) 과 그 아래 '끝날각 κr′ = …°' · 돌출 길이 · 최소 가공경 Dmin(둘은 비우면 그 검사 안 함) · **안전 여유**(처음 0.5, 비우면 0.5,
    0 도 됨, 키패드 안내 '… 소재 사이 직선 거리(공구대는 Z) · 비우면 0.5', 그 아래 회색 한 줄 '… 소재와 직선 거리로 이만큼(공구대는 Z) 떨어진 마지막 자리에서 멈추고 빠집니다
    (인서트 날은 깎는 벽에, 막힌 바닥 · 단 면처럼 지금 깎거나 이어서 깎을 면에는 바 · 인서트 · 최소 가공경 구간이 붙어도 됨).' — 사용자 결정 2026-10-28 직선 거리 기준,
    예외 범위는 calc-design '안전 기준', 프리셋이 바꾸지 않는다). 틀리면 칸 빨간 테두리 +
    아래 빨간 글(`barFieldText`, 예 'f 는 8.435 이상이어야 합니다 (바 윗면이 노즈보다 낮게)' · '안전 여유는 0 이상이어야 합니다') 이고 아래 카드는 없다.
  - 값은 앱을 켠 동안만(Saver, 키 `h/*`) — 공구 값을 기억하는 것은 승인되지 않았다(C10).
- **간섭 좌표 · 안전 끝점 · 잘린 NC**(사용자 요청 2026-10-27 "간섭충돌 나는 이송 실제 좌표를 정확하게 찝어줘 … 간섭이 발생하지 않는 좌표까지 가공시키려고",
  결정: 첫 간섭 직전 안전 좌표에서 멈추고 빠진다 · 안전 여유 칸 기본 0.5 — 예전 결정 D5 '숫자 · NC 는 그대로' 를 바꿈. 계산은 calc-design.md 의 `HolderStop` 절):
  화면은 `HolderStop.plan`(값이 바뀔 때만 센다) 하나로 노드 표 위에 두 카드를 낸다. 바 값이 없으면 파란 '보링바 값을 넣으면 …', 되돌아가는 원호는 파란 '… 검사를 하지 않습니다'.
  **간섭 목록**(`HolderHitsCard`, 예전 노란 띠 자리 — 노랑 경고 아이콘 '보링바 간섭 — n곳')은 닿는 곳마다 한 줄: 부분 · 벽('바 · 맞은편 벽' · '공구대 · 입구 끝면' ·
  '최소 가공경 Ø20' — 입체 어림이라 뒤에), 빨간 'NC n줄'(전체 NC 줄), 닿기 시작 · 끝 X(직경) · Z 두 줄(고정폭), 깊이(반경 겹침 · 인선에서 앞/뒤 — 이송 방향 기준.
  닿음 · 깊이 · 처음 좌표는 10-28 에도 반경 겹침 그대로라 이 목록의 글 · 숫자는 바뀌지 않았다 — 직선 거리는 안전 여유 쪽만).
  좌표 · 줄은 보정 방식 스위치를 따르고 머리 아래 작은 글이 기준을 적는다 — 기계 보정 = 도면 명령 좌표(노즈가 닿는 도면 점. 진입은 그 프로그램 자기 진입 — 도면 G00 점에
  선 자세 · 보정 시작 블록에서 닿는 명령점이라 목록의 진입 줄이 직접 보정과 다를 수 있다, 10-28),
  직접 보정 = 가상 인선(첫 줄 주석을 센 줄). 줄을 누르면 그 줄에 초점색 띠, 형상 확인이 그 처음 점을 가운데로 맞춤의 3배(`ViewerState.focus`) · 그 자세의 보링바를 그리고
  화면이 형상 확인으로 오르며, NC 카드는 전체로 바뀌어 그 줄을 초점색 바탕으로 짚는다.
  **가공 가능 끝점**(`SafeEndCard`)은 끊으면 초록 깃발 '여기까지 깎고 빠집니다' + 좌표(24sp, 잘린 NC 에 쓴 글 그대로) + 'n줄 중간(끝)에서 끊음' · (기계 보정이 블록을
  안전 끝점에서 끝낼 수 없어 0.01 mm 넘게 덜 깎으면 '안전 끝점보다 경로로 … mm 앞에서 끊음 — … 직접 보정은 그만큼 더 깎음') · 그곳의 가장 가까운 부분 · **여유(직선 거리)**
  (사용자 결정 2026-10-28 — 공구 단면과 소재 사이 유클리드 거리, 깎는 면은 뺀 값 = 계산기 `SafeEnd.margin`. 공구대는 'Z 여유', 인서트의 깎는 쪽 벽은 기준이 '겹치지 않음',
  겹친 자세(기계 보정 G00 점부터 모자람)는 '겹침 … mm') · **깎는 면 예외**가 쓰였으면 그 바로 아래 작은 회색 글(caption) 한 줄 '깎는 면 예외: 바 앞면이 지금 깎는 면(X… Z…)에서
  0.226 mm — 바 앞면은 주절삭날 연장선이라 깎는 면에는 겹치지 않음만 봅니다.'(형상 요소의 끝점에서 바와 소재 직선 거리 `SafeEnd.gap` — 깎는 면도 넣은 것 — 이 안전 여유
  − 1e-4 보다 짧을 때. 그때 바의 나머지는 안전 여유를 지켰으니 가장 가까운 소재 `gapAt` 이 깎는 면이다. 예 C 막힌 단차 + 바 A, 골든 `round-inner-c-stop`) ·
  후퇴 방식 한 줄(X 빼고 Z 로 — 그 줄 글, 또는 온 길을 거꾸로 n줄, 후퇴 중 가장 좁은 여유(직선 거리). 기계 보정은 '첫 줄 G40 은 보정을 풀며 지금 인선 자리를 명령 — 더 들어가지 않음'),
  후퇴 여유가 안전 여유보다 좁으면 노랑 한 줄. 10-27 의 '바와 소재 직선 거리' 줄과 '직선 거리가 안전 여유보다 짧습니다 — 안전 여유는 반경(X) 방향 …' 노랑 줄은 기준이 직선 거리가
  되어 없앴다(예 B 처럼 반경 0.5 · 직선 0.043 인 끝점이 더는 나오지 않는다). 끝까지 안전하면 초록 체크 '끝까지 안전합니다' +
  '바와 소재 사이 가장 좁은 여유(직선 거리) … mm (n줄)'(`HolderResult.barClear` — 깎는 면 뺌. 관통 단차 + A16 은 1.911, 반경으로는 2.970 이었다), 진입점부터 · 첫 가공점 전에
  모자라거나 기계 보정만 끊을 자리가 없으면 노랑 경고와 까닭. 기계 보정은 그 프로그램 자기 진입으로 본다 — 도면 G00 점에
  선 자세부터 모자라면 '진입점부터 …'(그 G00 점과 '직접 보정은 진입이 달라 깎을 수 있습니다' 를 적음), 진입 블록에서 모자라면
  '첫 가공점에 닿기 전에 …'(그 명령점과 블록 — 보정 시작 블록, 띄운 진입이면 '직각 진입 뒤 블록' 도. 줄은 `at.machineLine`). 예 E(돔 + A16, DMIN 20)는 직접 보정 진입 이동 Z0.5 ·
  기계 보정 시작 블록 명령점 Z0.643 에서 멈춘다(반경 기준 땐 Z0 · Z0.286 과 '면을 넘자마자 여유가 모자람'). 결과 띠(아래 독)의 노란 수는 닿거나 끊을 때 하나 더한다.
  깎는 면 예외는 끝점 자세에서만 알린다 — 끝까지 안전한 경로에서 바 앞면이 깎는 면에 붙어 지나가는 것은 계산기가 경로 전체의 그 값을 내지 않아 적지 않는다.
  **NC 카드**(내경은 `HolderNcCard` — 테이퍼 화면의 `NcCard` 와 같은 꼴, 그 파일은 다른 작업이 쓰고 있어 따로 둠)는 끊으면 처음이 **잘린 NC**(앞 줄은 원래 글 그대로,
  끊은 마지막 블록 초록 띠 · 옅은 초록 바탕, 후퇴 줄 파랑 — 아래 색 이름표), 머리 아래 스위치 '안전 끝까지 (잘린 NC) / 전체 (확인용)'(360dp 반 칸에 들게 짧게). 전체는 간섭 줄 빨강 ·
  빨간 칩 '간섭 포함 — 확인용' · 노랑 테 복사 단추 '복사 · 전체 (간섭 포함) n줄'. 복사는 늘 보이는 글 그대로. 기계 보정 잘린 NC 의 칩은 '보정 시작 G41 만 넣기 · G40 은 있음'.
  깎을 수 없으면 스위치 없이 전체만(확인용 표시). 닿지 않고 끝까지 안전하면 예전 카드와 같다.
- **형상 확인**(`RoundViewer` + 보링바): 지름 전체 단면(`ViewerStock(mirror = true)` — 맞은편 벽도 빗금, 1dp 뒤집은 도면), 고른 자리의 바 · 인서트
  단면(초록 `drawHolder` · 면 `drawHolderFill`, 입구 끝면 너머 max(3, 깊이 15 %) 에서 자름), 겹친 곳 빨강 α .5(바와 같은 곳에서 자름), 닿는 경로 구간 빨강 3dp
  (직접 보정은 가상 인선, 기계 보정은 노즈가 닿는 도면 점 사이 — ✕ 표와 같은 곳. 진입 이동은 지금 NC 의 간섭 목록 그대로 — 기계 보정은 그 진입 블록(보정 시작 블록,
  띄운 진입이면 직각 진입 뒤 블록) 위 두 명령점 사이),
  처음 닿는 곳 ✕ '간섭 n줄'(간섭 목록과 같은 좌표 · 줄, 최소 가공경만이면 'Dmin n줄'),
  안전 끝점 초록 깃발 '안전 끝 n줄'(`MarkKind.Flag`, 깎기 전에 멈추면 '안전 끝'), 잘린 NC 의 후퇴 길(그 글을 다시 읽은 것 — 파란 `info.label` 점선 · 빈 화살).
  카드 아래(크게 보기에서도) '보링바를 그릴 자리' 알약: (목록에서 누른 간섭) · 안전 끝 · 첫 간섭 · 가장 깊은 곳 · 가장 좁은 곳 · P1…(처음은 맨 앞).
  범례 '보링바 단면' · '간섭' · '안전 끝' · '후퇴 (잘린 NC)'. 색 대비(뷰어 바탕): drawHolder 라이트 4.5 · 다크 10.6.
- 도움말 `RoundHelp`: '형상 고르기' 에 외경/내경 스위치, '치수 넣기' 에 입구 구멍 깊이 · 막힘/관통 · 입구 R 은 3점호, 새 묶음 '보링바 간섭 (내경)'
  (칸 · 간섭 목록 · 안전 여유(직선 거리 — 공구대는 Z)와 가공 가능 끝점 · 깎는 면 예외(막힌 바닥 · 단 면처럼 이송 앞을 막는 곧은 면을 깎는 동안 · 오목하게 이어져 곧 깎을 때 — 끝내 깎는 면만 — 그 평면에는 바 앞면 · 인서트 · 최소 가공경 구간도 겹치지만 않으면 됨, 끝점 카드에 작은 글) · 잘린 NC · 후퇴 ·
  단면의 한계(DMIN 어림 · 진입 길 안 봄) · 프리셋은 제조사마다 다름) — 내경 입력 화면은 이 묶음부터 연다. '결과와 NC' 의 기계 보정 줄은 G00 을 노즈R · 팁에 맞게 띄운다는 것도 적는다.
  '경고' 에 보링바 간섭은 노란 표가 붙은 목록 카드.

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
- 처음 값(사용자 결정 2026-10-17): 다섯 칸 모두 빈칸 — 자리표시 '0' 도 없앴다(사용자 요청 2026-10-28 "0 이 적힌 칸은 전부 빈칸"). 스크린샷 · 골든은 예전 처음 값 밑변 10 · 각도 A 25° 로 찍는다
  (A = 30° 면 X 직경 = 빗변이 되어 헷갈려서 피했다).

## 나사 G76 화면 (`screens/ThreadScreen.kt`, `screens/thread/`)
| 파일 | 내용 |
|---|---|
| `ThreadScreen.kt` | `ThreadScreen()` — 외경/내경 → (파란 안내 · 빨간 멈춤) → '스레드 및 소재 설정'(접이 '고급 공차 및 인선 설정' 포함) → (노란 확인 · 역가공 안내) → 'G76 파라미터 입력 및 분석' → 회차별 분석 → 나사 단면 → NC. 고르기 시트 `ThreadSheet`, 결과 칸 `resultCells`(골든도 씀), 도움말 `ThreadHelp` |
| `thread/ThreadModel.kt` | `ThreadField`(숫자칸 10 · `advanced`), `Pick`(고르기 시트 7), `ThreadState`(+ Saver · `input` · `missing` · `bad` · `flagged` · `placeholder` · `switchInner` · `switchKind` · `pickSize`), G76 낱말 `g76Words` · `NcWord` · `ncText`, 경고 글 `threadWarningText`, 칸 안내 `fieldHint`, 급수 이름 `classText` · `classNote` |
| `thread/ThreadParts.kt` | 드롭다운 칸 `ChoiceField` · 시트 `ChoiceSheet` · `ChoiceRow` · 칩 격자 `ChoiceGrid`, 색 띠 칸 `Marked`, 접이 막대 `AdvancedBar`, G76 한 줄 `G76Line`, 결과 칸 `ResultCell`, 분석표 `PassTable`, 자동 값 색 `AppColors.threadAuto()` |
| `thread/ThreadArt.kt` | 나사 단면 `ThreadSection(SectionGeom)`, 자료 `sectionGeom(inner, sol)`, 날 단면 반폭 `toolHalfWidth` |

- 순서 · 이름은 원본 08 그대로(글은 한글): ② 외경 가공 (OD) / 내경 가공 (ID) → ③ 스레드 및 소재 설정 → ④ 공차 급수 상자(이름 + 오른쪽 드롭다운 '2A (보통)') →
  ⑤ 나사 종류 · 사이즈 · 산수 (TPI)(미터는 '피치') → ⑥ 가공 소재 · 방향(정가공(우) / 역가공(좌)) → ⑦ Z축 시작점 · 회전수 (RPM) → ⑧ 가공 목표점 (Z) · 나사산 각도 · 면취량 →
  ⑨ 고급 공차 및 인선 설정(막대 오른쪽 '목표 80%') → ⑩ G76 파라미터 입력 및 분석 → ⑪ 안내문 → ⑫ G76 줄. 그 아래(원본에서 잘린 곳)는 우리가 정했다:
  G76 둘째 줄 · 첫 절입 칸 · 결과 격자 → 회차별 분석 → 나사 단면 → NC.
- 처음 값(사용자 결정 10-28 '입력 칸은 처음에 빈칸'): 숫자칸은 모두 빈칸, 사이즈는 안 고름(나사 종류는 미터). 도면 값(사이즈 · Z축 시작점 · 가공 목표점 · 첫 절입 Δd)은
  다 넣을 때까지 파란 안내 '값을 넣으면 바로 계산합니다 — 남은 것: …'. 가공 값(공차 목표 % · 인선 R · 정삭 횟수 m · 최소 절입 Δdmin · 정삭 여유 d · 시작 X 여유)은 빈칸 =
  계산기 기본값이고 자리표시 '기본 80' · '기본 0.183'(인선 R 은 사이즈를 고르면 그 H/6 · H/8) 으로 알린다. 회전수는 자리표시 '안 씀'(넣으면 `G97 S` · 분당 이송 · 칸 아래 Vc 한 줄).
  드롭다운은 원본 화면 값이 기본(급수 6g/6H · 2A/2B, 나사산 각도 60°, 면취량 1.0P), 가공 소재는 '고르지 않음'. 첫 절입은 기본값이 없다(calc-design.md '나사 G76').
  급수는 외경/내경 · 나사 종류에 딸려 바꾸면 기본으로, 나사 종류를 바꾸면 사이즈를 비운다. 사이즈를 고르면 보통 나사(UNC) 피치가 먼저(호칭만 쓴 도면 M10 = 보통 나사).
- 칸 묶음('다음'): Z축 시작점 → 회전수 → 가공 목표점 → (고급 설정을 펴면 공차 목표 → 인선 R → 정삭 횟수 → 최소 절입 → 정삭 여유 → 시작 X 여유) → 첫 절입.
  키패드 결과 한 줄은 G76 둘째 줄. 멈춘 경고가 고급 설정 칸을 짚으면 접이가 저절로 펴진다.
- **G76 카드**(원본 ⑫ 'G76 [P 02] 1060 [Q 35] [R 0.02]'): 두 줄을 낱말 상자로 — **테두리 칸 = 눌러서 고치는 값**(첫 줄 P m · Q Δdmin · R d, 둘째 줄 Z · Q Δd — 누르면 그 칸
  키패드, 고급 설정 칸이면 접이를 편다. 빈칸이면 기본값을 흐리게, 넣어야 할 빈칸은 파란 '?', 고치는 중이면 초점색, 멈춘 칸은 빨간 테), **테 없는 주황 고정폭 = 자동 계산 값**
  (r·a '1060' · X · P(k) · F — 누르면 어디서 나온 값인지 토스트). 풀리면 낱말은 계산기 NC 글을 쪼갠 것이라 이으면 NC 와 한 글자도 같다(`ThreadStateTest` · `ThreadGoldenTest`),
  아직이면 넣은 값 · 기본값으로 첫 줄을 쓰고 둘째 줄의 X · P(k) 는 '—'. 줄이 칸보다 길면 낱말 단위로 다음 줄(FlowRow). 그 아래 [첫 절입 Δd][절입 횟수 '13 + 2'],
  풀리면 결과 격자(목표 피치경 · 나사 전 바깥지름(구멍 지름) · G76 X · 나사산 높이 k · 사이클 시작점 · 리드 F · 인선 R · 분당 이송).
- 색: 자동 계산 값(G76 줄 · 결과 격자 · 분석표 X · 결과 띠 값)은 `threadAuto()` — 나사 기능색 주황(#EA580C)을 라이트에서 글 색 쪽으로 25% 짙게(흰 카드 위 약 5.4:1 —
  #EA580C 그대로는 3.6:1 로 숫자 글 4.5:1 에 못 미침), 다크는 #FB923C 그대로(약 7:1). Z축 시작점 · 가공 목표점은 칸 왼쪽 4dp 색 띠(`Marked` — 시작 `drawT1` 주황 ·
  끝 `drawT2` 청록, 결과 띠의 '시작' · '끝' 이름과 같은 색). 원본의 노랑 · 빨강 칸 바탕은 쓰지 않았다(빨간 칸 = 계산 멈춤 칸 · '색의 뜻 고정').
- 고급 설정(펴면): [공차 목표 %][인선 R] / [정삭 횟수 m][최소 절입 Δdmin] / [정삭 여유 d][시작 X 여유] → 한계 치수 상자(고른 급수의 피치경 · 산봉우리 지름 범위와 목표 값,
  골 한계 — 사이즈만 골라도 보인다) → 접힌 설명. 막대는 남색 테 + 옅은 바탕(원본의 남색 채움 위 흰 글은 4.5:1 미만), ⌄ 는 펴면 위로.
- 경고: 빨강 = 계산기 멈춤 7가지 + 범위 밖 값(`ThreadState.bad()` = 계산기 BAD_INPUT 규칙 그대로 — 값이 모자라는 동안에도 바로, 글은 칸마다 범위). 노랑 = ROOT_INTERFERES ·
  ONE_ROUGH_PASS(설정 카드와 G76 카드 사이). 파랑 = 값 부족, `ThreadLimits.byFormula` 일 때 '공차 일부는 식으로 계산 — 표와 몇 μm 차이'(접힘), 역가공이면
  '역가공 (Z+) — 기계에서 한 번 확인'(접힘 — FANUC 매뉴얼 그림이 모두 Z− 라서).
- 결과 띠: '시작' X Z(사이클 시작점) · '끝' X Z(G76 X · 나사 끝 Z), 멈추면 첫 경고의 첫 문장.
- 회차별 분석(`PassTable`): 회 · 누적 깊이 · 절입(반경) · X(직경, 주황) · 정삭 표(정삭 줄은 옅은 바탕 + '정삭' — 색만으로 가르지 않게). 60 줄 넘으면 '… n줄 더 (NC 에는 모두)'.
- **나사 단면**(`ThreadSection`): 축을 지나는 단면 두 피치, 피치 기준 실제 비율 — 산봉우리 평면 · 60° 옆날 · 골은 인선 R 원호(날 끝이 마지막에 지난 자리), 외경은 소재(빗금)가
  아래 · 내경은 위. 왼쪽 산 높이 k 치수, 산봉우리 쪽 피치 P 치수, 오른쪽 이름 셋(바깥지름(안지름) Ø · 피치경 Ø(점선) · 골 X — 겹치면 벌림), 왼쪽 홈 날 끝 너머 '인선 R…',
  오른쪽 홈의 가는 선 = 거친 절입마다 날 끝 깊이. 그림이 계산과 같은지: 피치선의 홈 반폭이 P/4(피치경의 뜻)인지 `ThreadStateTest.sectionMatchesCalc` 가 본다.
  값이 없으면 피치 1 의 최대 실체 기본 형상에 이름만.
- NC: 테이퍼와 같은 `NcCard` — 프로그램 `(G97 S…)` · `G00` 시작점 · G76 두 줄, 꼭 알 것 'P · Q 는 μm 정수 (0.001 mm 기계)'(노랑) · 'M03/M04 · 공구 호출 없음' · '끝나면 시작점으로',
  설명에 IS-C(0.0001 mm) 기계는 P · Q 를 10배로 읽는다는 주의.
- **보정 방식 스위치는 없다**: G76 은 노즈R 보정(G41/G42)을 쓰지 않는다(이 화면의 NC 는 하나).
- 좁은 폰 · 큰 글꼴: 가공 소재 · 방향은 (칸 폭 ÷ 글꼴 배율) < 260dp 면 두 줄, 드롭다운 칸 이름은 띄어쓰기에서 두 줄까지, 1/4 칸 드롭다운은 50dp × 글꼴 배율보다 좁으면 ⌄ 를 뺀다,
  나사 종류는 'M (미터)' · 'UN (유니파이)'(줄어도 기호가 남게), 카드 제목 · 부제는 `keepWords`.
- 시험: `ThreadStateTest`(원본 08 = 손 계산 NC · 낱말 = NC 글 · 빈칸 안내 · 칸마다 범위 밖 값 ⇔ 계산기 BAD_INPUT · 멈춤이 짚는 칸 · 급수/사이즈 초기화 · 저장 · 자리표시 = 기본값 ·
  단면 그림 = 계산 · 스크린샷 상태의 경고), `ThreadGoldenTest`(골든 `thread*.txt` 8개 — `UiGoldenTest` 와 같은 폴더 · 같은 `-DupdateGolden=true`),
  `ThreadShotsTest`(`thread-*`: 빈 화면 · 원본 08 · 내경 M10 6H 50% · 4h 식 · 노랑 둘 · Z 순서 멈춤 · 역가공 · 범위 밖 · 키패드 둘 · 시트 셋 · 결과 띠 · 360dp 130% · 도움말).
  상태는 jvmTest `screens/thread/ThreadExample.kt`.

## 턴밀 극좌표 화면 (`screens/PolarScreen.kt`, `screens/polar/`)
| 파일 | 내용 |
|---|---|
| `PolarScreen.kt` | `PolarScreen()` — 외측/내측 → (파란 안내 · 빨간 멈춤) → 형상 뷰어 → '작업 도형 선택'(도형 · '치수 입력' · '가공 옵션') → (노란 확인) → '프로그램 생성'(결과 칸 · 이송 시간 · 시뮬레이션 단추) → 파란 접힌 띠 → NC. 도형 시트 `ShapeSheet`, 키패드 한 줄 `previewLine`, 도움말 `PolarHelp` |
| `polar/PolarModel.kt` | `PolarField`(숫자칸 8), `PolarState`(+ Saver · `input` · `missing` · `bad` · `flagged` · `placeholder` · `label` · `hint` · `shapeBad`), 도형 이름 `shapeName` · `shapeNote`, 경고 글 `polarWarningText`, 결과 칸 `polarResultCells`(골든도 씀), G코드 목록 낱말 `ncTokens`(`NcTok` — 흐린 좌표) |
| `polar/PolarArt.kt` | 그림 자료 `PolarGeom` · `polarGeom(s, sol)`, 같은 규칙의 도형 `roundedPolygon` · `twoFlatShape`, 그리기 `polarAxes` · `polarShape` · `polarDims` · `segPath` · `fitXf`, 형상 뷰어 `PolarFigure` |
| `polar/PolarSim.kt` | 재생 계획 `SimPlan`(줄 시간 · 지나온 경로 · 공구 자리 · Z), 재생 상태 `SimRun`(시작 · 정지 · 리셋 · 싱글 블록 · 속도 · `advance(dt)`), 모달 `PolarSimModal`(그래프 · 범례 · 좌표 줄 · 단추 · 속도 슬라이더 · G코드 목록) |

- 순서 · 이름은 원본 12 · 07(글은 한글, 바꾼 것은 `screens.md` '원본과 다르게 한 것'). 평면 점은 계산기와 같은 `Pt(z = −C, x = X 반경)` — Z 오른쪽 · X 위로 그리면
  원본 그림(X 위, C(+) 왼쪽)이고 `Arc.cw` = 화면 시계 = G02.
- 처음 값(사용자 결정 10-28 '입력 칸은 처음에 빈칸'): 숫자칸 모두 빈칸 · 외측 · 육각형 · X 직경값. 도면 값(외경(내측은 기초 구멍 지름) · W · 공구경 · 깊이 Z · 이송 F)은
  다 넣을 때까지 파란 안내 '남은 것', 가공 값(코너 R · 회전 · 여유)은 빈칸 = 0 · 0 · 1 이고 자리표시 '0' · '0' · '기본 1'. 범위 밖 값은 계산기 BAD_INPUT 과 같은 규칙
  (`PolarState.bad()`)으로 값이 모자라는 동안에도 바로 빨간 띠 · 칸 테. 칸 묶음('다음'): 외경 → W → 코너 R → 회전 → 공구경 → 여유 → 깊이 → 이송.
- **형상 뷰어**(`PolarFigure`, 270dp): 점선 축(X 세로 · C 가로, 끝 이름 X(+) · X(−) · C(+) · C(−)), 외경(내측은 기초 구멍) 호박색 점선 원과 이름 'Ø80'(원의 오른쪽 위 바깥),
  도형 면(청록 옅게) + 윤곽(청록 2.4dp), W 치수(첫 변의 법선 방향 · 중심을 지남 · 가운데 알약 'W 70'). 홀수 각은 맞은편이 변이 아니라 가는 점선 내접원까지.
  내측은 소재 덩어리(면 + 빗금)에서 다각형 구멍을 뺀 단면. 풀리면 계산기 단면(`sol.shape`) 그대로, 아니면 같은 규칙으로 만든 도형(외경 · W 가 비면 원본 12 의 비율 80 : 70,
  값이 서로 맞지 않아도 넣은 그대로 — 멈춘 까닭이 보이게). 아래 한 줄 '실제 비율 …' / '예시 비율 — …'. 키패드 그림(외경 · W · 코너 R · 회전)은 같은 그림, 고치는 칸이
  외경이면 원 · W 면 치수를 초점색으로. 확대 · 이동은 없다(원본처럼 한눈 그림).
- **보정 방식**: '가공 옵션' 안 한 줄 두 칸 '기계 보정 G41' · '직접 보정 G40' — 테이퍼 · 라운드의 `CompModeSwitch` 와 같은 `AppState.compMode`(한쪽에서 바꾸면 다른 쪽도).
  아래 한 줄 '공구 보정 화면에 R5 · T0 …' / '앱이 계산한 공구 중심 좌표 그대로 …'. 결과 칸 · NC · 시뮬레이션이 따른다. 'G12.1 안 X 지령: 직경값 · 반경값'(`PolarState.xDiameter`,
  화면 상태 — 기계 매개변수라 기억하지 않음)과 '첫 가공은 싱글 블록으로 …' 한 줄.
- **프로그램 생성** 카드(풀리면, 결과 띠의 '결과' 가 가는 곳): 결과 칸 6(`ResultCell`, 값 `valueComp`) — 공구 보정 화면(R… · T0 / 보정 없음) · 시작점 G00 X… C0. ·
  꼭짓점 지름(2면폭은 외경) · 평면 길이 · 진입 · 후퇴 원호(직접 보정은 공구 중심 R) · 가장 두꺼운 살, 그 아래 '이송 시간 ≈ …'(`SimPlan.feedSeconds` — 경로 길이와 Z 이송 ÷ F,
  급송 · 공구 교환 빼고), '시뮬레이션 — 공구 경로 재생' 단추(52dp, `appsFill`). 그 아래 파란 접힌 띠 '기계마다 다른 것 — M 코드 · X 직경/반경' → `NcCard`(꼭 알 것 'G98 · F mm/min' ·
  'G12.1 안 X = 직경'(노랑) · 'M 코드 · T 없음' · '공구 보정 R5 · T0'). 첫 줄 주석 `(R5 T0)` · `(G40 DIRECT R5 T0)` 은 계산기가 넣는다(화면이 또 붙이지 않음).
- 경고: 빨강 = 계산기 멈춤 7가지(`polarWarningText` — 칸은 `flagged`), 노랑 = INNER_CORNER_LT_TOOL · STOCK_LEFT · CORNERS_OUTSIDE_OD(입력 카드와 결과 카드 사이), 파랑 = 값 부족.
  결과 띠: '시작' X(G00 시작점 직경) · Z(깊이), 멈추면 첫 경고의 첫 문장.
- **시뮬레이션 모달**(`PolarSimModal`, 원본 07 · `Modal` 그대로): 제목 '시뮬레이션 · 기계 보정 G41'(직접 보정 G40) → 그래프(모달 높이의 34 %, 150~250dp — 짧은 PC 창에서도
  목록이 보이게; 축 · 외경 원 · 도형 · 지나온 공구 중심 경로 `viewPath` 2.5dp · 공구 원 `viewNose`(면 위 공기 중이면 점선 테) · 오른쪽 위 'n / m줄') → 범례 → 좌표 줄 세 칸
  'X (직경)'(반경 지령이면 'X (반경)') · 'C (mm)'(G12.1 앞 줄은 'C (°)') · 'Z (깊이)' = 지금 줄의 줄 끝 지령값(`PolarBlock.x · c · z`) → 단추 한 줄 [시작 / 정지 / 계속 / 다시]
  [리셋] [☑ 싱글 블록](48dp, 글은 좁으면 줄어듦) → '속도 x5' 슬라이더(x1 · 2 · 5 · 10 · 20 · 50 · 100, 처음 x10, 누르거나 끌기 · 48dp) → G코드 목록(LazyColumn, 민트 옅은 바탕).
  - 목록: 지금 줄 노란 띠(`currentBand` — 라이트 #FCD34D · 다크 #5C430D), 앞 줄과 같아 프로그램에서 뺀 X · C 는 흐린 낱말로 제자리에(`ncTokens` — 블록의 줄 끝 지령값을
    계산기와 같은 `Fmt.nc` 로, 복사 글에는 없음), 주석은 초록(`chipMint.label`), 지금 줄이 바뀌면 그 두 줄 위까지 저절로 내린다(`animateScrollToItem`).
  - 재생(`SimRun`): 공구 중심은 계산기의 줄별 경로(`PolarBlock.path` — G41 은 제어기 보정 흉내, G40 은 쓴 그대로)를 따라 실제 이송 F(평면 접선 mm/min) × 배율로 간다.
    Z 만 움직이는 G01 은 Z 거리 ÷ F, 평면을 움직이지 않는 줄(주석 · G98 · G00 · G12.1 · G13.1)은 배율과 상관없이 0.25 초 머문다(급송 속도는 기계마다 달라 만들지 않음).
    시간은 `LaunchedEffect` 안 `withFrameNanos`(프레임 시각) — 폰 · PC 같은 빠르기, 한 프레임 건너뜀은 0.25 초까지. 싱글 블록은 줄 끝마다 멈추고(방금 끝난 줄이 지금 줄)
    다시 누르면 다음 줄 — 기계의 싱글 블록처럼. 리셋 = 시작 전(공구 · 띠 없음).
- 시험: `PolarStateTest`(원본 12 입력 · 원본 07 줄 · 빈칸 안내 · 자리표시 = 계산 기본값 · 칸마다 범위 밖 값 ⇔ 계산기 BAD_INPUT(1650 가지) · 멈춤이 짚는 칸 · 저장 ·
  그림 = 계산(99 가지 — 둘레를 촘촘히 짚어 서로 0.0001 mm 안) · 예시 비율 · 흐린 좌표 = 앞서 마지막으로 쓴 낱말(NC 를 다시 읽어 셈) · 재생 계획(줄 시간 = 길이 ÷ F, 줄 끝 공구 = 경로 끝,
  줄 가운데 공구는 경로 위) · 재생 진행(프레임 합 = 줄 시간 합 · 배율 · 싱글 블록 · 리셋 · 계속)), `PolarGoldenTest`(골든 `polar*.txt` 9개 — 두 프로그램 · 흐린 좌표 `[ ]` ·
  결과 칸 · 이송 시간, `-DupdateGolden=true`), `PolarShotsTest`(`polar-*`: 빈 화면 · 원본 12(기계 · 직접) · 07 값 · 내측 둘 · 2면폭 · 삼각형(반경 지령) · 멈춤 · 범위 밖 ·
  시뮬레이션 여섯(원본 07 과 같은 줄 · G41 · 싱글 블록 · 내측 · 시작 전 · 짧은 PC 창) · 시트 둘 · 키패드 둘 · 결과 띠 · 360dp 130% 둘 · 도움말). 상태는 jvmTest `screens/polar/PolarExample.kt`.

## 재료·절삭조건 화면 (`screens/CuttingScreen.kt`, `screens/cutting/`)
| 파일 | 내용 |
|---|---|
| `CuttingScreen.kt` | `CuttingScreen()` — 헤더 아래 고정 밑줄 탭 셋(`UnderlineTabs`) + 탭마다 스크롤 본문(`TaperPage`, 결과 띠 없음). 탭을 바꾸면 키패드를 닫고 스크롤은 맨 위. 도움말 `CuttingHelp`(탭 셋 · 기본계산기 · 재료분석 · 드릴링 · 경고 — 지금 탭의 묶음부터 열림) |
| `cutting/CuttingModel.kt` | `CutTab`, 숫자칸 `CutBox`(기본계산기 9 · 드릴링 3, `cut` = 계산기 `CutField`), `CuttingState`(+ Saver · `set` — Vc/n · f/fz 마지막에 넣은 쪽이 기준 · `input` · 드릴 고르기 `pickMaker` · `pickSeries` · `pickIso` · `pickRow` · `drill()` · `drillMissing`), 숫자 글 `fixed` · `trim` · `feedText` · `rpmText` · `diaText`, 화면 글 `basicView`(`BasicView`) · `cutWarningText` · `basicPreview` · `drillLines`(`DrillLine`) · `drillWarningText` · `drillPreview` · `sourceText` · `rowSub` · `seriesTitle` · `coolantText` · `drillTypeText` — 골든도 이것을 쓴다 |
| `cutting/BasicTab.kt` | 기본계산기 카드 다섯 |
| `cutting/MaterialTab.kt` | 재료분석 — 안내 카드 + ISO 군 카드 여섯 |
| `cutting/DrillTab.kt` | 드릴링 — 공구 카드 · 메이커 · 공구 종류 · 피삭재 · 직경과 결과, 빨간 테 칸 `drillBad` |
| `cutting/CuttingParts.kt` | `UnderlineTabs`, ISO 513 색 `AppColors.iso(g)` · `IsoBadge` · `IsoChips` · `IsoBand`, 고르는 카드 `PickCard`, 안내 상자 `NoticeBox`, 결과 칸 `ValueCell`, 드릴 표 `DrillTable`, 드릴 모양 `DrillArt`, `StepTitle` · `Rule`, 강조색 `cutAccent()`(재료 기능색) |

- 원본 03 은 드릴링 탭 위쪽만 보인다(`screens.md` 3.2). 탭 이름 · 순서 · 드릴링의 ①~③ 차례는 원본, 나머지(기본계산기 · 재료분석 전체, 드릴링 피삭재 아래)는 우리가 정했다.
  처음 탭은 기본계산기. 계산 · 자료는 모두 calc(`Cutting` · `WorkMaterials` · `DrillCatalog`, calc-design.md '재료·절삭조건') — 화면은 숫자를 만들지 않고 글로만 바꾼다.
- 처음 값(사용자 결정 '입력 칸은 처음에 빈칸'): 숫자칸 모두 빈칸, 드릴링은 메이커 · 공구 종류 · 피삭재를 하나도 고르지 않는다(고르지 않은 행의 숫자가 나오지 않게).
  빈칸인 줄은 결과를 비우고 무엇을 넣으면 되는지 적는다(0 으로 계산하지 않음). 정해진 기본값이 있는 칸만 자리표시: 날 수 '기본 2' · G50 S '안 씀'.
- **밑줄 탭**: 같은 폭 세 칸 52dp(원본은 글 폭만큼 왼쪽부터 — 장갑 · 360dp 에서 누르기 쉽게 같은 폭), 고른 칸 = 옅은 강조 바탕 + 3dp 밑줄 + 강조색 굵은 글(색만으로 가르지 않음).
  강조색은 재료 기능색(`features[CUTTING].title`, 라이트 #0369A1 · 다크 #38BDF8).
- **기본계산기**(카드 다섯, 칸 묶음 순서 D → Vc → n → f → fz → Z → L → 노즈 R → G50 S):
  - '회전수 · 절삭 속도' [지름 D] / [Vc][n] — Vc 와 n 중 **나중에 넣은 쪽이 기준**이고 다른 칸은 글을 비워 `autoValue`(청록 '자동')로 계산값을 보인다. 아래 한 줄 안내.
  - '이송' [f] / [fz][날 수 Z] — f 와 fz 도 같은 규칙(fz 를 넣으면 f 칸이 자동 = fz × Z). 아래 결과 칸 '분당 이송 vf'.
  - '가공 시간' [L] · 결과 '가공 시간 … 분'(아래 '… 초'). G96 을 쓰면 시간이 다르다는 한 줄.
  - '이론 거칠기 (선삭)' [노즈 R] · Rmax · Ra(μm) · 실제 어림(강 × 1.5~3 · 주철 × 3~5, KORLOY). f ≥ 2r 이면 노란 띠.
  - 'G96 주속 일정 · G50 S 제한' [G50 S] · 막히기 시작하는 지름 · 그 지름의 회전수 · 막히면 실제 주속과 **막힌 회전수로 낸 분당 이송**(calc-design.md 'G96 주의' 를 화면이 이렇게 정함 —
    위 카드들의 n · vf · 시간은 식의 회전수 그대로 두고 노란 띠가 '기계는 S… 로 돕니다' 라고 적는다).
  - 숫자 글: n 정수 · Vc 소수 1 · f 소수 3 · vf 소수 1 · 시간 분 소수 2 · 초 소수 1 · 거칠기 소수 2 · 지름 3(5 는 올림). 키패드 한 줄 = n · vf · 시간 · Rmax.
  - 잘못된 칸(계산기 `CutResult.bad` — 숫자 아님 · 0 이하 · 한도 밖 · 날 수 1~100 정수 밖)은 빨간 테 + 위 빨간 띠. 그 칸에 기대는 결과만 비고 나머지는 계산한다(띠 글이 그렇게 말함).
- **재료분석**: 안내 카드(ISO 513 · 색은 군 식별색이고 경고 색이 아님 · 대조는 KORLOY 대조표 칸 그대로 · 공구사마다 군이 다를 수 있음) → 군 카드 여섯(P M K N S H). 카드 = 왼쪽 6dp
  ISO 513 색 띠 + 40dp 글자 판 · 이름 · 'ISO P · ISO 513 파랑' → 들어가는 재료 · 가공 특성 → 대표 재질(KS 기호 고정폭 굵게 + 'KS' · 이름, 아래 'JIS … · ISO … · 미국 …', 메모)
  → 출처 줄(설명 · 대표 재질). KS 가 없는 줄(S 군)은 미국 칸(Inconel 718) 또는 이름이 머리. 절삭 속도 숫자는 없다.
- **ISO 513 색**(`AppColors.iso`): ISO 513:2012 표 5 의 식별색 P 파랑 · M 노랑 · K 빨강 · N 초록 · S 갈색 · H 회색. 라이트 #1D4ED8 · #EAB308 · #DC2626 · #15803D · #92400E · #6B7280,
  다크 #60A5FA · #FACC15 · #F87171 · #4ADE80 · #D19A6A · #9CA3AF. 글자 판 위 글은 `onFill`(흰 글 4.8:1 이상 · 밝은 판은 짙은 글). 노랑 · 빨강이 경고 색과 겹치지만
  업계가 인서트 상자 · 카탈로그에 쓰는 색이라 그대로 두고, 늘 군 글자와 함께 띠 · 판으로만 쓴다(바탕 · 경고 자리에는 안 씀). 색은 이 화면만 써서 `AppColors` 가 아니라 `CuttingParts.kt` 에 둔다.
- **드릴링**(원본 03 순서): 남은 것이 있으면 맨 위 파란 안내 '남은 것: 메이커 · 공구 종류 · 피삭재 · 직경' →
  - '드릴링 가공 조건 계산' 카드: 안내 상자 '카탈로그 참고값'(원본은 노랑 상자 — 안내라 파랑) → **공구 카드**(원본 ⑤ 제품 사진 자리: 104dp 회색 판에 형식별 드릴 모양 `DrillArt` —
    솔리드 초경 · 헤드 교환식(헤드 색 · 이음선 · 굵은 생크) · 인서트(곧은 홈 · 날 끝 인서트 둘); 오른쪽 형식 이름(강조색) · 시리즈 이름 그대로 · 직경(카탈로그 주문 범위) · 깊이 · 급유
    (앞 낱말 internal/external/both 만 한글로, 각주는 카탈로그 글 그대로)) → ① 메이커 선택(`ChoiceGrid` 3열: Sandvik · ISCAR · Mitsubishi / YG-1 · KORLOY · 직접 입력)
    → ② 공구 종류(`PickCard` — 제목 = 시리즈 이름의 첫 ' (' · ' — ' 앞, 아래 나머지 + '솔리드 초경 · Ø3 ~ 16'; 칸 폭 ÷ 글꼴 배율 ≥ 320dp 면 두 줄(원본), 아니면 한 줄).
  - '③ 피삭재' 카드: ISO 군 여섯 칸(`IsoChips` — 위 4dp 색 띠 + 글자, 고르면 그 색 채움 + 굵은 테; 그 시리즈 표에 행이 없는 군은 흐리게 · 누르면 '이 공구 표에는 M 군(스테인리스강) 행이 없습니다'
    토스트) → 군 이름 · 행 수 → 행 목록(`ChoiceRow` — 재질 글의 한글 쪽, 아래 경도 · 'Vc 120 ~ 170 (권장 145)' 또는 '회전수 표' · 그 행 직경 범위).
  - '④ 직경 · 절삭 조건' 카드(직접 입력은 '②', 칸 [직경][Vc] / [f]): 직경 칸 자리표시 = 그 행 범위 'Ø3 ~ 16'(키패드 안내에 시리즈 주문 범위도) → 경고 띠 → **표**(`DrillTable`:
    최소 · 권장 · 최대 × Vc · 회전수 n · 이송 f · 분당 이송 vf, 권장 칸은 굵은 계산값 파랑, 없는 칸 '—'; 단위 줄에 '환산'(회전수 표에서 낸 Vc) · '표 값'(표 회전수)) → 한 줄 안내
    (그 직경이 든 이송 구간 '… 값 그대로(보간 없음)' · 회전수 표 · 권장값 없는 표 · **깊이별 이송 감소 · 급유 각주는 계산에 넣지 않음**) → '카탈로그 행: P · (영어 원문) · 경도' →
    '출처: 카탈로그 제목 · 판 · 쪽 …' → 접힌 '… 카탈로그 각주 · 확인 기록'(메이커 `notes` 그대로).
  - 숫자 글: Vc 끝 0 없는 소수 1 · n · vf 정수 · f 소수 2~3('0.20' · '0.025'). 키패드 한 줄 = 권장(없으면 범위) Vc · n · f · vf.
  - 경고(`drillWarningText`): 빨강 = BAD_INPUT(칸 빨간 테 — `drillBad`, 계산기와 같은 규칙) · OUTSIDE_ROW(같은 재질의 다른 직경 행이 있으면 띠 아래 알약 'Ø21 ~ 33 행으로' — 누르면 그 행),
    노랑 = OUTSIDE_SERIES · NOT_IN_FEED_TABLE(앞뒤 구간 값을 글로) · NO_FEED_IN_TABLE(깊이 각주) · NO_VC_IN_TABLE, 파랑 = MISSING_FEED(직접 입력 — 회전수만), 값 부족은 맨 위 안내.
  - 고르기 규칙: 메이커를 바꾸면 공구 종류 · 행을, 공구 종류를 바꾸면 행을 비운다(표가 달라서). ISO 군은 남는다. 행을 고르면 그 군이 고른 군이 된다.
- 시험: `CuttingStateTest`(빈 처음 · 마지막에 넣은 쪽이 기준 · 손 계산 글 · 잘못된 칸 ⇔ 계산기 bad · 드릴 고르기 초기화 · 드릴 표 글 = 손 계산 · 경고 다섯 · 글 함수 · 저장),
  `CuttingGoldenTest`(골든 `cutting-*.txt` 17개 — 기본계산기 6 · 드릴링 11, `-DupdateGolden=true`), `CuttingShotsTest`(`cutting-*`: 기본계산기 여섯 · 재료분석 둘 · 드릴링 아홉 ·
  키패드 둘 · 도움말 · 360dp 130% 넷). 상태는 jvmTest `screens/cutting/CuttingExample.kt`.
## 시뮬레이터 편집기 (`screens/SimulatorScreen.kt`, `screens/sim/`)
설계 · 정한 것 전부는 `simulator-design.md` 7절(원래 계획)과 14절(만들며 바꾼 것). 계산(파서 · 실행기 · 예제)은 calc `calc.sim`.
| 파일 | 내용 |
|---|---|
| `SimEditorPage.kt` | `SimulatorPage(sim, onTrajectory)` — 프로그램 줄 → 찾기 줄 → 편집기 → 문제 띠 → CNC 키패드(닫으면 [키패드] · [▶ 궤적 확인]). 0.3초 뒤 백그라운드 실행 · 0.5초 뒤 임시 저장, PC 키 `pcKey` |
| `GEditor.kt` | 편집기 — 줄 번호 칸(문제 표 · 지금 줄 막대) · 구문 색(`syntaxLine` = `GText.tokens`) · 커서 · 고른 글 · 물결 밑줄 · 커서 줄과 멈춘 줄 아래 문제 글 · 스크롤 막대 |
| `CncKeypad.kt` | 화면 아래에 붙는 7 × 4 키패드(위층 아님 — 연 채 편집기를 누를 수 있게), 머리 ↶ ↷ SHIFT 🔍 ▶ 궤적 ✕ |
| `EditorState.kt` | 글 모델(대문자 · 되돌리기 200 · 찾기 · 줄), 키 배치 `CncRows` · `pressKey` · `nextShift` |
| `SimModel.kt` | `SimState`(임시 글 · 저장소 · 키패드 · 찾기 · 공구표 · 실행 결과 · 시트 · 묻는 창, Saver) · `ProgramStore`(`sim/prog/<번호>` · `sim/progs`) |
| `ProgramSheet.kt` | 목록 시트(저장한 프로그램 · 예제 14개) · ⋯ 시트 · 문제 목록 시트 · 묻는 창(번호 · 덮어쓰기 · 저장 안 한 글 · 지우기) |
| `SimColors.kt` · `SimIcons.kt` · `SimTexts.kt` | 구문 · 키패드 색(대비표), 선 아이콘, 문제 글 `simIssueText` · 도움말 `SimHelp` |

- 화면 글은 한글, G코드 · 주소 글자 · 단위는 그대로. 번호를 묻는 칸만 숫자 칸(`NumberField` + 칸 묶음, 처음 빈칸) — 나머지 입력은 CNC 키패드.
- 색: 구문 색(라이트 = 09, 대비가 모자란 X·Z·U·W · P·Q·L 은 짙게), 키 색은 키 종류라 원본 그대로(⌫ 빨강 · , - . 노랑 — 상태 색 아님). 켜진 것 = 청록(찾기), SHIFT 는 09 의 보라.
- 공구표는 앱을 켠 동안만(C10 대기) — 공구 설정 창이 생기기 전에는 사용자가 쓴 G41/G42 글이 '공구 설정에 넣어 주세요' 로 멈춘다.
- 테스트: `EditorStateTest` · `SyntaxTest`(09 낱말 색 · 대비) · `SimStateTest`(저장 · 불러오기 · 임시 글 · 공구표 글) · `PcKeyTest` · `SimGoldenTest`(골든 `sim-editor-errors` · `sim-editor-examples` · `sim-issue-texts`) ·
  `SimShotsTest`: `sim-editor`(09 꼴) · `-closed` · `-shift` · `-shift-lock`(360dp) · `-errors` · `-issues` · `-find` · `-empty` · `sim-programs` · `sim-more` · `sim-ask-delete` · `-number` · `-unsaved`,
  `-w360` · `-fs130` · `sim-editor-errors-w360` · `sim-editor-closed-fs130-w360`, PC `pc-wide-sim`(1280 × 720) · `pc-short-sim`(412 × 640).

### 궤적 창 (화면 B — `simulator-design.md` 6 · 7.4~7.8 · 15 · 16절)
| 파일 | 내용 |
|---|---|
| `TrajectoryWindow.kt` | `TrajectoryWindow(sim, ui, onClose, onEditor)` — 화면 전체 위층(`Overlay`, 뒤로 · Esc 로 닫힘): 위 띠(2D/3D · ? · ✕) → 멈춤 · 닿음 띠 → 뷰포트(2D 뷰 + 설정 · 공구 · 범례 알약, S/F 카드, 오른쪽 단추 ∠ \|◀ ▶\| ⤢ 📏, 기장여유 알약, 코드 띠 — 3D 면 `Sim3DView` + 설정 · 공구 알약 · 안내 칩, S/F 카드 아래 회전 · 자유, 오른쪽 1/4 \|◀ ▶\| ⤢) → 좌표 카드 → 가공 시간 · 속도 → 단추 줄. 재생 시계(`withFrameNanos`) · 소재 계산(백그라운드 끝까지 한 번 → 재생 시각을 따라) · PC 키. `TrajUi`(고른 고스트 · 측정 · 각도 · 시트 · 코드 목록 · 보기 · 3D 켬 · `Cam3D`) |
| `Sim3D.kt` | 3D 보기(설계 6 · 16절): `Cam3D`(시점 · 확대 · 이동 · 회전 · 자유 · 1/4 · 끄는 중) · `sceneOf`(맞출 구 · 바닥 · 격자) · `viewOf`/`View3D`(원근 35°, 주점 = 겹친 단추를 뺀 곳의 가운데) · `ZRaster`(CPU z-버퍼 — 뒷면 버림 · 눈 앞 자르기 · 구로 음영 띠) · `Renderer3D`(메시 캐시 · 끄는 중 0.5배 · 그림 셋 돌려 쓰기) · `Sim3DView`(바닥 격자 Canvas + 그림, 백그라운드 그리기 고리 · 재생 중 메시 0.1 초에 한 번 · 손짓) |
| `Pixels.kt` · `androidMain/…/Pixels.android.kt` · `jvmMain/…/Pixels.jvm.kt` | `argbToImageBitmap`(expect/actual — 안드로이드 `Bitmap.setPixels`, PC Skia `installPixels`, 같은 크기면 다시 씀) |
| `Sim2DView.kt` | `Sim2DView` — 겹친 Canvas 넷(① 격자 · 척 · 기장여유 띠 · 처음 소재 ② 남은 소재 · 나사 톱니 ③ 경로 · 고스트 · 각도 알약 · ▲ ✕ — `Offscreen` 층, 재생 중 다시 그리지 않음 ④ 지금 공구 · 측정 십자와 좌표 알약 · Z0 (소재면) · 축척)과 손짓(한 손가락 이동 · 두 손가락 확대 · 휠 확대 · 누르기 = 고스트/측정점 고르기). `SimPaths`(실행마다 한 번 만든 모델 좌표 토막 목록: 급송 · 층 · 명령 · 보정 · 원호 · 노즈) → `screenPath`(보이는 토막만 화면 좌표로 잘라 · 픽셀로 같은 토막은 한 번 — 큰 프로그램도 긋는 양이 화면 크기에 묶임) · 급송 점선 + 빈 화살 |
| `PlayState.kt` | `PlayState`(재생 시각 · M00/M01 · 싱글 블록 멈춤 · \|◀ ▶\| · 줄로 가기 · 척 멈춤), 속도 미끄럼 로그 변환, `timeText` · `feedText` · `speedText` |
| `SimSetupModel.kt` | 설정 칸(`StockField` · `MachineField` · `ViewFlags`) · `RunSetup`(백그라운드 실행에 넘길 설정 + 원점 자동 값) · `stockOf`(소재 칸 + 자동 소재) · `machineOf` · `numText` |
| `SimSettingsSheet.kt` | 시뮬레이션 설정 시트(소재 · 기계 · 프로그램 읽기 · 보기 — 한 칸 묶음, 빈칸 = 자동/가정) · 공구 설정 시트(보정 번호마다 카드: 홀더(선택) · 종류 · 노즈 R · 팁(테이퍼의 `TipSheet`) · 인서트 · κr · 폭 · 나사 각) |
| `CompanyHolders.kt` · `HolderSheet.kt` | 회사 사용 홀더 `COMPANY_HOLDERS`(`CompanyHolder` — 품번 · 묶음 `HolderGroup` · 종류 · 인서트 · κr · 제조사 · 까닭, `fill` · `edited`) · 공구 카드의 홀더 칸 `HolderBox` · 홀더 고르기 시트 `HolderSheet` (아래 '홀더 고르기') |

- 색: `SimColors` 의 `layer` · `ghost` · `part2d` · `partEdge` · `chuck` · `valueSf` · `valueTime` · `focusPill`(대비표는 KDoc). 켜진 단추 = 청록, 닫기 · 리셋 · 기장여유 = 중립(원본의 빨강 · 주황 · 노랑 대신).
  3D 는 `Sim3DColors`(두 테마 같은 색 — 바탕 `viewport3d` 가 어두움): tag 마다 음영 띠(금속 · 단면 한 단계 밝게 · 척 몸통 남색 · 턱), 바닥 격자, 안내 칩(05 — 어두운 바탕 + 초록 테), 어두운 켬/끔 단추 회전 · 자유.
- 설정 저장: 기계 · 읽기 · 보기는 `sim/machine` · `sim/view`(앱 전체, 다음에 켜도 그대로), 소재 칸 · 공구표(고른 홀더 이름표 `toolHolders` — Saver 목록 9번째 `n=id;…`) · 속도 · 싱글 블록 · M01 은 화면 Saver(앱을 켠 동안).
- **홀더 고르기**(사용자 10-29 "회사에서 사용하는 팁홀더도 찾아서 홀더 종류에 추가"): 공구 카드 머리 아래 '홀더 (선택)' 칸(이름 옆 '수정됨' · 칸에는 품번과 '고르기 ⌄' 만 —
  360dp · 글꼴 1.3 에서도 품번이 줄지 않게, 아랫줄에 묶음 · 제조사(제조사로 나뉜 줄은 그 제조사 하나)와 까닭) → 시트 '홀더 고르기 · 회사 사용': 고르지 않음 →
  외경 바이트(DCKNL2525M12 · MVJNR2525M16 · PCLNL2525M12) · 나사(SNR0020L09A · THCER2525M16) · 보링바(SCLCR 다섯 · STFCR 넷, 지름 순) · 소경 보링(SH0516-100 · SH0616-100 · PICCO)
  — 20 줄 17 품번, 외경 · 나사 묶음 안은 품번 알파벳 순. 줄 = 품번 + 채우는 값(종류 · 인서트 · κr) · 제조사(없으면 '제조사 확인 필요') + 까닭.
  κr 이 제조사마다 다른 STFCR(한국야금 90° · 대구텍 91°)은 제조사마다 한 줄(품번 옆 제조사).
  고르면(`SimState.pickHolder` → `CompanyHolder.fill`) **종류**, 바이트면 **인서트 모양**(출처에 없으면 그대로) · **κr**(출처에 없으면 빈칸 = 가정 값)을 채운다 — 사용자가 고칠 수 있고 고치면 '수정됨'.
  노즈 R(인서트 코너 R) · 팁 번호 · 홈 폭 · **나사 각**(인서트 모양 — 미터 60° · 관용 55°, 홀더 출처에 없음 · 빈칸이면 앱이 60° 가정)은 홀더가 정하지 않아 건드리지 않는다.
  **팁 번호는 제안하지 않는다**: 홀더 손(L/R)과 외경/내경으로 정할 수 없다 — 같은 손의 홀더가 척 쪽(−Z)으로 깎는지 그 반대로 깎는지는 주축 회전 방향(M03/M04)과
  홀더를 다는 방식(터렛 앞/뒤 · 인서트 위/아래)에 따라 달라 팁(3 ↔ 4 · 2 ↔ 1)이 갈린다. 시트 안내 글이 '팁 번호(기계 터렛 방향)는 홀더가 정하지 않아 그대로 둡니다' 라고 적는다.
  **DCKNL 은 κr 을 비운다**(C 95° 가정): 카탈로그 75° 는 K 꼴의 정면(X) 이송 기준(대구텍 A44 · 한국야금 B91 그림)인데 시뮬레이터 κr 은 Z 이송 기준이라, 75 를 넣으면
  B 꼴 75° 바이트가 되어 정면 깎기에서 끝면을 쐐기로 판다(Ø50 끝면 Z1 → Z0 에서 z −1 반경 3.56). Z 이송으로 보면 100° 모서리라 κr 95° · 뒤날 −15° 여서
  앱 공구 모양(80° 모서리 · 뒤날 0° 이상)으로는 그릴 수 없다 — 빈칸이면 끝면 쪽 날(정면 뒤날각 5°)은 실제와 같고 지름 쪽 날만 다르다. 까닭 줄이 이를 적는다
  (계산기에 모서리 각 · 이송 기준을 더하지는 않았다). 근거 · 값은 `company-holders.json` 의 DCKNL `simulator` 칸, 시험 `VerifyHoldersTest`(3 · 3').
  THCER 는 공식 카탈로그 · 제품 페이지에서 찾지 못해 제조사 · 값 없이 종류(나사)만. 값 · 제조사는 `company-holders.json` 의 `holders` 와 같다(`CompanyHoldersJsonTest.simHoldersMatchJson` — κr 은 `simulator` 칸이 있으면 그 값).
  예제를 열어 그 번호의 공구표가 예제 것으로 바뀌면 그 번호의 이름표는 뺀다. 시험: `CompanyHolderTest`(채우기 · 그대로 두는 칸 · 수정됨 · Saver · 예제),
  몽키 `SimMonkeyTest.toolSheet` 의 '회사 홀더 차례'(BMT_MONKEY — 홀더 칸 → 20 줄을 차례로 눌러 카드 값 · 이름표를 보고 다섯 줄마다 2D 재생 · 3D, 팁 칸은 `TIP_BOX` 로 가른다),
  `UiMonkeyTest.roundInner` 는 회사 프리셋 · 빈 프리셋을 고른 시작 상태를 더했다.
- 테스트: `PlayStateTest`(M00 · M01 · 싱글 블록 · 싱글 블록이 먼저 멈춰도 뒤 M00 남김 · \|◀ ▶\| · 척 멈춤 · 속도 · 글 꼴 · 설정 저장 · Saver · 원점 자동) · `TrajPerfTest`(이동 10만 개 프로그램의 ×200 재생 프레임 — `build/perf/traj-big.txt`) · `TrajGoldenTest`(골든 `sim-trajectory` — 예제마다 자동 소재 · 원점 · 시간 · 닿음 · 나사 · 남은 넓이 · 50 % 의 S/F, `sim-trajectory-scenes` — 스크린샷 장면의 카드 값, 06 의 X34.000 Z-2.186) ·
  `TrajShotsTest`(장면은 `TrajScenes` — 재생을 멈춘 채 시각 고정): `sim-2d`(06 꼴) · `sim-2d-measure`(10 꼴) · `sim-2d-mid`(G71 층 반쯤) · `sim-2d-angle` · `sim-2d-thread` · `sim-2d-chuck` · `sim-stop-error` · `sim-settings` · `sim-tools` · `sim-legend` · `-w360` · `-fs130` · `-fs130-w360` · `pc-wide-traj` · `pc-short-traj`,
  회사 홀더 `sim-tools-holder`(05 꼴 부품의 공구 넷에 홀더 — PCLNL 은 κr 을 고쳐 '수정됨', 내경은 제조사로 나뉜 S16R-STFCR11 대구텍 · `-fs130-w360`) · `sim-holders`(공구 설정 위 홀더 고르기 시트 · `-w360` · `-fs130-w360` · 20 줄이 다 보이는 `-all`).
- 3D 테스트: `Sim3DTest`(z-버퍼 앞면 · 뒷면 · 깊이 · 눈 앞 자르기, 카메라 맞춤 · 처음 시점, 05 꼴 부품 글이 멈춤 · 닿음 없이 돎, 1/4 단면 픽셀 · 재생 중간 ≠ 끝 · 0.5배) · `Traj3DGoldenTest`(골든 `sim-3d` — 예제마다 메시 삼각형 · 부피와 단면 회전 부피, `sim-3d-scenes` — 3D 장면의 메시 · 412 × 500 그림의 tag 별 픽셀) ·
  `Sim3DPerfTest`(메시 · z-버퍼 한 장 · ×200 재생 프레임 — `build/perf/sim-3d.txt`) · `Traj3DShotsTest`(장면은 `TrajScenes.all3d`, 05 꼴 부품 `PART05`): `sim-3d`(재생 중간 — 바깥 나사를 내는 중) · `sim-3d-end`(05 꼴) · `sim-3d-full`(절개 끔 · 자유 · 돌린 시점) · `sim-3d-mid`(09 글 G71 층 반쯤) · `sim-3d-thread`(G76) · `-w360` · `-fs130` · `pc-wide-3d` · `pc-short-3d`.
  calc `RevolveMeshTest`(부피 손 계산 · 1/4 = 270° 몫 · 바깥쪽 감김 · 삼각형 한도 · 나사 골 자리 = 2D 톱니 · 척 · 둥근 홈 법선).
- 4단계 최종 점검 시험: `SimRobustnessTest`(예제 11개를 실제 화면으로 · 무작위 프로그램 2 400개 × 공구표 · 소재 · 기계 칸 — `build/perf/sim-robust.txt`) · `SimLongProgramTest`(글 5 000줄 — 실행 · 편집기 키 하나 · 궤적 창 재생 값 `build/perf/sim-long.txt`, 스크린샷 `sim-editor-long`). 두 시험의 장면은 `UiQueue`(효과를 프레임 사이에 시험 스레드에서 — 앱처럼 한 UI 스레드)로 돌린다. 결과는 `stage4-report.md`.

