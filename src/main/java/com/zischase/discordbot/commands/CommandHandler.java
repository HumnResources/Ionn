package com.zischase.discordbot.commands;

import com.zischase.discordbot.DatabaseHandler;
import com.zischase.discordbot.commands.audiocommands.*;
import com.zischase.discordbot.commands.general.Clear;
import com.zischase.discordbot.commands.general.Help;
import com.zischase.discordbot.commands.general.Prefix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CommandHandler
{
	private static final Logger             LOGGER   = LoggerFactory.getLogger(CommandHandler.class);
	private final List<Command>      commands = new ArrayList<>();

	{
		addCommand(new Help());
		addCommand(new Radio());
		addCommand(new Play());
		addCommand(new Volume());
		addCommand(new Stop());
		addCommand(new Skip());
		addCommand(new Previous());
		addCommand(new NowPlaying());
		addCommand(new Youtube());
		addCommand(new Prefix());
		addCommand(new Playlist());
  		addCommand(new Lyrics());
//		addCommand(new Spam());
		addCommand(new Clear());
		addCommand(new Queue());
		addCommand(new Join());
		addCommand(new Shuffle());
		addCommand(new Repeat());
		
		if (getCommandCount() <= 0)
		{
			LOGGER.warn("Commands not added !!");
			System.exit(1);
		}

	}
	
	public List<Command> getCommandList()
	{
		return List.copyOf(commands);
	}
	
	public void invoke(CommandContext ctx)
	{
		String prefix = DatabaseHandler.get(ctx.getGuild().getId(), "prefix");
		
		String invoke = ctx.getMessage()
						   .getContentRaw()
						   .replaceFirst("(?i)" + Pattern.quote(prefix), "") // Remove prefix
						   .split("\\s")[0] // Select first word (command)
						   .toLowerCase();
		
		Command cmd = getCommand(invoke);
		
		if (cmd == null)
		{
			return;
		}

		boolean isGuildPremium = Boolean.parseBoolean(DatabaseHandler.get(ctx.getGuild().getId(), "premium"));

		if (cmd.isPremium() && ! isGuildPremium )
		{
			ctx.getChannel()
			   .sendMessage("Sorry, this feature is for premium guilds only :c")
			   .queue();
			return;
		}
		
		LOGGER.info(cmd.getName());
		cmd.handle(ctx);
	}
	
	public int getCommandCount()
	{
		return commands.size();
	}
	
	
	public Command getCommand(String search)
	{
		for (Command cmd : commands)
		{
			List<String> aliases = cmd.getAliases()
									  .stream()
									  .map(String::toLowerCase)
									  .collect(Collectors.toList());
			
			if (cmd.getClass()
				   .getSimpleName()
				   .equalsIgnoreCase(search) || aliases.contains(search))
			{
				return cmd;
			}
		}
		return null;
	}
	
	private void addCommand(Command command)
	{
		boolean commandFound = commands.stream()
									   .anyMatch(cmd -> cmd.getName()
														   .equalsIgnoreCase(command.getName()));
		
		if (commandFound)
		{
			LOGGER.warn("Command '{}' already present !", command.getName());
			return;
		}
		
		commands.add(command);
	}
}
