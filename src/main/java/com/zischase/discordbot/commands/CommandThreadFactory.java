package com.zischase.discordbot.commands;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;

public class CommandThreadFactory implements ThreadFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandThreadFactory.class);
    private final Thread.UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER = new CommandThreadExceptionHandler();

    private int threadCount = 0;

    public CommandThreadFactory() {
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread t = new Thread(r, r.toString());
        t.setUncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER);
        return t;
    }
}
