package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.audioplayer.MusicManager;
import com.zischase.discordbot.commands.CommandManager;
import net.dv8tion.jda.api.entities.Guild;

import javax.annotation.Nullable;

@Nullable
public class GuildContext {
    private final Guild guild;
    private final boolean premium;
    private final MusicManager musicManager;
    private final CommandManager commandManager;

    public GuildContext(Guild guild, boolean isPremium) {
        this.guild = guild;
        this.premium = isPremium;
        this.musicManager = new MusicManager(guild);
        this.commandManager = new CommandManager();
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public MusicManager getMusicManager() {
        return musicManager;
    }

    public Guild getGuild() {
        return guild;
    }

    public boolean isPremium() {
        return premium;
    }
}
