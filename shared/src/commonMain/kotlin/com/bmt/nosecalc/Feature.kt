package com.bmt.nosecalc

/**
 * 앱의 7기능. 메뉴(01.jpg)와 각 기능 헤더(1.4)의 글은 원본이 조금씩 달라 따로 둔다.
 * 메뉴 부제(menuSub)는 그 화면에서 하는 일을 짧게 적는다. 헤더 제목은 모두 같은 꼴(20sp 굵게)이고 색만
 * [com.bmt.nosecalc.ui.theme.AppColors.features] 로 다르다.
 */
enum class Feature(
    val menuTitle: String,
    val menuSub: String,
    val title: String,
    val sub: String,
    /** 다 만든 기능. 아니면 메뉴 카드에 '준비 중' 표시, 화면은 [com.bmt.nosecalc.ui.ComingSoonPage] */
    val ready: Boolean = false,
) {
    TAPER("테이퍼 계산", "외경 · 내경 · 홈 · 2중 테이퍼 노즈R 보정", "CNC 테이퍼 계산", "단일·복합 테이퍼 노즈R 보정", ready = true),
    CUTTING("재료·절삭조건 계산", "Vc ↔ 회전수 · 이송 · 가공 시간", "재료·절삭조건 계산", "재료 & 절삭조건 정밀계산기"),
    ROUND("라운드 R 계산", "구 · 돔 · 모서리 R 노즈R 보정", "라운드 R 계산", "구 · 돔 · 모서리 R 의 노즈R 보정", ready = true),
    SIMULATOR("G코드 시뮬레이터", "FANUC G코드 경로 확인", "G코드 시뮬레이터", "FANUC 호환 G코드 사이클 시뮬레이터"),
    TRIANGLE("직각삼각형 계산", "아는 값 둘로 변 · 각 · X/Z", "직각삼각형 계산", "아는 값 둘로 나머지 변 · 각 · X/Z", ready = true),
    POLAR("턴밀 극좌표 다각가공", "G12.1 다각가공 프로그램", "턴밀 극좌표 다각가공", "턴밀 극좌표(G12.1) 다각가공 프로그램 생성"),
    THREAD("나사 G76 계산", "G76 나사 사이클 생성", "나사 G76 계산", "G76 나사 사이클 생성 & 분석"),
    ;

    companion object {
        /** 메뉴 순서(01.jpg). 첫 화면인 테이퍼는 빠진다 */
        val menu = listOf(CUTTING, ROUND, SIMULATOR, TRIANGLE, POLAR, THREAD)
    }
}
