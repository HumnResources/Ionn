package com.zischase.discordbot;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;

public final class SQLConnectionHandler
{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SQLConnectionHandler.class);
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

		if (uri != null)
		{
			USER = uri.getUserInfo().split(":")[0];
			PASS = uri.getUserInfo().split(":")[1];
			URL = "jdbc:postgresql://" + uri.getHost() + ':' + uri.getPort() + uri.getPath();
		}
		else
		{
			USER = null;
			PASS = null;
			URL = null;
		}
		
		Jdbi.create(SQLConnectionHandler::connect)
			.useHandle(handle ->
			{
				handle.execute(
						/* language=PostgreSQL */
						" CREATE TABLE IF NOT EXISTS guild_settings( " +
								"id SERIAL PRIMARY KEY," +
								"guild_id VARCHAR(20) NOT NULL," +
								"ispremium VARCHAR(10) NOT NULL DEFAULT 'false'," +
								"prefix VARCHAR(255) NOT NULL DEFAULT '"+Config.get("DEFAULT_PREFIX")+"')");
				handle.execute(
						/* language=PostgreSQL */
						" CREATE TABLE IF NOT EXISTS media_settings( " +
								"id SERIAL PRIMARY KEY," +
								"guild_id VARCHAR(20) NOT NULL," +
								"volume VARCHAR NOT NULL DEFAULT '"+Config.get("DEFAULT_VOLUME")+"')");
			});
		
		LOGGER.info("Connection established!");
	}
	
	private SQLConnectionHandler()
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
//			System.exit(1);
		}
		return null;
	}
	
}