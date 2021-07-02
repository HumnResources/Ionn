package com.zischase.discordbot;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {

	public static String get(String key) {
		if (System.getenv().isEmpty() || !System.getenv().containsKey(key)) {
			return Dotenv.load().get(key);
		}

		return System.getenv(key.toUpperCase());
	}

}
