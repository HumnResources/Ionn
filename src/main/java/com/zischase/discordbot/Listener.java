package com.zischase.discordbot;

import com.zischase.discordbot.commands.CommandManager;
import com.zischase.discordbot.commands.general.Prefix;
import com.zischase.discordbot.guildcontrol.GuildManager;
import me.duncte123.botcommons.BotCommons;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Listener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static ThreadPoolExecutor EXECUTOR;

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        LOGGER.info("{} is ready", event.getJDA().getSelfUser().getAsTag());
        event.getJDA()
                .getGuilds()
                .forEach(GuildManager::setGuild);

        int POOL_COUNT = GuildManager.getGuildCount() * (CommandManager.getCommandCount() / 4);
        Executor THREAD_POOL = Executors.newFixedThreadPool(POOL_COUNT);
        EXECUTOR = (ThreadPoolExecutor) THREAD_POOL;

        LOGGER.info("Guild Count: " + GuildManager.getGuildCount() + " - Thread Pool: " + EXECUTOR.getMaximumPoolSize());
    }

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage())
            return;

        String prefix = new Prefix().getPrefix(event.getGuild());
        String raw = event.getMessage().getContentRaw();

        if (event.getAuthor().getId().equals(Config.get("OWNER_ID"))) {
            if (raw.equalsIgnoreCase(prefix + "shutdown")) {
                LOGGER.info("Shutting down...");
                shutdown(event);
                return;
            }
            else if (raw.equalsIgnoreCase(prefix + "restart")) {
                try {
                    Runtime.getRuntime().exec("cmd /c start powershell.exe java -jar discordbot-" + Config.get("VERSION") + ".jar");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                shutdown(event);
                return;
            }
        }

        if (raw.startsWith(prefix))
            execute(event);
    }

    public static ThreadPoolExecutor getExecutor() {
        return EXECUTOR;
    }

    private void execute(GuildMessageReceivedEvent event) {
        new CompletableFuture<Void>().completeAsync(() -> {
            GuildManager.getContext(event.getGuild())
                    .getCommandManager()
                    .invoke(event);
            return null;
        }, EXECUTOR);
    }

    protected static void shutdown(GuildMessageReceivedEvent event) {
        LOGGER.info("Shutting down...\n" +
                "Tasks completed: " + EXECUTOR.getCompletedTaskCount() + "\n" +
                "Active command accesses: " + EXECUTOR.getActiveCount() + "\n" +
                "Terminating....");
        EXECUTOR.shutdown();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!EXECUTOR.isShutdown()) {
            EXECUTOR.shutdownNow();
        }
        BotCommons.shutdown(event.getJDA());
        event.getJDA().shutdown();
    }
}
