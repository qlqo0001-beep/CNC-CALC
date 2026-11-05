package com.bmt.nosecalc.screens.sim

import com.bmt.nosecalc.calc.sim.GSim
import com.bmt.nosecalc.calc.sim.IssueCode
import com.bmt.nosecalc.calc.sim.IssueLevel
import com.bmt.nosecalc.calc.sim.SimIssue
import com.bmt.nosecalc.calc.sim.ToolKind
import com.bmt.nosecalc.ui.HelpSection

/** 낱말 글("X44." · ",C1.")의 주소 부분("X" · ",C") */
private fun addrOf(word: String?): String = word?.takeWhile { !it.isDigit() && it != '.' && it != '-' && it != '+' } ?: ""

/**
 * 문제 → 화면 글(설계 1.5 표의 '화면 글'). 줄 번호는 붙이지 않는다 — 부르는 곳이 '12줄' 을 앞에 둔다.
 * args 의 뜻은 calc 의 그 코드를 내는 곳 주석(SimIssue.kt · GSim.kt)을 따른다.
 */
internal fun simIssueText(i: SimIssue): String {
    val w = i.word ?: ""
    val a = addrOf(i.word)
    val a0 = i.args.getOrNull(0) ?: ""
    val a1 = i.args.getOrNull(1) ?: ""
    return when (i.code) {
        IssueCode.BAD_CHAR -> "'$w' 는 읽을 수 없는 글입니다 — 주소 글자(G X Z …) + 숫자, 주석 ( ) 만 씁니다"
        IssueCode.NO_NUMBER -> "$a 뒤에 숫자가 없습니다 — 주소와 숫자는 붙여 씁니다(X44.)"
        IssueCode.NUMBER_TOO_LONG -> "$w: 숫자가 너무 깁니다(9자리까지 · N 5자리 · O 8자리)"
        IssueCode.NUMBER_TOO_BIG -> "$w: 값이 너무 큽니다"
        IssueCode.DUP_ADDRESS -> "$a 를 한 블록에 두 번 썼습니다"
        IssueCode.DUP_GROUP -> "$w 와 $a0 은 같은 그룹이라 한 블록에 같이 쓰지 않습니다"
        IssueCode.TWO_M -> "M 은 한 블록에 하나만 씁니다($w)"
        IssueCode.COMMENT_OPEN -> "주석 괄호 ( 가 닫히지 않았습니다 — 같은 줄에 ) 를 넣으세요"
        IssueCode.COMMENT_NESTED -> "주석 안에 ( 를 또 썼습니다 — 괄호는 겹쳐 쓰지 않습니다"
        IssueCode.UNKNOWN_ADDRESS -> "$a 주소는 쓰지 않습니다"
        IssueCode.HAAS_FORMAT -> "Haas 1줄 형식($w)입니다 — FANUC 2줄 형식(G71 U_ R_ ; G71 P_ Q_ …)으로 써 주세요"
        IssueCode.G01_CORNER_IKR -> "G01 의 $a 모서리(쉼표 없는 I · K · R)는 지원하지 않습니다 — ,C · ,R 로 쓰세요"
        IssueCode.UNSUPPORTED_G -> "$w 는 지원하지 않습니다"
        IssueCode.INCH -> "G20(인치)은 지원하지 않습니다 — mm(G21)로 씁니다"
        IssueCode.WORK_OFFSET -> "$w(좌표계 옮김)는 지원하지 않습니다 — 옮김 값을 모릅니다. G54 만 씁니다"
        IssueCode.G50_COORD -> "G50 의 $a(좌표계 설정)는 지원하지 않습니다 — G50 은 S(최대 회전수)만"
        IssueCode.G80_IN_CYCLE -> "G90 · G92 · G94 모드 중 G80 은 지원하지 않습니다 — G00 으로 모드를 끝내세요"
        IssueCode.MID_BLOCK_SKIP -> "/ (블록 건너뛰기)는 블록 맨 앞에만 씁니다"
        IssueCode.DECIMAL_NOT_ALLOWED ->
            if (a == "P" || a == "Q") "$w: $a 는 소수점 없는 정수입니다 — 사이클의 P · Q 는 순번이나 µm(Q3000 = 3 mm)"
            else "$w: $a 는 소수점 없는 정수로 씁니다"
        IssueCode.NO_POINT_FEED -> "$w: 회전당 이송(G99) · 나사 리드의 F 는 소수점을 씁니다(F0.2) — 소수점 없는 F 는 단위가 기계마다 다릅니다"
        IssueCode.NO_POINT_COORD -> "$w 를 $a0 mm 로 읽었습니다(FANUC 표준 — 소수점 없는 수는 ×0.001). mm 면 $w. 처럼 점을 찍으세요"
        IssueCode.T_FORMAT -> "$w: T 는 3~4자리(T0101 = 공구 1 · 보정 01)로 씁니다"
        IssueCode.BAD_VALUE -> when {
            a0 == "R" -> "$w: G76 다듬질 여유 R 이 나사산 높이 P 보다 큽니다"
            a0 == "X" && w.isEmpty() -> "G76 시작점 X 와 끝 X 가 같습니다 — 나사산 쪽을 알 수 없습니다"
            w.isEmpty() -> "값이 맞지 않습니다"
            else -> "$w: 쓸 수 없는 값입니다"
        }
        IssueCode.ABS_INC_BOTH -> "$a0 와 ${if (a0 == "X") "U" else "W"} 를 한 블록에 같이 썼습니다 — 절대 · 증분 중 하나만"
        IssueCode.DUP_PROGRAM -> "${if (a0.isNotEmpty()) "O$a0" else w} 프로그램이 둘입니다"
        IssueCode.WORD_NOT_USED -> "$w: 이 블록의 명령은 $a 를 쓰지 않습니다"
        IssueCode.WORD_MISSING -> "$a0 가 없습니다 — 이 명령에 꼭 필요합니다"
        IssueCode.G04_MIXED -> "G04(드웰)는 다른 명령과 한 블록에 쓰지 않습니다($w)"
        IssueCode.FEED_MISSING -> "이송 F 가 없습니다 — 이 블록이나 앞 블록에 F 를 넣으세요"
        IssueCode.SPINDLE_STOPPED -> "주축이 서 있습니다 — 회전당 이송(G99) · 나사는 M03 / M04 를 먼저"
        IssueCode.S_MISSING -> "S 가 없습니다 — 회전수(G97)나 절삭 속도(G96)를 먼저 넣으세요"
        IssueCode.NO_CLAMP_G96 -> "G50 최대 회전수 없이 G96 — 기계 최대 $a0 rpm 으로 봤습니다(척을 위해 G50 S 를 넣으세요)"
        IssueCode.RPM_OVER_LIMIT -> "$w: 최대 회전수 $a0 rpm 으로 잘립니다"
        IssueCode.THREAD_IN_CSS -> "나사는 G97(회전수 일정)로 — G96 이면 회전수가 바뀌어 나사가 어긋납니다"
        IssueCode.T_WITH_FEED -> "$w: T(공구 교환)는 이송 블록에 같이 쓰지 않습니다 — G00 블록에"
        IssueCode.ARC_NO_CENTER -> "원호에 R 이나 I · K 가 없습니다"
        IssueCode.ARC_R_AND_IK -> "원호에 R 과 I · K 를 같이 썼습니다 — 하나만"
        IssueCode.ARC_R_SHORT -> "R 이 두 점 사이 거리의 반보다 작습니다($a0 mm 모자람)"
        IssueCode.ARC_END_OFF -> "원호 끝점이 원 위에 없습니다(반지름 차 $a0 mm)"
        IssueCode.FULL_CIRCLE -> "원호 시작점과 끝점이 같습니다 — 완전한 원은 지원하지 않습니다"
        IssueCode.COMP_START_ARC -> "보정 시작(G41 / G42)은 직선 블록에서 — 원호로 시작할 수 없습니다"
        IssueCode.COMP_CANCEL_ARC -> "보정 취소(G40)는 직선 블록에서 — 원호로 끝낼 수 없습니다"
        IssueCode.COMP_START_ZERO ->
            if (a0.isNotEmpty()) "${a0}줄 G41 / G42 뒤 움직이는 블록 없이 보정을 끕니다 — 형식 C 의 옮길 방향(다음 이동 블록의 법선)이 없습니다"
            else "보정 시작 블록에 움직임이 없습니다 — G41 / G42 는 움직이는 블록에(형식 A · B 기계)"
        IssueCode.COMP_ACUTE_OUTER -> "바깥 모서리가 90° 보다 급하게 꺾입니다(${a0}줄과 이음) — 제어기마다 끼우는 길이 달라 지원하지 않습니다"
        IssueCode.COMP_INTERFERENCE -> "노즈 R 보정 간섭 — 보정 경로가 거꾸로 갑니다(${a0}줄과 이음). 요소가 노즈 R 보다 짧거나 홈이 좁습니다"
        IssueCode.COMP_ARC_SMALL -> "오목 원호 R$a0 이 노즈 R$a1 보다 작아 보정할 수 없습니다"
        IssueCode.COMP_IDLE_BLOCKS -> "보정 중 움직임 없는 블록이 둘 넘게 이어집니다 — 보정 경로를 정할 수 없습니다"
        IssueCode.COMP_SWITCH -> when {
            a0 == "G40" -> "G41 ↔ G42 를 바꾼 바로 다음 블록에서 보정을 끕니다 — 그 경로는 확인하지 못해 지원하지 않습니다(한 블록 더 간 뒤에)"
            a0.isNotEmpty() -> "${a0}줄 원호 끝에서 G41 ↔ G42 를 바꾸는데 두 쪽 보정선이 만나지 않습니다 — 그 경로는 지원하지 않습니다(직선 끝에서 바꾸세요)"
            else -> "보정 시작 블록 바로 다음 블록에서는 G41 ↔ G42 를 바꿀 수 없습니다(FANUC) — 한 블록 더 간 뒤에, 또는 G40 으로 끈 뒤 다시"
        }
        IssueCode.COMP_G40_IK -> "G40 의 I · K(빠지는 방향)는 지원하지 않습니다"
        IssueCode.COMP_TOOL_CHANGE -> "보정 중 공구 · 보정 번호를 바꿨습니다($w) — G40 으로 먼저 끄세요"
        IssueCode.COMP_IN_CYCLE -> "보정(G41 / G42) 중에는 고정 사이클을 부르지 않습니다 — 보정은 P~Q 안에서 켜고 끄세요"
        IssueCode.COMP_AT_G28 -> "보정 중 G28 · G30 은 지원하지 않습니다 — G40 으로 먼저 끄세요"
        IssueCode.COMP_CANCEL_NO_MOVE -> "${a0}줄에서 움직임 없이 보정을 끈 뒤 원호 · 모서리 · 사이클 · 다시 보정 — 그 경로는 확인하지 못해 지원하지 않습니다(그 앞에 직선 이동을)"
        IssueCode.COMP_CANCEL_ALONE ->
            if (a0 == "C") "움직임 없는 G40 — 형식 C: 이 줄에서 노즈가 앞 블록 끝 법선에서 지령점(팁 벡터만큼 뒤)으로 옮겨집니다"
            else "움직임 없는 G40 — 앞 블록 끝에서 법선으로 서고 다음 직선 이동에서 보정을 풀었습니다(형식 A · B). 형식 C 기계는 이 자리가 다릅니다"
        IssueCode.COMP_NO_OFFSET -> "G41 / G42 인데 T 보정 번호가 00 이라 보정하지 않습니다"
        IssueCode.COMP_NOT_CANCELLED -> "G40 으로 보정을 끄지 않고 프로그램이 끝납니다"
        IssueCode.COMP_TYPE_A ->
            if (a0 == "C") "이 이동 블록에서 보정이 시작 · 취소됩니다(혼자 줄 G41 / G42 뒤 보정 번호를 이 블록 T 로 준 것도). 바깥 모서리라 형식 A(법선, No.5003#0 SUP = 0)로 그렸습니다 — SUP = 1 기계는 이 블록 경로가 다릅니다"
            else "보정 시작 · 취소가 바깥 모서리 — 형식 A(법선)로 그렸습니다. 형식 B 기계는 이 블록 경로가 다릅니다"
        IssueCode.AGAINST_TOOL -> "바이트 반대쪽으로 깎습니다 — 팁 번호를 확인하세요"
        IssueCode.TOOL_DATA_MISSING -> {
            val off = a1.padStart(2, '0')
            "T${a0.padStart(2, '0')}$off — 보정 $off 의 노즈 R · 팁 번호가 없습니다. 공구 설정에 넣어 주세요"
        }
        IssueCode.CORNER_C_ARC -> "$w: ,C 의 이웃이 원호입니다 — 지원하지 않습니다(직선끼리만)"
        IssueCode.CORNER_TOO_BIG -> "$w: 모서리가 이웃 블록보다 깁니다"
        IssueCode.CORNER_NO_VERTEX -> "$w: 꺾이는 곳이 없어(일직선) 모서리를 넣을 수 없습니다"
        IssueCode.CORNER_NO_NEXT -> "$w: 다음 블록이 G01~G03 이동이 아닙니다 — 모서리는 바로 다음 이동과 잇습니다"
        IssueCode.CORNER_ON_RAPID -> "$w: G00 블록에는 모서리를 쓰지 않습니다"
        IssueCode.CORNER_BOTH -> ",C 와 ,R 을 한 블록에 같이 썼습니다"
        IssueCode.CYCLE_PQ_FORBIDDEN -> "$w: $a0 의 P~Q 안에서 쓸 수 없는 명령입니다"
        IssueCode.CYCLE_COMP_OPEN -> "P~Q 끝에서 보정(G41 / G42)이 켜진 채입니다 — Q 블록까지 G40 으로 끄세요"
        IssueCode.CYCLE_MOTION_MISSING -> "G00~G03 이 없습니다 — 고정 사이클 뒤 첫 이동은 G 를 다시 씁니다"
        IssueCode.CYCLE_FIRST_LINE -> "$a0 첫 줄이 없습니다 — 기계 기본값을 몰라 첫 줄 값을 프로그램에 씁니다"
        IssueCode.CYCLE_BLOCK_TM -> "$w: 사이클 블록에 S · T · M 을 같이 쓰지 않습니다 — 앞 블록에"
        IssueCode.CYCLE_REPEAT_MST -> "$a0 모드에서 축 없이 $w — 제어기는 사이클을 한 번 더 돌며 함께 실행해 순서를 알 수 없습니다(지원 안 함). G00 으로 모드를 먼저 끝내세요"
        IssueCode.CYCLE_ONESHOT -> "$a0 모드 중 $w — G00 으로 모드를 먼저 끝내세요"
        IssueCode.CYCLE_PQ_NOT_FOUND -> "$a1 블록이 이 프로그램에 없습니다($a0)"
        IssueCode.CYCLE_PQ_DUP -> "$a0 블록이 둘입니다"
        IssueCode.CYCLE_PQ_ORDER -> "Q 블록이 P 블록보다 앞이거나 P~Q 에 윤곽 이동이 없습니다"
        IssueCode.CYCLE_PQ_BEFORE -> "$a0 의 P~Q 가 사이클 블록보다 앞 — 사이클이 끝없이 되풀이됩니다. P~Q 를 뒤에 두세요"
        IssueCode.G7X_P_BLOCK -> "$a0 의 P 블록은 G00 / G01 로 " + when (a0) {
            "G71" -> "X(U)만 씁니다"
            "G72" -> "Z(W)만 씁니다"
            else -> "씁니다"
        }
        IssueCode.G71_TYPE2 -> "G71 형식 II(P 블록에 X · Z 둘 다)는 지원하지 않습니다"
        IssueCode.G72_TYPE2 -> "G72 형식 II(P 블록에 X · Z 둘 다)는 지원하지 않습니다"
        IssueCode.G71_NOT_MONOTONE -> "윤곽이 $a0 방향으로 한쪽으로만 가지 않습니다(형식 I) — 되돌아가는 곳이 있습니다"
        IssueCode.G7X_SIGN -> "다듬질 여유 $a0 의 부호가 깎는 쪽과 반대입니다"
        IssueCode.G7X_RELIEF -> "$w: 바닥 도피(R)는 지원하지 않습니다 — 0 이나 빈칸으로"
        IssueCode.G73_COUNT -> "$w: G73 패스 수 R 은 2 이상입니다"
        IssueCode.M98_IN_PQ -> "$w: P~Q 안에서는 M98 / M99 를 쓰지 않습니다"
        IssueCode.SUB_NOT_FOUND ->
            if (a0.startsWith("N")) "M99 P: $a0 블록이 없습니다" else "O${a0.padStart(4, '0')} 이 이 글에 없습니다"
        IssueCode.SUB_SKIPPED -> "O${a0.padStart(4, '0')} 이 이 글에 없습니다 — 기계에 따로 있는 부프로그램으로 보고 건너뜁니다(그 안의 이동 · 시간은 빠짐)"
        IssueCode.SUB_NEST -> "부프로그램은 ${a0}단까지 겹칩니다"
        IssueCode.SUB_NO_M99 -> "$a0 부프로그램이 M99 없이 끝납니다"
        IssueCode.M99_MAIN -> "M99: 처음으로 돌아가 반복 — 시뮬레이션은 한 번만"
        IssueCode.MACHINE_M -> "$w: 기계마다 뜻이 다른 M 코드 — 움직임 없이 건너뜁니다"
        IssueCode.NO_END -> "M30 / M02 없이 글이 끝납니다"
        IssueCode.EXEC_LIMIT -> "실행 블록이 너무 많습니다(${GSim.EXEC_LIMIT}) — M98 · M99 고리나 너무 작은 절입을 확인하세요"
        IssueCode.RAPID_CUT -> "G00 이 소재를 지납니다"
        IssueCode.CHUCK_HIT -> "공구가 척에 닿습니다"
        IssueCode.DIE_PATH -> (if (w.isNotEmpty()) "$w: " else "") + "다이스 헤드(DIE 공구)의 이송은 X0. 에서 −Z 로 곧게만 — X 이동 · +Z · 원호 · 모서리는 지원하지 않습니다"
        IssueCode.DIE_NOT_PER_REV -> "다이스 헤드는 회전당 이송(G99)으로 피치에 맞춥니다 — 분당 이송(G98)은 지원하지 않습니다"
        IssueCode.DIE_IN_CYCLE -> "다이스 헤드(DIE 공구)로 $a0 사이클은 지원하지 않습니다 — X0. 에서 G01 한 번(−Z)으로 씁니다"
        IssueCode.DIE_DATA_MISSING -> {
            val off = a1.padStart(2, '0')
            "T${a0.padStart(2, '0')}$off — 다이스 지름 · 피치가 비었거나 나사 각이 60° · 55° 가 아닙니다. 경로만 그리고 나사 모양은 그리지 않습니다(공구 설정)"
        }
        IssueCode.DIE_FEED_OVER_PITCH -> "이송 F$a0 이 다이스 피치 $a1 mm 보다 큽니다 — 머리를 밀어붙여 끝에서 안 열리거나 나사가 어긋날 수 있습니다"
    }
}

/** 공구 종류 이름(공구 설정의 종류 고르기 · 홀더 시트 줄) — 순서 번호에 기대지 않게 종류마다 */
internal fun toolKindLabel(k: ToolKind): String = when (k) {
    ToolKind.TURN -> "바이트"
    ToolKind.GROOVE -> "홈"
    ToolKind.THREAD -> "나사"
    ToolKind.ROUND -> "둥근"
    ToolKind.DIE -> "다이스"
}

/** '12줄' — 줄 없는 문제(0)는 '프로그램' */
internal fun lineLabel(line: Int): String = if (line > 0) "${line}줄" else "프로그램"

/** 문제 띠 한 줄: '오류 2 · 확인 1 · 안내 1' */
internal fun issueCounts(issues: List<SimIssue>): String {
    val stop = issues.count { it.level == IssueLevel.STOP }
    val warn = issues.count { it.level == IssueLevel.WARN }
    val info = issues.count { it.level == IssueLevel.INFO }
    return listOfNotNull(
        "오류 $stop".takeIf { stop > 0 },
        "확인 $warn".takeIf { warn > 0 },
        "안내 $info".takeIf { info > 0 },
    ).joinToString(" · ")
}

/** 도움말의 '궤적 창 조작' 묶음 번호 — 궤적 창의 ? 는 여기부터(설계 7.11) */
internal const val SIM_HELP_TRAJ = 3

/** 도움말(설계 7.11) — 편집기 화면은 '편집기와 키패드' 부터, 궤적 창은 '궤적 창 조작' 부터 */
internal val SimHelp: List<HelpSection> = listOf(
    HelpSection(
        "편집기와 키패드",
        listOf(
            "실제 기계에 넣을 FANUC 프로그램을 쓰고, 오류 · 확인할 곳을 줄 번호와 함께 봅니다. 글은 늘 대문자로 들어갑니다.",
            "글을 누르면 그 자리로 커서가 갑니다. 길게 누르면 그 낱말을 고르고, 누른 채 끌면 고른 범위가 늘어납니다. 한 손가락으로 끌면 글이 위아래로 움직입니다.",
            "CNC 키패드: 왼쪽 세 열은 주소 글자, 가운데는 숫자, 오른쪽은 ⌫(길게 누르면 계속 지움) · 쉼표 · 빈칸(SP) · 새 줄(⏎).",
            "SHIFT 를 한 번 누르면 다음 한 글자만 키 오른쪽 위 작은 글자가 들어갑니다 — O → ( · N → ) · G → / · X → I · Z → K · S → L · W → P · T → C · R → Q. 길게 누르면 고정, 다시 누르면 풀립니다.",
            "쉼표 모서리는 쉼표 키 다음 SHIFT + T(C) 또는 R: G01 Z-30. ,C1.",
            "↶ 되돌리기 · ↷ 다시하기(200 걸음), 🔍 찾기(찾는 글도 이 키패드로), ▶ 궤적 = 궤적 확인, ✕ 또는 뒤로 키 = 키패드 닫기.",
            "PC 는 키보드로 바로 칩니다: 화살표 · Home · End · Shift 로 고르기, Ctrl+Z / Y 되돌리기 · 다시하기, Ctrl+C / X / V, Ctrl+A 모두 고르기, Ctrl+F 찾기.",
        ),
    ),
    HelpSection(
        "오류 · 확인 표시",
        listOf(
            "글을 고치고 0.3초 쉬면 다시 읽습니다. 줄 번호 옆 빨간 팔각형 = 오류(그 블록에서 실행이 멈춤), 노란 삼각형 = 확인할 것(실행은 계속), 파란 원 = 안내.",
            "문제가 있는 낱말 아래에는 물결 밑줄이 그어지고, 커서가 있는 줄과 실행이 멈춘 줄 아래에 그 글이 나옵니다.",
            "아래 띠 '오류 n · 확인 n' 을 누르면 목록이 열립니다. 줄을 누르면 그 줄로 갑니다.",
            "소수점 없는 좌표(X44)는 FANUC 표준대로 0.044 mm 로 읽고 노란 표를 붙입니다. mm 면 X44. 처럼 점을 찍으세요.",
            "확인하지 못한 제어기 동작은 흉내 내지 않고 '지원하지 않습니다' 로 멈춥니다(틀린 그림보다 낫게).",
        ),
    ),
    HelpSection(
        "저장 · 목록 · 예제",
        listOf(
            "'저장' 은 첫 O 번호를 이름으로 저장합니다(O 번호가 없으면 번호를 묻고 첫 줄에 넣습니다). 같은 번호가 있으면 덮어쓸지 묻습니다.",
            "'목록' 에서 저장한 프로그램을 열고, 지우기(휴지통 단추 또는 길게 누르기)는 한 번 더 묻습니다. 예제 16개(Siemens 설명서의 사이클 예 · 원본 화면의 글 · 우리 예 — 출처는 이름 아래)도 여기서 엽니다.",
            "쓰던 글은 0.5초마다 저절로 남아 앱을 다시 켜도 그대로입니다(저장한 프로그램과는 따로). 프로그램은 10만 자 · 200개까지.",
            "⋯ 에서 새 프로그램 · 붙여넣기 · 전체 복사.",
        ),
    ),
    HelpSection(
        "궤적 창 조작",
        listOf(
            "▶ 궤적(키패드) 또는 궤적 확인을 누르면 열립니다. 재생 · 멈춤, 리셋(처음으로), 속도 미끄럼(×1 ~ ×200 — ×1 = 실제 가공 시간).",
            "싱글 블록을 켜면 블록 끝마다 멈춥니다(고정 사이클은 층 · 패스마다). M01 을 켜면 M01 에서 멈춥니다. M00 은 늘 멈춥니다 — 재생을 누르면 이어서.",
            "|◀ 지금 블록 처음으로(이미 처음이면 앞 블록) · ▶| 지금 블록 끝까지. 사이클은 사이클 줄 하나가 한 블록입니다.",
            "아래 코드 띠는 지금 줄과 앞 · 뒤 줄입니다. 손잡이를 누르거나 끌어 올리면 전체 목록 — 줄을 누르면 그 블록 처음으로 갑니다.",
            "한 손가락으로 끌면 이동, 두 손가락으로 확대 · 이동, ⤢ = 맞춤. PC 는 끌기 · 휠 확대, 빈칸 재생/멈춤 · ← → 블록 · Home 리셋 · + − 0.",
            "∠ 각도 확인을 켜면 기운 직선마다 Z 축에서 잰 각이 나옵니다(예 56.31°).",
            "그림: 보라 = 실제 인선 경로(보정 뒤), 초록 = 프로그램에 쓴 경로(보정이 있을 때), 파랑 = 원호, 회색 점선 = 급송(가운데 빈 화살 = 가는 쪽), 회색 가는 선 = 황삭 층 · 사이클 이동, 회색 면 = 남은 소재. 범례 알약에 전부 있습니다.",
        ),
    ),
    HelpSection(
        "3D 보기",
        listOf(
            "위 띠에서 3D 를 고르면 남은 소재를 돌린 몸체로 봅니다. 재생하면 깎여 가는 모습이 같이 움직입니다(재생 · 카드 · 코드 띠는 2D 와 같음). 공구 · 경로 · 고스트는 3D 에 그리지 않습니다 — 2D 에서 보세요.",
            "◐ 1/4 = 앞 위 1/4 을 잘라 안쪽(구멍 · 안 나사 · 홈 단면)을 봅니다(처음 켬). 단면은 조금 밝게 칠합니다.",
            "회전 = 공작물이 축 둘레로 천천히 돕니다(초당 20°). 자유 = 한 손가락으로 시점을 돌립니다 — 끄면 한 손가락은 이동. 두 손가락 = 확대 · 이동, ⤢ = 처음 시점. PC 는 끌기 · 휠(가까이/멀리) · + − 0.",
            "끌거나 돌리거나 재생하는 동안은 거칠게(빠르게) 그리고, 멈추면 곱게 다시 그립니다.",
            "나사는 마지막 패스 깊이로 오른나사 모양으로 그립니다 — 나선 방향은 확인하지 않습니다(피치 · 깊이 · 길이만 보세요).",
            "척(몸통 + 턱 셋)은 그림입니다. 척 닿음 검사는 2D 와 같이 척 면 왼쪽 · 척 지름 안을 봅니다.",
        ),
    ),
    HelpSection(
        "보정 좌표 · 측정점",
        listOf(
            "고스트 원 = 블록 끝의 노즈(반지름 = 노즈 R), 원 안 ㄱ = 가상 인선. 원을 누르면 아래 카드가 '보정 좌표' — 노즈R 보정이 걸린 실제 인선 자리, 곧 G40(직접 보정)으로 쓸 좌표입니다. 겹친 원은 다시 누르면 다음 원.",
            "📏 측정을 켜면 블록 끝 명령점(작은 □)을 누릅니다 — 카드가 '측정점', 프로그램에 쓴 좌표입니다. G41/G42 프로그램이면 도면 치수와 같고, 직접 보정 좌표로 쓴 프로그램이면 다를 수 있습니다.",
            "아무것도 고르지 않으면 카드는 '위치 (절대)' — 재생 중 지금 실제 인선 X(직경) · Z.",
        ),
    ),
    HelpSection(
        "시뮬레이션 · 공구 설정",
        listOf(
            "시뮬레이션 설정: 소재(봉재 지름 · 돌출 · 기장여유 · 구멍 · 척), 기계(급송 · 공구 교환 · 최대 회전수 · 원점 · 제2 원점 · 급송 경로 · 보정 시작 · 취소 형식), 프로그램 읽기(소수점 없는 수 · 블록 스킵 · G92 끝 빼기), 보기 스위치. 빈칸은 자동 값이나 가정 값을 흐린 청록 글로 보입니다.",
            "보정 시작 · 취소 형식은 기계 매개변수 No.5003#1 SUV 입니다. 형식 C(SUV = 1, 처음 값)는 이동 없이 쓴 G41 · G42 줄에서 노즈를 다음 이동 블록에 직각으로, G40 줄에서 앞 블록 끝 법선에서 지령점으로 노즈 R 만큼 옮깁니다. 형식 A · B(SUV = 0)는 그 줄에서 움직이지 않습니다. 이동과 같이 쓴 G41 · G42 · G40 은 두 설정 모두 형식 A 로 그립니다.",
            "형식 C 에서 G40 줄의 옮김이 소재를 깎으면 노란 ▲ 와 띠로 알립니다 — 팁 번호가 바이트 반대쪽이면 마지막에 깎은 면을 팝니다.",
            "공구 설정: 프로그램의 보정 번호마다 종류 · 노즈 R · 팁 번호 · 인서트 · 절입각(홈은 폭, 나사는 각도, 다이스는 다이스 지름 · 피치 · 나사 각). G41/G42 를 쓰는 번호는 노즈 R · 팁이 있어야 돕니다(빨간 테).",
            "다이스 헤드(종류 '다이스'): G01 은 X0. 에서 −Z 로만 받고 시간은 F 로 잽니다. 나사 모양은 공구 설정의 다이스 지름 · 피치 · 각(60° · 55°)으로 그린 모형입니다 — 머리가 열리며 끌려 나가는 길이와 머리 몸통은 그리지 않습니다. X 이동 · 원호 · 고정 사이클 · G98 은 멈춥니다.",
            "카드 맨 위 '홀더 (선택)' 에서 회사에서 쓰는 홀더를 고르면 종류 · 인서트 모양 · 절입각을 제조사 카탈로그 값으로 채웁니다(고칠 수 있음, 고치면 '수정됨'). 노즈 R · 팁 번호 · 나사 각은 홀더가 정하지 않아 그대로입니다.",
            "공구표 · 소재 칸은 앱을 켠 동안만, 기계 · 읽기 · 보기 설정은 다음에 켜도 그대로입니다.",
        ),
    ),
    HelpSection(
        "지원하는 G · M 코드",
        listOf(
            "G00 · G01 · G02 · G03(R 또는 I · K, I 는 반경값) · G04 · G18 · G21 · G28 · G30(제2 원점 — 설정, 빈칸이면 G28 원점) · G40 · G41 · G42 · G50 S(같은 블록 M 받음) · G54 · G70~G76(FANUC 2줄 형식) · G80 · G90 · G92 · G94 · G96 · G97 · G98 · G99, 모서리 ,C · ,R.",
            "M00 · M01 · M02 · M30 · M03 · M04 · M05 · M08 · M09 · M98 · M99, M23 · M24(나사 끝 빼기 켬 · 끔 — 흔한 FANUC 계열 기계 M 정의, 기계마다 다를 수 있음. 처음은 켬이고 M24 뒤의 G92 · G76 은 빼지 않음). 그 밖의 M 은 움직임 없이 건너뛰고 노란 표. 글에 없는 부프로그램(M98 P)도 노란 표 뒤 건너뜁니다.",
            "G90 · G92 · G94 모드에서 기계 M 만 쓴 줄(나사 끝의 M24 따위)은 제어기처럼 사이클을 한 번 더 돈 뒤 M 을 실행합니다. 주축(M03~M05) · 끝 · S · T 만 쓴 줄은 순서를 몰라 멈춥니다 — G00 으로 모드를 먼저 끝내세요.",
            "지원 안 함: G20(인치) · G32 · G50 X/Z · G55~G59 · Haas 1줄 사이클 형식 · G71/G72 형식 II · G01 의 I/K/R 모서리 등 — 줄 번호와 함께 알립니다.",
        ),
    ),
    HelpSection(
        "정확 · 모형 · 지원 안 함",
        listOf(
            "정확 = 출처(FANUC · Siemens · Haas 설명서) 그대로라 숫자를 믿어도 됩니다: G00~G03 · ,C ,R · 노즈R 보정(형식 A, 이동 없는 시작 · 취소는 설정의 형식 A · B / C) · G90 · G92 · G94 · G70 · G74 · G75.",
            "모형 = 순서는 출처대로, 층 위치 같은 세부는 우리가 정함(시간 · 층 그림만 다를 수 있음): G71 · G72 층, G73, G76 진입. 남는 모양은 같습니다.",
            "지원 안 함 = 오류로 멈춤.",
        ),
    ),
    HelpSection(
        "사이클 타임에 넣지 않는 것",
        listOf(
            "가공 시간은 급송(축마다 제 속도) · 이송(G96 은 지름마다 회전수) · 드웰 · 공구 교환만 더합니다.",
            "넣지 않는 것(실제보다 짧게 나옴): 축 가감속 · 주축 가감속(M03 이 회전수에 닿기까지) · 공구 교환 중 이동 · 블록 처리 시간 · 보정 원호의 이송 가감.",
            "급송 속도 · 공구 교환 시간 · 최대 회전수의 빈칸은 가정 값입니다 — 기계 사양서 값을 설정에 넣으세요.",
        ),
    ),
    HelpSection(
        "소재 · 충돌 검사의 한계",
        listOf(
            "남은 소재는 인서트 모양(종류 · 노즈 R · 팁 · 인서트 · 절입각 · 폭)으로 깎습니다. 홀더 몸통 · 공구대는 보지 않습니다.",
            "급송(G00)이 소재를 깎으면 노란 ▲ 와 띠, 공구가 척에 닿으면 빨간 ✕ 와 띠 — 재생이 그 자리에서 멈춥니다. 이송이 깎은 양은 경고하지 않습니다.",
            "소재 칸이 빈칸이면 이송이 닿은 곳에서 잡은 자동 소재(구멍 없음)입니다 — 내경 가공은 구멍 지름을 넣어야 급송 경고가 맞습니다.",
            "나사(G92 · G76)는 소재를 깎지 않고 마지막 패스 깊이의 톱니로 그립니다(나선은 한 단면에서 한 각도만 깎으므로). 다이스 헤드도 소재를 깎지 않고 공구 설정의 지름 · 피치 · 각으로 톱니를 그립니다.",
        ),
    ),
)
