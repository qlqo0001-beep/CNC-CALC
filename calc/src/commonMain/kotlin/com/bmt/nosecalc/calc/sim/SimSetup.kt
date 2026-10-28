package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.InsertShape
import com.bmt.nosecalc.calc.Pt

/** 급송 경로 모양: DOGLEG = 축마다 제 속도로 따로(꺾임, S 2-23~2-24 · H 32), LINEAR = 두 축이 같이 끝나는 직선(S 2-24 'G0 linear mode') */
enum class RapidPath { DOGLEG, LINEAR }

/**
 * 기계 설정(`docs/simulator-design.md` 4.1). 기본값은 출처로 정할 수 없어 '가정' 이다 — 화면이 빈칸일 때 이 값을 보인다.
 * [rapidX] · [rapidZ] mm/min — X 는 반경 방향 슬라이드 속도(직경 변화 ΔX 는 ΔX/2 를 움직인다).
 * [home] = G28 원점이자 실행 시작 때 공구 자리(공작물 좌표, x 반경). 처음 값 X200 Z100 — 화면은 4.1 의 자동 값
 * (X = max(200, 소재 Ø + 100), Z = 끝면 + 100)을 채워 넘긴다.
 * [g92ChamferLeads] = G92 끝 빼기(리드 배수, 0 = 없음) — 고정 사이클 단계가 쓴다.
 */
data class MachineSetup(
    val rapidX: Double = 20_000.0,
    val rapidZ: Double = 24_000.0,
    val rapidPath: RapidPath = RapidPath.DOGLEG,
    val toolChangeSec: Double = 1.0,
    val maxRpm: Double = 4_000.0,
    val home: Pt = Pt(100.0, 100.0),
    val decimal: DecimalMode = DecimalMode.STANDARD,
    val blockSkip: Boolean = false,
    val g92ChamferLeads: Double = 0.0,
)

enum class ToolKind { TURN, GROOVE, THREAD, ROUND }

/**
 * 공구 보정표 한 줄(보정 번호 = T 뒤 2자리). [noseR] · [tip] 은 G41/G42 에 꼭 필요하다(빈칸이면 TOOL_DATA_MISSING).
 * G40 에서는 빈칸이면 노즈 0 · 팁 0(인선 = 노즈 중심)으로 그린다. 나머지 칸은 소재 깎기 단계(5.3)가 쓴다.
 */
data class ToolOffset(
    val noseR: Double? = null,
    val tip: Int? = null,
    val kind: ToolKind = ToolKind.TURN,
    val insert: InsertShape = InsertShape.C,
    val kappaDeg: Double? = null,
    val width: Double? = null,
    val threadDeg: Double? = null,
)

/** 소재(5.1) — 소재 단계가 쓴다. [length] = 척 면 ~ 끝면, [faceZ] = 끝면 Z(기장여유) */
data class StockSpec(val dia: Double, val length: Double, val faceZ: Double, val boreDia: Double = 0.0, val chuckDia: Double)

/** 실행 입력. [tools] 열쇠 = 보정 번호 */
data class SimInput(val text: String, val machine: MachineSetup = MachineSetup(), val tools: Map<Int, ToolOffset> = emptyMap())
