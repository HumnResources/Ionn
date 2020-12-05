package com.zischase.discordbot;

import io.github.cdimascio.dotenv.Dotenv;

public class Config
{
	
	private static final Dotenv dotEnv = Dotenv.load();
	
	public static String get(String key)
	{
		if (System.getenv().isEmpty() || ! System.getenv().containsKey(key))
			return dotEnv.get(key);

		return System.getenv(key.toUpperCase());
	}
	
}
