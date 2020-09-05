package com.zischase.discordbot.guildcontrol;

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
		
		String dbIDQuery = Jdbi.create(SQLConnectionHandler::getConnection)
							   .withHandle(handle ->
							   {
								   String r = handle.createQuery("SELECT guild_id FROM guild_settings WHERE guild_id = ?")
													.bind(0, guild.getId())
													.mapTo(String.class)
													.findFirst()
													.orElse(null);
			
								   handle.close();
								   return r;
							   });
		
		if (dbIDQuery == null)
		{
			Jdbi.create(SQLConnectionHandler::getConnection)
				.useHandle(handle ->
						handle.execute("INSERT INTO guild_settings(guild_id) VALUES (?)", guild.getId()));
		}
		
		Volume.init(guild);
	}
	
	public static GuildContext getContext(Guild guild)
	{
		return GUILDS.get(guild.getIdLong());
	}
}
