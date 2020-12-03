package com.zischase.discordbot;

import com.zischase.discordbot.commands.CommandManager;
import me.duncte123.botcommons.BotCommons;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;

public class Bot
{
	private static JDA jda = null;
	private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);
	
	static
	{
		Runtime.getRuntime()
				.addShutdownHook(new Thread(() -> {

					LOGGER.warn("SHUTTING DOWN . . .");

					CommandManager.shutdown();
					BotCommons.shutdown(jda);
					jda.shutdownNow();

					LOGGER.info("Successful Shutdown");
				}));
		//Operating system sends SIGFPE to the JVM
		//the JVM catches it and constructs a
		//ArithmeticException class, and since you
		//don't catch this with a try/catch, dumps
		//it to screen and terminates.  The shutdown
		//hook is triggered, doing final cleanup.

		SQLConnectionHandler.getConnection();
		try
		{
			jda = JDABuilder.createDefault(Config.get("TOKEN"))
				.setActivity(Activity.watching("Starting..."))
				.addEventListeners(new Listener())
				.build();
			// DEV_TOKEN = TESTING PURPOSES ONLY
			

		}
		catch (LoginException e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public static void main(String[] args)
	{
		jda.getPresence()
				.setActivity(Activity.listening("For Music"));
	}
	
	public static int guildCount()
	{
		return jda.getGuilds()
				  .size();
	}
}
