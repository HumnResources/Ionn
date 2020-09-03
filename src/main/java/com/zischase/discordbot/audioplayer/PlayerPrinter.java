package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.zischase.discordbot.Config;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
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
	private final AudioManager audioManager;
	
	public PlayerPrinter(Guild guild)
	{
		this.audioManager = GuildManager.getContext(guild)
				.audioManager();
	}
	
	public void printNowPlaying(TextChannel channel)
	{
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
			AudioTrackInfo info = player.getPlayingTrack()
										.getInfo();
			embed.setThumbnail(Config.get("MEDIA_PLAYER_ICON"));
			embed.setTitle("Now Playing");
			embed.appendDescription(info.title + "\n\n" + info.author + "\n");
			embed.setFooter(info.uri);
			
			if (player.isPaused())
			{
				embed.appendDescription("Paused");
			}
			
		}
		
		long delayMS = 2000;
		if (player.getPlayingTrack() != null)
		{
			delayMS = player.getPlayingTrack()
							.getDuration() - player.getPlayingTrack()
												   .getPosition();
		}
		
		Message message = new MessageBuilder().setEmbed(embed.build())
											  .build();
		
		long finalDelayMS = delayMS;
		channel.getHistory()
			   .retrievePast(100)
			   .queue(messages -> {
			   		List<Message> deleteList = messages.stream()
							.filter(msg -> msg.getAuthor()
											  .isBot())
							.filter(msg -> ! msg.getEmbeds()
												.isEmpty())
							.filter(msg ->
							{
								if (msg.getEmbeds()
									   .get(0)
									   .getTitle() != null)
								{
									String title = msg.getEmbeds()
													  .get(0)
													  .getTitle();
									assert title != null;
									return title.equalsIgnoreCase("Now Playing");
								}
								return false;
							})
							.collect(Collectors.toList());
				
				   if (! deleteList.isEmpty())
				   {
					   if (deleteList.size() == 1)
					   {
						   channel.deleteMessageById(deleteList.get(0)
															 .getId())
								  .queue(null, Throwable::getSuppressed);
					   }
					   else
					   {
						   channel.deleteMessages(deleteList)
								  .queue(null, Throwable::getSuppressed);
					   }
				   }
				   channel.sendMessage(message)
						  .queue(msg ->
						  {
						  	if (msg == null)
							{
								return;
							}
							  msg.delete()
								 .queueAfter(finalDelayMS, TimeUnit.MILLISECONDS);
						  });
			   });
	}
	
	public void printQueue(TextChannel channel)
	{
		deletePrevious(channel);
		
		ArrayList<AudioTrack> queue = audioManager.getScheduler()
												  .getQueue();
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(Color.BLUE);
		
		if (queue.isEmpty())
		{
			embed.appendDescription("Nothing in the queue.");
			
			channel.sendMessage(embed.build())
				   .queue(msg -> msg.delete().queueAfter(5000, TimeUnit.MILLISECONDS));
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
				
				if (index > 1)
				{
					embed.appendDescription(index + ". ");
				}
				index--;
				
				embed.appendDescription(track.getInfo().title + "\n");
				
				// Limit is 2048 characters per embed description. This allows some buffer. Had issues at 2000 characters.
				if (embed.getDescriptionBuilder()
						 .toString()
						 .length() >= 1800)
				{
					embed.appendDescription("```");
					channel.sendMessage(embed.build())
						   .queue();
					
					embed = new EmbedBuilder();
					embed.setColor(Color.BLUE);
					embed.appendDescription("```\n");
				}
			}
			embed.appendDescription("```");
		}
		
		embed.appendDescription(" ```fix\nUp Next: " + queue.get(0).getInfo().title + "```");
		
		channel.sendMessage(embed.build())
			   .queue();
	}
	
	private void deletePrevious(TextChannel textChannel)
	{
		
		textChannel.getHistory()
				   .retrievePast(100)
				   .queue(messages ->
				   {
					   List<Message> msgList = messages.stream()
													   .filter(msg -> msg.getAuthor()
																		 .isBot())
													   .filter(msg -> ! msg.getEmbeds()
																		   .isEmpty())
													   .filter(msg -> msg.getTimeCreated()
																		 .isBefore(OffsetDateTime.now()))
													   .collect(Collectors.toList());
					   if (msgList.size() == 1)
					   {
						   textChannel.deleteMessageById(msgList.get(0)
																.getId())
									  .queue(null, Throwable::getSuppressed);
					   }
					   else if (msgList.size() > 1)
					   {
						   textChannel.deleteMessages(msgList)
									  .queue(null, Throwable::getSuppressed);
					   }
				   });
		
	}
	
}
