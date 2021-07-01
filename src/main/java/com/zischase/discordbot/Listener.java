package com.zischase.discordbot;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import com.zischase.discordbot.guildcontrol.GuildHandler;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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

public class Listener extends ListenerAdapter
{
	private static final Logger               LOGGER = LoggerFactory.getLogger(Listener.class);
	private final AtomicReference<Member> executeSlashCommand = new AtomicReference<>(null);
	
	public Listener()
	{
	}
	
	@Override
	public void onGuildJoin(@NotNull GuildJoinEvent event) {
		new GuildContext(event.getGuild());
		LOGGER.info("Joined a new guild : " + event.getGuild().getName() + " " + event.getGuild().getId());
	}
	
	@Override
	public void onReady(@Nonnull ReadyEvent event)
	{
		for (Guild g: event.getJDA().getGuilds()) {
			GuildContext ctx = new GuildContext(g);
			
			CompletableFuture.runAsync(() -> {
				for (Command customCommand: ctx.commandHandler().getCommandList()) {
					String name = customCommand.getName().toLowerCase();
					
					boolean commandExists = g.retrieveCommands()
											 .complete()
											 .stream()
											 .anyMatch(command -> command.getName().equalsIgnoreCase(customCommand.getName()));
					
					if (!commandExists) {
						g.upsertCommand(name, customCommand.shortDescription()).addOption(OptionType.STRING, "args", "input args, see help for details.").queue();
					}
				}
			});
		}
		
		LOGGER.info("{} is ready", event.getJDA()
				.getSelfUser()
				.getAsTag());
	}
	
	@Override
	public void onSlashCommand(@NotNull SlashCommandEvent event) {
		event.isAcknowledged();
		
		event.deferReply(false).queue((m) -> {
			MessageBuilder mb = new MessageBuilder();
			
			/* Using getGuildChannel() instead of getGuild() directly from event. This ensures we have null safety*/
			mb.append(DatabaseHandler.get(event.getGuildChannel().getGuild().getId(), "prefix"));
			mb.append(event.getName());
			
			/* Checks to see if args were input, adding if needed. */
			if (!event.getOptions().isEmpty()) {
				mb.append(" ".concat(event.getOptions().get(0).getAsString()));
			}
			
			/* Ensure we skip detection of bot message in channel until we start processing the command. */
			executeSlashCommand.set(event.getMember());
			event.getChannel().sendMessage(mb.build()).queue();
			
			event.getHook().deleteOriginal().queue();
		});
	}
	
	@Override
	public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event)
	{
		if (executeSlashCommand.get() == null && event.getAuthor().isBot() || event.isWebhookMessage())
		{
			return;
		}
		
		String prefix = DatabaseHandler.get(event.getGuild().getId(), "prefix");
		String raw = event.getMessage().getContentRaw();
		
		if (raw.startsWith(prefix))
		{
			String[] msgArr = raw.replaceFirst("(?i)" + Pattern.quote(prefix), "")
								 .split("\\s");
			
			List<String> args = Arrays.asList(msgArr).subList(1, msgArr.length);
			
			CommandContext ctx;
			if (executeSlashCommand.get() != null) {
				ctx = new CommandContext(event, args, executeSlashCommand.get());
			}
			else {
				ctx = new CommandContext(event, args);
			}
			
			CompletableFuture.runAsync(() -> GuildHandler.getContext(ctx.getGuild()).commandHandler().invoke(ctx))
							 .thenRun(() -> executeSlashCommand.set(null));
		}
	}

	@Override
	public void onShutdown(@NotNull ShutdownEvent event) {
		LOGGER.info("Deleting event listener.");
		event.getJDA().removeEventListener(this);
	}
}