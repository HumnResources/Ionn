package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import net.dv8tion.jda.api.entities.TextChannel;

public class Spam extends Command
{
	
	public Spam()
	{
		super(false);
	}
	
	@Override
	public String getHelp()
	{
		return "Spam [amount] ~ Spams the current text channel with x messages. Defaults to 25.";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		
		TextChannel textChannel = ctx.getChannel();
		
		if (ctx.getArgs()
			   .isEmpty())
		{
			for (int i = 0; i < 25; i++)
			{
				textChannel.sendMessage("Spam !! : " + (i + 1))
						   .complete();
			}
		}
		
		else if (ctx.getArgs()
					.get(0)
					.matches("\\d+"))
		{
			for (int i = 0; i < Integer.parseInt(ctx.getArgs()
													.get(0)); i++)
			{
				textChannel.sendMessage("Spam !! : " + (i + 1))
						   .complete();
			}
		}
		
		
	}
}
