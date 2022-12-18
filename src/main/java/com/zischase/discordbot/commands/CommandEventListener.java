package com.zischase.discordbot.commands;

import com.zischase.discordbot.DBQuery;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CommandEventListener extends ListenerAdapter
{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CommandEventListener.class);
	
	public CommandEventListener()
	{
	}
	
	@Override
	public void onReady(@Nonnull ReadyEvent event)
	{
		LOGGER.info("Command Event Listener - Ready");
	}
	
	@Override
	public void onShutdown(@NotNull ShutdownEvent event)
	{
		LOGGER.info("Deleting event listener.");
		event.getJDA().removeEventListener(this);
	}
	
	@Override
	public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event)
	{
		if (event.isAcknowledged()) return;
		
		event.deferReply(true).queue();
		
		if (event.getGuild() == null) return;
		
		List<String>         args           = new ArrayList<>();
		MessageCreateBuilder commandMessage = new MessageCreateBuilder();
		commandMessage.addContent(event.getName().concat(" "));
		
		if (event.getSubcommandName() != null) args.add("-".concat(event.getSubcommandName()));
		
		event.getOptions().forEach((opt) ->
		{
			if (opt.getType() == OptionType.STRING || opt.getType() == OptionType.INTEGER)
			{
				args.add(opt.getAsString());
			}
		});
		
		String       vChannelID   = DBQueryHandler.get(event.getGuild().getId(), DBQuery.VOICECHANNEL);
		VoiceChannel voiceChannel = GuildContext.get(event.getGuild().getId()).guild().getVoiceChannelById(vChannelID);
		
		CommandContext ctx = new CommandContext(event.getGuild(), event.getMember(), args, commandMessage.build(), event.getChannel().asTextChannel(), voiceChannel, event);
		
		CompletableFuture.runAsync(() ->
		{
			GuildContext.get(ctx.getGuild().getId()).commandHandler().invoke(ctx);
			ctx.getEvent().getHook().deleteOriginal().submit();
		});
	}
	
	@Override
	public void onGuildJoin(@NotNull GuildJoinEvent event)
	{
		new GuildContext(event.getGuild());
		LOGGER.info("Joined a new guild : " + event.getGuild().getName() + " " + event.getGuild().getId());
		updateSlashCommands(event.getJDA(), true);
	}
	
	@Override
	public void onGuildLeave(@NotNull GuildLeaveEvent event)
	{
		GuildContext.get(event.getGuild().getId());
		super.onGuildLeave(event);
	}
	
	private void updateSlashCommands(JDA jda, boolean force)
	{
		if (force)
		{
			LOGGER.warn("Force creating slash commands.");
			for (Guild g : jda.getGuilds())
			{
				/* Reinitialize the commands */
				GuildContext.get(g.getId()).commandHandler();
				for (com.zischase.discordbot.commands.Command c : CommandHandler.getCommandList())
				{
					g.upsertCommand(c.getCommandData()).queue((cmd) -> LOGGER.info("Added slash command {} to server {} ", cmd.getName(), g.getName()));
				}
			}
			return;
		}
		
		LOGGER.info("Creating slash commands.");
		/* Loop through guilds to replace command */
		for (Guild g : jda.getGuilds())
		{
			
			/* Get list of already installed commands */
			List<Command> slashCommands = g.retrieveCommands().complete();
			
			/* Reinitialize the commands */
			GuildContext.get(g.getId()).commandHandler();
			for (com.zischase.discordbot.commands.Command c : CommandHandler.getCommandList())
			{
				
				/* Comparator to ensure we don't overwrite */
				if (slashCommands.stream().noneMatch((sc) -> sc.getName().equals(c.getCommandData().getName()) && sc.getDescription().equals(c.getCommandData().getDescription())))
					g.upsertCommand(c.getCommandData()).queue((cmd) -> LOGGER.info("Added slash command {} to server {} ", cmd.getName(), g.getName()));
			}
		}
	}
	
	private void deleteSlashCommands(JDA jda)
	{
		LOGGER.info("Delete slash commands.");
		/* Delete all global commands */
		for (Command c : jda.retrieveCommands().complete())
		{
			c.delete().complete();
		}
		for (Guild g : jda.getGuilds())
		{
			/* Delete entire list of commands for guild */
			for (Command c : g.retrieveCommands().complete())
			{
				/* Logs the successful deletion of a command. Returns null if delete fails */
				c.delete().queue((nul) -> LOGGER.info("Deleted command {} from server {}", c.getName(), g.getName()), null);
			}
		}
	}
}