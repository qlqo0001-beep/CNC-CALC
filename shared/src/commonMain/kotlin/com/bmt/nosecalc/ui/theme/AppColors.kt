package com.bmt.nosecalc.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.bmt.nosecalc.Feature

/** 역할색 한 벌(바탕 · 테두리 · 라벨 · 값): 입력칸 · 경고 띠 · 형상 카드 칩 */
@Immutable
data class RoleColors(val bg: Color, val border: Color, val label: Color, val value: Color)

/** 기능 하나의 색: 헤더 제목(한 색) · 메뉴 아이콘 */
@Immutable
data class FeatureColors(val title: Color, val menu: Color)

/**
 * 색 토큰. 이름·라이트 값은 docs/screens.md 1.1 (11.png 의 Tailwind slate 계열 값을 우선).
 * 다크("딥 다크")는 우리가 정한 것 — 3D 뷰포트 #09111D 계열 짙은 남색 바탕, 강조색은 같은 색상을 밝게.
 *
 * 색의 뜻은 고정이다: 빨강 = 계산 멈춤·지우기, 노랑 = 확인할 것, 파랑 = 안내. 기준선·시작/끝점·각도 같은 보통 요소에는
 * 빨강·노랑을 쓰지 않는다. 숫자 글자는 바탕 대비 4.5:1 이상(결과 좌표 약 7:1), 뜻 있는 그림 선은 3:1 이상.
 *
 * 대비(WCAG, 라이트 / 다크) — 바탕: 카드 #FFFFFF / #142032, 뷰어 #EFF2F7 / #0B1421
 * | 토큰 | 라이트 | 다크 | 바탕 |
 * |---|---|---|---|
 * | valueComp | 7.0 | 9.8 | 카드 |
 * | drawRef | 4.8 | 6.4 | 카드 |
 * | drawAngle | 5.9 | 9.1 | 카드 |
 * | drawToolLine | 7.6 | 11.0 | 카드 |
 * | drawToolBody (면) | 1.5 | 2.4 | 카드 |
 * | drawMetalEdge | 10.4 | 11.0 | 카드 |
 * | drawStock · drawHatch (면 · 빗금) | 1.2 · 1.5 | 1.2 · 1.6 | 카드 |
 * | viewportAxis | 4.2 | 3.9 | 뷰어 |
 * | viewNose | 5.3 | 8.6 | 뷰어 |
 * | viewPath | 5.6 | 7.5 | 뷰어 |
 * | viewRapid | 4.2 | 7.2 | 뷰어 |
 * | viewGridMajor (보조선) | 1.4 | 1.5 | 뷰어 |
 * | viewLabelT1 | 4.5 | 11.1 | 뷰어 |
 * | viewLabelT2 | 4.9 | 9.9 | 뷰어 |
 * | drawHolder | 4.5 | 10.6 | 뷰어 |
 * | onToast / toast | 12.8 | 13.1 | 토스트 |
 * | info.label · info.value | 6.2 · 9.5 | 6.6 · 11.8 | info.bg |
 * | warn.label · error.label (띠 머리말 글) | 4.8 · 5.7 | 9.4 · 6.3 | 각 띠 bg |
 */
@Immutable
data class AppColors(
    val dark: Boolean,
    // 바탕·면
    val page: Color,
    /** APP MENU 가 열렸을 때 헤더 바탕(01: 어두워짐) */
    val headerDim: Color,
    val card: Color,
    val subtle: Color,
    val field: Color,
    val segTrack: Color,
    val menuTop: Color,
    val menuBottom: Color,
    val scrim: Color,
    val viewport: Color,
    val viewportGrid: Color,
    val viewportAxis: Color,
    val viewport3d: Color,
    // 선
    val lineCard: Color,
    val lineField: Color,
    val lineStrong: Color,
    val lineTabIdle: Color,
    val lineFocus: Color,
    // 글자·아이콘
    val textStrong: Color,
    val textValue: Color,
    val textBody: Color,
    val textMuted: Color,
    val textHint: Color,
    val icon: Color,
    /** 채운 강조 바탕 위 글자 */
    val onAccent: Color,
    // 강조
    val teal: Color,
    val tealDeep: Color,
    val appsFill: Color,
    val appsBorder: Color,
    val appsOpenBg: Color,
    val green: Color,
    val blue: Color,
    val orange: Color,
    val amber: Color,
    val red: Color,
    val indigo: Color,
    val purple: Color,
    /** 헤더 원형 버튼 바탕 */
    val circleBg: Color,
    val bmtBlue: Color,
    // 결과 숫자(고정폭)
    val valueComp: Color,
    val valuePos: Color,
    // 역할 칸
    val fieldNormal: RoleColors,
    val warn: RoleColors,
    val error: RoleColors,
    /** 안내 띠(파랑): 경고가 아닌 알림 */
    val info: RoleColors,
    val toast: Color,
    val onToast: Color,
    // 형상 그림
    val drawProfile: Color,
    /** 기준선 · 보조선 · 중심선(보통 요소라 빨강이 아닌 회색) */
    val drawRef: Color,
    val drawT1: Color,
    val drawT2: Color,
    /** 치수 · 각도(칸 라벨의 파랑과 같은 계열) */
    val drawAngle: Color,
    val drawToolBody: Color,
    val drawToolLine: Color,
    val drawInsert: Color,
    val simProgram: Color,
    val simArc: Color,
    /** 라운드 TYPE 그림(04 자리)의 금속 몸통: 위 → 아래 그라데이션 5단, 윤곽·모서리선 */
    val drawMetal: List<Color>,
    val drawMetalEdge: Color,
    /** 소재 단면: 바탕 면 · 45° 빗금 */
    val drawStock: Color,
    val drawHatch: Color,
    // 형상 보기(ShapeViewer)
    /** 노즈 원 · 노즈 중심 경로 */
    val viewNose: Color,
    /** 프로그램 경로 */
    val viewPath: Color,
    /** 급송(G00) */
    val viewRapid: Color,
    /** 굵은 격자선 */
    val viewGridMajor: Color,
    /** 뷰어 안 T1 · T2 이름표 */
    val viewLabelT1: Color,
    val viewLabelT2: Color,
    /** 보링바(홀더) 단면: 윤곽선 · 면(같은 색 α .16) — 빨강(간섭) · 노랑 · 경로 보라 · 노즈 파랑과 갈리는 초록 */
    val drawHolder: Color,
    val drawHolderFill: Color,
    /** 라운드 TYPE 칩(04): 홀수 하늘 · 짝수 민트(bg · border · label = 글자) */
    val chipSky: RoleColors,
    val chipMint: RoleColors,
    val features: Map<Feature, FeatureColors>,
)

val LightColors = AppColors(
    dark = false,
    page = Color(0xFFE8EDF3),
    headerDim = Color(0xFFD5DCE4),
    card = Color(0xFFFFFFFF),
    subtle = Color(0xFFF8FAFC),
    field = Color(0xFFF1F5F9),
    segTrack = Color(0xFFE2E8F0),
    menuTop = Color(0xF7FDFDFD),
    menuBottom = Color(0xE6E2E3E7),
    scrim = Color(0x9940444A),
    viewport = Color(0xFFEFF2F7),
    viewportGrid = Color(0xFFDCE1E8),
    viewportAxis = Color(0xFF64748B),
    viewport3d = Color(0xFF09111D),
    lineCard = Color(0xFFE2E8F0),
    lineField = Color(0xFFCBD5E1),
    lineStrong = Color(0xFF94A3B8),
    lineTabIdle = Color(0xFF83ABEB),
    lineFocus = Color(0xFF617FC5),
    textStrong = Color(0xFF1E293B),
    textValue = Color(0xFF0F172A),
    textBody = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    textHint = Color(0xFF94A3B8),
    icon = Color(0xFF374151),
    onAccent = Color(0xFFFFFFFF),
    teal = Color(0xFF0D9488),
    tealDeep = Color(0xFF0F766E),
    appsFill = Color(0xFF14B8A6),
    appsBorder = Color(0xFF0D9488),
    appsOpenBg = Color(0xFFF0FDFA),
    green = Color(0xFF16A34A),
    blue = Color(0xFF2B7BC0),
    orange = Color(0xFFEA580C),
    amber = Color(0xFFD97706),
    red = Color(0xFFE11D48),
    indigo = Color(0xFF6366F1),
    purple = Color(0xFF9333EA),
    circleBg = Color(0xFFFFFFFF),
    bmtBlue = Color(0xFF0A4AA6),
    valueComp = Color(0xFF0F5E8C),
    valuePos = Color(0xFF2F8A5E),
    fieldNormal = RoleColors(Color(0xFFF1F5F9), Color(0xFFCBD5E1), Color(0xFF0284C7), Color(0xFF0F172A)),
    warn = RoleColors(Color(0xFFFFFBEB), Color(0xFFFCD34D), Color(0xFFB45309), Color(0xFF78350F)),
    error = RoleColors(Color(0xFFFFF1F2), Color(0xFFFDA4AF), Color(0xFFBE123C), Color(0xFF881337)),
    info = RoleColors(Color(0xFFEFF6FF), Color(0xFFBFDBFE), Color(0xFF1D4ED8), Color(0xFF1E3A8A)),
    toast = Color(0xF21E293B),
    onToast = Color(0xFFFFFFFF),
    drawProfile = Color(0xFF334155),
    drawRef = Color(0xFF64748B),
    drawT1 = Color(0xFFD97706),
    drawT2 = Color(0xFF0D9488),
    drawAngle = Color(0xFF0369A1),
    drawToolBody = Color(0xFFCBD5E1),
    drawToolLine = Color(0xFF475569),
    drawInsert = Color(0xFFFBBF24),
    simProgram = Color(0xFF2F9E6B),
    simArc = Color(0xFF2563EB),
    drawMetal = listOf(Color(0xFFCBD5E1), Color(0xFFF1F5F9), Color(0xFFE2E8F0), Color(0xFFCBD5E1), Color(0xFFA9B6C8)),
    drawMetalEdge = Color(0xFF334155),
    drawStock = Color(0xFFE6EBF2),
    drawHatch = Color(0xFFC8D2DF),
    viewNose = Color(0xFF0369A1),
    viewPath = Color(0xFFA21CAF),
    viewRapid = Color(0xFF64748B),
    viewGridMajor = Color(0xFFC8D0DB),
    viewLabelT1 = Color(0xFFB45309),
    viewLabelT2 = Color(0xFF0F766E),
    drawHolder = Color(0xFF15803D),
    drawHolderFill = Color(0x2915803D),
    chipSky = RoleColors(Color(0xFFFFFFFF), Color(0xFFA5F3FC), Color(0xFF155E75), Color(0xFF155E75)),
    chipMint = RoleColors(Color(0xFFFFFFFF), Color(0xFFA7F3D0), Color(0xFF065F46), Color(0xFF065F46)),
    features = mapOf(
        Feature.TAPER to FeatureColors(Color(0xFF0D9488), Color(0xFF0D9488)),
        Feature.CUTTING to FeatureColors(Color(0xFF0369A1), Color(0xFFBE123C)),
        Feature.ROUND to FeatureColors(Color(0xFF0891B2), Color(0xFF0F766E)),
        Feature.SIMULATOR to FeatureColors(Color(0xFF1E293B), Color(0xFF4338CA)),
        Feature.TRIANGLE to FeatureColors(Color(0xFF6D28D9), Color(0xFF6D28D9)),
        Feature.POLAR to FeatureColors(Color(0xFF0D9488), Color(0xFFB45309)),
        Feature.THREAD to FeatureColors(Color(0xFFEA580C), Color(0xFF047857)),
    ),
)

/**
 * 강조색으로 채운 칸(고른 칩·세그먼트) 위 글자색. 다크의 밝은 강조색(청록 #2DD4BF·보라 #A78BFA)에는 흰 글자의 대비가
 * 2:1 아래라 짙은 글자를 쓴다. 라이트의 강조색은 모두 짙어 흰 글자 그대로
 */
fun AppColors.onFill(fill: Color): Color = if (fill.luminance() > 0.3f) (if (dark) page else textStrong) else onAccent

val DarkColors = AppColors(
    dark = true,
    page = Color(0xFF0D1624),
    headerDim = Color(0xFF09101B),
    card = Color(0xFF142032),
    subtle = Color(0xFF172537),
    field = Color(0xFF1B2A3F),
    segTrack = Color(0xFF0F1A2A),
    menuTop = Color(0xF5172537),
    menuBottom = Color(0xEB0F1A2A),
    scrim = Color(0x73000000),
    viewport = Color(0xFF0B1421),
    viewportGrid = Color(0xFF1C2838),
    viewportAxis = Color(0xFF64748B),
    viewport3d = Color(0xFF09111D),
    lineCard = Color(0xFF24344C),
    lineField = Color(0xFF30435F),
    lineStrong = Color(0xFF41587A),
    lineTabIdle = Color(0xFF3D63A8),
    lineFocus = Color(0xFF7896E6),
    textStrong = Color(0xFFE8EEF7),
    textValue = Color(0xFFF3F6FB),
    textBody = Color(0xFFB7C3D5),
    textMuted = Color(0xFF8D9BB2),
    textHint = Color(0xFF627290),
    icon = Color(0xFFC3CEDF),
    onAccent = Color(0xFFFFFFFF),
    teal = Color(0xFF2DD4BF),
    tealDeep = Color(0xFF5EEAD4),
    appsFill = Color(0xFF0F8F82),
    appsBorder = Color(0xFF2DD4BF),
    appsOpenBg = Color(0xFF103A3A),
    green = Color(0xFF34D399),
    blue = Color(0xFF3B82F6),
    orange = Color(0xFFFB923C),
    amber = Color(0xFFFBBF24),
    red = Color(0xFFFB7185),
    indigo = Color(0xFF818CF8),
    purple = Color(0xFFC084FC),
    circleBg = Color(0xFF1B2A3F),
    bmtBlue = Color(0xFF6E9BF0),
    valueComp = Color(0xFF7DD3FC),
    valuePos = Color(0xFF6EE7B7),
    fieldNormal = RoleColors(Color(0xFF1B2A3F), Color(0xFF30435F), Color(0xFF38BDF8), Color(0xFFF3F6FB)),
    warn = RoleColors(Color(0xFF2A2210), Color(0xFF8A6516), Color(0xFFFBBF24), Color(0xFFFDE68A)),
    error = RoleColors(Color(0xFF2E1520), Color(0xFF9F2F4E), Color(0xFFFB7185), Color(0xFFFECDD3)),
    info = RoleColors(Color(0xFF0F1E33), Color(0xFF1E3A8A), Color(0xFF60A5FA), Color(0xFFBFDBFE)),
    toast = Color(0xF2E2E8F0),
    onToast = Color(0xFF0F172A),
    drawProfile = Color(0xFFCBD5E1),
    drawRef = Color(0xFF94A3B8),
    drawT1 = Color(0xFFFBBF24),
    drawT2 = Color(0xFF2DD4BF),
    drawAngle = Color(0xFF93C5FD),
    drawToolBody = Color(0xFF4B5B73),
    drawToolLine = Color(0xFFCBD5E1),
    drawInsert = Color(0xFFFBBF24),
    simProgram = Color(0xFF4ADE80),
    simArc = Color(0xFF60A5FA),
    drawMetal = listOf(Color(0xFF2B3B53), Color(0xFF4A5B75), Color(0xFF34455E), Color(0xFF26354B), Color(0xFF1C2839)),
    drawMetalEdge = Color(0xFFCBD5E1),
    drawStock = Color(0xFF1E2D44),
    drawHatch = Color(0xFF2D4060),
    viewNose = Color(0xFF38BDF8),
    viewPath = Color(0xFFE879F9),
    viewRapid = Color(0xFF94A3B8),
    viewGridMajor = Color(0xFF26364D),
    viewLabelT1 = Color(0xFFFBBF24),
    viewLabelT2 = Color(0xFF2DD4BF),
    drawHolder = Color(0xFF4ADE80),
    drawHolderFill = Color(0x294ADE80),
    chipSky = RoleColors(Color(0xFF10242E), Color(0xFF1E6A80), Color(0xFF67E8F9), Color(0xFF67E8F9)),
    chipMint = RoleColors(Color(0xFF0E241F), Color(0xFF1C7A5C), Color(0xFF6EE7B7), Color(0xFF6EE7B7)),
    features = mapOf(
        Feature.TAPER to FeatureColors(Color(0xFF2DD4BF), Color(0xFF2DD4BF)),
        Feature.CUTTING to FeatureColors(Color(0xFF38BDF8), Color(0xFFFB7185)),
        Feature.ROUND to FeatureColors(Color(0xFF22D3EE), Color(0xFF5EEAD4)),
        Feature.SIMULATOR to FeatureColors(Color(0xFFE2E8F0), Color(0xFFA5B4FC)),
        Feature.TRIANGLE to FeatureColors(Color(0xFFA78BFA), Color(0xFFC4B5FD)),
        Feature.POLAR to FeatureColors(Color(0xFF2DD4BF), Color(0xFFFBBF24)),
        Feature.THREAD to FeatureColors(Color(0xFFFB923C), Color(0xFF34D399)),
    ),
)
