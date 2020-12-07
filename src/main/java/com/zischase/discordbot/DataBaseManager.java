package com.zischase.discordbot;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


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
		
		Jdbi.create(SQLConnectionHandler::getConnection)
				.useHandle(handle ->
				{
					if (checkTable(handle, "guild_settings", setting))
					{
						handle.execute("UPDATE guild_settings SET " + setting + " = ? WHERE guild_id = ?", value, guildID);
					}
					else if (checkTable(handle, "media_settings", setting))
					{
						handle.execute("UPDATE media_settings SET " + setting + " = ? WHERE guild_id = ?", value, guildID);
					}
//					handle.close();
//					return null;
				});
			
	}
	
	public static String get(String guildID, String setting)
	{
		return Jdbi.create(SQLConnectionHandler::getConnection)
				   .withHandle(handle ->
				   {
				   		String r = "";
				   		if (checkTable(handle, "guild_settings", setting))
						{
							r = handle.createQuery("SELECT "+setting+" FROM guild_settings WHERE guild_id = :guildID")
									  .bind("guildID", guildID)
									  .mapTo(String.class)
									  .findFirst()
									  .orElse("");
						}
				   		else if (checkTable(handle, "media_settings", setting))
						{
							r = handle.createQuery("SELECT "+setting+" FROM media_settings WHERE guild_id = :guildID")
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
		List<String> settings = handle.createQuery( /* Language=PostgreSQL */
				"SELECT column_name" +
						" FROM information_schema.columns" +
						" WHERE table_schema = 'public'" +
						" AND table_name     = '"+table+"'")
									  .mapTo(String.class)
									  .list();
		
		return settings.contains(setting);
	}
}
