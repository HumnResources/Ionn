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
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.zischase.discordbot.DBQueryHandler;
import net.dv8tion.jda.api.entities.Guild;

import java.util.Timer;
import java.util.TimerTask;


public class AudioManager {

	private final static AudioPlayerManager PLAYER_MANAGER         = new DefaultAudioPlayerManager();
	private static final long               OBSERVER_TICK_SPEED_MS = 10;

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

	private final AudioPlayer              player;
	private final TrackScheduler           scheduler;
	private final TrackLoader              trackLoader;
	private final NowPlayingMessageHandler nowPlayingMessageHandler;
	private final QueueMessageHandler      queueMessageHandler;
	private final Timer timer = new Timer();

	public AudioPlayerState getPlayerState() {
		return playerState;
	}

	private AudioPlayerState playerState = AudioPlayerState.STOPPED;

	public AudioManager(Guild guild) {
		this.player                   = PLAYER_MANAGER.createPlayer();
		this.scheduler                = new TrackScheduler(this.getPlayer(), guild);
		this.trackLoader              = new TrackLoader(guild.getId());
		this.nowPlayingMessageHandler = new NowPlayingMessageHandler(this, guild);
		this.queueMessageHandler      = new QueueMessageHandler(this);

		this.player.addListener(scheduler);
		guild.getAudioManager().setSendingHandler(this.getSendHandler());
		audioPlayerStateObserver(guild);
	}

	private void audioPlayerStateObserver(Guild guild) {
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				AudioTrack track = player.getPlayingTrack();
				boolean isPaused = DBQueryHandler.get(guild.getId(), "paused").equals("t");

				while(playerState != AudioPlayerState.STOPPED) {
					if (track == null || playerState != AudioPlayerState.LOADING_TRACK || track.getState() == AudioTrackState.INACTIVE) {
						playerState = AudioPlayerState.STOPPED;
					}
					else if (track.getState() == AudioTrackState.PLAYING && !isPaused) {
						playerState = AudioPlayerState.PLAYING;
					}
					else if ((track.getState() == AudioTrackState.PLAYING || track.getState() == AudioTrackState.LOADING || track.getState() == AudioTrackState.STOPPING) && isPaused) {
						playerState = AudioPlayerState.PAUSED;
					}
					else if (track.getState() == AudioTrackState.LOADING) {
						playerState = AudioPlayerState.LOADING_TRACK;
					}
				}
			}
		}, 0, OBSERVER_TICK_SPEED_MS);
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

	public NowPlayingMessageHandler getNowPlayingMessageHandler() {
		return nowPlayingMessageHandler;
	}

	public QueueMessageHandler getQueueMessageHandler() {
		return queueMessageHandler;
	}
}