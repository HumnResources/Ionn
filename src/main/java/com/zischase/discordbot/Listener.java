package com.zischase.discordbot;

import com.zischase.discordbot.commands.CommandThreadHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.OffsetDateTime;

public class Listener extends ListenerAdapter
{
	private static final Logger               LOGGER = LoggerFactory.getLogger(Listener.class);
	private final        CommandThreadHandler commandThreadHandler;

	public Listener(JDA jda)
	{

		this.commandThreadHandler = new CommandThreadHandler(jda);

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
	}
	
	
	
	@Override
	public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event)
	{
		if (event.getAuthor().isBot() || event.isWebhookMessage())
		{
			return;
		}

		String prefix = DatabaseHandler.get(event.getGuild().getId(), "prefix");
		
		if (prefix == null) {
			return;
		}
		
		String raw = event.getMessage()
						  .getContentRaw();
		
		if (event.getAuthor().getId().equals(Config.get("OWNER_ID")))
		{
			if (raw.equalsIgnoreCase(prefix + "shutdown"))
			{
				LOGGER.info("Shutting down...");
				onShutdown(new ShutdownEvent(event.getJDA(), OffsetDateTime.now(), 0));
				return;
			}
		}
		if (raw.startsWith(prefix))
		{
			this.commandThreadHandler.asyncCommand(event);
		}
	}

	@Override
	public void onShutdown(@NotNull ShutdownEvent event) {
		this.commandThreadHandler.shutdown();
	}
}