package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;

public class Stop extends Command
{
	
	public Stop()
	{
		super(false);
	}
	
	@Override
	public String getHelp()
	{
		return "Stop ~ Ends currently playing audio and leave's the channel.";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		AudioManager audioManager = GuildManager.getContext(ctx.getGuild())
												.audioManager();
		
		audioManager.getPlayer()
					.stopTrack();
		
		audioManager.getScheduler()
					.clearQueue();
		
		ctx.getJDA()
		   .getDirectAudioController()
		   .disconnect(ctx.getGuild());
	}
}
