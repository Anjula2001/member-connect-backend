package com.memberconnect.backend.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Thread pool for outbound member notifications.
 *
 * Sending an email is a network call to a third party, and it was previously made on
 * the request thread: marking a retirement request INCOMPLETE waited for the whole
 * Gmail conversation before the browser saw a response. On a slow link the TCP connect
 * alone measured 9.5 seconds, so the frontend's 15s axios timeout fired while the
 * status change had in fact already been committed - the worst kind of failure, one
 * that reports an error for work that succeeded.
 *
 * Moving the SMTP call here decouples the two: the status change commits, the HTTP
 * response returns, and the email goes out behind it.
 *
 * Bounded on purpose. The default SimpleAsyncTaskExecutor starts a new thread per task
 * and never refuses work, which against an unresponsive SMTP server would accumulate
 * threads indefinitely. Two threads are enough for a notification volume measured in
 * tens per day, and CallerRunsPolicy means a full queue degrades to the old synchronous
 * behaviour - slow - rather than silently dropping a member's notification.
 */
@Configuration
@EnableAsync
public class AsyncNotificationConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    @Bean(NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Let an in-flight send finish on shutdown rather than cutting the connection
        // mid-message, which Gmail would otherwise see as an aborted transaction.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
