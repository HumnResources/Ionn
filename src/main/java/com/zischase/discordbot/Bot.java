package com.zischase.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;

import javax.security.auth.login.LoginException;

public class Bot
{
	private static JDA jda = null;
	
	static
	{
		PostgreSQL.getConnection();
		
		try
		{
			// DEV_TOKEN = TESTING PURPOSES ONLY
			
			jda = JDABuilder.createDefault(Config.get("TOKEN"))
							.setActivity(Activity.watching("Starting..."))
							.addEventListeners(new Listener())
							.build();
		}
		catch (LoginException e)
		{
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws LoginException
	{
		jda.getPresence()
		   .setActivity(Activity.watching("Everything"));
	}
	
	public static int guildCount()
	{
		return jda.getGuilds()
				  .size();
	}
}
