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
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class CommandEventListener extends ListenerAdapter {

	private static final Logger                  LOGGER          = LoggerFactory.getLogger(CommandEventListener.class);
	private final        AtomicReference<Member> proxyCallMember = new AtomicReference<>(null);
	private              ThreadPoolExecutor      poolExecutor;

	public CommandEventListener() {
	}

	@Override
	public void onReady(@Nonnull ReadyEvent event) {
		event.getJDA()
				.getGuilds()
				.forEach(GuildContext::new);

		LOGGER.info("{} is ready", event.getJDA()
				.getSelfUser()
				.getAsTag());

		JDA jda = event.getJDA();

		int defaultPoolCount = Integer.parseInt(Config.get("DEFAULT_COMMAND_THREADS"));
		int POOL_COUNT       = jda.getGuilds().size() * 2;

		if (POOL_COUNT > defaultPoolCount) {
			poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(POOL_COUNT);
		} else {
			poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(defaultPoolCount);
		}

		poolExecutor.setThreadFactory(new CommandThreadFactory(poolExecutor));
		poolExecutor.setKeepAliveTime(30000, TimeUnit.MILLISECONDS);
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
				mb.append("-".concat(event.getSubcommandName()));
			}

			event.getOptions().forEach((opt) -> {
				if (opt.getType() == OptionType.STRING) {
					mb.append(" ".concat(opt.getAsString()));
				}
			});

			/* Ensure we skip detection of bot message in channel until we start processing the command. */
			proxyCallMember.set(event.getMember());

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
					LOGGER.info("Creating slash commands.");
					return;
				}
				if (msgArr[0].equalsIgnoreCase("delslash")) {
					deleteSlashCommands(event.getJDA());
					LOGGER.info("Delete slash commands.");
					return;
				}
			}

			proxyCallMember.set(null);
			poolExecutor.execute(() -> GuildContext.get(ctx.getGuild().getId()).commandHandler().invoke(ctx));
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

		poolExecutor.execute(() -> {
			/* Loop through guilds to replace command */
			for (Guild g : jda.getGuilds()) {

				/* Get list of already installed commands */
				List<Command> slashCommands = g.retrieveCommands().complete();

				/* Reinitialize the commands */
				for (com.zischase.discordbot.commands.Command c : GuildContext.get(g.getId()).commandHandler().getCommandList()) {
					/* Comparator to ensure we don't overwrite */
					if (slashCommands.stream().noneMatch((sc) -> sc.getName().equalsIgnoreCase(c.getName())))
						g.upsertCommand(c.getCommandData()).complete();
				}
			}
		});
	}

	private void deleteSlashCommands(JDA jda) {
		poolExecutor.execute(() -> {
			/* Delete all global commands */
			for (Command c : jda.retrieveCommands().complete()) {
				c.delete().complete();
			}
			for (Guild g : jda.getGuilds()) {
				/* Delete entire list of commands for guild */
				for (Command c : g.retrieveCommands().complete()) {
					c.delete().complete();
				}
			}
		});
	}
}