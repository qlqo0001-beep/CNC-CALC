package com.bmt.nosecalc.screens.cutting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bmt.nosecalc.calc.CutWarning
import com.bmt.nosecalc.calc.Cutting
import com.bmt.nosecalc.calc.FeedFrom
import com.bmt.nosecalc.calc.SpeedFrom
import com.bmt.nosecalc.ui.AppCard
import com.bmt.nosecalc.ui.CardHeader
import com.bmt.nosecalc.ui.NumberField
import com.bmt.nosecalc.ui.WarnLevel
import com.bmt.nosecalc.ui.WarningBanner
import com.bmt.nosecalc.ui.keepWords
import com.bmt.nosecalc.ui.theme.AppTheme
import com.bmt.nosecalc.ui.theme.AppType
import com.bmt.nosecalc.ui.theme.Space

/**
 * 기본계산기 탭: 회전수 · 절삭 속도(Vc ↔ n) → 이송(f · fz × Z → vf) → 가공 시간 → 이론 거칠기 → G96 회전수 제한.
 * 칸을 바꾸면 모든 줄이 바로 다시 계산된다(계산 단추 없음). Vc · n 과 f · fz 는 넣은 쪽이 기준이고 다른 칸은 자동 값(청록 · '자동').
 * 빈칸인 줄은 결과를 비우고 무엇을 넣으면 되는지 적는다.
 */
@Composable
internal fun ColumnScope.BasicTab(s: CuttingState) {
    val c = AppTheme.colors
    val input = s.input()
    val r = remember(input) { Cutting.calc(input) }
    val v = basicView(s, r)
    val preview = basicPreview(s, v)

    @Composable
    fun field(b: CutBox, modifier: Modifier, autoValue: String? = null, placeholder: String = "", hint: String? = null) = NumberField(
        b.label, s.value(b), { s.set(b, it) }, modifier,
        unit = b.unit, placeholder = placeholder, autoValue = autoValue, error = b.cut in r.bad, hint = hint, id = b.key, preview = preview,
    )

    @Composable
    fun note(text: String) = Text(keepWords(text), Modifier.padding(top = 8.dp), style = AppType.caption, color = c.textMuted)

    if (CutWarning.BAD_INPUT in r.warnings) WarningBanner(cutWarningText(CutWarning.BAD_INPUT, s, r), level = WarnLevel.Error)

    // 회전수 · 절삭 속도
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("회전수 · 절삭 속도", "Vc = π × D × n / 1000")
        Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
            field(CutBox.D, Modifier.fillMaxWidth(), hint = "선반은 깎는 지름, 드릴은 드릴 지름")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                field(CutBox.VC, Modifier.weight(1f), autoValue = v.vcAuto, hint = "넣으면 회전수 n 을 계산합니다(n 칸은 비움)")
                field(CutBox.N, Modifier.weight(1f), autoValue = v.nAuto, hint = "넣으면 절삭 속도 Vc 를 계산합니다(Vc 칸은 비움)")
            }
        }
        note(
            when {
                v.vcAuto != null || v.nAuto != null -> "Vc 와 n 중 넣은 쪽이 기준이고, 다른 칸(청록 '자동')은 계산한 값입니다."
                s.num(CutBox.D) == null -> "지름 D 와 Vc(또는 회전수 n)를 넣으면 다른 하나를 계산합니다."
                else -> "Vc 나 회전수 n 중 하나를 넣으면 다른 하나를 계산합니다."
            },
        )
    }

    // 이송
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("이송", "vf = f × n  ·  드릴 f = fz × Z")
        Column(verticalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
            field(CutBox.F, Modifier.fillMaxWidth(), autoValue = v.fAuto, hint = "넣으면 날당 이송 fz 칸은 비웁니다")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
                field(CutBox.FZ, Modifier.weight(1.4f), hint = "드릴 · 날이 여럿인 공구 — 넣으면 f = fz × 날 수")
                field(CutBox.Z, Modifier.weight(1f), placeholder = "기본 ${Cutting.TEETH_DEFAULT}", hint = "빈칸이면 ${Cutting.TEETH_DEFAULT}날(드릴 날 둘)")
            }
        }
        Spacer(Modifier.height(14.dp))
        ValueCell(
            "분당 이송 vf", v.vf, "mm/min", Modifier.fillMaxWidth(),
            sub = when {
                v.vf != null && s.feedFrom == FeedFrom.FZ -> "f ${v.fAuto} mm/rev (fz × ${r.z}날) × n"
                v.vf != null -> "f × n"
                else -> "이송 f(또는 fz)와 회전수가 있으면"
            },
        )
    }

    // 가공 시간
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("가공 시간", "T = L / vf (회전수 일정)")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap), verticalAlignment = Alignment.Bottom) {
            field(CutBox.LENGTH, Modifier.weight(1f), hint = "이송 방향으로 깎는 길이(들어가고 나오는 거리 포함)")
            ValueCell(
                "가공 시간", v.timeMin, "분", Modifier.weight(1f).padding(bottom = 2.dp),
                sub = v.timeSec?.let { "$it 초" } ?: "길이와 분당 이송이 있으면",
            )
        }
        if (s.speedFrom == SpeedFrom.VC && r.g96 != null) note("G96(주속 일정)으로 지름이 바뀌며 깎으면 회전수가 달라져 이 시간과 다릅니다.")
    }

    // 이론 거칠기
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("이론 거칠기 (선삭)", "Rmax = f² / (8 r) · Ra = f² / (18√3 · r)")
        field(CutBox.NOSE_R, Modifier.fillMaxWidth(), hint = "인서트 노즈 R(카탈로그 값, 0.4 · 0.8 …)")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.fieldGap)) {
            ValueCell("최대 높이 Rmax", v.rz, "μm", Modifier.weight(1f))
            ValueCell("산술 평균 Ra", v.ra, "μm", Modifier.weight(1f))
        }
        if (CutWarning.FEED_TOO_BIG_FOR_NOSE in r.warnings) {
            Spacer(Modifier.height(10.dp))
            WarningBanner(cutWarningText(CutWarning.FEED_TOO_BIG_FOR_NOSE, s, r), level = WarnLevel.Warn)
        }
        note(
            v.practical?.let { "$it (KORLOY 카탈로그: 실제는 이론값의 강 1.5~3배 · 주철 3~5배). 이론값 — 마모 · 진동 · 구성인선은 넣지 않습니다." }
                ?: "이송 f 와 노즈 R 을 넣으면 계산합니다. 이론값 — 실제 거칠기는 더 큽니다.",
        )
    }

    // G96 회전수 제한
    AppCard(Modifier.fillMaxWidth(), padding = Space.cardPad) {
        CardHeader("G96 주속 일정 · G50 S 제한", "FANUC: G50 S 를 넘는 회전수는 S 로 막힌다")
        field(CutBox.SMAX, Modifier.fillMaxWidth(), placeholder = "안 씀", hint = "G96 앞에 쓰는 G50 S(최대 회전수)")
        if (CutWarning.RPM_CLAMPED in r.warnings) {
            Spacer(Modifier.height(10.dp))
            WarningBanner(cutWarningText(CutWarning.RPM_CLAMPED, s, r), level = WarnLevel.Warn)
        }
        if (v.g96.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                v.g96.forEach { Text(keepWords(it), style = AppType.body, color = c.textBody) }
            }
        } else {
            note(
                if (s.num(CutBox.SMAX) != null) "절삭 속도 Vc(또는 지름과 회전수)가 있으면 어느 지름부터 막히는지 계산합니다."
                else "비워 두면 쓰지 않습니다. 넣으면 Vc 로 어느 지름부터 회전수가 막히는지 계산합니다(NC 줄은 만들지 않음).",
            )
        }
    }
}
