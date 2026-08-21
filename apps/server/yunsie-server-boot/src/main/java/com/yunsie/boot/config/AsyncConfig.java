package com.yunsie.boot.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步执行器装配（Stage 1.6 learning-profile / Stage 1.7 notify 事件消费）。
 * 最小配置：仅提供事件消费所需的线程池；
 * TaskDecorator 复制 MDC（traceId），保证异步线程日志可链路追踪（api-design）。
 * 项目禁 MQ：事件消费为进程内 @Async（CONFLICTS #18 / #21）。
 */
@Configuration
public class AsyncConfig {

    @Bean("profileSyncExecutor")
    public ThreadPoolTaskExecutor profileSyncExecutor() {
        return buildExecutor("learn-profile-sync-");
    }

    @Bean("notifyExecutor")
    public ThreadPoolTaskExecutor notifyExecutor() {
        return buildExecutor("notify-event-");
    }

    private ThreadPoolTaskExecutor buildExecutor(String namePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix(namePrefix);
        executor.setTaskDecorator(taskDecorator());
        executor.initialize();
        return executor;
    }

    /** 复制父线程 MDC 到执行线程；执行后清理（复用 TraceIdFilter 的 MDC key） */
    private TaskDecorator taskDecorator() {
        return runnable -> {
            var context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
