package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.TrackLoader;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;

public class Play extends Command
{
	
	public Play()
	{
		super(false);
	}
	
	@Override
	public String getName()
	{
		return "Play/Pause";
	}
	
	@Override
	public List<String> getAliases()
	{
		return List.of("P");
	}
	
	@Override
	public String getHelp()
	{
		return "`Play/Pause : Play or pause the player.`\n" + "`Play [url] : Adds the specified song/playlist to queue.`\n" + "`Play -[next|n] [url] : Adds the specified song/playlist to next in queue`" + "`Aliases : " + String
				.join(" ", getAliases()) + "`";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		List<String> args = ctx.getArgs();
		Guild guild = ctx.getGuild();
		TrackLoader trackLoader = GuildManager.getContext(ctx.getGuild())
				.audioManager()
				.getTrackLoader();
		
		if (args.isEmpty())
		{
			AudioPlayer player = GuildManager.getContext(guild)
											 .audioManager()
											 .getPlayer();
			boolean isPaused = player.isPaused();
			player.setPaused(! isPaused);
		}
		else if (args.get(0).matches("(?i)(-next|-n)"))
		{
			String song = String.join(" ", args.subList(1, args.size()));
			playNext(song, ctx.getEvent(), trackLoader);
		}
		else
		{
			trackLoader.load(ctx.getChannel(), ctx.getMember(), args.get(0));
		}
	}
	
	private void playNext(String song, GuildMessageReceivedEvent event, TrackLoader trackLoader)
	{
		AudioManager audioManager = GuildManager.getContext(event.getGuild())
				.audioManager();
		
		ArrayList<AudioTrack> currentQueue = audioManager.getScheduler().getQueue();
		
		String trackName;
		AudioTrack nextTrack = null;
		for (AudioTrack track : currentQueue)
		{
			trackName = track.getInfo().title.toLowerCase();
			if (song.matches("\\d+") && currentQueue.indexOf(track) == Integer.parseInt(song) - 1) // Subtract '1' for '0' based counting.
			{
				nextTrack = track;
				break;
			}
			else if (trackName.contains(song.trim().toLowerCase()))
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
			
			audioManager.getScheduler().clearQueue();
			audioManager.getScheduler().queueList(currentQueue, event.getChannel());
		}
		else
		{
			audioManager.getScheduler().clearQueue();
			
			trackLoader.load(event.getChannel(), event.getMember(), song);
            /*
                Wait 1s to allow for the new entry to load. Event's are handled asynchronously.
             */
			try
			{
				Thread.sleep(1000);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			
			audioManager.getScheduler().queueList(currentQueue, event.getChannel());
		}
		
		GuildManager.getContext(event.getGuild())
					.playerPrinter()
					.printQueue(GuildManager.getContext(event.getGuild()).audioManager(), event.getChannel());
		
		GuildManager.getContext(event.getGuild())
					.playerPrinter()
					.printNowPlaying(GuildManager.getContext(event.getGuild()).audioManager(), event.getChannel());
	}
	
//	private void jumpPosition()
//	{
//
//	}
	
	
	
}
