package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Previous extends Command {


    public Previous() {
        super(false);
    }

    @Override
    public List<String> getAliases() {
        return List.of("Prev", "pr");
    }

    @Override
    public String helpText() {
        return """
                %s
                				
                Usage:
                	prev/pr
                """.formatted(shortDescription());
    }

    @Override
    public @NotNull String shortDescription() {
        return "Plays previous song.";
    }

    @Override
    public void handle(CommandContext ctx) {

        GuildContext.get(ctx.getGuild().getId())
                .audioManager()
                .getScheduler()
                .prevTrack();

    }

}
