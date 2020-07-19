package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import net.dv8tion.jda.api.entities.Guild;


public class MusicManager {
    private final static AudioPlayerManager AUDIO_PLAYER_MANAGER = new DefaultAudioPlayerManager();
    private final AudioPlayer player;
    private final TrackScheduler scheduler;

    static {
        AUDIO_PLAYER_MANAGER.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
        AUDIO_PLAYER_MANAGER.registerSourceManager(new YoutubeAudioSourceManager());
        AUDIO_PLAYER_MANAGER.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        AUDIO_PLAYER_MANAGER.registerSourceManager(new BandcampAudioSourceManager());
        AUDIO_PLAYER_MANAGER.registerSourceManager(new VimeoAudioSourceManager());
        AUDIO_PLAYER_MANAGER.registerSourceManager(new TwitchStreamAudioSourceManager());
        AUDIO_PLAYER_MANAGER.registerSourceManager(new BeamAudioSourceManager());
        AUDIO_PLAYER_MANAGER.registerSourceManager(new HttpAudioSourceManager());
        AUDIO_PLAYER_MANAGER.registerSourceManager(new LocalAudioSourceManager());
        AudioSourceManagers.registerRemoteSources(AUDIO_PLAYER_MANAGER);
        AudioSourceManagers.registerLocalSource(AUDIO_PLAYER_MANAGER);
    }

    public MusicManager(Guild guild) {
        this.player = AUDIO_PLAYER_MANAGER.createPlayer();
        this.scheduler = new TrackScheduler(this);
        this.player.addListener(scheduler);

        guild.getAudioManager().setSendingHandler(this.getSendHandler());
    }

    public AudioPlayerManager getAudioPlayerManager() {
        return AUDIO_PLAYER_MANAGER;
    }

    public AudioPlayer getPlayer() {
        return player;
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }

    public AudioPlayerSendHandler getSendHandler() {
        return new AudioPlayerSendHandler(player);
    }
}