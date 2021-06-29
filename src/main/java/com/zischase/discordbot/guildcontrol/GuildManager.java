package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.DataBaseManager;
import com.zischase.discordbot.SQLConnectionHandler;
import net.dv8tion.jda.api.entities.Guild;
import org.jdbi.v3.core.Jdbi;

import java.util.HashMap;
import java.util.Map;

public final class GuildManager
{
	
	private static final Map<Long, GuildContext> GUILDS = new HashMap<>();
	
	public static void setGuild(GuildContext guildContext)
	{
		Guild guild = guildContext.guild();
		GUILDS.putIfAbsent(guild.getIdLong(), guildContext);
		
		boolean hasGuildSettings = DataBaseManager.get(guild.getId(), "prefix") != null;
		
		if (!hasGuildSettings)
		{
			Jdbi.create(SQLConnectionHandler::getConnection)
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
		int v = Integer.parseInt(DataBaseManager.get(guild.getId(), "volume"));
		guildContext.audioManager()
					.getPlayer()
					.setVolume(v);
	}
	
	public static GuildContext getContext(Guild guild)
	{
		return GUILDS.get(guild.getIdLong());
	}
}
