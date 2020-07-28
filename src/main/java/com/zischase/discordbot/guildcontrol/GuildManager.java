package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.commands.audiocommands.Volume;
import net.dv8tion.jda.api.entities.Guild;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class GuildManager {

    private static final Map<Long, GuildContext> GUILDS = new HashMap<>();

    public static void setGuild(Guild guild) {
        GUILDS.putIfAbsent(guild.getIdLong(), new GuildContext(guild));
        ((Volume) Objects.requireNonNull(getContext(guild)
                        .getCommandManager()
                        .getCommand("Volume"))
        ).set(guild);
    }

    public static GuildContext getContext(Guild guild) {
        return GUILDS.get(guild.getIdLong());
    }
}
