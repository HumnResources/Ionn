package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.DatabaseHandler;
import com.zischase.discordbot.DBConnectionHandler;
import net.dv8tion.jda.api.entities.Guild;
import org.jdbi.v3.core.Jdbi;

import java.util.HashMap;
import java.util.Map;

public final class GuildHandler
{
	
	private static final Map<Long, GuildContext> GUILDS = new HashMap<>();
	
	public static void setGuild(GuildContext guildContext)
	{
		Guild guild = guildContext.guild();
		GUILDS.putIfAbsent(guild.getIdLong(), guildContext);
		
		boolean initSettings = DatabaseHandler.get(guild.getId(), "prefix").isEmpty();
		
		if (initSettings)
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
		int v = Integer.parseInt(DatabaseHandler.get(guild.getId(), "volume"));
		guildContext.audioManager()
					.getPlayer()
					.setVolume(v);
	}
	
	public static GuildContext getContext(Guild guild)
	{
		return GUILDS.get(guild.getIdLong());
	}
}
