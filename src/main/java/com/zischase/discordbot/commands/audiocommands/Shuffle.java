package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

public class Shuffle extends Command {


    public Shuffle() {
        super(true);
    }

    @Override
    public String helpText() {
        return """
                %s
                				
                Usage:
                	`shuffle`
                	`shuffle [on|off]`
                """;
    }

    @Override
    public @NotNull String shortDescription() {
        return "Sets shuffle on/off for current playlist.";
    }

    @Override
    public void handle(CommandContext ctx) {
        GuildContext guildContext = GuildContext.get(ctx.getGuild().getId());

        ArrayList<AudioTrack> currentQueue = guildContext.audioManager()
                .getScheduler()
                .getQueue();

        Collections.shuffle(currentQueue);

        guildContext.audioManager()
                .getScheduler()
                .clearQueue();

        guildContext.audioManager()
                .getScheduler()
                .queueList(currentQueue);

        Objects.requireNonNull(guildContext.commandHandler().getCommand("Queue"))
                .handle(ctx);

    }

}
