package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Prefix extends Command {

	public Prefix() {
		super(false);
	}

	@Override
	public String helpText() {
		return "Prefix [newPrefix] ~ Sets new prefix for commands.";
	}

	@Override
	public @NotNull String shortDescription() {
		return "Sets the prefix to use for commands.";
	}

	@Override
	public void handle(CommandContext ctx) {
		Guild        guild = ctx.getGuild();
		List<String> args  = ctx.getArgs();

		String prefix = DBQueryHandler.get(guild.getId(), "prefix");

		if (args.isEmpty()) {
			ctx.getEvent()
					.getChannel()
					.sendMessage("The current prefix is `" + prefix + "`")
					.queue();
			return;
		}

		DBQueryHandler.set(guild.getId(), "prefix", args.get(0));
		prefix = DBQueryHandler.get(guild.getId(), "prefix");

		ctx.getEvent()
				.getChannel()
				.sendMessage("The new prefix has been set to `" + prefix + "`")
				.queue();
	}
}
