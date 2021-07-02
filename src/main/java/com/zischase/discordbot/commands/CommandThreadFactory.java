package com.zischase.discordbot.commands;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

public class CommandThreadFactory implements ThreadFactory {
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
    private final Thread.UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER = new CommandThreadExceptionHandler();
    private final ThreadPoolExecutor poolExecutor;

    public CommandThreadFactory(ThreadPoolExecutor poolExecutor) {
        this.poolExecutor = poolExecutor;
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread t = new Thread(r, "Command Pool | Thread-" + poolExecutor.getActiveCount() + 1);
        t.setUncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER);
        return t;
    }
}
