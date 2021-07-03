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
					if (checkTable(handle,
							"guild_settings",
							setting
					)) {
						handle.createUpdate(
								"UPDATE guild_settings SET <setting> = :value WHERE guild_id = :guildID")
								.define("setting", setting)
								.bind("value", value)
								.bind("guildID", guildID)
								.execute();
					} else if (checkTable(handle,
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

	private static boolean checkTable(Handle handle, String table, String setting) {
		/* Language=PostgreSQL */
		return handle.createQuery(
				"SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = :table")
				.bind("table", table.toLowerCase())
				.mapTo(String.class)
				.list()
				.contains(setting.toLowerCase());
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
					if (checkTable(handle, table, setting)) {
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
					if (checkTable(handle,
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
					} else if (checkTable(handle,
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
					if (checkTable(handle, table, setting)) {
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
					if (checkTable(handle, table, setting)) {
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

}
