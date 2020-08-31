package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.*;
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
		return "`Queue : Show current songs in the queue.`\n" +
				"`Queue -[clear|c] : Clears the current queue.`\n" +
				"`Aliases : " + String.join(" ", getAliases()) + "`";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		List<String> args = ctx.getArgs();
		
		
		if (! args.isEmpty())
		{
			if (args.get(0).matches("(?i)(-clear|-c)"))
			{
				EmbedBuilder embed = new EmbedBuilder();
				embed.setColor(Color.BLUE);
				
				GuildManager.getContext(ctx.getGuild())
						.getAudioManager()
						.getScheduler()
						.clearQueue();
				
				embed.appendDescription("Queue cleared.");
				ctx.getChannel()
						.sendMessage(embed.build())
						.queue();
			}
		}
		
		
		GuildManager.getContext(ctx.getGuild())
				.getPlayerPrinter()
				.printQueue(ctx.getChannel());
		
	}
	
	
}
