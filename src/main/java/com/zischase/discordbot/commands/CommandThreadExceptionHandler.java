package com.zischase.discordbot.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandThreadExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandThreadExceptionHandler.class);

    @Override
    public void uncaughtException(Thread t, Throwable e) {

        LOGGER.warn(e.getLocalizedMessage());

    }
}
