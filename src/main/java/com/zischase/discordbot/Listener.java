package com.zischase.discordbot;

import com.zischase.discordbot.commands.CommandManager;
import com.zischase.discordbot.commands.general.Prefix;
import com.zischase.discordbot.guildcontrol.GuildContext;
import me.duncte123.botcommons.BotCommons;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class Listener extends ListenerAdapter
{
	private static final Logger LOGGER = LoggerFactory.getLogger(Listener.class);
	
	public Listener()
	{
	
	}
	
	@Override
	public void onReady(@Nonnull ReadyEvent event)
	{
		event.getJDA()
			 .getGuilds()
			 .forEach(GuildContext::new);
		
		LOGGER.info("{} is ready", event.getJDA()
										.getSelfUser()
										.getAsTag());

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			LOGGER.warn("SHUTTING DOWN . . .");

			CommandManager.shutdown();
			BotCommons.shutdown(event.getJDA());
			event.getJDA().shutdownNow();

			LOGGER.info("Successful Shutdown");
		}));
		//Operating system sends SIGFPE to the JVM
		//the JVM catches it and constructs a
		//ArithmeticException class, and since you
		//don't catch this with a try/catch, dumps
		//it to screen and terminates.  The shutdown
		//hook is triggered, doing final cleanup.
	}
	
	@Override
	public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event)
	{
		if (event.getAuthor().isBot() || event.isWebhookMessage())
		{
			return;
		}
		
		String prefix = Prefix.getPrefix(event.getGuild());
		String raw = event.getMessage()
						  .getContentRaw();
		
		if (event.getAuthor().getId().equals(Config.get("OWNER_ID")))
		{
			if (raw.equalsIgnoreCase(prefix + "shutdown"))
			{
				LOGGER.info("Shutting down...");
				shutdown(event.getJDA());
				return;
			}
//			else if (raw.equalsIgnoreCase(prefix + "restart"))
//			{
//				LOGGER.info("Restarting...");
//				restart(event);
//				return;
//			}
			else if (raw.equalsIgnoreCase(prefix + "threadreport"))
			{
				event.getChannel()
					 .sendMessage(CommandManager.getReport())
					 .queue();
				return;
			}
		}
		if (raw.startsWith(prefix))
		{
			CommandManager.invoke(event);
		}
	}
	
//	private static void restart(GuildMessageReceivedEvent event)
//	{
//		shutdown(event.getJDA());
//
//		try
//		{
//			Runtime.getRuntime().exec("java -jar discordbot-" + Config.get("VERSION") + ".jar");
//		}
//		catch (IOException e)
//		{
//			e.printStackTrace();
//		}
//
//	}
	
	private static void shutdown(JDA jda)
	{
		CommandManager.shutdown();
		
		BotCommons.shutdown(jda);
		jda.shutdown();
	}
}