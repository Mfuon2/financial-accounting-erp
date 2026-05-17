package com.qesuite.accounting.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableAsync
class AsyncConfig {

    /**
     * Dedicated thread pool for audit log persistence.
     *
     * Isolated from the main request pool so that audit write pressure never
     * starves business-logic threads. CallerRunsPolicy is the safety net: if the
     * queue is full, the calling thread writes the log itself rather than dropping it.
     */
    @Bean("auditExecutor")
    fun auditExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize    = 2
            maxPoolSize     = 4
            queueCapacity   = 1_000
            setThreadNamePrefix("audit-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            initialize()
        }
}
