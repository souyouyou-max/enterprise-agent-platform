package com.sinosig.aip.common.core.util;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * RetryUtils - 通用指数退避重试工具
 * <p>
 * 统一替代散落在各处的重试循环（BaseAgent、ToolExecutionService、SubTaskAnalysisService 等）。
 * <p>
 * 退避公式：第 n 次重试前等待 {@code initialDelayMs * 2^(n-1)} 毫秒。
 * 例如 initialDelayMs=500：第 1 次等 500ms，第 2 次等 1000ms，第 3 次等 2000ms …
 */
@Slf4j
public final class RetryUtils {

    private RetryUtils() {}

    /**
     * 带指数退避的重试执行。
     *
     * @param action         要执行的操作（抛出异常视为失败）
     * @param maxAttempts    最大尝试次数（含首次，必须 >= 1）
     * @param initialDelayMs 首次重试前的等待毫秒数
     * @param tag            日志标识（如 "[Executor]工具调用"）
     * @param <T>            返回值类型
     * @return 操作成功时的返回值
     * @throws RuntimeException 达到最大次数后仍失败，包装原始异常
     */
    public static <T> T withExponentialBackoff(Supplier<T> action,
                                               int maxAttempts,
                                               long initialDelayMs,
                                               String tag) {
        int safeMax = Math.max(1, maxAttempts);
        Exception lastException = null;
        for (int attempt = 1; attempt <= safeMax; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < safeMax) {
                    long delay = initialDelayMs * (1L << (attempt - 1));
                    log.warn("{} 第 {}/{} 次失败，{}ms 后重试: {}", tag, attempt, safeMax, delay, e.getMessage());
                    sleep(delay);
                } else {
                    log.error("{} 第 {}/{} 次失败，已达最大重试次数: {}", tag, attempt, safeMax, e.getMessage());
                }
            }
        }
        throw new RuntimeException(tag + " 失败，已重试 " + safeMax + " 次", lastException);
    }

    /**
     * 无返回值版本（Runnable）。
     */
    public static void withExponentialBackoff(Runnable action,
                                              int maxAttempts,
                                              long initialDelayMs,
                                              String tag) {
        withExponentialBackoff(() -> {
            action.run();
            return null;
        }, maxAttempts, initialDelayMs, tag);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("重试等待被中断", ie);
        }
    }
}
