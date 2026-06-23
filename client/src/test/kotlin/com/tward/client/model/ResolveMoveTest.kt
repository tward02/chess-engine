package com.tward.client.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolveMoveTest {

    @Test
    fun `a normal move resolves to a single candidate`() {
        val legal = listOf("e2e4", "g1f3", "d2d4")
        assertEquals(listOf("e2e4"), resolveMove(legal, "e2", "e4"))
    }

    @Test
    fun `a promotion square returns every promotion candidate`() {
        val legal = listOf("e7e8q", "e7e8r", "e7e8b", "e7e8n", "a2a3")
        assertEquals(listOf("e7e8q", "e7e8r", "e7e8b", "e7e8n"), resolveMove(legal, "e7", "e8"))
    }

    @Test
    fun `no matching move returns an empty list`() {
        assertTrue(resolveMove(listOf("e2e4", "d2d4"), "e2", "e5").isEmpty())
    }

    @Test
    fun `only moves with the exact from and to are returned`() {
        // A promotion to e8 and a capture-promotion to d8 share the e7 origin; e7->e8 must not match d8.
        val legal = listOf("e7e8q", "e7d8q", "e7d8n")
        assertEquals(listOf("e7e8q"), resolveMove(legal, "e7", "e8"))
        assertEquals(listOf("e7d8q", "e7d8n"), resolveMove(legal, "e7", "d8"))
    }
}
