package com.zischase.discordbot.commands;

import java.util.List;

public abstract class Command
{
	public boolean premiumCommand;
	
	public Command(boolean premiumCommand)
	{
		this.premiumCommand = premiumCommand;
	}
	
	public String getName()
	{
		return this.getClass()
				   .getSimpleName();
	}
	
	public List<String> getAliases()
	{
		return List.of();
	}
	
	public String getHelp()
	{
		return "No description provided.";
	}
	
	public abstract void handle(CommandContext ctx) ;
}
