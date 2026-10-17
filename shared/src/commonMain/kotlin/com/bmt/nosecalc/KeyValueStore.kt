package com.bmt.nosecalc

/** 작은 설정 저장(테마 등). 안드로이드는 SharedPreferences, PC 는 사용자 홈의 설정 파일 */
expect class KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}
