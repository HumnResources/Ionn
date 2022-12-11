package com.zischase.discordbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class Command {

	private final boolean premiumCommand;

	public Command(boolean premiumCommand) {
		this.premiumCommand = premiumCommand;
	}

	public SlashCommandData getCommandData() {
		return SlashCommandData.fromData(DataObject.fromJson("""
				{
					"name": "%s",
					"description": "%s"
				}
				""".formatted(getName().toLowerCase(), shortDescription())));
	}

	public String getName() {
		return this.getClass().getSimpleName();
	}

	@NotNull
	public abstract String shortDescription();

	public final boolean isPremium() {
		return premiumCommand;
	}

	public List<String> getAliases() {
		return List.of();
	}

	public abstract String helpText();

	public abstract void handle(CommandContext ctx);

}
