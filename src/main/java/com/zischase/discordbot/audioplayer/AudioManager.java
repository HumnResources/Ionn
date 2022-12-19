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
	private final TimerTask timerTask = getPlayerStateTimerTask();
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
		
		timer.scheduleAtFixedRate(this.timerTask, 0, OBSERVER_TICK_SPEED_MS);
	}
	
	public AudioPlayer getPlayer()
	{
		return player;
	}
	
	public AudioPlayerSendHandler getSendHandler()
	{
		return new AudioPlayerSendHandler(player);
	}
	
	private TimerTask getPlayerStateTimerTask()
	{
		return new TimerTask()
		{
			@Override
			public void run()
			{
				if (player.getPlayingTrack() == null)
				{
					return;
				}
				
				saveAudioState();
				
				if (player.getPlayingTrack() == null)
				{
					return;
				}
				
				AudioTrackState trackState    = player.getPlayingTrack().getState();
				boolean    isPaused = DBQueryHandler.get(guild_id, DBQuery.PAUSED).equals("t");
				
				switch (trackState)
				{
					case LOADING -> playerState = AudioPlayerState.LOADING_TRACK;
					case INACTIVE -> playerState = AudioPlayerState.STOPPED;
					case PLAYING ->
					{
						if (isPaused)
						{
							playerState = AudioPlayerState.PAUSED;
							break;
						}
						playerState = AudioPlayerState.PLAYING;
					}
					case STOPPING ->
					{
						if (isPaused)
						{
							playerState = AudioPlayerState.PAUSED;
							break;
						}
						playerState = AudioPlayerState.STOPPED;
					}
					case SEEKING -> playerState = AudioPlayerState.PLAYING;
					case FINISHED ->
					{
						if (AudioManager.this.getScheduler().getQueue().isEmpty())
						{
							playerState = AudioPlayerState.PLAYING;
						}
					}
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
				
				audioManager.scheduler.setVolume(Integer.parseInt(DBQueryHandler.get(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.VOLUME)));
				
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
				
				audioManager.scheduler.setRepeatSong(Boolean.getBoolean(DBQueryHandler.get(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.REPEATSONG)));
				audioManager.scheduler.setRepeatQueue(Boolean.getBoolean(DBQueryHandler.get(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.REPEATQUEUE)));
				audioManager.scheduler.setPaused(Boolean.getBoolean(DBQueryHandler.get(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.PAUSED)));
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
		timerTask.cancel();
		timer.cancel();
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
			DBQueryHandler.set(guild_id, DBQuery.ACTIVESONG, playingTrack.getInfo().uri);
			DBQueryHandler.set(guild_id, DBQuery.ACTIVESONGDURATION, playingTrack.getPosition());
		}
		else
		{
			DBQueryHandler.set(guild_id, DBQuery.ACTIVESONG, "");
			DBQueryHandler.set(guild_id, DBQuery.ACTIVESONGDURATION, 0);
		}
	
		net.dv8tion.jda.api.managers.AudioManager discordAudioManager = GuildContext.get(guild_id).guild().getAudioManager();
		if (discordAudioManager.isConnected() && discordAudioManager.getConnectedChannel() != null) {
			DBQueryHandler.set(guild_id, DBQuery.VOICECHANNEL, discordAudioManager.getConnectedChannel().getId());
		}
		
		/* Check not required as empty queue adds nothing */
		DBQueryHandler.set(guild_id, DBQuery.CURRENTQUEUE, queueURLs);
		DBQueryHandler.set(guild_id, DBQuery.PAUSED, scheduler.isPaused());
		DBQueryHandler.set(guild_id, DBQuery.REPEATSONG, scheduler.isRepeatSong());
		DBQueryHandler.set(guild_id, DBQuery.REPEATQUEUE, scheduler.isRepeatQueue());
		DBQueryHandler.set(guild_id, DBQuery.VOLUME, scheduler.getVolume());
		
		if (audioManager.getNowPlayingMessageHandler().getNowPlayingMessage() != null)
		{
			DBQueryHandler.set(guild_id, DBQuery.MEDIA_SETTINGS, DBQuery.TEXTCHANNEL, audioManager.getNowPlayingMessageHandler().getNowPlayingMessage().getChannel().getId());
		}
		
	}
	
}