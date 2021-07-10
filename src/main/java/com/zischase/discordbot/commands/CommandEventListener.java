package com.zischase.discordbot.commands;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class CommandEventListener extends ListenerAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(CommandEventListener.class);

	public CommandEventListener() {
	}

	@Override
	public void onReady(@Nonnull ReadyEvent event) {
		LOGGER.info("Command Event Listener - Ready");
	}

	@Override
	public void onShutdown(@NotNull ShutdownEvent event) {
		LOGGER.info("Deleting event listener.");
		event.getJDA().removeEventListener(this);
	}

	@Override
	public void onSlashCommand(@NotNull SlashCommandEvent event) {
		if (event.isAcknowledged()) {
			return;
		}

		event.isAcknowledged();
		event.deferReply(false).queue(
				(m) -> event.getHook().deleteOriginal().queue(),
				err -> LOGGER.warn("Timeout for command {} !", event.getName())
		);

//		event.reply(event.getUser().getAsTag() + " - " + event.getName()).queue(null, err -> LOGGER.warn("Timeout for command {} !", event.getName()));

		List<String>   args           = new ArrayList<>();
		MessageBuilder commandMessage = new MessageBuilder();
		commandMessage.append(event.getName().concat(" "));

		if (event.getSubcommandName() != null) {
			args.add("-".concat(event.getSubcommandName()));
		}

		event.getOptions().forEach((opt) -> {
			if (opt.getType() == OptionType.STRING || opt.getType() == OptionType.INTEGER) {
				args.add(opt.getAsString());
			}
		});

		CommandContext ctx = new CommandContext(event.getGuild(), event.getMember(), args, commandMessage.build(), event.getTextChannel(), null);
		executeCommand(ctx);
	}

	private void executeCommand(CommandContext ctx) {
		CompletableFuture.runAsync(() -> GuildContext.get(ctx.getGuild().getId()).commandHandler().invoke(ctx));
	}

	@Override
	public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
		String prefix = DBQueryHandler.get(event.getGuild().getId(), "prefix");
		String raw    = event.getMessage().getContentRaw();

		if (raw.startsWith(prefix)) {
			String[]     msgArr = raw.replaceFirst("(?i)" + Pattern.quote(prefix), "").split("\\s");
			List<String> args   = Arrays.asList(msgArr).subList(1, msgArr.length);

			CommandContext ctx = new CommandContext(event.getGuild(), event.getMember(), args, event.getMessage(), event.getChannel(), null);

			if (event.getAuthor().getId().equals(Config.get("OWNER_ID"))) {
				if (msgArr[0].equalsIgnoreCase("slash")) {
					updateSlashCommands(event.getJDA());
					return;
				}
				if (msgArr[0].equalsIgnoreCase("delslash")) {
					deleteSlashCommands(event.getJDA());
					return;
				}
			}
			executeCommand(ctx);
		}
	}

	@Override
	public void onGuildJoin(@NotNull GuildJoinEvent event) {
		new GuildContext(event.getGuild());
		LOGGER.info("Joined a new guild : " + event.getGuild().getName() + " " + event.getGuild().getId());
	}

	@Override
	public void onGuildLeave(@NotNull GuildLeaveEvent event) {
		GuildContext.get(event.getGuild().getId());
		super.onGuildLeave(event);
	}

	private void updateSlashCommands(JDA jda) {
		LOGGER.info("Creating slash commands.");
		/* Loop through guilds to replace command */
		for (Guild g : jda.getGuilds()) {

			/* Get list of already installed commands */
			List<Command> slashCommands = g.retrieveCommands().complete();

			/* Reinitialize the commands */
			for (com.zischase.discordbot.commands.Command c : GuildContext.get(g.getId()).commandHandler().getCommandList()) {

				/* Comparator to ensure we don't overwrite */
				if (slashCommands.stream().noneMatch((sc) -> sc.getName().equals(c.getCommandData().getName()) && sc.getDescription().equals(c.getCommandData().getDescription())))
					g.upsertCommand(c.getCommandData()).queue((cmd) -> LOGGER.info("Added slash command {} to server {} ", cmd.getName(), g.getName()));
			}
		}
	}

	private void deleteSlashCommands(JDA jda) {
		LOGGER.info("Delete slash commands.");
		/* Delete all global commands */
		for (Command c : jda.retrieveCommands().complete()) {
			c.delete().complete();
		}
		for (Guild g : jda.getGuilds()) {
			/* Delete entire list of commands for guild */
			for (Command c : g.retrieveCommands().complete()) {
				/* Logs the successful deletion of a command. Returns null if delete fails */
				c.delete().queue((nul) -> LOGGER.info("Deleted command {} from server {}", c.getName(), g.getName()), null);
			}
		}
	}
}