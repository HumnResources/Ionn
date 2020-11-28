package com.zischase.discordbot;

import io.github.cdimascio.dotenv.Dotenv;

public class Config
{
	
	private static final Dotenv dotEnv = Dotenv.load();
	
	public static String get(String key)
	{
		return System.getenv().get(key.toUpperCase());
	}
	
}
