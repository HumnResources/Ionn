package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.PostgreSQL;
import net.dv8tion.jda.api.entities.Guild;
import org.jdbi.v3.core.Jdbi;

public final class PremiumManager
{
	public static boolean getPremium(Guild guild)
	{
		
		return Jdbi.create(PostgreSQL::getConnection).withHandle(handle ->
		{
			boolean b = handle.createQuery("SELECT premium FROM guild_settings WHERE guild_id = ?")
					.bind(0, guild.getId())
					.mapTo(boolean.class)
					.findFirst()
					.orElse(false);
			
			handle.close();
			return b;
		});
	}
}
