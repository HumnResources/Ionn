package com.zischase.discordbot;

import me.duncte123.botcommons.BotCommons;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.ShutdownEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.time.OffsetDateTime;

public class Bot
{
	private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);

	static
	{
		SQLConnectionHandler.getConnection();
	}
	
	public static void main(String[] args)
	{
		JDA jda = null;
		try {
			jda = JDABuilder.createDefault(Config.get("TOKEN")).build();
		} catch (LoginException e) {
			e.printStackTrace();
		}
		if (jda == null)
		{
			throw new RuntimeException();
		}

		setShutdownHook(jda);
		jda.addEventListener(new Listener(jda));
		jda.getPresence()
				.setActivity(Activity.listening(" music"));
	}

	private static void setShutdownHook(JDA jda)
	{
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			LOGGER.info("SHUTTING DOWN . . .");

			Listener listener = (Listener) (jda.getRegisteredListeners().get(0));
			listener.onShutdown(new ShutdownEvent(jda, OffsetDateTime.now(), 0));
			BotCommons.shutdown(jda);
			jda.shutdown();

			Runtime.getRuntime().halt(0);
		}));
	}
}
