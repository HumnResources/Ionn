package com.zischase.discordbot;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

enum DBSetting
{
	VOLUME,
	PREFIX
}

public final class DataBaseManager
{
	private static final Logger LOGGER = LoggerFactory.getLogger(DataBaseManager.class);
	
	public static void update(String guildID, String setting, String value)
	{
		boolean isValidSetting = Arrays.stream(DBSetting.values())
									   .anyMatch(dbSetting -> dbSetting.toString().equalsIgnoreCase(setting));
		
		if (isValidSetting)
		{
			Jdbi.create(SQLConnectionHandler::getConnection)
				.useHandle(handle ->
						handle.execute("UPDATE guild_settings SET "+setting+" = ? WHERE guild_id = ?", value, guildID));
		}
		
		else
		{
			LOGGER.warn("Invalid setting access on database !! -- {}", setting);
		}
		
	}
	
	public static String get(String guildID, String setting)
	{
		boolean isValidSetting = Arrays.stream(DBSetting.values())
									   .anyMatch(dbSetting -> dbSetting.toString().equalsIgnoreCase(setting));
		
		if (isValidSetting)
		{
			return Jdbi.create(SQLConnectionHandler::getConnection)
					   .withHandle(handle ->
					   {
						   String r = handle.createQuery("SELECT "+setting+" FROM guild_settings WHERE guild_id = :guildID")
											.bind("guildID", guildID)
											.mapTo(String.class)
											.findFirst()
											.orElseThrow();
						   handle.close();
						   return r;
					   });
		}
		
		return null;
	}
	
}
