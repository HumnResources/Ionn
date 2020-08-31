package com.zischase.discordbot;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;

public class PostgreSQL
{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PostgreSQL.class);
	private static final String url;
	private static final String user;
	private static final String pass;
	
	static
	{
		url = Config.get("DB_URL");
		user = Config.get("DB_USER");
		pass = Config.get("DB_PASSWORD");
		
		Jdbi.create(PostgreSQL::connect)
			.useHandle(handle -> handle.execute(
					/* language=PostgreSQL */
					" CREATE TABLE IF NOT EXISTS guild_settings( " + "id SERIAL PRIMARY KEY," + "guild_id VARCHAR(20) NOT NULL," + "isPremium VARCHAR(10) NOT NULL DEFAULT 'false'," + "prefix VARCHAR(255) NOT NULL DEFAULT '!!'," + "volume VARCHAR(100) NOT NULL DEFAULT '10')"));
		
		LOGGER.info("Connection established!");
	}
	
	private PostgreSQL()
	{
	}
	
	public static Connection getConnection()
	{
		return connect();
	}
	
	private static Connection connect()
	{
		try
		{
			Class.forName("org.postgresql.Driver");
			return DriverManager.getConnection(url, user, pass);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
		return null;
	}
}