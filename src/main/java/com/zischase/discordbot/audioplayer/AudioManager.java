package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import net.dv8tion.jda.api.entities.Guild;


public class AudioManager {

	private final static AudioPlayerManager PLAYER_MANAGER = new DefaultAudioPlayerManager();

	static {
		PLAYER_MANAGER.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
		PLAYER_MANAGER.getConfiguration().setOpusEncodingQuality(128);
		PLAYER_MANAGER.registerSourceManager(new YoutubeAudioSourceManager());
		PLAYER_MANAGER.registerSourceManager(new HttpAudioSourceManager());
		PLAYER_MANAGER.registerSourceManager(new LocalAudioSourceManager());
		PLAYER_MANAGER.registerSourceManager(new BeamAudioSourceManager());
		AudioSourceManagers.registerRemoteSources(PLAYER_MANAGER);
		AudioSourceManagers.registerLocalSource(PLAYER_MANAGER);
	}

	private final AudioPlayer    player;
	private final TrackScheduler scheduler;
	private final TrackLoader    trackLoader;

	public AudioManager(Guild guild) {
		this.player      = PLAYER_MANAGER.createPlayer();
		this.scheduler   = new TrackScheduler(this.getPlayer(), guild);
		this.trackLoader = new TrackLoader(guild.getId());
		this.player.addListener(scheduler);
		guild.getAudioManager().setSendingHandler(this.getSendHandler());
	}

	public AudioPlayer getPlayer() {
		return player;
	}

	public AudioPlayerSendHandler getSendHandler() {
		return new AudioPlayerSendHandler(player);
	}

	public TrackLoader getTrackLoader() {
		return trackLoader;
	}

	public AudioPlayerManager getPlayerManager() {
		return PLAYER_MANAGER;
	}

	public TrackScheduler getScheduler() {
		return scheduler;
	}

}