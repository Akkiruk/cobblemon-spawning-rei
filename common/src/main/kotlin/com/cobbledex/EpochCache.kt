package com.cobbledex

import java.util.concurrent.ConcurrentHashMap

/**
 * A per-key memo that is thrown away wholesale whenever its [epoch] changes (a new data version, a
 * language switch), safe to hit from any thread.
 *
 * The recipe viewers call into the same caches from different threads at the same time - EMI runs
 * plugin registration on its own reload thread while the client tick warms category sizes - which a
 * plain map checked, cleared and filled by separate steps can't survive. Here each epoch gets its
 * own immutable-identity [Slot], so a stale epoch is dropped by swapping one reference rather than
 * by clearing a map another thread is reading, and every value is computed at most once: a second
 * caller for the same key waits for the first instead of repeating a build that can take over a
 * second. A computation that throws is not remembered, so it can be retried.
 */
class EpochCache<E : Any, V : Any> {

    private class Slot<E, V>(val epoch: E) {
        val values = ConcurrentHashMap<String, Lazy<V>>()
        val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }

    @Volatile private var slot: Slot<E, V>? = null

    private fun slotFor(epoch: E): Slot<E, V> {
        slot?.takeIf { it.epoch == epoch }?.let { return it }
        return synchronized(this) {
            slot?.takeIf { it.epoch == epoch } ?: Slot<E, V>(epoch).also { slot = it }
        }
    }

    fun get(epoch: E, key: String, compute: () -> V): V {
        val slot = slotFor(epoch)
        val values = slot.values
        val entry = values.computeIfAbsent(key) {
            lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
                slot.inFlight.add(key)
                try { compute() } finally { slot.inFlight.remove(key) }
            }
        }
        try {
            return entry.value
        } catch (t: Throwable) {
            // Forget the failed entry itself (not whatever a racing caller may have put there since),
            // so the next caller runs its own computation rather than this one's again.
            values.remove(key, entry)
            throw t
        }
    }

    /** The value for [key] only if it has already been computed for [epoch]; never computes one. */
    fun peek(epoch: E, key: String): V? {
        val lazyValue = slot?.takeIf { it.epoch == epoch }?.values?.get(key) ?: return null
        return if (lazyValue.isInitialized()) lazyValue.value else null
    }

    /**
     * True while some thread is in the middle of computing [key] for [epoch]. Lets a caller that
     * must not stall (the game thread) go do something else instead of waiting on someone else's
     * build.
     */
    fun isComputing(epoch: E, key: String): Boolean =
        slot?.takeIf { it.epoch == epoch }?.inFlight?.contains(key) == true

    fun clear() {
        slot = null
    }
}
