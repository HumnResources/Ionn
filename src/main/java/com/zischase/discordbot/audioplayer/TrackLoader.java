package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sun.istack.Nullable;
import com.zischase.discordbot.DBQuery;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.MessageSendHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.apache.commons.collections4.map.LinkedMap;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

public class TrackLoader implements AudioLoadResultHandler
{
	
	private static final LinkedMap<String, AudioTrack> CACHE        = new LinkedMap<>(50);
	private final        String                        guildID;
	private final        AtomicReference<Boolean>      attemptRetry = new AtomicReference<>();
	private final        Semaphore                     semaphore    = new Semaphore(1);
	private              MessageSendHandler            messageSendHandler;
	
	public TrackLoader(String guildID)
	{
		this.guildID = guildID;
		
		/* Once its fully loaded it will update the message delivery method */
		CompletableFuture.runAsync(() ->
		{
			while (GuildContext.get(guildID) == null)
			{
				messageSendHandler = GuildContext.get(guildID).messageSendHandler();
			}
		});
	}
	
	public void loadYTSearchResults(TextChannel textChannel, @Nullable VoiceChannel voiceChannel, String uri)
	{
		if (joinChannels(voiceChannel, textChannel))
		{
			uri = "ytsearch: " + uri;
			
			GuildContext.get(guildID)
					.audioManager()
					.getPlayerManager()
					.loadItem(uri, this);
		}
	}
	
	private boolean joinChannels(@Nullable VoiceChannel voiceChannel, TextChannel textChannel)
	{
		if (voiceChannel == null)
		{
			voiceChannel = textChannel.getGuild().getVoiceChannelById(DBQueryHandler.get(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.VOICECHANNEL));
			if (voiceChannel == null)
			{
				return false;
			}
		}
		
		Member bot = textChannel.getGuild().getMember(textChannel.getJDA().getSelfUser());
		DBQueryHandler.set(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.TEXTCHANNEL, textChannel.getId());
		DBQueryHandler.set(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.VOICECHANNEL, voiceChannel.getId());
		
		if (!voiceChannel.getMembers().contains(bot))
		{
			textChannel.getJDA()
					.getDirectAudioController()
					.connect(voiceChannel);
		}
		
		return true;
	}
	
	public void loadNext(TextChannel textChannel, String songName)
	{
		String                guildID      = textChannel.getGuild().getId();
		AudioManager          audioManager = GuildContext.get(guildID).audioManager();
		ArrayList<AudioTrack> currentQueue = audioManager.getScheduler().getQueue();
		AudioTrack            nextTrack    = null;
		String                trackName;
		
		for (AudioTrack track : currentQueue)
		{
			trackName = track.getInfo().title.toLowerCase();
			if (songName.matches("\\d+") && currentQueue.indexOf(track) == Integer.parseInt(songName) - 1) // Subtract '1' for '0' based counting.
			{
				nextTrack = track;
				break;
			}
			else if (trackName.contains(songName.trim().toLowerCase()))
			{
				nextTrack = track;
				break;
			}
		}
		
		boolean songFound = nextTrack != null && currentQueue.contains(nextTrack);
		if (songFound)
		{
			currentQueue.remove(nextTrack);
			currentQueue.add(0, nextTrack);
		}
		else
		{
			AtomicReference<AudioTrack> track = new AtomicReference<>(null);
			audioManager.getPlayerManager()
					.loadItem("ytsearch: " + songName, new FunctionalResultHandler(
							track::set,
							playlist -> track.set(playlist.getTracks().get(0)),
							null,
							null)
					);
			
			OffsetDateTime start = OffsetDateTime.now();
			while (track.get() == null)
			{
				if (OffsetDateTime.now().isAfter(start.plusSeconds(5)))
				{
					return;
				}
			}
			
			currentQueue = audioManager.getScheduler().getQueue();
			currentQueue.add(0, track.get());
		}
		audioManager.getScheduler().clearQueue();
		audioManager.getScheduler().queueList(currentQueue);
	}
	
	public void load(TextChannel textChannel, @Nullable VoiceChannel voiceChannel, String uri)
	{
		if (joinChannels(voiceChannel, textChannel))
		{
			try
			{
				/* Checks to see if we have a valid URL */
				new URL(uri);
				if (CACHE.containsKey(uri))
				{
					GuildContext.get(guildID)
							.audioManager()
							.getScheduler()
							.queueAudio(CACHE.get(uri).makeClone());
				}
				else
				{
					GuildContext.get(guildID)
							.audioManager()
							.getPlayerManager()
							.loadItem(uri, this);
				}
			}
			/* No Match - Search YouTube instead */ catch (MalformedURLException e)
			{
				GuildContext.get(guildID)
						.audioManager()
						.getPlayerManager()
						.loadItem("ytsearch: " + uri, new FunctionalResultHandler(this::trackLoaded, (playlist) -> trackLoaded(playlist.getTracks().get(0)), this::noMatches, this::loadFailed));
			}
		}
	}
	
	public void loadURIListSequentially(List<String> uriList)
	{
		CompletableFuture.runAsync(() ->
		{
			for (String uri : uriList)
			{
				try
				{
					semaphore.acquire();
				} catch (InterruptedException e)
				{
					throw new RuntimeException(e);
				}
				
				GuildContext.get(guildID)
						.audioManager()
						.getPlayerManager()
						.loadItem(uri, new AudioLoadResultHandler()
						{
							@Override
							public void trackLoaded(AudioTrack audioTrack)
							{
								load(audioTrack);
								semaphore.release();
							}
							
							@Override
							public void playlistLoaded(AudioPlaylist audioPlaylist)
							{
								
								semaphore.release();
							}
							
							@Override
							public void noMatches()
							{
								semaphore.release();
							}
							
							@Override
							public void loadFailed(FriendlyException e)
							{
								semaphore.release();
							}
						});
			}
		});
	}
	
	private void load(AudioTrack audioTrack)
	{
		boolean oldTrack = GuildContext.get(guildID).audioManager().getScheduler().getLastTrack() != null &&
				GuildContext.get(guildID).audioManager().getScheduler().getLastTrack().getInfo().uri.equals(audioTrack.getInfo().uri);
		
		if (!oldTrack)
		{
			attemptRetry.set(true);
		}
		
		CACHE.putIfAbsent(audioTrack.getInfo().uri, audioTrack);
		if (CACHE.size() > 300)
		{
			CACHE.remove(0);
		}
		
		GuildContext.get(guildID)
				.audioManager()
				.getScheduler()
				.queueAudio(audioTrack);
	}
	
	public void load(VoiceChannel voiceChannel, TextChannel textChannel, AudioTrack audioTrack)
	{
		if (joinChannels(voiceChannel, textChannel))
		{
			this.trackLoaded(audioTrack);
		}
	}
	
	@Override
	public void trackLoaded(AudioTrack audioTrack)
	{
		load(audioTrack);
		
		if (GuildContext.get(guildID).audioManager().getScheduler().getQueue().isEmpty())
		{
			/* No point in displaying added message if it's the next song */
			return;
		}
		
		TextChannel channel = GuildContext.get(guildID)
				.guild()
				.getTextChannelById(DBQueryHandler.get(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.TEXTCHANNEL));
		
		songAddedConfirmation(channel, audioTrack);
	}
	
	private void songAddedConfirmation(TextChannel channel, AudioTrack audioTrack)
	{
		if (channel == null || audioTrack.getInfo() == null || messageSendHandler == null)
		{
			return;
		}
		
		String s;
		
		if (audioTrack.getInfo().title != null)
		{
			s = "Added: `%s` to queue.".formatted(audioTrack.getInfo().title);
		}
		else
		{
			s = "Added: `%s` to queue.".formatted(audioTrack.getIdentifier());
		}
		
		messageSendHandler.sendAndDeleteMessageChars.accept(channel, s);
	}
	
	@Override
	public void playlistLoaded(AudioPlaylist audioPlaylist)
	{
		GuildContext.get(guildID)
				.audioManager()
				.getScheduler()
				.queueList(audioPlaylist);
	}
	
	@Override
	public void noMatches()
	{
		TextChannel channel = GuildContext.get(guildID)
				.guild()
				.getTextChannelById(DBQueryHandler.get(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.TEXTCHANNEL));
		
		messageSendHandler.sendAndDeleteMessageChars.accept(channel, "Darn, no matches found !");
	}
	
	@Override
	public void loadFailed(FriendlyException e)
	{
		TextChannel channel = GuildContext.get(guildID)
				.guild()
				.getTextChannelById(DBQueryHandler.get(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.TEXTCHANNEL));
		AudioTrack lastSong = GuildContext.get(guildID)
				.audioManager()
				.getScheduler()
				.getLastTrack();
		
		
		if (CACHE.containsValue(lastSong)) CACHE.remove(lastSong.getInfo().uri);
		
		if (attemptRetry.get())
		{
			attemptRetry.set(false);
			VoiceChannel voiceChannel = GuildContext.get(guildID)
					.guild()
					.getVoiceChannelById(DBQueryHandler.get(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.VOICECHANNEL));
			
			this.load(channel, voiceChannel, lastSong.getInfo().uri);
		}
		
		messageSendHandler.sendAndDeleteMessageChars.accept(channel, "Loading failed for `" + lastSong.getInfo().title + "`, sorry.");
	}
}
