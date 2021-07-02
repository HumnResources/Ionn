package com.zischase.discordbot.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;

public class CommandThreadExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandThreadExceptionHandler.class);

    @Override
    public void uncaughtException(Thread t, Throwable e) {

        if (e.getMessage() != null)
        {
            LOGGER.warn("\n"+e.getMessage());
        }
        else if (e.getCause().getMessage() != null)
        {
            LOGGER.warn("\n"+e.getCause().getMessage());
        }
        else if (e.getLocalizedMessage() != null)
        {
            LOGGER.warn("\n"+e.getLocalizedMessage());
        }

        if (e instanceof SocketTimeoutException)
        {
            LOGGER.warn("Error connecting to url/websocket");
        }

    }
}