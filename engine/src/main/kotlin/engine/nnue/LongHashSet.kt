package com.tward.engine.nnue

/**
 * An open-addressing set of primitive longs: 8 bytes a key instead of a boxed `Long` in a
 * `HashSet`'s node (~50 bytes), which matters when deduplicating tens of millions of positions.
 * Not thread-safe.
 */
class LongHashSet(expected: Int = 1 shl 16) {
    private var keys = LongArray(capacityFor(expected))
    private var mask = keys.size - 1
    private var hasZero = false    // 0 marks an empty slot, so the key 0 is tracked separately

    var size = 0
        private set

    /** Adds [key]; returns false if it was already present. */
    fun add(key: Long): Boolean {
        if (key == 0L) {
            if (hasZero) return false
            hasZero = true
            size++
            return true
        }
        var i = slot(key)
        while (true) {
            val existing = keys[i]
            if (existing == 0L) break
            if (existing == key) return false
            i = (i + 1) and mask
        }
        keys[i] = key
        size++
        if (size * 2 > keys.size) grow()
        return true
    }

    operator fun contains(key: Long): Boolean {
        if (key == 0L) return hasZero
        var i = slot(key)
        while (true) {
            val existing = keys[i]
            if (existing == 0L) return false
            if (existing == key) return true
            i = (i + 1) and mask
        }
    }

    private fun slot(key: Long): Int {
        // Zobrist keys are already well mixed; the multiply guards against structured inputs.
        val h = key * -0x61c8864680b583ebL
        return (h ushr 32).toInt() and mask
    }

    private fun grow() {
        val old = keys
        keys = LongArray(old.size * 2)
        mask = keys.size - 1
        for (key in old) {
            if (key == 0L) continue
            var i = slot(key)
            while (keys[i] != 0L) i = (i + 1) and mask
            keys[i] = key
        }
    }

    private companion object {
        fun capacityFor(expected: Int): Int {
            var capacity = 16
            while (capacity < expected * 2) capacity = capacity shl 1
            return capacity
        }
    }
}
