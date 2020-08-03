package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TrackScheduler extends AudioEventAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackScheduler.class);
    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;
    private final AudioManager manager;

    private AudioTrack lastTrack;
    private TextChannel textChannel = null;

    public TrackScheduler(AudioManager manager) {
        this.player = manager.getPlayer();
        this.manager = manager;
        this.queue = new LinkedBlockingQueue<>();

        LOGGER.info("TrackScheduler created.");
    }

    public void queue(AudioTrack track, TextChannel textChannel) {
        this.textChannel = textChannel;

        if (manager.getPlayer().getPlayingTrack() != null)
            textChannel.sendMessage("Added `" + track.getInfo().title + "` to the queue.").queue();

        if (!player.startTrack(track, true)) { // noInterrupt: True == add to queue
            queue.offer(track);
        }
    }

    public ArrayList<AudioTrack> getQueue() {
        ArrayList<AudioTrack> trackList = new ArrayList<>();

        if (!this.queue.isEmpty())
            trackList.addAll(queue);

        return trackList;
    }

    public void clearQueue() {
        this.queue.clear();
    }

    public void queueList(ArrayList<AudioTrack> tracks, TextChannel channel) {
        if (tracks.isEmpty()) {
            return;
        }
        this.textChannel = channel;

        for (AudioTrack track : tracks) {
            this.queue.add(track.makeClone());
        }

        if (player.isPaused())
            player.setPaused(false);
        else if (player.getPlayingTrack() == null)
            player.startTrack(queue.poll(), false);
    }

    public void nextTrack() {
        this.player.startTrack(queue.poll(), false);
    }

    public void prevTrack() {
        this.player.startTrack(lastTrack, false);
    }

    @Override
    public void onPlayerPause(AudioPlayer player) {
        GuildManager.getContext(textChannel.getGuild())
                .getPlayerPrinter()
                .printNowPlaying(textChannel);
    }

    @Override
    public void onPlayerResume(AudioPlayer player) {
        GuildManager.getContext(textChannel.getGuild())
            .getPlayerPrinter()
            .printNowPlaying(textChannel);
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        GuildManager.getContext(textChannel.getGuild())
                .getPlayerPrinter()
                .printNowPlaying(textChannel);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.equals(AudioTrackEndReason.LOAD_FAILED)) {
            if (lastTrack.getInfo().identifier.equalsIgnoreCase(track.getInfo().identifier)) {
                textChannel.sendMessage("Sorry, too many errors. \nGotta move on.")
                        .complete()
                        .delete()
                        .queueAfter(2000, TimeUnit.MILLISECONDS);
                nextTrack();
                return;
            }

            textChannel.sendMessage("Loading failed. . . \nTrying again!")
                    .complete()
                    .delete()
                    .queueAfter(2000, TimeUnit.MILLISECONDS);

            Member member = textChannel.getGuild()
                    .getMember(textChannel.getJDA().getSelfUser());

            new TrackLoader().load(textChannel, member, track.getInfo().identifier);
        }
        else if (endReason.mayStartNext) {
            player.startTrack(queue.poll(), false);
        }

        this.lastTrack = track.makeClone();
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        manager.getPlayer().stopTrack();
        if (!queue.isEmpty()) {
            player.playTrack(queue.poll());
        }
    }
}
