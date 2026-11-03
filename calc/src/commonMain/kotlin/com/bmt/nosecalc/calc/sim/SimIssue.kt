package com.bmt.nosecalc.calc.sim

/** 문제의 무게. STOP 은 그 블록에서 실행을 멈추고 그 앞까지만 그린다 */
enum class IssueLevel { STOP, WARN, INFO }

/**
 * 시뮬레이터가 알리는 문제(`docs/simulator-design.md` 1.5 · 3.5 표). 화면 글은 shared 의 `simIssueText` 가 고른다.
 * 출처 약칭(H · S)은 그 문서 0.2.
 */
enum class IssueCode(val level: IssueLevel) {
    // ---- 글(파서) — 실행 전에 모두 모은다
    /** 받지 않는 글자(주석 밖의 한글 · 기호 · 주소 없는 숫자) */
    BAD_CHAR(IssueLevel.STOP),
    /** 주소 뒤에 숫자가 없음 */
    NO_NUMBER(IssueLevel.STOP),
    /** 숫자 9자리 넘음 */
    NUMBER_TOO_LONG(IssueLevel.STOP),
    /** 좌표 · 치수가 MAX_MM 넘음 */
    NUMBER_TOO_BIG(IssueLevel.STOP),
    /** 한 블록에 같은 주소 두 번 */
    DUP_ADDRESS(IssueLevel.STOP),
    /** 한 블록에 같은 그룹 G 둘 */
    DUP_GROUP(IssueLevel.STOP),
    /** 한 블록에 M 둘(H 133) */
    TWO_M(IssueLevel.STOP),
    /** `(` 가 같은 줄에서 안 닫힘 */
    COMMENT_OPEN(IssueLevel.STOP),
    /** 주석 안에 `(` — 겹친 괄호 */
    COMMENT_NESTED(IssueLevel.STOP),
    /** D · A · H · Y · B · C … */
    UNKNOWN_ADDRESS(IssueLevel.STOP),
    /** Haas 1줄 사이클 형식(D) · G76 의 A · G01 의 A(각도 모서리) */
    HAAS_FORMAT(IssueLevel.STOP),
    /** G01 의 I · K · R(쉼표 없는) 직각 면취 · 라운드 — `,C` · `,R` 만 받는다 */
    G01_CORNER_IKR(IssueLevel.STOP),
    UNSUPPORTED_G(IssueLevel.STOP),
    /** G20 */
    INCH(IssueLevel.STOP),
    /** G52 · G53 · G55~G59 · G154 */
    WORK_OFFSET(IssueLevel.STOP),
    /** G50 X/Z/U/W(좌표계 설정) */
    G50_COORD(IssueLevel.STOP),
    G80_IN_CYCLE(IssueLevel.STOP),
    /** 블록 가운데 `/` */
    MID_BLOCK_SKIP(IssueLevel.STOP),
    /** 정수 전용 주소에 소수점 */
    DECIMAL_NOT_ALLOWED(IssueLevel.STOP),
    /** G99 에 소수점 없는 F */
    NO_POINT_FEED(IssueLevel.STOP),
    /** 표준 설정에서 소수점 없는 좌표 — ×0.001 로 읽음 */
    NO_POINT_COORD(IssueLevel.WARN),
    /** T 가 3~4자리가 아님(T0 · T00 은 받음) */
    T_FORMAT(IssueLevel.STOP),
    /** 음수가 될 수 없는 값(N · M · T · P · Q · L · F · S · 드웰 · 모서리 크기) */
    BAD_VALUE(IssueLevel.STOP),
    /** 한 블록에 X 와 U(또는 Z 와 W) */
    ABS_INC_BOTH(IssueLevel.STOP),
    /** 같은 O 번호가 둘 */
    DUP_PROGRAM(IssueLevel.STOP),
    /** 그 블록의 명령이 쓰지 않는 주소(예: G00 의 R, G01 의 P) */
    WORD_NOT_USED(IssueLevel.STOP),
    /** 꼭 있어야 할 주소가 없음(예: G04 의 시간, G50 의 S, M98 의 P) */
    WORD_MISSING(IssueLevel.STOP),
    /** G04 와 다른 명령이 한 블록(S 3-72) */
    G04_MIXED(IssueLevel.STOP),

    // ---- 이송 · 주축
    FEED_MISSING(IssueLevel.STOP),
    SPINDLE_STOPPED(IssueLevel.STOP),
    S_MISSING(IssueLevel.STOP),
    NO_CLAMP_G96(IssueLevel.WARN),
    /** G97 S 가 G50 S 나 기계 최대 회전수를 넘어 잘림(H 49) */
    RPM_OVER_LIMIT(IssueLevel.WARN),
    THREAD_IN_CSS(IssueLevel.WARN),
    /** T 와 이송이 한 블록(S 2-24: T 블록은 G00) */
    T_WITH_FEED(IssueLevel.STOP),

    // ---- 원호
    ARC_NO_CENTER(IssueLevel.STOP),
    ARC_R_AND_IK(IssueLevel.STOP),
    ARC_R_SHORT(IssueLevel.STOP),
    ARC_END_OFF(IssueLevel.STOP),
    FULL_CIRCLE(IssueLevel.STOP),

    // ---- 노즈R 보정(3.5)
    /** 시작 블록이 원호 — 형식 C 이동 없는 시작은 G02/G03 모드(FF 5.4.2 시작 조건 (3) · 알람 PS0034) */
    COMP_START_ARC(IssueLevel.STOP),
    /** 취소 블록이 원호 — 형식 C 이동 없는 취소는 G02/G03 모드 */
    COMP_CANCEL_ARC(IssueLevel.STOP),
    /**
     * 형식 A · B: 시작 블록 움직임 0. 형식 C: 이동 없는 G41/G42 뒤 이동 블록 없이 보정을 끔(옮길 방향이 없음) — args = [시작 줄]
     */
    COMP_START_ZERO(IssueLevel.STOP),
    COMP_ACUTE_OUTER(IssueLevel.STOP),
    COMP_INTERFERENCE(IssueLevel.STOP),
    COMP_ARC_SMALL(IssueLevel.STOP),
    COMP_IDLE_BLOCKS(IssueLevel.STOP),
    /**
     * 보정 중 G41 ↔ G42 바꿈 가운데 그리지 않는 것(바꿈 자체는 FF 5.4.3 대로 받는다): args 없음 = 시작 블록 바로 다음 블록에서 바꿈(FF 189쪽 'not available'),
     * ["G40"] = 바꾼 바로 다음 블록이 이동 있는 취소, [줄] = 그 줄 원호 끝에서 두 쪽 보정선이 만나지 않음(끝이 원호 위에 없는 원호 — 옮기지 않음)
     */
    COMP_SWITCH(IssueLevel.STOP),
    COMP_G40_IK(IssueLevel.STOP),
    COMP_TOOL_CHANGE(IssueLevel.STOP),
    COMP_IN_CYCLE(IssueLevel.STOP),
    COMP_AT_G28(IssueLevel.STOP),
    /**
     * 형식 A · B 만: 이동 없는 블록에서 보정을 끈(G40 · T□□00) 뒤 원호 · 모서리(,C ,R) · 곧바로 다시 보정 시작 · 사이클 · G28 · G30 이 옴 — 그 경로를 출처로 확인하지 못함.
     * 직선(G00/G01)은 그린다(FF 5.4.4 그림 5.4.4(e) — [COMP_CANCEL_ALONE]). 형식 C 는 G40 블록에서 지령점으로 옮겨 이 일이 없다
     */
    COMP_CANCEL_NO_MOVE(IssueLevel.STOP),
    /**
     * 이동 없는 블록에서 보정을 끔. 형식 A · B: 앞 블록 끝 법선에 서고 다음 직선 이동에서 벡터를 지움.
     * 형식 C(args = ["C"]): 그 블록에서 끝 법선 → 지령점 − 팁 벡터로 옮김(FF 그림 5.4.4(f))
     */
    COMP_CANCEL_ALONE(IssueLevel.INFO),
    /** G41/G42 인데 T 보정 번호가 없어(00) 보정하지 않음(S 3-78: T 보정과 G41/G42 가 둘 다 있어야 보정) */
    COMP_NO_OFFSET(IssueLevel.WARN),
    /** 보정을 G40 으로 끄지 않고 프로그램이 끝남 — 마지막 블록은 끝 법선 자리까지 그림 */
    COMP_NOT_CANCELLED(IssueLevel.WARN),
    /** 이동 있는 시작 · 취소가 바깥 모서리 — 형식 A 로 봄(형식 B 기계는 그 블록 경로가 다름). args = ["C"] 면 기계 설정이 형식 C(그래도 이동 있는 블록은 SUP 를 따름) */
    COMP_TYPE_A(IssueLevel.INFO),
    AGAINST_TOOL(IssueLevel.WARN),
    TOOL_DATA_MISSING(IssueLevel.STOP),

    // ---- 모서리 ,C ,R (1.7)
    CORNER_C_ARC(IssueLevel.STOP),
    CORNER_TOO_BIG(IssueLevel.STOP),
    CORNER_NO_VERTEX(IssueLevel.STOP),
    /** `,C`/`,R` 블록 다음 블록이 G01~G03 이동이 아님(사이에 다른 블록 — G04 포함 — 도 안 됨) */
    CORNER_NO_NEXT(IssueLevel.STOP),
    /** G00 블록의 `,C`/`,R` */
    CORNER_ON_RAPID(IssueLevel.STOP),
    /** 한 블록에 `,C` 와 `,R` */
    CORNER_BOTH(IssueLevel.STOP),

    // ---- 고정 사이클(1.6 · 12절). 줄 = 사이클 블록, 단 P~Q 안 블록 때문이면 그 블록 줄
    /** P~Q 안에 받지 않는 명령(S 4-117 주 3: G04 밖의 한 번 G · G00~G03 밖의 01 그룹 · 06 그룹, 우리가 더: M02 · M30, G71~G73 은 G96~G99). args = [G7x] */
    CYCLE_PQ_FORBIDDEN(IssueLevel.STOP),
    /** P~Q 가 G41/G42 를 G40 으로 끄지 않고 끝남(줄 = Q 블록) */
    CYCLE_COMP_OPEN(IssueLevel.STOP),
    /** 사이클 뒤(또는 P~Q 의 첫 이동)에 G00~G03 을 쓰지 않음 — 사이클 뒤 01 그룹 모달은 비운다 */
    CYCLE_MOTION_MISSING(IssueLevel.STOP),
    /** 사이클 첫 줄 값을 프로그램에서 한 번도 주지 않음(기계 파라미터 기본값을 모름). args = [G7x] */
    CYCLE_FIRST_LINE(IssueLevel.STOP),
    /** G90/G92/G94 모드 블록에 축과 S · T · M · G96~G99 가 같이 있음(S 4-93), 또는 G70~G76 줄에 M */
    CYCLE_BLOCK_TM(IssueLevel.STOP),
    /**
     * G90/G92/G94 모드에서 축 없이 S · T · 주축(M03~M05) · 끝(M02 · M30) · M98/M99(또는 G96~G99)이 있는 블록. FANUC 은 이동 없는 블록에서도 사이클을 한 번 더 돌며
     * 그 기능을 '함께' 실행한다(FF 4.1.6 · F0 13.1 주 2(3)) — 순서가 결과를 바꿔 지원 안 함. G00/G01 로 모드를 먼저 끝낸다(FF 예 `N011 G00 T0202`).
     * 그 밖의 M(기계 M · M00 · M01 · M08 · M09)만 있는 블록은 사이클을 한 번 더 돈 뒤 M 을 실행한다. args = [G9x]
     */
    CYCLE_REPEAT_MST(IssueLevel.STOP),
    /** G90/G92/G94 모드 중 한 번 G(G28 · G30 · G50 · G70~G76), 또는 모드를 잇는 블록의 G18(FF 4.1.6: 모달 값을 지움) — G00~G03 으로 모드를 먼저 끝낸다. args = [G9x] */
    CYCLE_ONESHOT(IssueLevel.STOP),
    /** P · Q 순번 블록이 그 프로그램 단위에 없음. args = [P|Q, "N순번"] */
    CYCLE_PQ_NOT_FOUND(IssueLevel.STOP),
    /** P · Q 로 찾는 순번 블록이 그 단위에 둘. args = ["N순번"] */
    CYCLE_PQ_DUP(IssueLevel.STOP),
    /** Q 블록이 P 블록보다 앞(G71~G73 은 같아도), 또는 P~Q 에 윤곽 이동이 없음 */
    CYCLE_PQ_ORDER(IssueLevel.STOP),
    /**
     * G71~G73 의 P~Q 가 사이클 블록보다 앞. FANUC 은 황삭 뒤 Q 다음 블록으로 가므로(FF 4.2.1 '- Operations') 사이클 블록으로 되돌아와
     * 끝없이 되풀이한다 — 지원 안 함(P~Q 를 사이클 블록 뒤에 둔다). args = [G7x]
     */
    CYCLE_PQ_BEFORE(IssueLevel.STOP),
    /** G71~G73 의 P 블록이 G00/G01 이 아님, 또는 G71 P 블록이 X(U)만 · G72 P 블록이 Z(W)만이 아님(줄 = P 블록). args = [G7x] */
    G7X_P_BLOCK(IssueLevel.STOP),
    /** P 블록에 X · Z 둘 다 — 형식 II(S 4-104) 지원 안 함(줄 = P 블록) */
    G71_TYPE2(IssueLevel.STOP),
    G72_TYPE2(IssueLevel.STOP),
    /** G71/G72 의 A′→B 가 한 축이라도 한 방향이 아니거나 시작점 쪽으로 돌아오지 않음(S 4-103). 줄 = 그 블록, args = [축 "X"|"Z"] */
    G71_NOT_MONOTONE(IssueLevel.STOP),
    /** 다듬질 여유 U · W 부호가 깎는 쪽과 어긋남(S 4-103 그림 4-16). args = ["U"|"W"] */
    G7X_SIGN(IssueLevel.STOP),
    /** G74/G75 바닥 도피 R(Δd) ≠ 0(H 102 · 106 — 방향이 기계마다) */
    G7X_RELIEF(IssueLevel.STOP),
    /** G73 패스 수 R < 2 */
    G73_COUNT(IssueLevel.STOP),
    /** P~Q 안 M98/M99(S 4-117 주 2). args = [G7x] */
    M98_IN_PQ(IssueLevel.STOP),

    // ---- 부프로그램 · 끝(1.8)
    /** M99 P 의 N 이 없음(args = ["N순번"]) · 그 밖 부를 곳 없음 */
    SUB_NOT_FOUND(IssueLevel.STOP),
    /** M98 P 의 프로그램이 이 글에 없음 — 기계에만 있는 부프로그램으로 보고 건너뜀(그 안 이동은 그리지 않음). args = [번호] */
    SUB_SKIPPED(IssueLevel.WARN),
    SUB_NEST(IssueLevel.STOP),
    /** 부프로그램이 M99 없이 끝남 */
    SUB_NO_M99(IssueLevel.STOP),
    M99_MAIN(IssueLevel.INFO),
    MACHINE_M(IssueLevel.WARN),
    NO_END(IssueLevel.INFO),
    EXEC_LIMIT(IssueLevel.STOP),

    // ---- 소재(5.5 — 소재 단계가 쓴다)
    RAPID_CUT(IssueLevel.WARN),
    CHUCK_HIT(IssueLevel.STOP),
}

/**
 * [line] = 편집기 줄(1부터, 0 = 줄 없음), [cols] = 그 줄 안 글자 위치, [word] = 문제의 낱말 글(예 "X44"),
 * [args] = 화면 글에 넣을 값(코드마다 뜻이 다름 — 각 코드를 내는 곳의 주석).
 */
data class SimIssue(
    val code: IssueCode,
    val line: Int,
    val cols: IntRange? = null,
    val word: String? = null,
    val args: List<String> = emptyList(),
) {
    val level: IssueLevel get() = code.level
}
