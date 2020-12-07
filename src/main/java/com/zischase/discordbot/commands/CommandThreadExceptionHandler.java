package com.zischase.discordbot.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandThreadExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandThreadExceptionHandler.class);

    @Override
    public void uncaughtException(Thread t, Throwable e) {

        switch (e.getClass().getSimpleName()) {
            case "SocketTimeoutException" -> LOGGER.warn(t.getName() + "Encountered Socket Error - Terminating");
            case "ConnectTimeoutException" -> LOGGER.warn(t.getName() + "Encountered Connection Error - Terminating");
            case "FriendlyException" -> LOGGER.warn(t.getName() + "Encountered Friendly Exception - Terminating");
        }
    }
}
