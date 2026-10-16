package com.bmt.nosecalc.calc

import kotlin.test.Test
import kotlin.test.assertEquals

class NoseRTest {
    // 현장 값: 45° 면취에서 0.4R → 0.234, 0.8R → 0.468 (반경값)
    @Test
    fun chamfer45() {
        assertEquals(0.234, NoseR.taperDeltaZ(0.4, 45.0), 0.001)
        assertEquals(0.468, NoseR.taperDeltaZ(0.8, 45.0), 0.001)
        assertEquals(0.468, NoseR.taperDeltaXDia(0.4, 45.0), 0.001)
    }
}
