package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import org.jetbrains.annotations.NotNull;

public class Stop extends Command {

    public Stop() {
        super(false);
    }

    @Override
    public String helpText() {
        return "Stop ~ Ends currently playing audio and leave's the channel.";
    }

    @Override
    public @NotNull String shortDescription() {
        return "Stops song, cancels audio queue and leaves channel.";
    }

    @Override
    public void handle(CommandContext ctx) {
        AudioManager audioManager = GuildContext.get(ctx.getGuild().getId())
                .audioManager();

        audioManager.getPlayer()
                .stopTrack();

        audioManager.getScheduler()
                .clearQueue();

        ctx.getJDA()
                .getDirectAudioController()
                .disconnect(ctx.getGuild());
    }

}
