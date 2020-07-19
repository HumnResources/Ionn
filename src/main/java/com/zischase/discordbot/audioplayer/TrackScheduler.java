package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.zischase.discordbot.Config;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter implements AudioLoadResultHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackScheduler.class);

    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;
    private final MusicManager manager;

    private Audio currentTrack = null;
    private Audio lastTrack = null;

    private TextChannel textChannel = null;

    public TrackScheduler(MusicManager musicManager) {
        this.player = musicManager.getPlayer();
        this.manager = musicManager;
        this.queue = new LinkedBlockingQueue<>();
        LOGGER.info("TrackScheduler created.");
    }

    public void load(TextChannel channel, Member member, Audio audio) {
        if (connectToVoice(channel, member)) {
            this.currentTrack = audio;
            GuildManager.getContext(channel.getGuild())
                    .getMusicManager()
                    .getAudioPlayerManager()
                    .loadItem(audio.getUrl(), this);
        }
    }

    public void sendEmbed(TextChannel channel) {
        textChannel = channel;
        EmbedBuilder embed = new EmbedBuilder();

        embed.setColor(Color.CYAN);
        embed.setThumbnail(Config.get("MEDIA_PLAYER_ICON"));
        embed.setTitle("Now Playing");

        embed.appendDescription(currentTrack.getName());
        embed.appendDescription(System.lineSeparator());
        embed.appendDescription(currentTrack.getAuthor());
        embed.appendDescription(System.lineSeparator());
        embed.setFooter(currentTrack.getUrl());

        textChannel.sendMessage(embed.build()).queue();
    }

    private boolean connectToVoice(TextChannel channel, Member member) {
        List<VoiceChannel> voiceChannels = channel.getGuild().getVoiceChannels();
        textChannel = channel;

        if (!voiceChannels.isEmpty()) {
            for (VoiceChannel c : voiceChannels) {
                if (c.getMembers().contains(member)) {
                    c.getGuild()
                            .getJDA()
                            .getDirectAudioController()
                            .connect(c);
                    return true;
                }
            }
        }
        textChannel.sendMessage("You must be in a voice channel for that !").queue();
        return false;
    }

    public void queue(AudioTrack track) {
        // Calling startTrack with the noInterrupt set to true will start the track only if nothing is currently playing. If
        // something is playing, it returns false and does nothing. In that case the player was already playing so this
        // track goes to the queue instead.
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
    }

    public void nextTrack() {
        // Start the next track, regardless of if something is already playing or not. In case queue was empty, we are
        // giving null to startTrack, which is a valid argument and will simply stop the player.
        this.player.startTrack(queue.poll(), false);
    }

    public void prevTrack() {
        if (currentTrack != lastTrack)
            manager.getAudioPlayerManager().loadItem(lastTrack.getUrl(), this);

    }

    @Override
    public void onPlayerPause(AudioPlayer player) { }

    @Override
    public void onPlayerResume(AudioPlayer player) { }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        this.currentTrack = new Audio(track);
        sendEmbed(textChannel);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        this.lastTrack = currentTrack;
        if (endReason.mayStartNext)
            player.startTrack(queue.poll(), true);

        if (endReason == AudioTrackEndReason.LOAD_FAILED) {
            textChannel.sendMessage("Poop, loading failed, I'll try again!").queue();
            this.player.startTrack(track, true);
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        this.textChannel.sendMessage("Crap it crashed . . .").queue();
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        this.textChannel.sendMessage("Help! I'm Stuck!").queue();
    }

    @Override
    public void trackLoaded(AudioTrack track) {
        manager.getScheduler().queue(track);

        if (track != this.queue && !queue.isEmpty())
            textChannel.sendMessage("Added `" + currentTrack.getName() + "` to the queue.").queue();
    }

    @Override
    public void playlistLoaded(AudioPlaylist audioPlaylist) {
        AudioTrack firstTrack = audioPlaylist.getSelectedTrack();

        if (firstTrack == null)
            firstTrack = audioPlaylist.getTracks().get(0);

        manager.getScheduler().queue(firstTrack);
    }

    @Override
    public void loadFailed(FriendlyException e) {
        textChannel.sendMessage("Oops, there was an error playing that track, please try again :c").queue();
    }

    @Override
    public void noMatches() {
        textChannel.sendMessage("Sorry, no matches found ! ").queue();
    }
}
