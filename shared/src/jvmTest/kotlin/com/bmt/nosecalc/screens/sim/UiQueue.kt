package com.bmt.nosecalc.screens.sim

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

/**
 * 헤드리스 장면(`ImageComposeScene`)의 효과 디스패처를 앱처럼 '한 UI 스레드' 로: 효과 코루틴의 재개를 줄에 쌓았다가 시험 스레드가 프레임 사이에 [drain] 으로 돌린다.
 * 장면의 처음 값(Unconfined)이면 백그라운드(Dispatchers.Default)에서 돌아온 코루틴이 그 스레드에서 바로 이어져, 시험 스레드의 render 와
 * ① 레이아웃을 같이 건드리거나(`performMeasureAndLayout called during measure layout`) ② 프레임 시계 · 효과 잠금을 거꾸로 잡아 교착된다
 * (4단계 최종 점검에서 3D 재생 중 실제로 멈췄다 — 안드로이드 · PC 앱은 한 UI 스레드라 나지 않는다).
 */
internal class UiQueue : CoroutineDispatcher() {
    private val q = ConcurrentLinkedQueue<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        q.add(block)
    }

    /** 쌓인 재개를 이 스레드에서 모두(돌리는 중 새로 쌓인 것까지) */
    fun drain() {
        while (true) (q.poll() ?: return).run()
    }
}
