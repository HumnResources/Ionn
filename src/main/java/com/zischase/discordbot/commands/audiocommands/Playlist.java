package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.TextChannel;

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
		return """
         	`Playlist/Pl/Plist` : Displays a list of currently made playlists.
     		`Playlist [-Current | -C | -Queue | -Q]` : Creates a playlist of the current queue.
     		`Playlist [-Delete | -D | -Remove | -R] [Name...] : Deletes the playlist with the specified name.
     		`Playlist [-add | -new] [Name...]` : Adds a new playlist with the specified name.
     		`Playlist [-Play | -P] [Name...]` : Loads the prefix with the specified name.
     				Note: Active development and the playlists are currently not persistent. Use at your own risk.
			""";
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
		String playlistName = String.join(" ", args.subList(1, args.size()));
		
		if (cmd.matches("(?i)-(play|p)") || playlists.containsKey(cmd))
		{
			if (! playlists.containsKey(playlistName.toLowerCase()))
			{
				ctx.getChannel()
				   .sendMessage("Sorry, playlist not found.")
				   .queue();
				return;
			}
			
			ctx.getChannel()
			   .sendMessage("Loading playlist `" + playlistName + "`")
			   .queue();
			
			GuildManager.getContext(ctx.getGuild())
						.audioManager()
						.getScheduler()
						.queueList(getPlaylist(playlistName), ctx.getChannel());
			
			return;
		}
		else if (cmd.matches("(?i)-(current|c|q|queue)"))
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
		
		else if (cmd.matches("(?i)-(add|new)"))
		{
			ArrayList<AudioTrack> q = GuildManager.getContext(ctx.getGuild())
												  .audioManager()
												  .getScheduler()
												  .getQueue();
			
			addPlaylist(playlistName, q);
			printPlaylists(ctx.getChannel());
		}
		else if (cmd.matches("(?i)-(delete|d|remove|r)"))
		{
			playlists.remove(args.get(1)
								 .toLowerCase());
		}
		
		
	}
	
	private void addPlaylist(String name, ArrayList<AudioTrack> tracks)
	{
		playlists.putIfAbsent(name.toLowerCase(), tracks);
		
		
		
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
