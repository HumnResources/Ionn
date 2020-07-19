package com.zischase.discordbot.guildcontrol;

import net.dv8tion.jda.api.entities.Guild;

import java.util.HashMap;
import java.util.Map;

public abstract class GuildManager {

    private static final Map<Long, GuildContext> GUILDS = new HashMap<>();

    static {

    }

    public static void setGuild(Guild guild) {
        GUILDS.putIfAbsent(guild.getIdLong(), new GuildContext(guild, false)); // Only sets if no guild present.
    }

    public static GuildContext getContext(Guild guild) {
        return GUILDS.get(guild.getIdLong());
    }
}
