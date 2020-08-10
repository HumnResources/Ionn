package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.zischase.discordbot.Config;
import com.zischase.discordbot.Listener;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PlayerPrinter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerPrinter.class);
    private final Guild guild;

    public PlayerPrinter(Guild guild) {
        this.guild = guild;
    }

    public void printNowPlaying(TextChannel channel) {
        AudioPlayer player = GuildManager.getContext(guild)
                .getAudioManager()
                .getPlayer();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        if (player.getPlayingTrack() == null) {
            embed.setTitle("Nothing Playing");
            embed.setColor(Color.darkGray);
            embed.setFooter(". . .");
        }
        else {
            AudioTrackInfo info = player.getPlayingTrack().getInfo();
            embed.setThumbnail(Config.get("MEDIA_PLAYER_ICON"));
            embed.setTitle("Now Playing");
            embed.appendDescription(info.title + "\n\n" + info.author + "\n");
            embed.setFooter(info.uri);

            if (player.isPaused())
                embed.appendDescription("Paused");
//            else {
//                embed.appendDescription(progressBar(player.getPlayingTrack()));
//            }

        }

        long delayMS = 2000;
        if (player.getPlayingTrack() != null)
            delayMS = player.getPlayingTrack().getDuration() - player.getPlayingTrack().getPosition();

        Message message = new MessageBuilder()
                .setEmbed(embed.build())
                .build();

        List<Message> past =  channel.getHistory()
                .retrievePast(100)
                .complete();

        List<Message> delete = new ArrayList<>();
        if (!past.isEmpty())
            delete = past.stream()
                    .filter(msg -> !msg.getEmbeds().isEmpty())
                    .filter(msg -> msg.getEmbeds().get(0).getTitle() != null)
                    .filter(msg -> Objects.requireNonNull(msg.getEmbeds()
                            .get(0)
                            .getTitle())
                            .equalsIgnoreCase(message.getEmbeds().get(0).getTitle()))
                            .collect(Collectors.toList());

        if (!delete.isEmpty()) {
            if (delete.size() == 1)
                channel.deleteMessageById(delete.get(0).getId())
                        .queue();
            else
                channel.deleteMessages(delete)
                        .queue();
        }



            channel.sendMessage(message)
                    .complete()
                    .delete()
                    .queueAfter(delayMS, TimeUnit.MILLISECONDS, null, Throwable::getSuppressed);

    }

    public void printQueue(TextChannel channel) {


        List<Message> past =  channel.getHistory()
                .retrievePast(100)
                .complete();

        List<Message> delete = new ArrayList<>();
        if (!past.isEmpty())
            delete = past.stream()
                    .filter(msg -> !msg.getEmbeds().isEmpty())
                    .collect(Collectors.toList());

        if (!delete.isEmpty()) {
            if (delete.size() == 1)
                channel.deleteMessageById(delete.get(0).getId())
                        .queue();
            else
                channel.deleteMessages(delete)
                        .queue();
        }

        AudioManager audioManager = GuildManager.getContext(channel.getGuild())
                .getAudioManager();

        ArrayList<AudioTrack> queue = audioManager.getScheduler()
                .getQueue();

        new CompletableFuture<Void>().completeAsync(() -> {

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.BLUE);

        if (queue.isEmpty()) {
            embed.appendDescription("Nothing in the queue.");

            channel.sendMessage(embed.build())
                    .complete()
                    .delete()
                    .queueAfter(2000, TimeUnit.MILLISECONDS);
            return null;
        }

        Collections.reverse(queue);

        embed.appendDescription("```\n");

            if (queue.size() > 1) {
                // Subtract 1 to remove next song, display separate..
                for (AudioTrack track : queue) {
                    if (queue.get(queue.size() - 1) == track)
                        continue;

                    embed.appendDescription(track.getInfo().title + "\n");

                    // Limit is 2048 characters per embed description. This allows some buffer. Had issues at 2000 characters.
                    if (embed.getDescriptionBuilder().toString().length() >= 1800) {
                        embed.appendDescription("```");
                        channel.sendMessage(embed.build())
                                .queue();

                        embed = new EmbedBuilder();
                        embed.setColor(Color.BLUE);
                        embed.appendDescription("```\n");
                    }
                }
            }
            AudioTrack track = queue.get(queue.size() - 1);

            embed.appendDescription("``` ```fix\n" + track.getInfo().title + "```"); // fix syntax highlighting in a code block, default is yellow; used to signify next song in queue list.

            channel.sendMessage(embed.build())
                    .queue();

            printNowPlaying(channel);

            return null;
        }, Listener.getExecutor());


    }

    private void deletePrevious() {

    }

//    private String progressBar(AudioTrack track) {
//        long max = track.getDuration();
//        long now = track.getPosition();
//        long remaining = max - now;
//
//        LOGGER.info(String.valueOf(max));
//        LOGGER.info(String.valueOf(now));
//        LOGGER.info(String.valueOf(remaining));
//
//        ArrayList<String> result = new ArrayList<>();
//
//        if (max != Long.MAX_VALUE) {
//            long printAmt = (max % remaining) / 10000;
//            for (int i = 0; i < printAmt; i++) {
//                result.add("=");
//            }
//        }
//
//    String bar = "[                         ]";
//    return bar.replaceFirst("\\s{"+result.size()+"}", "=");
//    }
}
