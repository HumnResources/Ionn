package com.zischase.discordbot.commands;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CommandThreadHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandThreadHandler.class);
    private final ThreadPoolExecutor poolExecutor;
    private final JDA jda;


    public CommandThreadHandler(JDA jda) {

        this.jda = jda;

        int defaultPoolCount = Integer.parseInt(Config.get("DEFAULT_COMMAND_THREADS"));
        int POOL_COUNT = jda.getGuilds().size() * 2;

        if (POOL_COUNT > defaultPoolCount)
        {
            poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(POOL_COUNT);
        }
        else
        {
            poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(defaultPoolCount);
        }

        poolExecutor.setThreadFactory(new CommandThreadFactory(this));
        poolExecutor.setKeepAliveTime(30000, TimeUnit.MILLISECONDS);
    }

    public void asyncCommand(GuildMessageReceivedEvent event)
    {
        int activeThreads = poolExecutor.getActiveCount();

        Runnable runnable = () -> {
            GuildManager.getContext(event.getGuild())
                    .commandManager()
                    .invoke(event);
            LOGGER.info("{} | {}",event.getGuild().getName(), event.getMessage().getContentDisplay());
        };

        poolExecutor.setCorePoolSize(activeThreads + 1);
        poolExecutor.execute(runnable);


    }


    public void shutdown() {

        LOGGER.info("Terminating Command Threads");

        if (poolExecutor != null && ! poolExecutor.isTerminating())
        {
            LOGGER.info("""
    
				======================
				Active Tasks 	  : {}
				Completed Tasks   : {}
				----	 		  ----
				Current Pool Size : {}
				Total Thread Pool : {}
				======================
				
				""", poolExecutor.getActiveCount(),
                    poolExecutor.getCompletedTaskCount(),
                    poolExecutor.getPoolSize(),
                    poolExecutor.getMaximumPoolSize());

            poolExecutor.shutdown();

            try {
                poolExecutor.awaitTermination(2000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (! poolExecutor.isShutdown())
            {
                LOGGER.warn("Shutdown Took Too Long! Terminating Now...");
                poolExecutor.shutdownNow();
                return;
            }
        }

        LOGGER.info("Successfully Closed Threads");
    }

    public int getActiveThreadCount()
    {
        return poolExecutor.getActiveCount();
    }
}
