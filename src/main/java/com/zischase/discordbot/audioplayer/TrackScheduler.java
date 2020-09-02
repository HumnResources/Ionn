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

public class TrackScheduler extends AudioEventAdapter
{
	private static final Logger                    LOGGER = LoggerFactory.getLogger(TrackScheduler.class);
	private final        AudioPlayer               player;
	private final        BlockingQueue<AudioTrack> queue;
	
	
	private       boolean             repeat      = false;
	private       AudioTrack          lastTrack;
	private       TextChannel         textChannel = null;
	
	public TrackScheduler(AudioPlayer player)
	{
		this.player = player;
		this.queue = new LinkedBlockingQueue<>();
		
		LOGGER.info("TrackScheduler created.");
	}
	
	public void setRepeat(boolean repeat)
	{
		this.repeat = repeat;
	}
	
	public boolean isRepeat()
	{
		return repeat;
	}
	
	public void queueAudio(AudioTrack track, TextChannel textChannel)
	{
		this.textChannel = textChannel;
		boolean hasPlayingTrack = GuildManager.getContext(textChannel.getGuild())
											  .audioManager()
											  .getPlayer()
											  .getPlayingTrack() != null;
		
		if (hasPlayingTrack)
		{
			textChannel.sendMessage("Added `" + track.getInfo().title + "` to the queue.")
					   .queue();
		}
		
		if (! player.startTrack(track, true))
		{ // noInterrupt: True == add to queue; Returns true if added
			queue.offer(track);
		}
	}
	
	public void queueList(ArrayList<AudioTrack> tracks, TextChannel channel)
	{
		if (tracks.isEmpty())
		{
			return;
		}
		this.textChannel = channel;
		
		for (AudioTrack track : tracks)
		{
			this.queue.add(track.makeClone());
		}
		
		if (player.isPaused())
		{
			player.setPaused(false);
		}
		
		else if (player.getPlayingTrack() == null)
		{
			player.startTrack(queue.poll(), false);
		}
	}
	
	public void clearQueue()
	{
		this.queue.clear();
	}
	
	public ArrayList<AudioTrack> getQueue()
	{
		ArrayList<AudioTrack> trackList = new ArrayList<>();
		
		if (! this.queue.isEmpty())
		{
			trackList.addAll(queue);
		}
		
		return trackList;
	}
	
	public void nextTrack()
	{
		if (repeat)
		{
			if (player.getPlayingTrack() != null)
			{
				queue.add(player.getPlayingTrack().makeClone());
			}
			else if (lastTrack != null)
			{
				queue.add(lastTrack.makeClone());
			}
		}
		
		this.player.startTrack(queue.poll(), false);
	}
	
	public void prevTrack()
	{
		this.player.startTrack(lastTrack.makeClone(), false);
	}
	
	@Override
	public void onPlayerPause(AudioPlayer player)
	{
		GuildManager.getContext(textChannel.getGuild())
					.getPlayerPrinter()
					.printNowPlaying(textChannel);
	}
	
	@Override
	public void onPlayerResume(AudioPlayer player)
	{
		GuildManager.getContext(textChannel.getGuild())
					.getPlayerPrinter()
					.printNowPlaying(textChannel);
	}
	
	@Override
	public void onTrackStart(AudioPlayer player, AudioTrack track)
	{
		if (! queue.isEmpty())
		{
			GuildManager.getContext(textChannel.getGuild())
						.getPlayerPrinter()
						.printQueue(textChannel);
		}
		
		GuildManager.getContext(textChannel.getGuild())
					.getPlayerPrinter()
					.printNowPlaying(textChannel);
	}
	
	@Override
	public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason)
	{
		if (endReason.equals(AudioTrackEndReason.LOAD_FAILED))
		{
			boolean tooManyAttempts = false;
			
			if (lastTrack != null)
			{
				tooManyAttempts = lastTrack.getInfo()
						.identifier
						.equalsIgnoreCase(track.getInfo().identifier);
			}
			
			if (tooManyAttempts)
			{
				nextTrack();
			}
			else
			{
				textChannel.sendMessage("Loading failed. . . \nTrying again!")
						   .complete()
						   .delete()
						   .queueAfter(5000, TimeUnit.MILLISECONDS);
				
				Member member = textChannel.getGuild()
										   .getMember(textChannel.getJDA()
																 .getSelfUser());
				
				new TrackLoader().load(textChannel, member, track.getInfo().identifier);
			}
		}
		lastTrack = track;
		
		if (endReason.mayStartNext)
		{
			nextTrack();
		}
		
	}
	
	@Override
	public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception)
	{
		if (lastTrack.getIdentifier()
					 .matches(track.getIdentifier()))
		{
			textChannel.sendMessage("There was an error playing the song, I tried twice but... \nI'm afraid we have to move on.")
					   .complete()
					   .delete()
					   .queueAfter(5000, TimeUnit.MILLISECONDS);
		}
		else
		{
			lastTrack = track;
			player.playTrack(track.makeClone());
		}
	}
	
	@Override
	public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs)
	{
		player.stopTrack();
		
		if (! queue.isEmpty())
		{
			nextTrack();
		}
	}
}
