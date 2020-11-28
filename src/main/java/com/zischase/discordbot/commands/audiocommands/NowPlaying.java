package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;

import java.util.List;

public class NowPlaying extends Command
{
	
	public NowPlaying()
	{
		super(false);
	}
	
	@Override
	public List<String> getAliases()
	{
		return List.of("np", "Playing");
	}
	
	@Override
	public String getHelp()
	{
		return null;
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		GuildManager.getContext(ctx.getGuild())
					.playerPrinter()
					.printNowPlaying(ctx.getChannel());
		
	}
	
}
