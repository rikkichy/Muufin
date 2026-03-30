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
        while (true) {
            val waitMs = mutex.withLock {
                val now = System.currentTimeMillis()
                while (timestamps.isNotEmpty() && timestamps.first() + windowMs <= now) {
                    timestamps.removeFirst()
                }
                if (timestamps.size < maxRequests) {
                    timestamps.addLast(now)
                    return
                }
                (timestamps.first() + windowMs) - now
            }
            if (waitMs > 0) delay(waitMs)
        }
    }
}
