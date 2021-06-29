package com.zischase.discordbot;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class DataBaseManager
{
	private static final Logger LOGGER = LoggerFactory.getLogger(DataBaseManager.class);
	
	public static void update(String guildID, String setting, String value)
	{
		if (setting.matches("(?i)(premium|id|guild_id)"))
		{
			try
			{
				throw new IllegalAccessException();
			}
			catch (IllegalAccessException e)
			{
				LOGGER.warn("Illegal Access Attempt on DataBase!!");
				e.printStackTrace();
				return;
			}
		}
		
		Jdbi.create(SQLConnectionHandler::getConnection).useHandle(handle ->
				{
					if (checkTable(handle, "guild_settings", setting))
					{
						handle.createUpdate("UPDATE guild_settings SET <setting> = :value WHERE guild_id = :guildID")
							  .define("setting", setting)
							  .bind("value", value)
							  .bind("guildID", guildID)
							  .execute();
					}
					else if (checkTable(handle, "media_settings", setting))
					{
						handle.createUpdate("UPDATE media_settings SET <setting> = :value WHERE guild_id = :guildID")
							  .define("setting", setting)
							  .bind("value", value)
							  .bind("guildID", guildID)
							  .execute();
					}
				});
	}
	
	public static String get(String guildID, String setting)
	{
		return Jdbi.create(SQLConnectionHandler::getConnection).withHandle(handle ->
				   {
				   		String r = "";
				   		if (checkTable(handle, "guild_settings", setting))
						{
							r = handle.createQuery("SELECT <setting> FROM guild_settings WHERE guild_id = :id")
									  .define("setting", setting)
									  .bind("id", guildID)
									  .mapTo(String.class)
									  .findFirst()
									  .orElse("");
						}
				   		else if (checkTable(handle, "media_settings", setting))
						{
							r = handle.createQuery("SELECT <setting> FROM media_settings WHERE guild_id = :guildID")
									  .define("setting", setting)
									  .bind("guildID", guildID)
									  .mapTo(String.class)
									  .findFirst()
									  .orElse("");
						}
				   	
					   handle.close();
					   return r;
				   });
	}
	
	private static boolean checkTable(Handle handle, String table, String setting)
	{
		/* Language=PostgreSQL */
		return handle.createQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = :table")
			  		 .bind("table", table)
			  		 .mapTo(String.class)
			  		 .list()
					 .contains(setting);
	}
}
