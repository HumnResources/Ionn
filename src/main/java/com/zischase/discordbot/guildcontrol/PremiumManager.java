package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.DataBaseManager;
import net.dv8tion.jda.api.entities.Guild;

public final class PremiumManager
{
	public static boolean getPremium(Guild guild)
	{
		return Boolean.parseBoolean(DataBaseManager.get(guild.getId(), "ispremium"));
	}
}
