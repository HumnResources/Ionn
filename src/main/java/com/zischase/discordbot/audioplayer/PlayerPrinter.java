package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
	private Message message = null;

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

			if (info.title != null && ! info.title.isEmpty())
			{
				embed.appendDescription(info.title + "\n\n");
			}
			else if (info.author != null && ! info.author.isEmpty())
			{
				embed.appendDescription(info.author + "\n\n");
			}
			else
			{
				embed.appendDescription("-----\n\n");
			}


			if (player.isPaused())
			{
				embed.appendDescription("Paused");
			}
			else if (player.getPlayingTrack().getDuration() == Long.MAX_VALUE)
			{
				embed.appendDescription("Live");
			}
			else
			{
				embed.appendDescription(timeCurrent + " - " + timeTotal);
				String progressBar = progressPercentage((int) position, (int) duration);
				embed.appendDescription(System.lineSeparator() + progressBar);
			}
			
			embed.setFooter(info.uri);
		}
		
		long delayMS = 2000;
		if (player.getPlayingTrack() != null)
		{
			delayMS = player.getPlayingTrack().getDuration() - player.getPlayingTrack().getPosition();
		}
		
		this.message = new MessageBuilder().setEmbed(embed.build())
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
				.filter(msg ->
				{
					boolean isBot = msg.getAuthor().isBot();
					boolean hasEmbeds = ! msg.getEmbeds().isEmpty();
					boolean notPinned = ! msg.isPinned();
					boolean createdBeforeNow = msg.getTimeCreated().isBefore(OffsetDateTime.now());
					boolean lessThan2Weeks = msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14));

					if (isBot && hasEmbeds && notPinned && createdBeforeNow && lessThan2Weeks)
					{
						MessageEmbed embed = msg.getEmbeds().get(0);
						if (embed.getTitle() != null)
						{
							return embed.getTitle().matches("(?i)("+titleSearch+"|Nothing Playing)");
						}
					}
					return false;
				})
				.collect(Collectors.toList());

		if (msgList.size() == 1)
		{
			textChannel.deleteMessageById(msgList.get(0).getId())
					.queue(null, null);
		}
		else if (msgList.size() > 1)
		{
			textChannel.deleteMessages(msgList)
					.queue(null, null);
		}
	}

	private String progressPercentage(int done, int total) {
		int size = 25;
		String iconLeftBoundary = "|";
		String iconDone = "~";
		String iconRemain = "_";
		String iconRightBoundary = "|";

		if (done > total) {
			throw new IllegalArgumentException();
		}
		int donePercents = (100 * done) / total;
		int doneLength = size * donePercents / 100;

		StringBuilder bar = new StringBuilder(iconLeftBoundary);
		for (int i = 0; i < size; i++) {
			if (i < doneLength) {
				bar.append(iconDone);
			} else {
				bar.append(iconRemain);
			}
		}
		bar.append(iconRightBoundary);

		return bar.toString();
	}
	
}
