package vn.esstar.ainovel.data.translate.llm

import kotlinx.coroutines.delay

/**
 * Shares one provider quota between what the user is waiting for and background prefetch.
 * Background work yields while an interactive request is recent or the provider just rate-limited us,
 * so a reader tapping "Dịch bằng AI" is not starved by the Wi-Fi prefetch worker.
 */
class LlmThrottle(private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var interactiveUntil = 0L
    @Volatile private var cooldownUntil = 0L

    /** Called when the user is waiting on a request; background callers back off for [INTERACTIVE_WINDOW_MS]. */
    fun markInteractive() {
        interactiveUntil = clock() + INTERACTIVE_WINDOW_MS
    }

    /** Called on HTTP 429 with the wait the provider asked for. */
    fun onRateLimited(waitMs: Long) {
        cooldownUntil = maxOf(cooldownUntil, clock() + waitMs.coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS))
    }

    val isCoolingDown: Boolean get() = clock() < cooldownUntil

    /** Suspends a background caller until no interactive window or cooldown is active (bounded wait). */
    suspend fun awaitBackgroundSlot() {
        val deadline = clock() + MAX_BACKGROUND_WAIT_MS
        while (clock() < deadline) {
            val now = clock()
            val until = maxOf(interactiveUntil, cooldownUntil)
            if (now >= until) return
            delay(minOf(until - now, POLL_MS).coerceAtLeast(100))
        }
    }

    companion object {
        const val INTERACTIVE_WINDOW_MS = 30_000L
        const val MIN_COOLDOWN_MS = 5_000L
        const val MAX_COOLDOWN_MS = 90_000L
        const val MAX_BACKGROUND_WAIT_MS = 5 * 60_000L
        const val POLL_MS = 1_000L
    }
}
