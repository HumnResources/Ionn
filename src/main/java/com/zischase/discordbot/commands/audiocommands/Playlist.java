package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.SQLConnectionHandler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import org.jdbi.v3.core.Jdbi;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Playlist extends Command
{
	
	private final Map<String, ArrayList<AudioTrack>> playlists;
	
	public Playlist()
	{
		super(true);
		playlists = new HashMap<>();
	}
	
	@Override
	public List<String> getAliases()
	{
		return List.of("pl", "plist");
	}
	
	@Override
	public String getHelp()
	{
		return null;
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		List<String> args = ctx.getArgs();
		
		if (args.isEmpty())
		{
			printPlaylists(ctx.getChannel());
			return;
		}
		
		
		String cmd = args.get(0);
		String playlist = String.join(" ", args.subList(1, args.size()));
		
		if (cmd.matches("(?i)(-play|-p)") || playlists.containsKey(cmd))
		{
			if (! playlists.containsKey(playlist.toLowerCase()))
			{
				ctx.getChannel()
				   .sendMessage("Sorry, playlist not found.")
				   .queue();
				return;
			}
			
			ctx.getChannel()
			   .sendMessage("Loading playlist `" + playlist + "`")
			   .queue();
			
			GuildManager.getContext(ctx.getGuild())
						.audioManager()
						.getScheduler()
						.queueList(getPlaylist(playlist), ctx.getChannel());
			
			return;
		}
		else if (cmd.matches("(?i)(-current|-c|-np|-nowplaying)"))
		{
			ArrayList<AudioTrack> queue = GuildManager.getContext(ctx.getGuild())
													  .audioManager()
													  .getScheduler()
													  .getQueue();
			
			addPlaylist("playlist-" + (playlists.size() + 1), queue);
			printPlaylists(ctx.getChannel());
			
			return;
		}
		
		if (args.size() < 2)
		{
			ctx.getChannel()
			   .sendMessage("Not enough arguments ! Type `[prefix]help playlist` for help.")
			   .queue();
		}
		
		else if (cmd.matches("(?i)(-add|-new)"))
		{
			ArrayList<AudioTrack> q = GuildManager.getContext(ctx.getGuild())
												  .audioManager()
												  .getScheduler()
												  .getQueue();
			
			addPlaylist(playlist, q);
			printPlaylists(ctx.getChannel());
		}
		else if (cmd.matches("(?i)(-delete|-d|-remove|-r)"))
		{
			playlists.remove(args.get(1)
								 .toLowerCase());
		}
		
		
	}
	
	private void addPlaylist(String name, ArrayList<AudioTrack> tracks)
	{
		playlists.putIfAbsent(name.toLowerCase(), tracks);
		
		Jdbi.create(SQLConnectionHandler::getConnection)
			.useHandle(handle ->
			{
			
				handle.execute("");
			
			});
		
	}
	
	private ArrayList<AudioTrack> getPlaylist(String name)
	{
		return (playlists.get(name.toLowerCase()));
	}
	
	private void printPlaylists(TextChannel textChannel)
	{
		
		if (playlists.isEmpty())
		{
			textChannel.sendMessage("Sorry, no available playlists! :c")
					   .queue();
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(Color.BLUE);
		embed.setTitle("Playlists");
		
		for (String key : playlists.keySet())
		{
			embed.appendDescription(key + System.lineSeparator());
		}
		
		textChannel.sendMessage(embed.build())
				   .queue();
	}
	
}
