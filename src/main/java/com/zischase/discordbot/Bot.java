package com.zischase.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;

import javax.security.auth.login.LoginException;

public class Bot
{
	
	static
	{
		PostgreSQL.getConnection();
	}
	
	public static void main(String[] args) throws LoginException
	{
		// DEV_TOKEN = TESTING PURPOSES ONLY
		JDA jda = JDABuilder.createDefault(Config.get("DEV_TOKEN"))
							.setActivity(Activity.watching("Starting..."))
							.addEventListeners(new Listener())
							.build();
		
		jda.getPresence()
		   .setActivity(Activity.watching("Everything"));
	}

}
