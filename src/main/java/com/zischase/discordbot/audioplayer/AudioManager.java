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
import com.zischase.discordbot.DBQuery;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Guild;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public class AudioManager
{
	
	private static final AudioPlayerManager PLAYER_MANAGER         = new DefaultAudioPlayerManager();
	private static final long               OBSERVER_TICK_SPEED_MS = 10;
	
	static
	{
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
	private final Timer                    timer     = new Timer();
	private final String                   guild_id;
	private       TimerTask                timerTask = null;
	private AudioPlayerState playerState = AudioPlayerState.STOPPED;
	
	public AudioManager(Guild guild)
	{
		this.player                   = PLAYER_MANAGER.createPlayer();
		this.scheduler                = new TrackScheduler(this.getPlayer(), guild);
		this.trackLoader              = new TrackLoader(guild.getId());
		this.nowPlayingMessageHandler = new NowPlayingMessageHandler(this, guild);
		this.queueMessageHandler      = new QueueMessageHandler(this);
		this.guild_id                 = guild.getId();
		
		this.player.addListener(scheduler);
		guild.getAudioManager().setSendingHandler(this.getSendHandler());
		audioPlayerStateObserver();
	}
	
	public AudioPlayer getPlayer()
	{
		return player;
	}
	
	public AudioPlayerSendHandler getSendHandler()
	{
		return new AudioPlayerSendHandler(player);
	}
	
	private void audioPlayerStateObserver()
	{
		if (this.timerTask == null)
		{
			this.timerTask = getPlayerStateTimerTask();
		}
		
		timer.scheduleAtFixedRate(timerTask, 0, OBSERVER_TICK_SPEED_MS);
	}
	
	private TimerTask getPlayerStateTimerTask()
	{
		return new TimerTask()
		{
			@Override
			public void run()
			{
				AudioTrack track    = player.getPlayingTrack();
				boolean    isPaused = DBQueryHandler.get(guild_id, DBQuery.PAUSED).equals("t");
				
				if (track != null && track.getState() == AudioTrackState.LOADING)
				{
					playerState = AudioPlayerState.LOADING_TRACK;
				}
				else if (track == null || track.getState() == AudioTrackState.INACTIVE)
				{
					playerState = AudioPlayerState.STOPPED;
				}
				else if (track.getState() == AudioTrackState.PLAYING && !isPaused)
				{
					playerState = AudioPlayerState.PLAYING;
				}
				else if ((track.getState() == AudioTrackState.PLAYING || track.getState() == AudioTrackState.LOADING || track.getState() == AudioTrackState.STOPPING) && isPaused)
				{
					playerState = AudioPlayerState.PAUSED;
				}
			}
		};
	}
	
	public AudioPlayerManager getPlayerManager()
	{
		return PLAYER_MANAGER;
	}
	
	public NowPlayingMessageHandler getNowPlayingMessageHandler()
	{
		return nowPlayingMessageHandler;
	}
	
	public QueueMessageHandler getQueueMessageHandler()
	{
		return queueMessageHandler;
	}
	
	public void loadAudioState(boolean reload)
	{
		Guild   guild          = GuildContext.get(guild_id).guild();
		String  activeSongURL  = DBQueryHandler.get(guild_id, DBQuery.ACTIVESONG);
		boolean hasActiveMedia = !activeSongURL.isEmpty();
		
		if (hasActiveMedia)
		{
			CompletableFuture.runAsync(() ->
			{
				AudioManager audioManager = GuildContext.get(guild_id).audioManager();
				
				audioManager.player.setPaused(Boolean.getBoolean(DBQueryHandler.get(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.PAUSED)));
				audioManager.scheduler.setRepeatSong(Boolean.getBoolean(DBQueryHandler.get(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.REPEATSONG)));
				audioManager.scheduler.setRepeatQueue(Boolean.getBoolean(DBQueryHandler.get(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.REPEATQUEUE)));
				audioManager.player.setVolume(Integer.parseInt(DBQueryHandler.get(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.VOLUME)));
				
				if (!reload)
				{
					return;
				}
				
				CommandContext ctx = new CommandContext(guild, guild.getSelfMember(), List.of("join"));
				GuildContext.get(guild.getId()).commandHandler().invoke(ctx);
				
				String       currentQueue        = DBQueryHandler.get(guild_id, DBQuery.CURRENTQUEUE);
				List<String> queueURLs           = new java.util.ArrayList<>(List.of(currentQueue.split(",")));
				long         currentSongPosition = Long.parseLong(DBQueryHandler.get(guild_id, DBQuery.ACTIVESONGDURATION));
				
				queueURLs.add(0, activeSongURL);
				
				audioManager.getTrackLoader().loadURIListSequentially(queueURLs);
				
				OffsetDateTime   start      = OffsetDateTime.now();
				int              timeoutSec = 10;
				AudioPlayerState pState     = audioManager.getPlayerState();
				while (pState != AudioPlayerState.PLAYING && OffsetDateTime.now().isBefore(start.plusSeconds(timeoutSec)))
				{
					pState = audioManager.getPlayerState();
				}
				
				String currentSongURI = audioManager.getScheduler().getLastTrack() == null ? "" :
						audioManager.getScheduler().getLastTrack().getInfo().uri;
				
				/* Inverse - A match indicates the song originally playing first at time of reload is now the old one. */
				boolean correctSong = !currentSongURI.matches(audioManager.player.getPlayingTrack().getInfo().uri);
				
				/* Attempts to seek to last saved location in the song */
				if (currentSongPosition > 0 && audioManager.getPlayer().getPlayingTrack().isSeekable() && correctSong)
				{
					audioManager.getPlayer().getPlayingTrack().setPosition(currentSongPosition);
				}
			});
		}
	}
	
	public TrackLoader getTrackLoader()
	{
		return trackLoader;
	}
	
	public AudioPlayerState getPlayerState()
	{
		return playerState;
	}
	
	public TrackScheduler getScheduler()
	{
		return scheduler;
	}
	
	public void onShutdown()
	{
		timer.cancel();
		timerTask = null;
		saveAudioState();
	}
	
	public void saveAudioState()
	{
		AudioManager audioManager = GuildContext.get(guild_id).audioManager();
		
		String queueURLs = audioManager.getScheduler()
				.getQueue()
				.stream()
				.map((track) -> track.getInfo().uri)
				.collect(Collectors.joining(","));
		
		AudioTrack playingTrack = audioManager.getPlayer().getPlayingTrack();
		
		if (playingTrack != null)
		{
			DBQueryHandler.set(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.ACTIVESONG, playingTrack.getInfo().uri);
			DBQueryHandler.set(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.ACTIVESONGDURATION, playingTrack.getPosition());
		}
		else
		{
			DBQueryHandler.set(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.ACTIVESONG, "");
			DBQueryHandler.set(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.ACTIVESONGDURATION, 0);
		}
		
		/* Check not required as empty queue adds nothing */
		DBQueryHandler.set(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.CURRENTQUEUE, queueURLs);
		DBQueryHandler.set(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.PAUSED, player.isPaused());
		DBQueryHandler.set(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.REPEATSONG, scheduler.isRepeatSong());
		DBQueryHandler.set(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.REPEATQUEUE, scheduler.isRepeatQueue());
		DBQueryHandler.set(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.VOLUME, player.getVolume());
	}
}