package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Queue extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(Queue.class);

    public Queue() {
        super(false);
    }

    @Override
    public void execute(CommandContext ctx) {
        List<String> args = ctx.getArgs();

        AudioManager audioManager = GuildManager.getContext(ctx.getGuild()).getAudioManager();
        ArrayList<AudioTrack> queue = audioManager.getScheduler().getQueue();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.BLUE);

        if (args.isEmpty()) {
            if (queue.isEmpty())
                embed.appendDescription("Nothing in the queue, add some tunes!");
            else {
                Collections.reverse(queue);

                String length = "";
                for (int i = 0; i < queue.size(); i++) {
                    AudioTrack track = queue.get(i);
                    int index = queue.size() - i;

                    embed.appendDescription(index + ". `" + track.getInfo().title + "`" + System.lineSeparator());

                    length = length.concat(index +". `" + track.getInfo().title + "`" + System.lineSeparator());

                    // Limit is 2048 characters per embed. This allows some buffer.
                    if (length.length() >= 2000) {
                        length = "";
                        ctx.getChannel().sendMessage(embed.build()).queue();
                        embed = new EmbedBuilder();
                        embed.setColor(Color.BLUE);
                    }

                }
            }
        }
        else if (args.get(0).matches("(?i)(-clear|-c)")) {
            GuildManager.getContext(ctx.getGuild())
                    .getAudioManager()
                    .getScheduler()
                    .clearQueue();

            embed.appendDescription("Queue cleared.");
        }

        ctx.getChannel()
                .sendMessage(embed.build())
                .queue();

        Objects.requireNonNull(GuildManager.getContext(ctx.getGuild())
                .getCommandManager()
                .getCommand("NowPlaying"))
                .execute(ctx);
    }

    @Override
    public List<String> getAliases() {
        return List.of("Q", "Qu");
    }
}
