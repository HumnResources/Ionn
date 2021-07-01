package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildHandler;
import org.jetbrains.annotations.NotNull;

public class Repeat extends Command
{
	
	public Repeat()
	{
		super(true);
	}
	
	@Override
	public @NotNull String shortDescription() {
		return "Sets repeat on/off for the current queue.";
	}
	
	@Override
	public String helpText() {
		return """
				%s
				
				Usage:
					`repeat`
					`repeat [on|off]`
				""";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		boolean repeat = GuildHandler.getContext(ctx.getGuild())
                                     .audioManager()
                                     .getScheduler()
                                     .isRepeat();
		
		if (! ctx.getArgs()
				 .isEmpty())
		{
			if (ctx.getArgs()
				   .get(0)
				   .matches("(?i)(on|start|yes)"))
			{
				repeat = true;
			}
			else if (ctx.getArgs()
						.get(0)
						.matches("(?i)(off|stop|no)"))
			{
				repeat = false;
			}
			
			GuildHandler.getContext(ctx.getGuild())
                        .audioManager()
                        .getScheduler()
                        .setRepeat(repeat);
		}
		
		String message;
		if (repeat)
		{
			message = "`On`";
		}
		else
		{
			message = "`Off`";
		}
		
		ctx.getMessage()
		   .getChannel()
		   .sendMessage("Repeat is " + message)
		   .queue();
	}
}
