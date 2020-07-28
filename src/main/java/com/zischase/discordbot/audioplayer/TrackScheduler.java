package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.zischase.discordbot.Config;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackScheduler.class);
    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;
    private final AudioManager manager;

    private AudioTrack lastTrack;
    private TextChannel textChannel = null;

    public TrackScheduler(AudioManager audioManager) {
        this.player = audioManager.getPlayer();
        this.manager = audioManager;
        this.queue = new LinkedBlockingQueue<>();

        LOGGER.info("TrackScheduler created.");
    }

    public void queue(AudioTrack track, TextChannel textChannel) {
        this.textChannel = textChannel;

        if (manager.getPlayer().getPlayingTrack() != null)
            textChannel.sendMessage("Added `" + track.getInfo().title + "` to the queue.").queue();

        if (!player.startTrack(track, true)) { // noInterrupt. True == add to queue
            this.queue.offer(track);
        }
    }

    public void nextTrack() {
        this.player.startTrack(queue.poll(), false);
    }

    public void prevTrack() {
        this.player.startTrack(lastTrack, false);
    }

    public ArrayList<AudioTrack> getQueue() {
        ArrayList<AudioTrack> trackList = new ArrayList<>();

        if (!this.queue.isEmpty())
            trackList.addAll(this.queue);

        return trackList;
    }

    public void clearQueue() {
        this.queue.clear();
    }

    public void queueList(ArrayList<AudioTrack> tracks, TextChannel channel) {
        if (tracks.isEmpty()) {
            return;
        }
        textChannel = channel;

        for (AudioTrack track : tracks) {
            this.queue.add(track.makeClone());
        }

        if (player.isPaused())
            this.player.setPaused(false);
        else if (player.getPlayingTrack() == null)
            this.player.startTrack(this.queue.poll(), false);
    }

    public void sendEmbed(TextChannel channel) {
        this.textChannel = channel;
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);

        if (player.getPlayingTrack() == null) {
            embed.setTitle("Nothing Playing");
            embed.setColor(Color.BLUE);
            embed.setFooter("You should add some songs . . .");
        }
        else {
            AudioTrackInfo info = player.getPlayingTrack().getInfo();
            embed.setThumbnail(Config.get("MEDIA_PLAYER_ICON"));
            embed.setTitle("Now Playing");
            embed.appendDescription(info.title);
            embed.appendDescription(System.lineSeparator());
            embed.appendDescription(info.author);
            embed.appendDescription(System.lineSeparator());
            embed.setFooter(info.uri);
        }
        textChannel.sendMessage(embed.build()).queue();
    }

    @Override
    public void onPlayerPause(AudioPlayer player) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.BLUE);
        embed.setTitle("Player Paused !");
        embed.appendDescription("Currently playing: " + System.lineSeparator() + "`" + player.getPlayingTrack().getInfo().title + "`");

        textChannel.sendMessage(embed.build()).queue();
    }

    @Override
    public void onPlayerResume(AudioPlayer player) {
        sendEmbed(textChannel);
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        sendEmbed(textChannel);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason == AudioTrackEndReason.LOAD_FAILED) {
        textChannel.sendMessage("Poop, loading failed, I'll try again!").queue();
        this.queue(track, textChannel);
    }
        this.lastTrack = track.makeClone();

        if (endReason.mayStartNext)
            player.startTrack(queue.poll(), true);
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        this.queue(track, textChannel);
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        manager.getPlayer().stopTrack();
        if (!queue.isEmpty()) {
            this.queue(track, textChannel);
        }
    }
}
