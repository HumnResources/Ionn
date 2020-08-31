package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;

public class Repeat extends Command
{
	
	public Repeat()
	{
		super(true);
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		boolean repeat = GuildManager.getContext(ctx.getGuild())
									 .getAudioManager()
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
			
			GuildManager.getContext(ctx.getGuild())
						.getAudioManager()
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
