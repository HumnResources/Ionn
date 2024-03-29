package com.zischase.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public final class DBQueryHandler
{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(DBQueryHandler.class);
	
	/* Using generic object to pass data types to database - This means you must know the sata type being used however */
	public static void set(String guildID, DBQuery setting, Object value)
	{
		if (setting.toString().matches("(?i)(premium|id|guild_id|is_valid|expiry_date)"))
		{
			try
			{
				throw new IllegalAccessException();
			} catch (IllegalAccessException e)
			{
				LOGGER.warn("Illegal Access Attempt on DataBase!!");
				e.printStackTrace();
				return;
			}
		}
		
		Jdbi.create(DBConnectionHandler::getConnection)
				.useHandle(handle ->
				{
					if (tableContainsColumn(handle, DBQuery.GUILD_SETTINGS, setting))
					{
						handle.createUpdate(
										"UPDATE guild_settings SET <setting> = :value WHERE guild_id = :guildID")
								.define("setting", setting)
								.bind("value", value)
								.bind("guildID", guildID)
								.execute();
					}
					else if (tableContainsColumn(handle, DBQuery.MEDIA_SETTINGS, setting))
					{
						handle.createUpdate(
										"UPDATE media_settings SET <setting> = :value WHERE guild_id = :guildID")
								.define("setting", setting)
								.bind("value", value)
								.bind("guildID", guildID)
								.execute();
					}
				});
	}
	
	private static boolean tableContainsColumn(Handle handle, DBQuery table, DBQuery setting)
	{
		/* Language=PostgreSQL */
		List<String> l = handle.createQuery(
						"SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = :table")
				.bind("table", table.toString().toLowerCase())
				.mapTo(String.class)
				.list();
		
		return l.contains(setting.toString().toLowerCase());
	}
	
	public static void set(String guildID, DBQuery table, DBQuery setting, Object value)
	{
		if (setting.toString().matches("(?i)(premium|id|guild_id|is_valid|expiry_date)"))
		{
			try
			{
				throw new IllegalAccessException();
			} catch (IllegalAccessException e)
			{
				LOGGER.warn("Illegal Access Attempt on DataBase!!");
				e.printStackTrace();
				return;
			}
		}
		
		Jdbi.create(DBConnectionHandler::getConnection)
				.useHandle(handle ->
				{
					if (tableContainsColumn(handle, table, setting))
					{
						handle.createUpdate(
										"UPDATE <table> SET <setting> = :value WHERE guild_id = :guildID")
								.define("table", table)
								.define("setting", setting)
								.bind("value", value)
								.bind("guildID", guildID)
								.execute();
					}
				});
	}
	
	public static String get(String guildID, DBQuery table, DBQuery setting)
	{
		return Jdbi.create(DBConnectionHandler::getConnection)
				.withHandle(handle ->
				{
					String r = "";
					if (tableContainsColumn(handle, table, setting))
					{
						r = handle.createQuery(
										"SELECT <setting> FROM <table> WHERE guild_id = :id")
								.define("setting", setting)
								.define("table", table)
								.bind("id", guildID)
								.mapTo(String.class)
								.findFirst()
								.orElse("");
					}
					handle.close();
					return r;
				});
	}
	
	public static List<String> getList(String guildID, DBQuery table, DBQuery setting)
	{
		return Jdbi.create(DBConnectionHandler::getConnection)
				.withHandle(handle ->
				{
					List<String> r = List.of();
					if (tableContainsColumn(handle, table, setting))
					{
						r = handle.createQuery(
										"SELECT <setting> FROM <table> WHERE guild_id = :id")
								.define("setting", setting)
								.define("table", table)
								.bind("id", guildID)
								.mapTo(String.class)
								.list();
					}
					handle.close();
					return r;
				});
	}
	
	public static void addPlaylistEntry(String guildID, String name, String uri)
	{
		Jdbi.create(DBConnectionHandler::getConnection).useHandle((h) ->
				h.execute("INSERT INTO playlists VALUES (?, ?, ?)", guildID, name, uri));
	}
	
	public static void deletePlaylistEntry(String id, String name)
	{
		Jdbi.create(DBConnectionHandler::getConnection).useHandle(handle ->
				handle.execute("DELETE FROM playlists WHERE guild_id = ? AND name = ?", id, name));
	}
	
	public static String getPlaylist(String guildID, String name)
	{
		return Jdbi.create(DBConnectionHandler::getConnection).withHandle((h) ->
		{
			String r = h.createQuery("SELECT url FROM playlists WHERE guild_id = ? AND name = ?")
					.bind(0, guildID)
					.bind(1, name)
					.mapTo(String.class)
					.first();
			h.close();
			return r;
		});
	}
	
	public static void addGuild(Guild guild)
	{
		boolean add = DBQueryHandler.get(guild.getId(), DBQuery.PREFIX).isEmpty();
		if (add)
		{
			Jdbi.create(DBConnectionHandler::getConnection)
					.useHandle(handle ->
							handle.createUpdate("""
											INSERT INTO guilds(id, name) VALUES (:guildID, :name);
											INSERT INTO media_settings(guild_id) VALUES (:guildID);
											INSERT INTO guild_settings(guild_id) VALUES (:guildID);
											""")
									.bind("name", guild.getName())
									.bind("guildID", guild.getId())
									.execute());
		}
	}
	
	public static String get(String guildID, DBQuery settingQuery)
	{
		String setting = settingQuery.toString().toLowerCase();
		return Jdbi.create(DBConnectionHandler::getConnection)
				.withHandle(handle ->
				{
					String r = "";
					if (tableContainsColumn(handle, DBQuery.GUILD_SETTINGS, settingQuery))
					{
						r = handle.createQuery(
										"SELECT <setting> FROM guild_settings WHERE guild_id = :id")
								.define("setting", setting)
								.bind("id", guildID)
								.mapTo(String.class)
								.findFirst()
								.orElse("");
					}
					else if (tableContainsColumn(handle, DBQuery.MEDIA_SETTINGS, settingQuery))
					{
						r = handle.createQuery(
										"SELECT <setting> FROM media_settings WHERE guild_id = :guildID")
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
	
	public static boolean getPremiumStatus(String guildID)
	{
		return Jdbi.create(DBConnectionHandler::getConnection).withHandle((h) ->
		{
			boolean r = h.createQuery("SELECT is_valid FROM premium_status WHERE guild_id = :id")
					.bind("id", guildID)
					.mapTo(boolean.class)
					.first();
			h.close();
			return r;
		});
	}
}
