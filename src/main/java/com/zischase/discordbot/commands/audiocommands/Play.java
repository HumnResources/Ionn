package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.TrackLoader;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.entities.Guild;

import java.util.ArrayList;
import java.util.List;

public class Play extends Command
{
	
	public Play()
	{
		super(false);
	}
	
	@Override
	public String getName()
	{
		return "Play/Pause";
	}
	
	@Override
	public List<String> getAliases()
	{
		return List.of("P");
	}
	
	@Override
	public String getHelp()
	{
		return "`Play/Pause : Play or pause the player.`\n" + "`Play [url] : Adds the specified song/playlist to queue.`\n" + "`Play -[next|n] [url] : Adds the specified song/playlist to next in queue`" + "`Aliases : " + String
				.join(" ", getAliases()) + "`";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		List<String> args = ctx.getArgs();
		Guild guild = ctx.getGuild();
		
		if (args.isEmpty())
		{
			AudioPlayer player = GuildManager.getContext(guild)
											 .audioManager()
											 .getPlayer();
			boolean isPaused = player.isPaused();
			player.setPaused(! isPaused);
		}
		else if (args.get(0)
					 .matches("(?i)(-next|-n)"))
		{
			AudioManager manager = GuildManager.getContext(ctx.getGuild())
											   .audioManager();
			ArrayList<AudioTrack> queueCopy = manager.getScheduler()
													 .getQueue();
			
			manager.getScheduler()
				   .clearQueue();
			
			
			new TrackLoader().load(ctx.getChannel(), ctx.getMember(), args.get(1));

            /*
                Wait 2s to allow for the new entry to load. Event's are handled asynchronously.
             */
			try
			{
				Thread.sleep(2000);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			
			manager.getScheduler()
				   .queueList(queueCopy, ctx.getChannel());
		}
		else
		{
			new TrackLoader().load(ctx.getChannel(), ctx.getMember(), args.get(0));
		}
	}
	
	
}
