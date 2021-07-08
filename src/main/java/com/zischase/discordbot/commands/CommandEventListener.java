package com.zischase.discordbot.commands;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class CommandEventListener extends ListenerAdapter {

	private static final Logger                  LOGGER          = LoggerFactory.getLogger(CommandEventListener.class);
	private final        AtomicReference<Member> proxyCallMember = new AtomicReference<>(null);

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
		event.isAcknowledged();

		event.deferReply(false).queue((m) -> {
			MessageBuilder mb = new MessageBuilder();

			/* Using getGuildChannel() instead of getGuild() directly from event. This ensures we have null safety*/
			String prefix = DBQueryHandler.get(event.getGuildChannel().getGuild().getId(), "prefix");

			/* Builds a command for the bot to issue : `<prefix><command> -<option> <args>` */
			mb.append("%s%s ".formatted(prefix, event.getName()));

			if (event.getSubcommandName() != null) {
				mb.append("-".concat(event.getSubcommandName()).concat(" "));
			}

			event.getOptions().forEach((opt) -> {
				if (opt.getType() == OptionType.STRING || opt.getType() == OptionType.INTEGER) {
					mb.append(opt.getAsString().concat(" "));
				}
			});

			/* Ensure we skip detection of bot message in channel until we start processing the command. */
			this.proxyCallMember.set(event.getMember());

			/* Delete the command issued by the bot */
			event.getChannel().sendMessage(mb.build()).queue((cmdMsg) -> cmdMsg.delete().queue());
			event.getHook().deleteOriginal().queue();
		});
	}

	@Override
	public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
		if (proxyCallMember.get() == null && event.getAuthor().isBot() || event.isWebhookMessage()) {
			return;
		}

		String prefix = DBQueryHandler.get(event.getGuild().getId(), "prefix");
		String raw    = event.getMessage().getContentRaw();

		if (raw.startsWith(prefix)) {
			String[]     msgArr = raw.replaceFirst("(?i)" + Pattern.quote(prefix), "").split("\\s");
			List<String> args   = Arrays.asList(msgArr).subList(1, msgArr.length);

			CommandContext ctx = proxyCallMember.get() != null ? new CommandContext(event, args, proxyCallMember.get()) : new CommandContext(event, args);

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

			this.proxyCallMember.set(null);

			CompletableFuture.runAsync(() -> GuildContext.get(ctx.getGuild().getId()).commandHandler().invoke(ctx));
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