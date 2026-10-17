package com.bmt.nosecalc

import androidx.compose.ui.text.font.FontWeight

/**
 * 앱의 7기능. 메뉴(01.jpg)와 각 기능 헤더(1.4)의 글은 원본이 조금씩 달라 따로 둔다.
 * 헤더 제목 색은 [com.bmt.nosecalc.ui.theme.AppColors.features].
 */
enum class Feature(
    val menuTitle: String,
    val menuSub: String,
    val title: String,
    val sub: String,
    /** 헤더 제목 최대 크기(sp)·굵기·자간(em) — 원본 헤더마다 다르다. 폭이 모자라면 줄여 한 줄(CUTTING 은 두 줄)에 맞춘다 */
    val titleSize: Int,
    val titleWeight: FontWeight,
    val titleTracking: Float = 0f,
    /** 0 이상이면 제목의 이 글자부터 강조색(시뮬레이터: "시뮬레이터" 만 파랑). -1 이면 제목 전체를 그라데이션 */
    val accentFrom: Int = -1,
) {
    TAPER("테이퍼 계산", "단일/복합 테이퍼 계산", "CNC 테이퍼 계산", "단일·복합 테이퍼 노즈R 보정", 21, FontWeight.Bold, -0.01f),
    CUTTING("재료·절삭조건 계산", "재료 & 절삭조건 계산기", "재료·절삭조건 계산", "재료 & 절삭조건 정밀계산기", 21, FontWeight.Bold),
    ROUND("라운드 R 계산", "복합 다중 라운드 계산", "라운드 R 계산", "구 · 돔 · 모서리 R 의 노즈R 보정", 23, FontWeight.Black, -0.02f),
    SIMULATOR(
        "G코드 시뮬레이터", "FANUC 호환 그래픽 시뮬레이터",
        "G코드 시뮬레이터", "FANUC 호환 G코드 사이클 시뮬레이터", 21, FontWeight.Bold, accentFrom = 4,
    ),
    TRIANGLE("직각삼각형 계산", "직각 삼각형 계산", "직각삼각형 계산", "아는 값 둘로 나머지 변 · 각 · X/Z", 21, FontWeight.Bold),
    POLAR(
        "턴밀 극좌표 다각가공", "턴밀 극좌표(G12.1) 다각가공 프로그램 생성",
        "턴밀 극좌표 다각가공", "턴밀 극좌표(G12.1) 다각가공 프로그램 생성", 20, FontWeight.Bold,
    ),
    THREAD(
        "나사 G76 계산", "G76 나사 복합 사이클 생성 & 분석",
        "나사 G76 계산", "G76 나사 사이클 생성 & 분석", 21, FontWeight.Bold,
    ),
    ;

    companion object {
        /** 메뉴 순서(01.jpg). 첫 화면인 테이퍼는 빠진다 */
        val menu = listOf(CUTTING, ROUND, SIMULATOR, TRIANGLE, POLAR, THREAD)
    }
}
