package com.zischase.discordbot;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;

public final class DBConnectionHandler
{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(DBConnectionHandler.class);
	private static final String URL;
	private static final String USER;
	private static final String PASS;
	
	static
	{
		URI uri = null;
		try {
			uri = new URI(Config.get("DATABASE_URL"));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		
		if (uri != null && uri.getHost() != null)
		{
			USER = uri.getUserInfo().split(":")[0];
			PASS = uri.getUserInfo().split(":")[1];
			URL = "jdbc:postgresql://" + uri.getHost() + ':' + uri.getPort() + uri.getPath();
		}
		else
		{
			USER = Config.get("DB_USER");
			PASS = Config.get("DB_PASSWORD");
			URL = Config.get("DATABASE_URL");
		}
		
		Jdbi.create(DBConnectionHandler::connect).useHandle(handle ->
			{
				handle.execute("""
						CREATE TABLE IF NOT EXISTS guilds(
							id VARCHAR(20) NOT NULL PRIMARY KEY,
							name VARCHAR NOT NULL);
						 
						CREATE TABLE IF NOT EXISTS guild_settings(
							guild_id VARCHAR NOT NULL UNIQUE,
							ispremium VARCHAR(5) NOT NULL DEFAULT 'false',
							prefix VARCHAR(1) NOT NULL DEFAULT '/',
							CONSTRAINT fk_guild_id FOREIGN KEY(guild_id) REFERENCES guilds(id)
								ON UPDATE CASCADE
								ON DELETE CASCADE);
								
						CREATE TABLE IF NOT EXISTS media_settings(
							guild_id VARCHAR NOT NULL UNIQUE,
							volume INT NOT NULL DEFAULT 10,
							CONSTRAINT chk_volume CHECK(volume BETWEEN -1 AND 101),
							CONSTRAINT fk_guild_id FOREIGN KEY(guild_id) REFERENCES guilds(id)
								ON UPDATE CASCADE
								ON DELETE CASCADE);
								
						CREATE TABLE IF NOT EXISTS playlists(
							guild_id VARCHAR NOT NULL,
							name VARCHAR NOT NULL,
							url VARCHAR NOT NULL,
							CONSTRAINT fk_guild_id FOREIGN KEY(guild_id) REFERENCES guilds(id)
								ON UPDATE CASCADE
								ON DELETE CASCADE);
						""");
			});
		
		LOGGER.info("DataBase Connection Established");
	}
	
	private DBConnectionHandler()
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
			return DriverManager.getConnection(URL, USER, PASS);
		}
		catch (Exception e)
		{
			LOGGER.warn("Error Connecting to DataBase !!");
			e.printStackTrace();
			System.exit(1);
		}
		return null;
	}
	
}