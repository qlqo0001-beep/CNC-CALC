package com.bmt.nosecalc.calc.sim

/**
 * 예제 프로그램 하나. [tools] = 이 글을 돌릴 공구 보정표(열쇠 = 보정 번호) — G41/G42 를 쓰는 글은 노즈 R · 팁이 있어야 돈다.
 * [source] = 출처(`docs/simulator-design.md` 0.2 약칭과 쪽). 글은 모두 오류(STOP) 0 이어야 한다(ExamplesTest).
 */
data class SimExample(val name: String, val source: String, val text: String, val tools: Map<Int, ToolOffset> = emptyMap())

/**
 * 편집기 '목록' 의 예제(설계 7.3). 출처 글을 바꾼 곳은 각 글의 주석과 [SimExample.source] 에 적는다:
 * S 예제는 앞에 공구 · 회전 · 이송 줄(T0101 · G97 S1000 M03 · G99)을 붙이고, 체계 B 의 G90(절대)은 뺐으며(12.10),
 * 소수점 없는 좌표는 소수점을 붙였다(S 4-111 `X120` · `Z70`, S 4-116 `X60640 Z25000 R200`). 프로그램 번호는 9000번대.
 */
object SimExamples {
    private val OD = mapOf(1 to ToolOffset(0.4, 3))
    private val PRE = listOf("T0101", "G97 S1000 M03", "G99")

    private fun text(o: String, comment: String, body: List<String>) = (listOf("$o ($comment)") + body).joinToString("\n")

    val all: List<SimExample> = listOf(
        SimExample(
            "외경 G71 황삭 + G70 다듬질 (,C · ,R)",
            "원본 09 화면의 글 — 끝에 Q 블록 · G70 · 후퇴를 더함",
            text(
                "O1001", "외경 G71 + G70",
                listOf(
                    "N11 T0101 M08", "G50 S2000", "G96 S200 M03", "G00 X56. Z5.", "G71 U1.5 R0.5",
                    "G71 P10 Q20 U0.4 W0.1 F0.25", "N10 G00 X16.", "G01 Z0. F0.15", "G01 X20. Z-2.", "G01 Z-8.",
                    "G02 X34. Z-15. R7.", "G01 Z-22. ,R1.", "G01 X44. ,R1.", "G01 Z-30. ,C1.", "G01 X54. ,R1.", "G01 Z-43. ,C1.",
                    "N20 G01 X58.", "G70 P10 Q20", "G00 X100. Z50. M09", "M30",
                ),
            ),
            OD,
        ),
        SimExample(
            "G71 길이 방향 황삭 (Siemens 예)",
            "S 4-109 그림 4-22 — N010 의 체계 B G90 뺌, N022 에 소수점",
            text(
                "O9002", "G71 S 4-109",
                PRE + listOf(
                    "N010 G00 X200.0 Z220.0", "N011 X142.0 Z171.0", "N012 G71 U4.0 R1.0", "N013 G71 P014 Q020 U4.0 W2.0 F0.3 S550",
                    "N014 G00 X40.0 F0.15 S700", "N015 G01 Z140.0", "N016 X60.0 Z110.0", "N017 Z90.0", "N018 X100.0 Z80.0", "N019 Z60.0",
                    "N020 X140.0 Z40.0", "N021 G70 P014 Q020", "N022 G00 X200. Z220.", "M30",
                ),
            ),
            OD,
        ),
        SimExample(
            "G72 단면 방향 황삭 (Siemens 예)",
            "S 4-110 그림 4-23 — N010 의 체계 B G90 뺌, N021 에 G00",
            text(
                "O9003", "G72 S 4-110",
                PRE + listOf(
                    "N010 G00 X220.0 Z190.0", "N011 G00 X162.0 Z132.0", "N012 G72 W7.0 R1.0", "N013 G72 P014 Q019 U4.0 W2.0 F0.3",
                    "N014 G00 Z59.5 F0.15 S200", "N015 G01 X120.0 Z70.0", "N016 Z80.0", "N017 X80.0 Z90.0", "N018 Z110.0", "N019 X36.0 Z132.0",
                    "N020 G70 P014 Q019", "N021 G00 X220.0 Z190.0", "M30",
                ),
            ),
            OD,
        ),
        SimExample(
            "G73 형상 반복 (Siemens 예)",
            "S 4-111 그림 4-24 — 소수점 없는 X120 · Z70 에 소수점",
            text(
                "O9004", "G73 S 4-111",
                PRE + listOf(
                    "N010 G00 X260.0 Z220.0", "N011 G00 X220.0 Z160.0", "N012 G73 U14.0 W14.0 R3", "N013 G73 P014 Q020 U4.0 W2.0 F0.3 S0180",
                    "N014 G00 X80.0 Z120.0", "N015 G01 Z100.0 F0.15", "N017 X120. Z90.0", "N018 Z70.", "N019 G02 X160.0 Z50.0 R20.0",
                    "N020 G01 X180.0 Z40.0 F0.25", "N021 G70 P014 Q020", "N022 G00 X260.0 Z220.0", "M30",
                ),
            ),
            OD,
        ),
        SimExample(
            "G90 외경 단일형 사이클 (Siemens 예)",
            "S 4-92 그림 4-2",
            text(
                "O9005", "G90 S 4-92",
                PRE + listOf("N10 G00 X94. Z62.", "N11 G90 X80. W-42. F0.3", "N12 X70.", "N13 X60.", "N14 G00 X100. Z100.", "M30"),
            ),
            OD,
        ),
        SimExample(
            "G92 나사 단일형 사이클 (Siemens 예)",
            "S 4-95 그림 4-7 — 체계 B G78 → 체계 A G92",
            text(
                "O9006", "G92 S 4-95",
                PRE + listOf("N30 G00 X80. Z76.2", "N31 G92 X66.4 Z25.4 F6.", "N32 X65.", "N33 X63.8", "N34 X62.64", "N35 G00 X100. Z100.", "M30"),
            ),
            mapOf(1 to ToolOffset(0.0, 0, ToolKind.THREAD)),
        ),
        SimExample(
            "G76 나사 복합 사이클 (Siemens 예)",
            "S 4-116 그림 4-30 — X60640 Z25000 R200 을 소수점 글로",
            text(
                "O9007", "G76 S 4-116",
                PRE + listOf("G00 X70. Z105.", "G76 P011060 Q100 R0.2", "G76 X60.64 Z25. P3680 Q1800 F6.0", "G00 X100.", "M30"),
            ),
            mapOf(1 to ToolOffset(0.0, 0, ToolKind.THREAD)),
        ),
        SimExample(
            "G75 외경 홈 (우리 예)",
            "우리 예 — 형식 S 4-113 · H 106",
            text(
                "O9008", "G75 홈",
                listOf("T0303", "G97 S800 M03", "G99", "G00 X52. Z-20.", "G75 R0.5", "G75 X40. Z-24. P3000 Q2500 F0.08", "G00 X100. Z50.", "M30"),
            ),
            mapOf(3 to ToolOffset(0.2, 3, ToolKind.GROOVE, width = 3.0)),
        ),
        SimExample(
            "G74 드릴 쪼아 들어가기 (우리 예)",
            "우리 예 — 형식 S 4-112 · H 102",
            text(
                "O9009", "G74 드릴",
                listOf("T0505", "G97 S1200 M03", "G99", "G00 X0. Z5.", "G74 R1.", "G74 Z-30. Q5000 F0.1", "G00 Z50.", "M30"),
            ),
        ),
        SimExample(
            "내경 면취 G41 보정 (06 꼴)",
            "원본 06 화면 꼴 — 진입 · 보정 취소를 더함",
            text(
                "O9010", "내경 G41 면취",
                listOf(
                    "T0202", "G97 S600 M03", "G99", "G00 X40. Z1.", "G41 G01 Z0. F0.08", "G01 X34. Z-2.", "G01 Z-30.",
                    "G40 G00 X32.", "G00 Z50.", "M30",
                ),
            ),
            mapOf(2 to ToolOffset(0.4, 2)),
        ),
        SimExample(
            "M98 부프로그램 — 홈 세 번",
            "형식 S 4-131 (P 앞 자리 = 반복 횟수)",
            text(
                "O9011", "M98 부프로그램",
                listOf(
                    "T0303", "G97 S800 M03", "G99", "G00 X52. Z-10.", "M98 P39012", "G00 X100. Z50.", "M30",
                    "O9012 (홈 하나 - W-8 옮김)", "G01 X44. F0.05", "G00 X52.", "W-8.", "M99",
                ),
            ),
            mapOf(3 to ToolOffset(0.2, 3, ToolKind.GROOVE, width = 3.0)),
        ),
    )
}
