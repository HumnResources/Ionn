package com.zischase.discordbot;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public final class DBQueryHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(DBQueryHandler.class);

	/* Using generic object to pass data types to database - This means you must know the sata type being used however */
	public static void set(String guildID, String setting, Object value) {
		if (setting.matches("(?i)(premium|id|guild_id)")) {
			try {
				throw new IllegalAccessException();
			} catch (IllegalAccessException e) {
				LOGGER.warn("Illegal Access Attempt on DataBase!!");
				e.printStackTrace();
				return;
			}
		}

		Jdbi.create(DBConnectionHandler::getConnection)
				.useHandle(handle ->
				{
					if (tableContainsColumn(handle,
							"guild_settings",
							setting
					)) {
						handle.createUpdate(
								"UPDATE guild_settings SET <setting> = :value WHERE guild_id = :guildID")
								.define("setting", setting)
								.bind("value", value)
								.bind("guildID", guildID)
								.execute();
					} else if (tableContainsColumn(handle,
							"media_settings",
							setting
					)) {
						handle.createUpdate(
								"UPDATE media_settings SET <setting> = :value WHERE guild_id = :guildID")
								.define("setting", setting)
								.bind("value", value)
								.bind("guildID", guildID)
								.execute();
					}
				});
	}

	private static boolean tableContainsColumn(Handle handle, String table, String setting) {
		/* Language=PostgreSQL */
		List<String> l = handle.createQuery(
				"SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = :table")
				.bind("table", table.toLowerCase())
				.mapTo(String.class)
				.list();

		return l.contains(setting.toLowerCase());
	}

	public static void set(String guildID, String table, String setting, Object value) {
		if (setting.matches("(?i)(premium|id|guild_id)")) {
			try {
				throw new IllegalAccessException();
			} catch (IllegalAccessException e) {
				LOGGER.warn("Illegal Access Attempt on DataBase!!");
				e.printStackTrace();
				return;
			}
		}

		Jdbi.create(DBConnectionHandler::getConnection)
				.useHandle(handle ->
				{
					if (tableContainsColumn(handle, table, setting)) {
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

	public static String get(String guildID, String setting) {
		return Jdbi.create(DBConnectionHandler::getConnection)
				.withHandle(handle ->
				{
					String r = "";
					if (tableContainsColumn(handle,
							"guild_settings",
							setting
					)) {
						r = handle.createQuery(
								"SELECT <setting> FROM guild_settings WHERE guild_id = :id")
								.define("setting", setting)
								.bind("id", guildID)
								.mapTo(String.class)
								.findFirst()
								.orElse("");
					} else if (tableContainsColumn(handle,
							"media_settings",
							setting
					)) {
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

	public static String get(String guildID, String table, String setting) {
		return Jdbi.create(DBConnectionHandler::getConnection)
				.withHandle(handle ->
				{
					String r = "";
					if (tableContainsColumn(handle, table, setting)) {
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

	public static List<String> getList(String guildID, String table, String setting) {
		return Jdbi.create(DBConnectionHandler::getConnection)
				.withHandle(handle ->
				{
					List<String> r = List.of();
					if (tableContainsColumn(handle, table, setting)) {
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

	public static void addPlaylistEntry(String guildID, String name, String uri) {
		Jdbi.create(DBConnectionHandler::getConnection).useHandle((h) ->
				h.execute("INSERT INTO playlists VALUES (?, ?, ?)", guildID, name, uri));
	}

	public static void deletePlaylistEntry(String id, String name) {
		Jdbi.create(DBConnectionHandler::getConnection).useHandle(handle ->
				handle.execute("DELETE FROM playlists WHERE guild_id = ? AND name = ?", id, name));
	}

	public static String getPlaylist(String guildID, String name) {
		return Jdbi.create(DBConnectionHandler::getConnection).withHandle((h) -> {
			String r = h.createQuery("SELECT url FROM playlists WHERE guild_id = ? AND name = ?")
					.bind(0, guildID)
					.bind(1, name)
					.mapTo(String.class)
					.first();
			h.close();
			return r;
		});
	}
}
