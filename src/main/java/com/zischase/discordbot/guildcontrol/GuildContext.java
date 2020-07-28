package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.CommandManager;
import net.dv8tion.jda.api.entities.Guild;

import javax.annotation.Nullable;

@Nullable
public class GuildContext {
    private final Guild guild;
    private final boolean premium;
    private final AudioManager audioManager;
    private final CommandManager commandManager;
    private final GuildSettingsManager settingsManager;

    public GuildContext(Guild guild) {
        this.premium = false;
        this.guild = guild;
        this.audioManager = new AudioManager(guild);
        this.commandManager = new CommandManager();
        this.settingsManager = new GuildSettingsManager();
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    public Guild getGuild() {
        return guild;
    }

    public boolean isPremium() {
        return premium;
    }
}
