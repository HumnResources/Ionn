package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;

import java.util.ArrayList;

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
        AudioManager audioManager = GuildManager.getContext(ctx.getGuild()).getAudioManager();

        audioManager.getPlayer().stopTrack();

        audioManager.getScheduler().queueList(new ArrayList<>(), null);

        ctx.getJDA().getDirectAudioController().disconnect(ctx.getGuild());
    }
}
