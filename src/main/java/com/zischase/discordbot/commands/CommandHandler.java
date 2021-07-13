package com.zischase.discordbot.commands;

import com.sun.istack.Nullable;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.audiocommands.*;
import com.zischase.discordbot.commands.general.Clear;
import com.zischase.discordbot.commands.general.Help;
import com.zischase.discordbot.commands.general.Prefix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class CommandHandler {

	private static final Logger                   LOGGER              = LoggerFactory.getLogger(CommandHandler.class);
	private static final HashMap<String, Command> COMMANDS            = new HashMap<>();
	private static final long                     COMMAND_TIMEOUT_SEC = 5;

	private final AtomicReference<Command> lastCommand         = new AtomicReference<>(null);
	private       OffsetDateTime           lastCommandExecTime = OffsetDateTime.now();

	static {
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

		/* Takes a hot minute. */
		CompletableFuture.runAsync(() -> addCommand(new Radio()));

		if (COMMANDS.size() <= 0) {
			LOGGER.warn("Commands not added !!");
			System.exit(1);
		}
	}

	public static List<Command> getCommandList() {
		return List.copyOf(COMMANDS.values());
	}

	public void invoke(CommandContext ctx) {
		String prefix = DBQueryHandler.get(ctx.getGuild().getId(), "prefix");
		String invoke = ctx.getMessage()
				.getContentRaw()
				.replaceFirst("(?i)" + Pattern.quote(prefix), "") // Remove prefix
				.split("\\s")[0] // Select first word (command)
				.toLowerCase();
		Command cmd = getCommand(invoke);


		if (cmd != null) {
			if (cmd.isPremium() && !ctx.isPremiumGuild()) {
				ctx.getChannel().sendMessage("Sorry, this feature is for premium guilds only :c").queue();
			} else {
				LOGGER.info("{} - {}:{}", ctx.getGuild().getName(), ctx.getMember().getUser().getName(), ctx.getMessage().getContentRaw());

				if (lastCommand.get() != null && cmd.getName().equalsIgnoreCase(lastCommand.get().getName())) {

					if (OffsetDateTime.now().isBefore(lastCommandExecTime.plusSeconds(COMMAND_TIMEOUT_SEC))) {
						lastCommand.set(cmd);
						ctx.getChannel().sendMessage("Command on cooldown").queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS));
						LOGGER.warn("Timeout! - {}:{}:{}", ctx.getGuild().getName(), ctx.getMember().getUser().getName(), cmd.getName());
						return;
					}
				}
				lastCommandExecTime = OffsetDateTime.now();
				lastCommand.set(cmd);
				cmd.handle(ctx);
			}
		}
	}

	@Nullable
	public Command getCommand(String search) {
		for (Command c : COMMANDS.values()) {
			if (search.equalsIgnoreCase(c.getName())) {
				return c;
			}
			for (String s : c.getAliases()) {
				if (s.equalsIgnoreCase(search)) {
					return c;
				}
			}
		}
		return null;
	}

	private static void addCommand(Command command) {
		if (COMMANDS.putIfAbsent(command.getName().toLowerCase(), command) == null) {
			LOGGER.info("{} - Added", command.getName());
		} else {
			LOGGER.warn("{} Already Present! - Replacing", command.getName());
		}
	}

}
