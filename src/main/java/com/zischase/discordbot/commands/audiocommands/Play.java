package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.TrackLoader;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Play extends Command
{
	
	public Play()
	{
		super(false);
	}
	
	@Override
	public @NotNull String shortDescription() {
		return "Plays a requested song.";
	}
	
	@Override
	public List<String> getAliases()
	{
		return List.of("P");
	}
	
	@Override
	public String helpText()
	{
		return "`Play/Pause : Play or pause the player.`\n" + "`Play [url] : Adds the specified song/playlist to queue.`\n" + "`Play -[next|n] [url] : Adds the specified song/playlist to next in queue`" + "`Aliases : " + String
				.join(" ", getAliases()) + "`";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		VoiceChannel voiceChannel = ctx.getMember().getVoiceState() != null ?
									ctx.getMember().getVoiceState().getChannel() : null;
		
		List<String> args = ctx.getArgs();
		Guild guild = ctx.getGuild();
		TrackLoader trackLoader = GuildHandler.getContext(ctx.getGuild())
											  .audioManager()
											  .getTrackLoader();
		
		if (args.isEmpty() || args.get(0).matches("(?i)-(pause)"))
		{
			AudioPlayer player = GuildHandler.getContext(guild)
											 .audioManager()
											 .getPlayer();
			player.setPaused(! player.isPaused());
		}
		else if (args.get(0).matches("(?i)-(next|n)"))
		{
			String song = String.join(" ", args.subList(1, args.size()));
			playNext(song, ctx.getEvent(), trackLoader);
		}
		else
		{
			List<Message.Attachment> attachments = ctx.getEvent()
													  .getMessage()
													  .getAttachments();
			
			/* Checks to see if we have a potential link in the message */
			if (!attachments.isEmpty()) {
				trackLoader.load(ctx.getChannel(), voiceChannel, attachments.get(0).getProxyUrl());
			}
			/* Otherwise we check to see if they input a string, process using YT */
			else if (!args.isEmpty()) {
				new Youtube().handle(ctx);
			}
		}
	}
	
	private void playNext(String song, GuildMessageReceivedEvent event, TrackLoader trackLoader)
	{
		VoiceChannel voiceChannel = event.getMember() != null && event.getMember().getVoiceState() != null ?
									event.getMember().getVoiceState().getChannel() : null;
		
		
		AudioManager audioManager = GuildHandler.getContext(event.getGuild())
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
		}
		else
		{
			audioManager.getScheduler().clearQueue();
			
			trackLoader.load(event.getChannel(), voiceChannel, song);
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
			
		}
		audioManager.getScheduler().queueList(currentQueue, event.getChannel());
		
		GuildHandler.getContext(event.getGuild())
					.playerPrinter()
					.printQueue(GuildHandler.getContext(event.getGuild()).audioManager(), event.getChannel());
		
		GuildHandler.getContext(event.getGuild())
					.playerPrinter()
					.printNowPlaying(GuildHandler.getContext(event.getGuild()).audioManager(), event.getChannel());
	}
	
}
