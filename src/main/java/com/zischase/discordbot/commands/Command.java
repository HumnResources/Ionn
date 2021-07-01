package com.zischase.discordbot.commands;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class Command
{
	private final boolean premiumCommand;
	
	
	public Command(boolean premiumCommand)
	{
		this.premiumCommand = premiumCommand;
	}
	
	public String getName()
	{
		return this.getClass().getSimpleName();
	}
	
	public boolean isPremium() {
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
