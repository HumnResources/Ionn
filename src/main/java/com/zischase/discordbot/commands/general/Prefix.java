package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.DataBaseManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import net.dv8tion.jda.api.entities.Guild;

import java.util.List;

public class Prefix extends Command
{
	
	public Prefix()
	{
		super(false);
	}
	
	public static String getPrefix(Guild guild)
	{
		return DataBaseManager.get(guild.getId(), "prefix");
	}
	
	@Override
	public String getHelp()
	{
		return "Prefix [newPrefix] ~ Sets new prefix for commands.";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		Guild guild = ctx.getGuild();
		List<String> args = ctx.getArgs();
		
		if (args.isEmpty())
		{
			ctx.getEvent()
			   .getChannel()
			   .sendMessage("The current prefix is `" + getPrefix(guild) + "`")
			   .queue();
			return;
		}
		
		DataBaseManager.update(guild.getId(), "prefix", args.get(0));

		ctx.getEvent()
		   .getChannel()
		   .sendMessage("The new prefix has been set to `" + getPrefix(guild) + "`")
		   .queue();
	}
}
