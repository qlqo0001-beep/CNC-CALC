package com.bmt.nosecalc.calc.sim

import com.bmt.nosecalc.calc.Fmt
import com.bmt.nosecalc.calc.MAX_MM
import kotlin.math.abs

/**
 * 소수점 없는 수를 읽는 법(S 1-13~1-14 표 1-2, IS-B).
 * STANDARD = FANUC 표준: X Z U W R I K `,C` `,R` 과 G04 X/U 의 정수는 ×0.001(`X44` = 0.044 mm).
 * CALCULATOR = 계산기식: 정수 = mm. 정수 전용 주소(N O G M T L P Q 등)는 둘 다 그대로.
 */
enum class DecimalMode { STANDARD, CALCULATOR }

/**
 * 낱말 하나. [key] = "G" · "X" · ",C" · ",R" …(대문자), [raw] = 쓴 숫자 글("44."), [value] = 읽은 값(소수점 규칙을 건 뒤 — 좌표는 mm,
 * X · U 는 쓴 그대로 직경값), [point] = 소수점을 썼나, [cols] = 줄 안 글자 위치(주소 글자부터 숫자 끝까지).
 */
data class Word(val key: String, val raw: String, val value: Double, val point: Boolean, val cols: IntRange) {
    /** 화면 · 문제 글에 쓰는 낱말 글(예 "X44", ",C1.") */
    val text: String get() = key + raw
}

/**
 * 블록 하나. [line] = 편집기 줄(1부터), [unit] = [ParsedProgram.units] 번호, [n] = 순번(N), [skip] = 맨 앞 `/`,
 * [words] = N · O 를 뺀 낱말(문제가 있는 낱말은 빠짐 — 그 블록에는 STOP 문제가 있다), [comment] = 주석 글(괄호 안, 여럿이면 빈칸으로 이음),
 * [text] = 블록 글, [cols] = 줄 안에서 이 블록이 차지한 자리(`;` 로 나뉜 블록끼리 다르다).
 */
data class Block(
    val line: Int,
    val unit: Int,
    val n: Int?,
    val skip: Boolean,
    val words: List<Word>,
    val comment: String?,
    val text: String,
    val cols: IntRange,
) {
    internal fun w(key: String): Word? = words.firstOrNull { it.key == key }
    internal fun has(key: String): Boolean = words.any { it.key == key }
    internal val gs: List<Int> get() = words.filter { it.key == "G" }.map { it.value.toInt() }
}

/** O 번호 하나의 프로그램(첫 O 앞 글은 번호 없는 주 프로그램 — [number] null). [firstLine] = 시작 줄 */
data class ProgramUnit(val number: Int?, val firstLine: Int, val blocks: List<Block>)

/** 파싱 결과. units[0] = 주 프로그램. [issues] = 글의 문제(STOP · NO_POINT_COORD 경고) — 줄 순 */
data class ParsedProgram(val units: List<ProgramUnit>, val issues: List<SimIssue>) {
    fun lineOf(unit: Int, block: Int): Int = units[unit].blocks[block].line

    private val stops: Map<Int, List<SimIssue>> by lazy { issues.filter { it.level == IssueLevel.STOP }.groupBy { it.line } }

    /** 그 블록 자리의 첫 STOP 문제(실행은 그 블록에서 멈춘다) */
    internal fun stopOf(b: Block): SimIssue? =
        stops[b.line]?.firstOrNull { it.cols == null || it.cols.first in b.cols }
}

/**
 * G코드 글 읽기. 규칙은 `docs/simulator-design.md` 1.1 · 1.2. 예외를 던지지 않는다 — 문제는 [ParsedProgram.issues] 로.
 * 출처: S = Siemens SINUMERIK ISO Turning 설명서 04/2007(FANUC 호환 ISO 방언), H = Haas ST/TL 워크북 2015(인쇄 쪽).
 */
object GText {
    /** 실행기가 아는 G(그 밖은 UNSUPPORTED_G · INCH · WORK_OFFSET) */
    internal val SUPPORTED_G = setOf(0, 1, 2, 3, 4, 18, 21, 28, 30, 40, 41, 42, 50, 54, 70, 71, 72, 73, 74, 75, 76, 80, 90, 92, 94, 96, 97, 98, 99)

    /** G 그룹(H 29 · S 표): 같은 그룹 둘이 한 블록이면 DUP_GROUP. 00 = 한 번만 쓰는 G */
    internal fun group(g: Int): Int? = when (g) {
        0, 1, 2, 3, 32, 34, 90, 92, 94 -> 1
        4, 10, 28, 30, 31, 50, 52, 53, 65, 70, 71, 72, 73, 74, 75, 76 -> 0
        96, 97 -> 2
        98, 99 -> 5
        20, 21 -> 6
        40, 41, 42 -> 7
        80, 81, 82, 83, 84, 85, 86, 87, 88, 89 -> 10
        54, 55, 56, 57, 58, 59 -> 14
        17, 18, 19 -> 16
        else -> null
    }

    private val COORD = setOf("X", "Z", "U", "W", "R", "I", "K", ",C", ",R")
    private val KNOWN = COORD + setOf("G", "M", "T", "S", "F", "P", "Q", "L")
    private val ALWAYS_INT = setOf("G", "M", "T", "P", "Q", "L")

    fun parse(text: String, decimal: DecimalMode = DecimalMode.STANDARD): ParsedProgram {
        val issues = mutableListOf<SimIssue>()
        val units = mutableListOf(UnitB(null, 1))
        val lines = text.split('\n')
        for ((li, raw) in lines.withIndex()) {
            val lineNo = li + 1
            val line = raw.removeSuffix("\r")
            if (line.trim() == "%") continue // 테이프 시작 · 끝(H 28)
            val toks = lex(line)
            var segStart = 0
            var seg = mutableListOf<Tok>()
            fun close(endExclusive: Int) {
                val cols = segStart until endExclusive
                if (seg.isNotEmpty()) block(lineNo, line, cols, seg, units, issues, decimal)
                seg = mutableListOf()
            }
            for (t in toks) {
                if (t is TSep) {
                    // `;` 앞에 아무것도 없으면 EOB 만인 블록(cols = 그 `;`) — FANUC 은 G90/G92/G94 모드에서 이 블록에서도 사이클을 다시 돈다(FF 4.1.6).
                    // 줄 끝 `;` 는 그 줄 블록의 EOB 라 새 블록을 만들지 않고, 빈 줄도 블록이 아니다(1.1)
                    if (seg.isEmpty()) units.last().blocks += Block(lineNo, 0, null, false, emptyList(), null, "", t.cols)
                    close(t.cols.first)
                    segStart = t.cols.last + 1
                } else seg += t
            }
            close(line.length)
        }
        // 첫 O 앞에 블록이 없으면 첫 O 가 주 프로그램
        val kept = if (units.size > 1 && !units[0].content) units.drop(1) else units
        val out = kept.mapIndexed { ui, u -> ProgramUnit(u.number, u.firstLine, u.blocks.map { it.copy(unit = ui) }) }
        for ((i, u) in kept.withIndex()) {
            val num = u.number ?: continue
            if (kept.subList(0, i).any { it.number == num }) issues += SimIssue(IssueCode.DUP_PROGRAM, u.firstLine, u.oCols, "O$num")
        }
        return ParsedProgram(out, issues.sortedBy { it.line })
    }

    /** 편집기 구문 색용: 줄의 조각마다 (cols, 종류) — 종류 = 주소("G" · "X" · ",C" …), "N" · "O", "(" 주석, ";" · "/", "%" , "!" 받지 않는 글 */
    fun tokens(line: String): List<Pair<IntRange, String>> {
        val l = line.removeSuffix("\r")
        if (l.trim() == "%") {
            val i = l.indexOf('%')
            return listOf(i..i to "%")
        }
        return lex(l).map { t ->
            when (t) {
                is TWord -> t.cols to (if (t.num == null || t.key == null) "!" else t.key)
                is TComment, is TClose -> t.cols to "("
                is TSep -> t.cols to ";"
                is TSlash -> t.cols to "/"
                is TBad -> t.cols to "!"
            }
        }
    }

    // ================================================================ 블록

    private class UnitB(val number: Int?, val firstLine: Int, val oCols: IntRange? = null) {
        val blocks = mutableListOf<Block>()
        /** 글로만 따라간 마지막 01 그룹 G — G01 의 A 가 Haas 형식인지 볼 때만 */
        var textMotion = 0

        /** 낱말 · 순번 · STOP 문제가 있는 블록이 있나(주석만 있는 단위는 버린다) */
        var content = false
    }

    private fun block(
        lineNo: Int, line: String, cols: IntRange, toks: List<Tok>,
        units: MutableList<UnitB>, issues: MutableList<SimIssue>, decimal: DecimalMode,
    ) {
        var skip = false
        val raws = mutableListOf<TWord>()
        val comments = mutableListOf<String>()
        val mine = mutableListOf<SimIssue>()
        for ((i, t) in toks.withIndex()) {
            when (t) {
                is TSlash -> if (i == 0) skip = true else mine += SimIssue(IssueCode.MID_BLOCK_SKIP, lineNo, t.cols, "/")
                is TComment -> {
                    comments += t.text
                    if (!t.closed) mine += SimIssue(IssueCode.COMMENT_OPEN, lineNo, t.cols)
                    if (t.nestedAt != null) mine += SimIssue(IssueCode.COMMENT_NESTED, lineNo, t.nestedAt..t.nestedAt)
                }
                is TBad -> mine += SimIssue(IssueCode.BAD_CHAR, lineNo, t.cols, line.substring(t.cols.first, t.cols.last + 1))
                is TWord -> when {
                    t.key == null -> mine += SimIssue(IssueCode.BAD_CHAR, lineNo, t.cols, line.substring(t.cols.first, t.cols.last + 1))
                    t.num == null -> mine += SimIssue(IssueCode.NO_NUMBER, lineNo, t.cols, t.key)
                    else -> raws += t
                }
                is TSep, is TClose -> {}
            }
        }

        // O 는 새 프로그램 단위
        val oWord = raws.firstOrNull { it.key == "O" }
        if (oWord != null) {
            val num = oWord.num!!
            val digits = num.count { it.isDigit() }
            when {
                '.' in num -> mine += SimIssue(IssueCode.DECIMAL_NOT_ALLOWED, lineNo, oWord.cols, "O$num")
                num.startsWith("-") || num.startsWith("+") -> mine += SimIssue(IssueCode.BAD_VALUE, lineNo, oWord.cols, "O$num")
                digits > 8 -> mine += SimIssue(IssueCode.NUMBER_TOO_LONG, lineNo, oWord.cols, "O$num")
                else -> units += UnitB(num.toInt(), lineNo, oWord.cols)
            }
        }
        val unit = units.last()
        val gWords = raws.filter { it.key == "G" && '.' !in it.num!! && it.num.firstOrNull()?.isDigit() == true }
        // 10자리 넘는 G 는 Int 를 넘는다 — 아래에서 NUMBER_TOO_LONG 으로 알리고 여기서는 뺀다(4단계 무작위 시험: 'G9999999999' 가 예외)
        val gs = gWords.mapNotNull { it.num!!.toIntOrNull() }
        val motionG = gs.lastOrNull { group(it) == 1 }
        val hasPQ = raws.any { it.key == "P" || it.key == "Q" }
        // G73 첫 줄(P · Q 없는 줄)의 R 은 패스 수(S 4-106) — 소수점 없는 수를 ×0.001 하지 않는다. 소수점도 받는다(FF 4.2.3 주: 반올림해 씀 — 실행기가)
        val g73Count = 73 in gs && !hasPQ
        val intKeys = if (g73Count) ALWAYS_INT + "R" else ALWAYS_INT

        var n: Int? = null
        val words = mutableListOf<Word>()
        val seen = mutableSetOf<String>()
        val gGroups = mutableMapOf<Int, Int>()
        var mCount = 0
        for (t in raws) {
            val key = t.key!!
            val num = t.num!!
            val word = key + num
            val point = '.' in num
            val signed = num.startsWith("-") || num.startsWith("+")
            val digits = num.count { it.isDigit() }
            if (key == "O") continue
            if (digits > 9) {
                mine += SimIssue(IssueCode.NUMBER_TOO_LONG, lineNo, t.cols, word)
                continue
            }
            if (key == "N") {
                when {
                    point -> mine += SimIssue(IssueCode.DECIMAL_NOT_ALLOWED, lineNo, t.cols, word)
                    signed -> mine += SimIssue(IssueCode.BAD_VALUE, lineNo, t.cols, word)
                    digits > 5 -> mine += SimIssue(IssueCode.NUMBER_TOO_LONG, lineNo, t.cols, word)
                    n != null -> mine += SimIssue(IssueCode.DUP_ADDRESS, lineNo, t.cols, word)
                    else -> n = num.toInt()
                }
                continue
            }
            if (key !in KNOWN) {
                val haas = when (key) {
                    // Haas 1줄 사이클(G71 … D · G73 … D · G76 … D), H 77~111
                    "D" -> gs.any { it in setOf(71, 72, 73, 76) }
                    // Haas G76 의 A(공구 각) · G01 의 A(모서리 각), H 36~38 · 111
                    "A" -> 76 in gs || motionG == 1 || (motionG == null && unit.textMotion == 1)
                    else -> false
                }
                mine += SimIssue(if (haas) IssueCode.HAAS_FORMAT else IssueCode.UNKNOWN_ADDRESS, lineNo, t.cols, word)
                continue
            }
            if (key in intKeys) {
                when {
                    key == "G" && (point || signed) -> {
                        mine += SimIssue(IssueCode.UNSUPPORTED_G, lineNo, t.cols, word)
                        continue
                    }
                    point && !(g73Count && key == "R") -> {
                        mine += SimIssue(IssueCode.DECIMAL_NOT_ALLOWED, lineNo, t.cols, word)
                        continue
                    }
                    signed -> {
                        mine += SimIssue(IssueCode.BAD_VALUE, lineNo, t.cols, word)
                        continue
                    }
                }
            }
            var v = num.toDouble()
            if (key in COORD && key !in intKeys) {
                if (!point && decimal == DecimalMode.STANDARD) {
                    v *= 0.001
                    mine += SimIssue(IssueCode.NO_POINT_COORD, lineNo, t.cols, word, listOf(Fmt.nc(v)))
                }
                if (abs(v) > MAX_MM) {
                    mine += SimIssue(IssueCode.NUMBER_TOO_BIG, lineNo, t.cols, word)
                    continue
                }
            }
            if ((key == "F" || key == "S") && v < 0) {
                mine += SimIssue(IssueCode.BAD_VALUE, lineNo, t.cols, word)
                continue
            }
            when (key) {
                "G" -> {
                    val g = v.toInt()
                    when {
                        g == 20 -> mine += SimIssue(IssueCode.INCH, lineNo, t.cols, word)
                        g == 52 || g == 53 || g in 55..59 || g == 154 -> mine += SimIssue(IssueCode.WORK_OFFSET, lineNo, t.cols, word)
                        g !in SUPPORTED_G -> mine += SimIssue(IssueCode.UNSUPPORTED_G, lineNo, t.cols, word)
                    }
                    val grp = group(g)
                    if (grp != null) {
                        if (gGroups.containsKey(grp)) mine += SimIssue(IssueCode.DUP_GROUP, lineNo, t.cols, word, listOf("G${gGroups[grp]}"))
                        else gGroups[grp] = g
                    }
                }
                "M" -> if (++mCount > 1) mine += SimIssue(IssueCode.TWO_M, lineNo, t.cols, word)
                "T" -> if (num != "0" && num != "00" && digits !in 3..4) mine += SimIssue(IssueCode.T_FORMAT, lineNo, t.cols, word)
                else -> if (!seen.add(key)) mine += SimIssue(IssueCode.DUP_ADDRESS, lineNo, t.cols, word)
            }
            words += Word(key, num, v, point, t.cols)
        }
        fun both(a: String, b: String) = words.firstOrNull { it.key == b }?.takeIf { words.any { w -> w.key == a } }
        both("X", "U")?.let { mine += SimIssue(IssueCode.ABS_INC_BOTH, lineNo, it.cols, it.text, listOf("X")) }
        both("Z", "W")?.let { mine += SimIssue(IssueCode.ABS_INC_BOTH, lineNo, it.cols, it.text, listOf("Z")) }
        both(",C", ",R")?.let { mine += SimIssue(IssueCode.CORNER_BOTH, lineNo, it.cols, it.text) }
        if (motionG != null) unit.textMotion = motionG

        issues += mine
        val comment = comments.takeIf { it.isNotEmpty() }?.joinToString(" ")
        val body = line.substring(cols.first, cols.last + 1).trim()
        // 낱말이 없어도(주석만 · 받지 않는 글만) 블록으로 둔다 — 그 블록의 STOP 문제에서 실행이 멈춘다
        if (words.isEmpty() && n == null && comment == null && mine.none { it.level == IssueLevel.STOP } && !skip) return
        unit.blocks += Block(lineNo, 0, n, skip, words, comment, body, cols)
        if (words.isNotEmpty() || n != null || mine.any { it.level == IssueLevel.STOP }) unit.content = true
    }

    // ================================================================ 글자 나누기

    private sealed interface Tok { val cols: IntRange }

    /** key null = 주소가 아닌 글자, num null = 주소 뒤 숫자 없음 */
    private class TWord(val key: String?, val num: String?, override val cols: IntRange) : Tok
    private class TComment(val text: String, val closed: Boolean, val nestedAt: Int?, override val cols: IntRange) : Tok
    private class TSep(override val cols: IntRange) : Tok
    private class TSlash(override val cols: IntRange) : Tok
    private class TBad(override val cols: IntRange) : Tok

    /** 짝 없는 `)` — 무시한다(F0 12.1 125쪽 주: 짝 control-out 없이 읽은 control-in 은 무시) */
    private class TClose(override val cols: IntRange) : Tok

    private fun lex(s: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                c == ' ' || c == '\t' -> i++
                c == '(' -> {
                    var j = i + 1
                    var nested: Int? = null
                    while (j < n && s[j] != ')') {
                        if (s[j] == '(' && nested == null) nested = j
                        j++
                    }
                    val closed = j < n
                    val end = if (closed) j else n - 1
                    out += TComment(s.substring(i + 1, if (closed) j else n), closed, nested, i..end)
                    i = end + 1
                }
                c == ')' -> {
                    out += TClose(i..i)
                    i++
                }
                c == ';' -> {
                    out += TSep(i..i)
                    i++
                }
                c == '/' -> {
                    out += TSlash(i..i)
                    i++
                }
                c == ',' -> {
                    val k = s.getOrNull(i + 1)?.uppercaseChar()
                    if (k == 'C' || k == 'R') {
                        val (num, end) = number(s, i + 2)
                        out += TWord(",$k", num, i..(end - 1))
                        i = end
                    } else {
                        out += TBad(i..i)
                        i++
                    }
                }
                c in 'A'..'Z' || c in 'a'..'z' -> {
                    val (num, end) = number(s, i + 1)
                    out += TWord(c.uppercaseChar().toString(), num, i..(end - 1))
                    i = end
                }
                c.isAsciiNumberish() -> {
                    var j = i + 1
                    while (j < n && s[j].isAsciiNumberish()) j++
                    out += TBad(i until j)
                    i = j
                }
                else -> {
                    out += TBad(i..i)
                    i++
                }
            }
        }
        return out
    }

    private fun Char.isAsciiNumberish() = this in '0'..'9' || this == '.' || this == '+' || this == '-'

    /** [+-]?(\d+\.?\d*|\.\d+) — (숫자 글, 끝 다음 자리). 없으면 (null, from) */
    private fun number(s: String, from: Int): Pair<String?, Int> {
        var k = from
        if (k < s.length && (s[k] == '+' || s[k] == '-')) k++
        var digits = 0
        var point = false
        while (k < s.length) {
            val c = s[k]
            if (c in '0'..'9') digits++
            else if (c == '.' && !point) point = true
            else break
            k++
        }
        return if (digits == 0) null to from else s.substring(from, k) to k
    }
}
