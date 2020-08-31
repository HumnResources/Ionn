package com.zischase.discordbot.commands;

import com.zischase.discordbot.commands.audiocommands.*;
import com.zischase.discordbot.commands.general.Clear;
import com.zischase.discordbot.commands.general.Help;
import com.zischase.discordbot.commands.general.Prefix;
import com.zischase.discordbot.commands.general.Spam;
import com.zischase.discordbot.guildcontrol.GuildManager;
import com.zischase.discordbot.guildcontrol.PremiumManager;
import me.duncte123.botcommons.BotCommons;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CommandManager
{
	private static final List<Command> commands = new ArrayList<>();
	private static final Logger        LOGGER   = LoggerFactory.getLogger(CommandManager.class);
	private static final int                POOL_COUNT;
	private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR;
	
	public static List<Command> getCommandList()
	{
		return commands;
	}
	
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
		addCommand(new Lyrics());
		addCommand(new Clear());
		addCommand(new Spam());
		addCommand(new Queue());
		addCommand(new Join());
		addCommand(new Shuffle());
		addCommand(new Repeat());
		
		if (getCommandCount() <= 0)
		{
			LOGGER.warn("Commands not added !!");
			System.exit(1);
		}
		
		
		POOL_COUNT =  GuildManager.getGuildCount() * (CommandManager.getCommandCount() / 4);
		THREAD_POOL_EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(POOL_COUNT);
	}
	
	public static void invoke(GuildMessageReceivedEvent event)
	{
		String prefix = Prefix.getPrefix(event.getGuild());
		
		String[] split = event.getMessage().getContentRaw()
				.replaceFirst("(?i)" + Pattern.quote(prefix), "")
				.split("\\s");
		
		String  invoke = split[0].toLowerCase();
		Command cmd    = getCommand(invoke);
		
		if (cmd != null)
		{
			List<String>   args = Arrays.asList(split).subList(1, split.length);
			CommandContext ctx  = new CommandContext(event, args);
			
			if (cmd.premiumCommand && ! PremiumManager.getPremium(event.getGuild()))
			{
				event.getChannel()
						.sendMessage("Sorry, this feature is for premium guilds only :c")
						.queue();
				return;
			}
			
			cmd.handle(ctx);
		}
	}
	
	public static int getCommandCount()
	{
		return commands.size();
	}
	
	
	@Nullable
	public static Command getCommand(String search)
	{
		for (Command cmd : commands)
		{
			List<String> aliases = cmd.getAliases().stream()
					.map(String::toLowerCase)
					.collect(Collectors.toList());
			
			if (cmd.getClass().getSimpleName().equalsIgnoreCase(search) || aliases.contains(search))
				return cmd;
		}
		return null;
	}
	
	public static ThreadPoolExecutor getThreadPoolExecutor()
	{
		return THREAD_POOL_EXECUTOR;
	}
	
	private static void addCommand(Command command)
	{
		boolean commandFound = commands
				.stream()
				.anyMatch(cmd -> cmd.getName().equalsIgnoreCase(command.getName()));
		
		if (commandFound)
		{
			LOGGER.warn("Command '{}' already present !", command.getName());
			return;
		}
		
		commands.add(command);
	}
	
	private static void shutdown(GuildMessageReceivedEvent event)
	{
		LOGGER.info(CommandManager.shutdownThreads());
		
		if (! CommandManager.getThreadPoolExecutor().isShutdown())
		{
			CommandManager.getThreadPoolExecutor().shutdownNow();
		}
		
		BotCommons.shutdown(event.getJDA());
		event.getJDA().shutdown();
	}

	public static String shutdownThreads()
	{
		String report = "Shutting down...\n" +
				"=======================\n" +
				"Tasks completed: " + THREAD_POOL_EXECUTOR.getCompletedTaskCount() + "\n" +
				"Active command accesses: " + THREAD_POOL_EXECUTOR.getActiveCount() + "\n" +
				"=======================\n" +
				"Current Pool Size: " + THREAD_POOL_EXECUTOR.getPoolSize() + "\n" +
				"Max Pool Size: " + THREAD_POOL_EXECUTOR.getMaximumPoolSize() + "\n" +
				"Core Pool Size: " + THREAD_POOL_EXECUTOR.getCorePoolSize() + "\n" +
				"=======================\n" +
				"Terminating....\n";
		THREAD_POOL_EXECUTOR.getQueue()
				.clear();
		THREAD_POOL_EXECUTOR.shutdown();
		try
		{
			Thread.sleep(2000);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		if (! THREAD_POOL_EXECUTOR.isShutdown())
		{
			THREAD_POOL_EXECUTOR.shutdownNow();
		}
		return report;
	}
	
}
