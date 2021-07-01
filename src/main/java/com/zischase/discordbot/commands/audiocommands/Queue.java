package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.audioplayer.TrackScheduler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;

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
	public @NotNull String shortDescription() {
		return "Adds audio to the current queue.";
	}
	
	@Override
	public List<String> getAliases()
	{
		return List.of("Q", "Qu");
	}
	
	@Override
	public String helpText()
	{
		return """
    			%s
    
    			Usage:
    				`Queue 			  # Show current songs in the queue.`
    				`Queue -[clear|c] # Clears the current queue.`
				
				`Aliases : %s`
				""".formatted(shortDescription(), String.join(" ", getAliases()));
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		List<String> args = ctx.getArgs();
		TrackScheduler scheduler = GuildContext.get(ctx.getGuild())
                                               .audioManager()
                                               .getScheduler();
		
		if (! args.isEmpty())
		{
			if (args.size() == 1)
			{
				if (args.get(0).matches("(?i)-(clear|c)"))
				{
					EmbedBuilder embed = new EmbedBuilder();
					embed.setColor(Color.BLUE);
					scheduler.clearQueue();
					
					embed.appendDescription("Queue cleared.");
					ctx.getChannel()
					   .sendMessageEmbeds(embed.build())
					   .queue();
				}
			}
			else if (args.size() == 2 && args.get(1).matches("(?i)(\\d+)"))
			{
				ArrayList<AudioTrack> queue = scheduler.getQueue();
				int index = Integer.parseInt(args.get(1));
				
				if (index < 2 || index > queue.size())
				{
					return;
				}
				index = index - 1; // Subtract 1 for '0' based numeration.
				
				if (args.get(0).matches("(?i)-(next|n)"))
				{
					queue.add(0, queue.get(index));
					queue.remove(index + 1); // Adding one to account for -> shift of list
					
					scheduler.clearQueue();
					scheduler.queueList(queue, ctx.getChannel());
				}
				else if (args.get(0).matches("(?i)-(jump|jumpto|j)"))
				{
					queue.addAll(queue.subList(0, index));
					
					ArrayList<AudioTrack> newQueue = new ArrayList<>(queue.subList(index, queue.size()));
					
					scheduler.clearQueue();
					scheduler.queueList(newQueue, ctx.getChannel());
				}
				else if (args.get(0).matches("(?i)-(clear|c)"))
				{
					queue.remove(index);
					
					scheduler.clearQueue();
					scheduler.queueList(queue, ctx.getChannel());
				}
			}
		}
		
		GuildContext.get(ctx.getGuild())
					.playerPrinter()
					.printQueue(GuildContext.get(ctx.getGuild()).audioManager(), ctx.getChannel());
		
		GuildContext.get(ctx.getGuild())
                    .playerPrinter()
                    .printNowPlaying(GuildContext.get(ctx.getGuild()).audioManager(), ctx.getChannel());
	}
}
