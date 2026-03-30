package cat.ri.muufin.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long,
) {
    private val timestamps = ArrayDeque<Long>()
    private val mutex = Mutex()

    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            // Prune expired timestamps
            while (timestamps.isNotEmpty() && timestamps.first() + windowMs <= now) {
                timestamps.removeFirst()
            }
            if (timestamps.size >= maxRequests) {
                val waitMs = (timestamps.first() + windowMs) - now
                if (waitMs > 0) {
                    mutex.unlock()
                    delay(waitMs)
                    mutex.lock()
                    // Re-prune after waiting
                    val newNow = System.currentTimeMillis()
                    while (timestamps.isNotEmpty() && timestamps.first() + windowMs <= newNow) {
                        timestamps.removeFirst()
                    }
                }
            }
            timestamps.addLast(System.currentTimeMillis())
        }
    }
}
