package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;

public class Skip extends Command
{
	
	public Skip()
	{
		super(false);
	}
	
	@Override
	public String getHelp()
	{
		return "Skip ~ Skip current track and play next in queue.";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		AudioManager audioManager = GuildManager.getContext(ctx.getGuild())
												.audioManager();
		
		audioManager.getScheduler()
					.nextTrack();
		
	}
}
