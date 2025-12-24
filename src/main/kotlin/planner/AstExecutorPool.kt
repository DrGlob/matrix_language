package planner

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Обёртка над [ThreadPoolExecutor] с конфигурацией из [AstExecutionConfig].
 */
class AstExecutorPool(config: AstExecutionConfig) {
    val executor: ThreadPoolExecutor = ThreadPoolExecutor(
        config.corePoolSize,
        config.maxPoolSize,
        config.keepAliveMillis,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(config.queueCapacity)
    )

    fun shutdownGracefully(timeout: Long, unit: TimeUnit) {
        executor.shutdown()
        if (!executor.awaitTermination(timeout, unit)) {
            executor.shutdownNow()
        }
    }

    fun shutdownNow() {
        executor.shutdownNow()
    }
}
