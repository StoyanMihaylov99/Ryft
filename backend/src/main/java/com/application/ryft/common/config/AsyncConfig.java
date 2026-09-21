package com.application.ryft.common.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Backs every {@code @Async("domainEventExecutor")} listener in {@code common.event} — the activity
 * log and notification fan-out run off this pool, never the request thread, so a slow/failing
 * downstream write can't add latency to (or fail) the REST call that triggered it. Sized small
 * (core 2 / max 4) since this is a single-instance portfolio deployment, not a high-throughput system.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("domainEventExecutor")
    public Executor domainEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("domain-event-");
        executor.initialize();
        return executor;
    }
}
