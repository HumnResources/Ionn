package com.zischase.discordbot.commands;

import java.util.List;

public interface ICommand
{
	default String getName()
	{
		return this.getClass().getSimpleName();
	}
	
	default List<String> getAliases()
	{
		return List.of();
	}
	
	default String getHelp()
	{
		return "No description provided.";
	}
	
}
