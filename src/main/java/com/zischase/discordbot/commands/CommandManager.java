package com.zischase.discordbot.commands;

import com.zischase.discordbot.Bot;
import com.zischase.discordbot.Config;
import com.zischase.discordbot.commands.audiocommands.*;
import com.zischase.discordbot.commands.general.Clear;
import com.zischase.discordbot.commands.general.Help;
import com.zischase.discordbot.commands.general.Prefix;
import com.zischase.discordbot.guildcontrol.PremiumManager;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CommandManager
{
	private static final List<Command>      commands = new ArrayList<>();
	private static final Logger             LOGGER   = LoggerFactory.getLogger(CommandManager.class);
	private static final int                POOL_COUNT;
	private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR;
	
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
		addCommand(new Queue());
		addCommand(new Join());
		addCommand(new Shuffle());
		addCommand(new Repeat());
		
		if (getCommandCount() <= 0)
		{
			LOGGER.warn("Commands not added !!");
			System.exit(1);
		}
		
		int defaultPoolCount = Integer.parseInt(Config.get("DEFAULT_COMMAND_THREADS"));
		POOL_COUNT = Bot.guildCount() * (CommandManager.getCommandCount() / 4);
		
		if (POOL_COUNT > defaultPoolCount)
		{
			THREAD_POOL_EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(POOL_COUNT);
		}
		else
		{
			THREAD_POOL_EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(defaultPoolCount);
		}
		THREAD_POOL_EXECUTOR.setKeepAliveTime(3000, TimeUnit.MILLISECONDS);
		THREAD_POOL_EXECUTOR.allowsCoreThreadTimeOut();
		THREAD_POOL_EXECUTOR.setCorePoolSize(THREAD_POOL_EXECUTOR.getActiveCount());
	}
	
	public static List<Command> getCommandList()
	{
		return commands;
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

		if (cmd != null)
		{
			new CompletableFuture<>().completeAsync(() ->
			{
			List<String> args = Arrays.asList(argsArr)
									  .subList(1, argsArr.length);

			CommandContext ctx = new CommandContext(event, args);
			
			boolean isGuildPremium = PremiumManager.getPremium(event.getGuild());
			Message lastMessage = event.getChannel()
										.getIterableHistory().getLast();

			boolean messageSent = event.getChannel()
					.getHistory()
					.retrievePast(1)
					.complete()
					.get(0)
					.getContentDisplay()
					.matches(lastMessage.getContentDisplay());

			if (cmd.premiumCommand && ! isGuildPremium && ! messageSent)
			{
				event.getChannel()
					 .sendMessage("Sorry, this feature is for premium guilds only :c")
					 .complete();
				return null;
			}
				cmd.handle(ctx);
				return null;
			}, THREAD_POOL_EXECUTOR);
			
			THREAD_POOL_EXECUTOR.setCorePoolSize(THREAD_POOL_EXECUTOR.getActiveCount() + 1);
		}
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
	
	public static void shutdown()
	{
		LOGGER.info(CommandManager.shutdownThreads());
	}
	
	
	public static String getReport()
	{
		return "=======================\n" +
				"Bot version: " + Config.get("VERSION") + "\n" +
				"Tasks completed: " + THREAD_POOL_EXECUTOR.getCompletedTaskCount() + "\n" +
				"Tasks in queue: " + THREAD_POOL_EXECUTOR.getQueue()
														 .size() + "\n" +
				"=======================\n" +
				"Active Thread Count: " + THREAD_POOL_EXECUTOR.getActiveCount() + "\n" +
				"Current Thread Count: " + THREAD_POOL_EXECUTOR.getCorePoolSize() + "\n" +
				"Max Thread Count: " + THREAD_POOL_EXECUTOR.getMaximumPoolSize() + "\n" +
				"=======================\n";
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
	
	private static String shutdownThreads()
	{
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
		finally
		{
			if (! THREAD_POOL_EXECUTOR.isShutdown())
			{
				THREAD_POOL_EXECUTOR.shutdownNow();
			}
		}
		
		return getReport() + "Shutting Down . . .\n";
	}
}
