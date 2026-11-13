package com.bmt.nosecalc.screens.sim

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * G코드 편집기의 글 · 커서 · 선택 · 되돌리기(설계 7.1). 넣는 글은 늘 대문자(소문자 키보드 · 붙여넣기도), 줄 끝은 \n 하나.
 * 선택 = [anchor] ~ [cursor](같으면 없음). 글을 바꾸는 일마다 되돌리기 한 걸음(최대 [UNDO_MAX]).
 * 쓰는 줄([writingLine] — 설계 20): 고치는 중인 줄은 떠날 때까지 문제를 찾지 않는다. 문제 찾기는 [checkedText] 로 한다.
 */
@Stable
internal class EditorState(text: String = "", cursor: Int = 0) {
    var text by mutableStateOf(clean(text))
        private set
    var cursor by mutableIntStateOf(cursor.coerceIn(0, this.text.length))
        private set
    var anchor: Int? by mutableStateOf(null)
        private set

    private class Snap(val text: String, val cursor: Int, val anchor: Int?)

    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()
    private var history by mutableIntStateOf(0) // 쌓임이 바뀐 것을 화면에 알림

    val canUndo: Boolean get() = history >= 0 && undoStack.isNotEmpty()
    val canRedo: Boolean get() = history >= 0 && redoStack.isNotEmpty()

    // ---- 쓰는 줄(설계 20 — 쓰는 중인 줄에는 오류를 띄우지 않는다)

    /** 쓰는 줄(0부터): 커서가 있고 들어온 뒤 고친 줄 — 떠날 때까지 그 줄의 문제를 보이지 않는다. 없으면 null */
    var writingLine: Int? by mutableStateOf(null)
        private set

    /** 검사 글: 쓰는 줄만 고치기 전 그대로인 글(쓰는 줄이 없으면 [text] 와 같다). 문제 찾기(실행)는 이 글로 한다 */
    var checkedText: String by mutableStateOf(this.text)
        private set

    /** 검사 글이 바뀐 횟수 — 화면은 이 값이 바뀌면 0.3초 뒤 다시 돈다 */
    var checkGen: Int by mutableIntStateOf(0)
        private set

    /** 막 끝낸 줄들(0부터): 그 줄을 넣은 새 결과가 화면에 오를 때까지([ran]) 옛 결과의 그 줄 문제를 가린다 */
    private var settling: Set<Int> by mutableStateOf(emptySet())

    /** 줄을 끝냄(단추 · 키패드 닫기 · 실행 · 저장 — 화면이 부른다). 쓰는 줄이 없으면 아무것도 하지 않는다 */
    fun finishLine() {
        if (writingLine != null) settle(text)
    }

    /** 붙여넣기: 한 줄 안에 붙여도 바로 검사한다 */
    fun paste(s: String) {
        insert(s)
        finishLine()
    }

    /** 검사 글 gen 번의 결과가 화면에 올랐다 — 지금 검사 글의 결과면 가림을 푼다(낡은 번호면 그대로) */
    fun ran(gen: Int) {
        if (gen == checkGen) settling = emptySet()
    }

    /** 그 줄(0부터)의 문제를 지금 보이지 않나 */
    fun hides(line: Int): Boolean = line == writingLine || line in settling

    /** 쓰는 줄이나 막 끝낸 줄이 글의 마지막 줄인가(뒤는 빈 줄 · 빈칸뿐) — 그동안은 글이 덜 끝나서 붙는 문제도 가린다(설계 21.6) */
    val atTextEnd: Boolean
        get() = (settling + listOfNotNull(writingLine)).any { l -> (lineEnd(l) until text.length).all { text[it].isWhitespace() } }

    /** 줄을 끝냄: 검사 글을 now 로. 고친 것이 있으면 번호를 올리고 쓰던 줄은 새 결과가 올 때까지 가린다 */
    private fun settle(now: String) {
        val l = writingLine
        writingLine = null
        if (checkedText == now) return // 쳤다가 도로 지움 — 화면의 결과가 그대로 맞다
        checkedText = now
        checkGen++
        if (l != null) settling = settling + l
    }

    /** 글이 바뀐 뒤. old = 바뀌기 전 글, at = 바뀐 자리, removed · added = 빠진 글 · 들어온 글 */
    private fun edited(old: String, at: Int, removed: CharSequence, added: CharSequence) {
        if (removed.isEmpty() && added.isEmpty()) return moved()
        if ('\n' in added || added.isEmpty() && '\n' in removed) return settle(text) // 줄이 나뉘거나 합쳐짐 = 줄 끝
        // 여러 줄을 고른 채 친 글자: 지운 데까지만 검사하고(줄 합침 = 줄 끝) 친 글자는 아래에서 쓰는 줄이 된다
        if ('\n' in removed) settle(text.removeRange(at, at + added.length))
        val line = lineOf(at) // 앞쪽 글이 같아 옛 글에서도 같은 줄
        if (writingLine != null && writingLine != line) settle(old) // 되돌리기 · 다시하기가 다른 줄을 고침 — 쓰던 줄은 끝
        writingLine = line // 검사 글은 그대로(고치기 전 글)
        // EOB · 글이 비면(다 지움 — 빈 편집기에 옛 결과를 남기지 않는다) 줄 끝 · (되돌리기가 커서를 다른 줄에 두면 줄 끝)
        if (';' in added || text.isBlank()) settle(text) else moved()
    }

    /** 커서 · 고른 범위가 쓰는 줄을 벗어나면 줄 끝 */
    private fun moved() {
        val l = writingLine ?: return
        if (lineOf(cursor) != l || anchor?.let { lineOf(it) != l } == true) settle(text)
    }

    /** 선택 범위(끝 제외). 없으면 null */
    val selection: IntRange?
        get() = anchor?.takeIf { it != cursor }?.let { minOf(it, cursor) until maxOf(it, cursor) }

    // ---- 줄
    private var startsFor: String? = null
    private var starts = intArrayOf(0)

    /** 줄마다 시작 자리 */
    fun lineStarts(): IntArray {
        val t = text
        if (startsFor !== t) {
            val out = ArrayList<Int>()
            out += 0
            t.forEachIndexed { i, ch -> if (ch == '\n') out += i + 1 }
            starts = out.toIntArray()
            startsFor = t
        }
        return starts
    }

    val lineCount: Int get() = lineStarts().size

    /** 자리 → 줄(0부터) */
    fun lineOf(pos: Int): Int {
        val s = lineStarts()
        var lo = 0
        var hi = s.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (s[mid] <= pos) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun lineStart(line: Int): Int = lineStarts()[line.coerceIn(0, lineCount - 1)]

    fun lineEnd(line: Int): Int {
        val s = lineStarts()
        return if (line + 1 < s.size) s[line + 1] - 1 else text.length
    }

    fun lineText(line: Int): String = text.substring(lineStart(line), lineEnd(line))

    val cursorLine: Int get() = lineOf(cursor)

    // ---- 바꾸기

    /** 커서 자리에 넣기(선택이 있으면 바꿈). 대문자로, \r 은 뺌 */
    fun insert(s: String) {
        val add = clean(s)
        val sel = selection
        if (add.isEmpty() && sel == null) return
        push()
        val a = sel?.first ?: cursor
        val b = sel?.let { it.last + 1 } ?: cursor
        val old = text
        text = old.substring(0, a) + add + old.substring(b)
        cursor = a + add.length
        anchor = null
        edited(old, a, old.subSequence(a, b), add)
    }

    fun newline() = insert("\n")

    /** ⌫: 선택이 있으면 지우고, 없으면 커서 앞 한 글자 */
    fun backspace() {
        val sel = selection
        if (sel != null) return deleteRange(sel.first, sel.last + 1)
        if (cursor == 0) return
        deleteRange(cursor - 1, cursor)
    }

    /** Delete 키: 커서 뒤 한 글자 */
    fun deleteForward() {
        val sel = selection
        if (sel != null) return deleteRange(sel.first, sel.last + 1)
        if (cursor >= text.length) return
        deleteRange(cursor, cursor + 1)
    }

    private fun deleteRange(a: Int, b: Int) {
        push()
        val old = text
        text = old.substring(0, a) + old.substring(b)
        cursor = a
        anchor = null
        edited(old, a, old.subSequence(a, b), "")
    }

    /** 새 글(불러오기 · 새 프로그램) — 되돌리기 쌓임을 비운다. 쓰는 줄 없이 바로 검사한다 */
    fun replaceAll(s: String, cursorAt: Int = 0) {
        text = clean(s)
        cursor = cursorAt.coerceIn(0, text.length)
        anchor = null
        undoStack.clear()
        redoStack.clear()
        history++
        writingLine = null
        settling = emptySet()
        checkedText = text
        checkGen++
    }

    fun undo() {
        val s = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(Snap(text, cursor, anchor))
        restore(s)
    }

    fun redo() {
        val s = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(Snap(text, cursor, anchor))
        restore(s)
    }

    private fun restore(s: Snap) {
        val old = text
        text = s.text
        cursor = s.cursor.coerceIn(0, text.length)
        anchor = s.anchor?.coerceIn(0, text.length)
        history++
        // 바뀐 토막 = 앞 · 뒤의 같은 글을 뺀 가운데(가운데에 줄바꿈이 없으면 한 줄 안에서만 바뀐 것)
        val new = text
        val max = minOf(old.length, new.length)
        var p = 0
        while (p < max && old[p] == new[p]) p++
        var e = 0
        while (e < max - p && old[old.length - 1 - e] == new[new.length - 1 - e]) e++
        edited(old, p, old.subSequence(p, old.length - e), new.subSequence(p, new.length - e))
    }

    private fun push() {
        undoStack.addLast(Snap(text, cursor, anchor))
        while (undoStack.size > UNDO_MAX) undoStack.removeFirst()
        redoStack.clear()
        history++
    }

    // ---- 커서 · 선택

    /** extend = 선택을 늘림(Shift + 화살표 · 끌기) */
    fun moveTo(pos: Int, extend: Boolean = false) {
        val p = pos.coerceIn(0, text.length)
        if (extend) {
            if (anchor == null) anchor = cursor
        } else anchor = null
        cursor = p
        moved()
    }

    fun select(range: IntRange) {
        val a = range.first.coerceIn(0, text.length)
        val b = (range.last + 1).coerceIn(a, text.length)
        anchor = a
        cursor = b
        moved()
    }

    fun selectAll() = select(0 until text.length)

    /** 그 자리의 낱말(주소 + 숫자 · 주석)을 고름 — 길게 누르기 */
    fun selectWordAt(pos: Int) {
        val p = pos.coerceIn(0, text.length)
        fun part(c: Char) = c != ' ' && c != '\n' && c != '\t'
        var a = p
        var b = p
        while (a > 0 && part(text[a - 1])) a--
        while (b < text.length && part(text[b])) b++
        if (a == b) moveTo(p) else select(a until b)
    }

    fun left(extend: Boolean = false) {
        val sel = selection
        if (sel != null && !extend) return moveTo(sel.first)
        moveTo(cursor - 1, extend)
    }

    fun right(extend: Boolean = false) {
        val sel = selection
        if (sel != null && !extend) return moveTo(sel.last + 1)
        moveTo(cursor + 1, extend)
    }

    /** 위 · 아래 줄의 같은 칸(짧으면 그 줄 끝). 갈 줄이 없어도(첫 줄의 위 · 끝 줄의 아래) 쓰던 줄은 끝낸다 */
    fun vertical(delta: Int, extend: Boolean = false) {
        val line = cursorLine
        val col = cursor - lineStart(line)
        val to = (line + delta).coerceIn(0, lineCount - 1)
        if (to == line) {
            moveTo(if (delta < 0) 0 else text.length, extend)
            return finishLine()
        }
        moveTo(minOf(lineStart(to) + col, lineEnd(to)), extend)
    }

    fun home(extend: Boolean = false) = moveTo(lineStart(cursorLine), extend)

    fun end(extend: Boolean = false) = moveTo(lineEnd(cursorLine), extend)

    fun selectedText(): String? = selection?.let { text.substring(it.first, it.last + 1) }

    /** 찾기: from 에서 앞(forward) 또는 뒤로, 끝에 닿으면 처음부터(돌아서). 대소문자는 가리지 않는다 */
    fun find(query: String, from: Int, forward: Boolean = true): IntRange? {
        if (query.isEmpty()) return null
        val t = text
        val i = if (forward) {
            t.indexOf(query, from.coerceIn(0, t.length), ignoreCase = true).takeIf { it >= 0 }
                ?: t.indexOf(query, 0, ignoreCase = true).takeIf { it >= 0 }
        } else {
            t.lastIndexOf(query, (from - 1).coerceAtLeast(-1), ignoreCase = true).takeIf { it >= 0 && from > 0 }
                ?: t.lastIndexOf(query, ignoreCase = true).takeIf { it >= 0 }
        }
        return i?.let { it until it + query.length }
    }

    companion object {
        const val UNDO_MAX = 200

        /**
         * 편집기 글: 대문자, 줄 끝 \n 하나. `\n\r\r`(줄마다 LF 뒤 CR 둘을 붙이는 편집기가 만든 글)도 줄 끝 하나 — CR 을 줄 끝으로 바꾸면
         * 줄마다 빈 줄 둘이 생긴다
         */
        fun clean(s: String): String = s.replace("\n\r\r", "\n").replace("\r\n", "\n").replace('\r', '\n').uppercase()
    }
}

/** CNC 키패드의 키 하나가 하는 일 */
internal sealed interface CncKey {
    /** 글자 · SHIFT 글자 · 숫자 · - . , 빈칸 */
    data class Text(val s: String) : CncKey
    data object Back : CncKey
    data object Enter : CncKey
}

/** SHIFT: 꺼짐 · 한 글자만(다음 글자 뒤 꺼짐) · 잠금(길게 누름) */
internal enum class ShiftState { OFF, ONCE, LOCK }

/** 키 색 종류(09): 글자 · 숫자 · ⌫ · 노랑 기호(, - .) · SP · ⏎ */
internal enum class KeyTone { LETTER, DIGIT, BACK, PUNCT, SPACE, ENTER }

/** 키 하나: 큰 글자 [main], 오른쪽 위 작은 SHIFT 글자 [shift] */
internal data class PadKey(val main: String, val shift: String?, val tone: KeyTone)

/**
 * 7열 × 4행(09 ④). SHIFT 글자는 원본 P · A · C · Q 에서 A(지원 안 하는 주소)를 빼고 I · K · L · ( · ) · / 를 더했다(설계 7.2 —
 * 원호 I/K, M98 L, 주석, 블록 스킵을 칠 수 있게).
 */
internal val CncRows: List<List<PadKey>> = listOf(
    listOf(PadKey("O", "(", KeyTone.LETTER), PadKey("N", ")", KeyTone.LETTER), PadKey("G", "/", KeyTone.LETTER),
        PadKey("7", null, KeyTone.DIGIT), PadKey("8", null, KeyTone.DIGIT), PadKey("9", null, KeyTone.DIGIT), PadKey("⌫", null, KeyTone.BACK)),
    listOf(PadKey("X", "I", KeyTone.LETTER), PadKey("Z", "K", KeyTone.LETTER), PadKey("S", "L", KeyTone.LETTER),
        PadKey("4", null, KeyTone.DIGIT), PadKey("5", null, KeyTone.DIGIT), PadKey("6", null, KeyTone.DIGIT), PadKey(",", null, KeyTone.PUNCT)),
    listOf(PadKey("M", null, KeyTone.LETTER), PadKey("U", null, KeyTone.LETTER), PadKey("W", "P", KeyTone.LETTER),
        PadKey("1", null, KeyTone.DIGIT), PadKey("2", null, KeyTone.DIGIT), PadKey("3", null, KeyTone.DIGIT), PadKey("SP", null, KeyTone.SPACE)),
    listOf(PadKey("F", null, KeyTone.LETTER), PadKey("T", "C", KeyTone.LETTER), PadKey("R", "Q", KeyTone.LETTER),
        PadKey("-", null, KeyTone.PUNCT), PadKey("0", null, KeyTone.DIGIT), PadKey(".", null, KeyTone.PUNCT), PadKey("⏎", null, KeyTone.ENTER)),
)

/** 키를 누르면 무엇이 들어가고 SHIFT 가 어떻게 되나. ONCE 는 글을 넣는 키 하나 뒤 꺼진다(⌫ · ⏎ 는 그대로) */
internal fun pressKey(k: PadKey, shift: ShiftState): Pair<CncKey, ShiftState> {
    val key = when (k.tone) {
        KeyTone.BACK -> CncKey.Back
        KeyTone.ENTER -> CncKey.Enter
        KeyTone.SPACE -> CncKey.Text(" ")
        else -> CncKey.Text(if (shift != ShiftState.OFF && k.shift != null) k.shift else k.main)
    }
    val next = if (key is CncKey.Text && shift == ShiftState.ONCE) ShiftState.OFF else shift
    return key to next
}

/** SHIFT 단추: 누름 = 꺼짐 ↔ 한 글자, 길게 = 잠금(잠금에서 누르면 꺼짐) */
internal fun nextShift(s: ShiftState, long: Boolean): ShiftState = when {
    long -> if (s == ShiftState.LOCK) ShiftState.OFF else ShiftState.LOCK
    s == ShiftState.OFF -> ShiftState.ONCE
    else -> ShiftState.OFF
}
