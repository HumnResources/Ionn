package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.DataBaseManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import net.dv8tion.jda.api.entities.Guild;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Prefix extends Command
{
	private final AtomicReference<String> prefix = new AtomicReference<>();
	
	public Prefix()
	{
		super(false);
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
			   .sendMessage("The current prefix is `" + prefix.get() + "`")
			   .queue();
			return;
		}
		
		DataBaseManager.update(guild.getId(), "prefix", args.get(0));
		prefix.set(args.get(0));

		ctx.getEvent()
		   .getChannel()
		   .sendMessage("The new prefix has been set to `" + prefix.get() + "`")
		   .queue();
	}
}
