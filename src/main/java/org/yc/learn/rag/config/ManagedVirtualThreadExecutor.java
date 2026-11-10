package org.yc.learn.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 可自动关闭的虚拟线程执行器。
 * <p>
 * 直接用 {@link ExecutorService#close()} 会无限等待所有任务结束，大文档入库时会让进程无法退出。
 * 这里在关闭时先给任务一个有限的宽限时间，超时后强制中断。
 *
 * @author zhengyanchuang
 */
public class ManagedVirtualThreadExecutor {

    private static final Logger log = LoggerFactory.getLogger(ManagedVirtualThreadExecutor.class);

    private final String name;
    private final long shutdownGraceSeconds;
    private final ExecutorService delegate;
    private final AtomicBoolean shutDown = new AtomicBoolean(false);

    public ManagedVirtualThreadExecutor(String name, Duration shutdownGrace) {
        this.name = name;
        this.shutdownGraceSeconds = Math.max(1, shutdownGrace.toSeconds());
        this.delegate = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void execute(Runnable task) {
        if (shutDown.get()) {
            throw new IllegalStateException(name + " 执行器已关闭");
        }
        try {
            delegate.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            if (!shutDown.get()) {
                throw e;
            }
        }
    }

    public void shutdown() {
        if (!shutDown.compareAndSet(false, true)) {
            return;
        }
        delegate.shutdown();
        try {
            if (!delegate.awaitTermination(shutdownGraceSeconds, TimeUnit.SECONDS)) {
                log.warn("{} 执行器在 {} 秒内未结束，强制中断剩余任务", name, shutdownGraceSeconds);
                delegate.shutdownNow();
            }
        } catch (InterruptedException e) {
            delegate.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
