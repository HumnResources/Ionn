package com.zischase.discordbot;

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

public class Listener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        LOGGER.info("{} is ready", event.getJDA().getSelfUser().getAsTag());
        event.getJDA().getGuilds().forEach(GuildManager::setGuild);
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
                event.getJDA().shutdown();
                BotCommons.shutdown(event.getJDA());
            }
            else if (raw.equalsIgnoreCase(prefix + "restart")) {

                try {
                    Runtime.getRuntime().exec("java -jar discordbot-" + Config.get("VERSION") + ".jar");
                    event.getJDA().shutdown();
                    BotCommons.shutdown(event.getJDA());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (raw.startsWith(prefix))
            GuildManager.getContext(event.getGuild()).getCommandManager().execute(event);
    }
}
