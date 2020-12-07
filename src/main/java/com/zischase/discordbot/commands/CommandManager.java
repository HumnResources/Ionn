package com.zischase.discordbot.commands;

import com.zischase.discordbot.commands.audiocommands.*;
import com.zischase.discordbot.commands.dev.Spam;
import com.zischase.discordbot.commands.general.Clear;
import com.zischase.discordbot.commands.general.Help;
import com.zischase.discordbot.commands.general.Prefix;
import com.zischase.discordbot.guildcontrol.PremiumManager;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CommandManager
{
	private static final List<Command>      commands = new ArrayList<>();
	private static final Logger             LOGGER   = LoggerFactory.getLogger(CommandManager.class);
	
	static
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
//  addCommand(new Lyrics());
		addCommand(new Spam());
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
	
	public static List<Command> getCommandList()
	{
		return List.copyOf(commands);
	}
	
	public static void invoke(GuildMessageReceivedEvent event)
	{
		String prefix = Prefix.getPrefix(event.getGuild());
		
		String[] argsArr = event.getMessage()
								.getContentRaw()
								.replaceFirst("(?i)" + Pattern.quote(prefix), "")
								.split("\\s");
		
		String invoke = argsArr[0].toLowerCase();
		Command cmd = getCommand(invoke);

		if (cmd == null)
		{
			return;
		}
		List<String> args = Arrays.asList(argsArr)
				.subList(1, argsArr.length);

		CommandContext ctx = new CommandContext(event, args);

		boolean isGuildPremium = PremiumManager.getPremium(event.getGuild());

		if (cmd.premiumCommand && ! isGuildPremium )
		{

			String premiumCMDMessage = "Sorry, this feature is for premium guilds only :c";

			event.getChannel()
					.sendMessage(premiumCMDMessage)
					.queue();
			return;

		}
		cmd.handle(ctx);
	}
	
	public static int getCommandCount()
	{
		return commands.size();
	}
	
	
	public static Command getCommand(String search)
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
	

	

	private static void addCommand(Command command)
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
