package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.audioplayer.TrackScheduler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.TextChannel;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Queue extends Command
{
	
	public Queue()
	{
		super(false);
	}
	
	@Override
	public List<String> getAliases()
	{
		return List.of("Q", "Qu");
	}
	
	@Override
	public String getHelp()
	{
		return "`Queue : Show current songs in the queue.`\n" + "`Queue -[clear|c] : Clears the current queue.`\n" + "`Aliases : " + String
				.join(" ", getAliases()) + "`";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		List<String> args = ctx.getArgs();
		TrackScheduler scheduler = GuildManager.getContext(ctx.getGuild())
											   .getAudioManager()
											   .getScheduler();
		
		if (! args.isEmpty())
		{
			if (args.get(0)
					.matches("(?i)(-clear|-c)"))
			{
				EmbedBuilder embed = new EmbedBuilder();
				embed.setColor(Color.BLUE);
				scheduler.clearQueue();
				
				embed.appendDescription("Queue cleared.");
				ctx.getChannel()
				   .sendMessage(embed.build())
				   .queue();
			}
			else if (args.get(0)
						 .matches("(?i)(\\d+)"))
			{
				bringToFront(Integer.parseInt(args.get(0)), scheduler, ctx.getChannel());
				
			}
			else if (args.get(0)
						 .matches("(?i)(-jump|-j)"))
			{
				if (args.get(1).matches("(?i)(\\d+)"))
				{
					jumpTo(Integer.parseInt(args.get(1)), scheduler, ctx.getChannel());
				}
			}
		}
		
		
		GuildManager.getContext(ctx.getGuild())
					.getPlayerPrinter()
					.printQueue(ctx.getChannel());
		
	}
	
	
	private void jumpTo(int index, TrackScheduler scheduler, TextChannel textChannel)
	{
		if (index < 2 || index > scheduler.getQueue()
										  .size())
		{
			return;
		}
		index = index - 1; // Subtract 1 for '0' based numeration.
		
		ArrayList<AudioTrack> queue = scheduler.getQueue();
		
		queue.addAll(queue.subList(0, index - 1));
		
		queue.retainAll(queue.subList(index, queue.size()));
		
		scheduler.clearQueue();
		scheduler.queueList(queue, textChannel);
	}
	
	private void bringToFront(int index, TrackScheduler scheduler, TextChannel textChannel)
	{
		if (index < 2 || index > scheduler.getQueue()
										  .size())
		{
			return;
		}
		index = index - 1; // Subtract 1 for '0' based numeration.
		
		ArrayList<AudioTrack> queue = scheduler.getQueue();
		
		queue.add(0, queue.get(index));
		queue.remove(index);
		
		scheduler.clearQueue();
		scheduler.queueList(queue, textChannel);
	}
	
	
}
