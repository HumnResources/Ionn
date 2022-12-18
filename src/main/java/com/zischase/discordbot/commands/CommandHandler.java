package com.zischase.discordbot.commands;

import com.sun.istack.Nullable;
import com.zischase.discordbot.commands.audiocommands.*;
import com.zischase.discordbot.commands.general.Clear;
import com.zischase.discordbot.commands.general.Help;
import com.zischase.discordbot.commands.general.MessageSendHandler;
import com.zischase.discordbot.commands.general.Prefix;
import com.zischase.discordbot.guildcontrol.GuildContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CommandHandler
{
	
	private static final Logger                   LOGGER              = LoggerFactory.getLogger(CommandHandler.class);
	private static final HashMap<String, Command> COMMANDS            = new HashMap<>();
	private static final long                     COMMAND_TIMEOUT_SEC = 5;
	
	static
	{
		addCommand(new Help());
		addCommand(new Play());
		addCommand(new Volume());
		addCommand(new Stop());
		addCommand(new Skip());
		addCommand(new Previous());
		addCommand(new NowPlaying());
		addCommand(new Youtube());
		addCommand(new Prefix());
		addCommand(new Playlist());
		addCommand(new Clear());
		addCommand(new Queue());
		addCommand(new Join());
		addCommand(new Shuffle());
		addCommand(new Repeat());
		addCommand(new Resume());
		
		/* Takes a hot minute. */
		CompletableFuture.runAsync(() -> addCommand(new Radio()));
//		CompletableFuture.runAsync(() -> addCommand(new Lyrics()));
		
		if (COMMANDS.size() <= 0)
		{
			LOGGER.warn("Commands not added !!");
			System.exit(1);
		}
	}
	
	private final AtomicReference<Command> lastCommand         = new AtomicReference<>(null);
	private       OffsetDateTime           lastCommandExecTime = OffsetDateTime.now();
	
	public static List<Command> getCommandList()
	{
		return List.copyOf(COMMANDS.values());
	}
	
	private static void addCommand(Command command)
	{
		if (COMMANDS.putIfAbsent(command.getName().toLowerCase(), command) == null)
		{
			LOGGER.info("{} - Added", command.getName());
		}
		else
		{
			LOGGER.warn("{} Already Present! - Replacing", command.getName());
		}
	}
	
	public void invoke(CommandContext ctx)
	{
		MessageSendHandler messageSendHandler = GuildContext.get(ctx.getGuild().getId()).messageSendHandler();
		Command cmd;
		if (ctx.getEvent() != null)
		{
			cmd = getCommand(ctx.getEvent().getName());
		}
		else
		{
			cmd = CommandHandler.getCommand(ctx.getArgs().get(0));
		}
		
		if (cmd == null) return;
		
		if (cmd.isPremium() && !ctx.isPremiumGuild())
		{
			messageSendHandler.sendAndDeleteMessageChars.accept(ctx.getChannel(), "Sorry, this feature is for premium guilds only :c");
			return;
		}
		
		LOGGER.info("{} - {}:{}", ctx.getGuild().getName(), ctx.getMember().getUser().getName(), ctx.getMessage().getContent());
		
		if ((lastCommand.get() != null && cmd.getName().equalsIgnoreCase(lastCommand.get().getName()) &&
				OffsetDateTime.now().isBefore(lastCommandExecTime.plusSeconds(COMMAND_TIMEOUT_SEC))
		))
		{
			lastCommand.set(cmd);
			messageSendHandler.sendAndDeleteMessageChars.accept(ctx.getChannel(), "Command on cooldown");
			LOGGER.warn("Timeout! - {}:{}:{}", ctx.getGuild().getName(), ctx.getMember().getUser().getName(), cmd.getName());
			return;
		}
		
		lastCommandExecTime = OffsetDateTime.now();
		lastCommand.set(cmd);
		cmd.handle(ctx);
	}
	
	@Nullable
	public static Command getCommand(String search)
	{
		for (Command c : COMMANDS.values())
		{
			if (search.equalsIgnoreCase(c.getName()) || c.getAliases().contains(search))
			{
				return c;
			}
		}
		return null;
	}
}