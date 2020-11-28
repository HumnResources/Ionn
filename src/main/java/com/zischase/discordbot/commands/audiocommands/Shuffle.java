package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.CommandManager;
import com.zischase.discordbot.guildcontrol.GuildManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

public class Shuffle extends Command
{
	
	
	public Shuffle()
	{
		super(true);
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		
		ArrayList<AudioTrack> currentQueue = GuildManager.getContext(ctx.getGuild())
														 .audioManager()
														 .getScheduler()
														 .getQueue();
		
		Collections.shuffle(currentQueue);
		
		GuildManager.getContext(ctx.getGuild())
					.audioManager()
					.getScheduler()
					.clearQueue();
		
		GuildManager.getContext(ctx.getGuild())
					.audioManager()
					.getScheduler()
					.queueList(currentQueue, ctx.getChannel());
		
		Objects.requireNonNull(CommandManager.getCommand("Queue"))
			   .handle(ctx);
		
	}
}
