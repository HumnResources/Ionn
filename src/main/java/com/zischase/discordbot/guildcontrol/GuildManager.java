package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.DataBaseManager;
import com.zischase.discordbot.SQLConnectionHandler;
import com.zischase.discordbot.commands.audiocommands.Volume;
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
		
		boolean hasGuildSettings = !DataBaseManager.get(guild.getId(), "prefix").isEmpty();
		boolean hasMediaSettings = !DataBaseManager.get(guild.getId(), "volume").isEmpty();
		
		if (!hasGuildSettings)
		{
			Jdbi.create(SQLConnectionHandler::getConnection)
				.useHandle(handle ->
				{
					handle.execute("INSERT INTO guild_settings(guild_id) VALUES (?)", guild.getId());
				});
		}
		if (!hasMediaSettings)
		{
			Jdbi.create(SQLConnectionHandler::getConnection)
				.useHandle(handle ->
				{
					handle.execute("INSERT INTO media_settings(guild_id) VALUES (?)", guild.getId());
				});
		}
		
		Volume.init(guild);
	}
	
	public static GuildContext getContext(Guild guild)
	{
		return GUILDS.get(guild.getIdLong());
	}
}
