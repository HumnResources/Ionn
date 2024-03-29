package com.zischase.discordbot.commands.dev;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.MessageSendHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import org.jetbrains.annotations.NotNull;

public class Spam extends Command
{
	
	public Spam()
	{
		super(true);
	}
	
	@Override
	public @NotNull String shortDescription()
	{
		return "Stupid idea, really. Helps with testing.";
	}
	
	@Override
	public String helpText()
	{
		return null;
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		MessageSendHandler messageSendHandler = GuildContext.get(ctx.getGuild().getId()).messageSendHandler();
		if (ctx.getMember().getId().equals(Config.get("OWNER_ID")))
		{
			if (ctx.getArgs().size() >= 1 && ctx.getArgs().get(0).matches("\\d+"))
			{
				int num = Integer.parseInt(ctx.getArgs().get(0));
				for (int i = 0; i < num; i++)
				{
					messageSendHandler.sendAndDeleteMessageChars.accept(ctx.getChannel(), "Playing `%s` next!".formatted(i + " - Spam ! !"));
				}
			}
		}
	}
	
}
