package com.zischase.discordbot;

import com.sun.tools.javac.Main;
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

public class Listener extends ListenerAdapter
{
	private static final Logger LOGGER   = LoggerFactory.getLogger(Main.class);
	
	public Listener()
	{
	
	}
	
	@Override
	public void onReady(@Nonnull ReadyEvent event)
	{
		event.getJDA()
			.getGuilds()
			.forEach(GuildManager::setGuild);
		
		LOGGER.info("{} is ready", event.getJDA().getSelfUser().getAsTag());
		
		LOGGER.info("Guild Count: " + GuildManager.getGuildCount() + " - Max Thread Pool: " + CommandManager.getThreadPoolExecutor().getMaximumPoolSize());
	}
	
	@Override
	public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event)
	{
		if (event.getAuthor().isBot() || event.isWebhookMessage())
			return;
		
		String prefix = Prefix.getPrefix(event.getGuild());
		String raw    = event.getMessage().getContentRaw();
		
		if (event.getAuthor().getId().equals(Config.get("OWNER_ID")))
		{
			if (raw.equalsIgnoreCase(prefix + "shutdown"))
			{
				LOGGER.info("Shutting down...");
				shutdown(event);
				return;
			}
			else if (raw.equalsIgnoreCase(prefix + "restart"))
			{
				try
				{
					Runtime.getRuntime()
							.exec("cmd /c start powershell.exe java -jar discordbot-" + Config.get("VERSION") + ".jar");
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				shutdown(event);
				return;
			}
			else if (raw.equalsIgnoreCase(prefix + "threadreport"))
			{
				event.getChannel()
						.sendMessage(CommandManager.getReport())
						.queue();
			}
		}
		
		if (raw.startsWith(prefix))
			CommandManager.invoke(event);
	}

	private static void shutdown(GuildMessageReceivedEvent event)
	{
		CommandManager.shutdown(event);
		
		if (! CommandManager.getThreadPoolExecutor().isShutdown())
		{
			CommandManager.getThreadPoolExecutor().shutdownNow();
		}
		
		BotCommons.shutdown(event.getJDA());
		event.getJDA().shutdown();
	}
}