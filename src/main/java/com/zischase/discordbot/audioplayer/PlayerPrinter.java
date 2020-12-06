package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PlayerPrinter
{
	public PlayerPrinter()
	{
	}
	
	public void printNowPlaying(AudioManager audioManager, TextChannel channel)
	{
		deletePrevious(channel, "Now Playing");

		AudioPlayer player = audioManager.getPlayer();
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(Color.CYAN);
		
		if (player.getPlayingTrack() == null)
		{
			embed.setTitle("Nothing Playing");
			embed.setColor(Color.darkGray);
			embed.setFooter(". . .");
		}
		else
		{
			AudioTrackInfo info = player.getPlayingTrack().getInfo();


			long duration = info.length / 1000;
			long position = player.getPlayingTrack().getPosition() / 1000;
			
			String timeTotal = String.format("%d:%02d:%02d", duration / 3600, (duration % 3600) / 60, (duration % 60));
			
			String timeCurrent = String.format("%d:%02d:%02d", position / 3600, (position % 3600) / 60, (position % 60));

			
//			embed.setThumbnail(Config.get("MEDIA_PLAYER_ICON"));
			embed.setTitle("Now Playing");
			embed.appendDescription(info.title + "\n\n");
			
			if (player.isPaused())
			{
				embed.appendDescription("Paused");
			}
			else if (player.getPlayingTrack().getInfo().length == Integer.MAX_VALUE)
			{
				embed.appendDescription("Live");
			}
			else
			{
				embed.appendDescription(timeCurrent + " - " + timeTotal);
			}
			
			embed.setFooter(info.uri);
		}
		
		long delayMS = 2000;
		if (player.getPlayingTrack() != null)
		{
			delayMS = player.getPlayingTrack().getDuration() - player.getPlayingTrack().getPosition();
		}
		
		Message message = new MessageBuilder().setEmbed(embed.build())
											  .build();
		
		long finalDelayMS = delayMS;


				   channel.sendMessage(message)
						  .queue(msg ->
						  {
							  if (channel.getHistory().getRetrievedHistory().contains(msg))
							  {
								  msg.delete()
										  .queueAfter(finalDelayMS, TimeUnit.MILLISECONDS);
							  }
						  });
	}
	
	public void printQueue(AudioManager audioManager, TextChannel channel)
	{
		
		ArrayList<AudioTrack> queue = audioManager.getScheduler()
												  .getQueue();
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(Color.BLUE);
		embed.setTitle("Queue");

		deletePrevious(channel, "Queue");

		if (queue.isEmpty())
		{
			embed.appendDescription("Nothing in the queue.");
			
			channel.sendMessage(embed.build())
				   .queue(msg ->
				   {
					   if (channel.getHistory().getRetrievedHistory().contains(msg))
					   {
						   msg.delete()
							  .queueAfter(5000, TimeUnit.MILLISECONDS);
					   }
				   });
			return;
		}


		if (queue.size() > 1)
		{
			Collections.reverse(queue);
			
			int index = queue.size();
			embed.appendDescription("```\n");
			// Subtract 1 to remove next(last in list) song in queue to display separately.
			for (AudioTrack track : queue)
			{
				if (queue.get(queue.size() - 1) == track)
				{
					continue;
				}
				
				embed.appendDescription((index) + ". ");
				index--;
				
				embed.appendDescription(track.getInfo().title + "\n");
				
				// Limit is 2048 characters per embed description. This allows some buffer. Had issues at 2000 characters.
				if (embed.getDescriptionBuilder().toString().length() >= 1800)
				{
					embed.appendDescription("```");
					channel.sendMessage(embed.build())
						   .queue();
					
					embed = new EmbedBuilder();
					embed.setColor(Color.BLUE);
					embed.setTitle("Queue");
					embed.appendDescription("```\n");
				}
			}
			embed.appendDescription("```");
		}
		
		embed.appendDescription(" ```fix\nUp Next: " + queue.get(queue.size() - 1).getInfo().title + "```");

		channel.sendMessage(embed.build())
			   .queue(msg ->
			   {
				   if (channel.getHistory().getRetrievedHistory().contains(msg))
				   {
					   msg.delete()
							   .queueAfter(5000, TimeUnit.MILLISECONDS);
				   }
			   });
	}
	
	private void deletePrevious(TextChannel textChannel, String titleSearch)
	{

		if (titleSearch == null)
		{
			return;
		}

		List<Message> msgList = textChannel.getHistory()
				.retrievePast(25)
				.complete()
				.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> ! msg.getEmbeds().isEmpty())
				.filter(msg -> ! msg.isPinned())
				.filter(msg ->
				{
					if (msg.getEmbeds().get(0).getTitle() != null)
					{

						String title = msg.getEmbeds().get(0).getTitle();
						assert title != null;
						return title.equalsIgnoreCase(titleSearch);
					}
					return false;
				})
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.collect(Collectors.toList());

		if (msgList.size() == 1)
		{
			textChannel.deleteMessageById(msgList.get(0).getId())
					.complete();
		}
		else if (msgList.size() > 1)
		{
			textChannel.deleteMessages(msgList)
					.complete();
		}
	}
	
}
