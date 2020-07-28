package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;

import java.util.List;

public class Previous extends Command {


    public Previous() {
        super(false);
    }

    @Override
    public String getHelp() {
        return null;
    }

    @Override
    public List<String> getAliases() {
        return List.of("Prev");
    }

    @Override
    public void execute(CommandContext ctx) {

        GuildManager.getContext(ctx.getGuild()).getAudioManager().getScheduler().prevTrack();

    }
}
