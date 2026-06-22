package com.tward.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveMoveTest {

    @Test
    fun `resolves a normal move from the legal list`() {
        val legal = listOf("e2e4", "g1f3", "d2d4")
        assertEquals("e2e4", resolveMove(legal, "e2", "e4"))
    }

    @Test
    fun `prefers the queen promotion when several promotions share the squares`() {
        val legal = listOf("e7e8r", "e7e8b", "e7e8q", "e7e8n")
        assertEquals("e7e8q", resolveMove(legal, "e7", "e8"))
    }

    @Test
    fun `returns null when no legal move matches`() {
        assertNull(resolveMove(listOf("e2e4", "d2d4"), "e2", "e5"))
    }
}
