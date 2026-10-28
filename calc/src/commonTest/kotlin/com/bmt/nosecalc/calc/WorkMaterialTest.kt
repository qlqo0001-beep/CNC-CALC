package com.bmt.nosecalc.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkMaterialTest {
    @Test
    fun sixGroupsInIsoOrder() {
        assertEquals(IsoGroup.entries.toList(), WorkMaterials.groups.map { it.group })
        for (g in WorkMaterials.groups) {
            assertTrue(g.title.isNotBlank() && g.covers.isNotBlank() && g.machining.isNotBlank(), "${g.group}")
            assertTrue(g.source.contains("Group ${g.group}"), g.source)
            assertTrue(g.examples.isNotEmpty(), "${g.group} 예가 없다")
            for (e in g.examples) {
                assertTrue(e.source.isNotBlank(), "${e.ks} 출처")
                // KS 대조가 있는 예는 KORLOY 대조표에서, 없는 예는 그 까닭을 적는다
                if (e.ks != null) assertTrue(e.source.startsWith("KORLOY Turning 2025-2026 F"), e.source)
                else assertTrue(e.note?.contains("KS 대조 없음") == true, "${e.name}")
            }
        }
    }

    /** 대조표(KORLOY Turning 2025-2026 F3~F6)에서 옮긴 대표 줄 — 쪽 그림과 글자층으로 확인한 값 */
    @Test
    fun crossReferenceRows() {
        fun row(ks: String) = WorkMaterials.groups.flatMap { g -> g.examples.map { g.group to it } }.single { it.second.ks == ks }
        val (g45, sm45c) = row("SM45C")
        assertEquals(IsoGroup.P, g45)
        assertEquals("S45C", sm45c.jis); assertEquals("1045, 1046", sm45c.us); assertEquals("C45, C45E4, C45M2", sm45c.iso)
        assertEquals("4140(H), 4142(H)", row("SCM440(H)").second.us)
        assertEquals("42CrMo4, 42CrMoS4", row("SCM440(H)").second.iso)
        val (gs, sts304) = row("STS304")
        assertEquals(IsoGroup.M, gs)
        assertEquals("S30400, 304", sts304.us); assertEquals("SUS304", sts304.jis)
        assertEquals("S31603, 316L", row("STS316L").second.us)
        assertEquals(IsoGroup.K, row("GCD400-15").first)
        assertEquals("60-40-18", row("GCD400-15").second.us)
        assertEquals(IsoGroup.N, row("A6061S").first)
        assertEquals("ADC12", row("ALDC8").second.jis)
        val (gh, std11) = row("STD11")
        assertEquals(IsoGroup.H, gh)
        assertEquals("SKD11", std11.jis); assertEquals("D2", std11.us)
        assertEquals("H13", row("STD61").second.us)
        assertEquals(WorkMaterials.of(IsoGroup.S).group, IsoGroup.S)
    }
}
