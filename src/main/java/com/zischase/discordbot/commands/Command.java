package com.zischase.discordbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public abstract class Command
{
	private final boolean         				premiumCommand;
	private final AtomicReference<CommandData>  commandData = new AtomicReference<>(null);
	
	public Command(boolean premiumCommand)
	{
		this.premiumCommand = premiumCommand;
	}
	
	@Nullable
	public CommandData getCommandData() {
		return this.commandData.get();
	}
	
	public boolean setCommandData(CommandData commandData) {
		if (getCommandData() == null) {
			this.commandData.set(commandData);
			return true;
		}
		return false;
	}
	
	public String getName()
	{
		return this.getClass().getSimpleName();
	}
	
	public final boolean isPremium() {
		return premiumCommand;
	}
	
	public List<String> getAliases()
	{
		return List.of();
	}
	
	public abstract String helpText();
	
	@NotNull
	public abstract String shortDescription();
	
	public abstract void handle(CommandContext ctx);
}
