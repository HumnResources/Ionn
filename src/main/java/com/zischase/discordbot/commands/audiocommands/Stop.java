package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.guildcontrol.GuildManager;
import com.zischase.discordbot.audioplayer.MusicManager;

public class Stop extends Command {

    public Stop() {
        super(false);
    }

    @Override
    public String getHelp() {
        return "Stop ~ Ends currently playing audio and leave's the channel.";
    }

    @Override
    public void handle(CommandContext ctx) {
        MusicManager musicManager = GuildManager.getContext(ctx.getGuild()).getMusicManager();

        musicManager.getPlayer().stopTrack();

        ctx.getJDA().getDirectAudioController().disconnect(ctx.getGuild());
    }
}
