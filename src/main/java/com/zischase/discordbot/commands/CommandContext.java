package com.zischase.discordbot.commands;

import com.zischase.discordbot.DBQueryHandler;
import me.duncte123.botcommons.commands.ICommandContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import java.util.List;

public class CommandContext implements ICommandContext {

	private final GuildMessageReceivedEvent event;
	private final List<String>              args;
	private final Member                    eventInitiator;

	public CommandContext(GuildMessageReceivedEvent event, List<String> args) {
		this.event          = event;
		this.args           = args;
		this.eventInitiator = event.getMember();
	}

	public CommandContext(GuildMessageReceivedEvent event, List<String> args, Member initiator) {
		this.event          = event;
		this.args           = args;
		this.eventInitiator = initiator;
	}

	public Member getEventInitiator() {
		return eventInitiator;
	}

	@Override
	public Guild getGuild() {
		return event.getGuild();
	}

	@Override
	public GuildMessageReceivedEvent getEvent() {
		return event;
	}

	public List<String> getArgs() {
		return args;
	}

	public final boolean isPremiumGuild() {
		return DBQueryHandler.getPremiumStatus(getGuild().getId());
	}
}
