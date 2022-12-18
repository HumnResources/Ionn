package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.DBQuery;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.general.MessageSendHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Playlist extends Command
{
	
	private final Map<String, String> playlists;
	
	private boolean playlistsInitialized = false;
	private MessageSendHandler messageSendHandler;
	
	public Playlist()
	{
		super(true);
		playlists = new HashMap<>();
	}
	
	@Override
	public SlashCommandData getCommandData()
	{
		OptionData name = new OptionData(OptionType.STRING, "name", " ", true);
		
		return super.getCommandData().addSubcommands(
				new SubcommandData("play", "Displays the current queue").addOptions(name.setDescription("Load playlist with specified name")),
				new SubcommandData("delete", "Moves the song at the current index to next in queue").addOptions(name.setDescription("Delete playlist with specified name")),
				new SubcommandData("add", "Add new playlist").addOptions(name.setDescription("Create playlist of current queue with specified name")),
				new SubcommandData("current", "Create playlist using current queue. Generates simple name"),
				new SubcommandData("display", "Shows a list of available playlists")
		);
	}
	
	@Override
	public @NotNull String shortDescription()
	{
		return "Play/Save a playlist of songs from youtube.";
	}
	
	@Override
	public List<String> getAliases()
	{
		return List.of("pl", "plist");
	}
	
	@Override
	public String helpText()
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
		VoiceChannel voiceChannel = ctx.getVoiceChannel();
		TextChannel textChannel = ctx.getChannel();
		messageSendHandler = GuildContext.get(ctx.getGuild().getId()).messageSendHandler();
		
		if (!playlistsInitialized)
		{
			List<String> dbPlaylists = DBQueryHandler.getList(ctx.getGuild().getId(), DBQuery.PLAYLISTS, DBQuery.NAME);
			
			for (String playlist : dbPlaylists)
			{
				this.playlists.put(playlist, DBQueryHandler.getPlaylist(ctx.getGuild().getId(), playlist));
			}
			
			this.playlistsInitialized = true;
		}
		
		List<String> args = ctx.getArgs();
		String       playlistName;
		
		if (args.isEmpty())
		{
			printPlaylists(ctx.getChannel());
			return;
		}
		
		String cmd = args.get(0).toLowerCase();
		
		playlistName = String.join(" ", args.subList(0, args.size()));
		
		if (cmd.startsWith("-"))
		{
			playlistName = playlistName.replaceAll(cmd, "").trim().toLowerCase();
		}
		
		if (playlistName.startsWith("-"))
		{
			messageSendHandler.sendAndDeleteMessageChars.accept(textChannel, "Sorry, names cannot start with '-'.");
			return;
		}
		
		if (cmd.matches("(?i)-(play|p)") || (playlists.containsKey(playlistName) && !cmd.startsWith("-")))
		{
			if (!playlistName.isEmpty() && !playlists.containsKey(playlistName.toLowerCase()))
			{
				messageSendHandler.sendAndDeleteMessageChars.accept(textChannel, "Sorry, playlist not found.");
				return;
			}
			else
			{
				messageSendHandler.sendAndDeleteMessageChars.accept(textChannel, "Loading playlist `" + playlistName + "`");
				GuildContext.get(ctx.getGuild().getId())
						.audioManager()
						.getTrackLoader()
						.load(ctx.getChannel(), voiceChannel, getPlaylistURL(ctx.getGuild().getId(), playlistName));
			}
			return;
		}
		else if (cmd.matches("(?i)-(current|c|q|queue)"))
		{
			ArrayList<AudioTrack> queue = GuildContext.get(ctx.getGuild().getId())
					.audioManager()
					.getScheduler()
					.getQueue();
			
			queue.add(0, GuildContext.get(ctx.getGuild().getId()).audioManager().getPlayer().getPlayingTrack());
			
			String youtubePlaylistURL = createPlaylistURL(queue);
			
			if (youtubePlaylistURL != null)
			{
				addPlaylist(ctx.getGuild().getId(), "playlist-" + (playlists.size() + 1), youtubePlaylistURL);
			}
		}
		else if (cmd.matches("(?i)-(add|a)"))
		{
			ArrayList<AudioTrack> queue = GuildContext.get(ctx.getGuild().getId())
					.audioManager()
					.getScheduler()
					.getQueue();
			
			queue.add(0, GuildContext.get(ctx.getGuild().getId()).audioManager().getPlayer().getPlayingTrack());
			
			String youtubePlaylistURL = createPlaylistURL(queue);
			
			if (youtubePlaylistURL != null)
			{
				addPlaylist(ctx.getGuild().getId(), playlistName, youtubePlaylistURL);
			}
			
		}
		else if (cmd.matches("(?i)-(delete|d|remove|r)"))
		{
			this.playlists.remove(playlistName);
			DBQueryHandler.deletePlaylistEntry(ctx.getGuild().getId(), playlistName);
		}
		
		printPlaylists(ctx.getChannel());
	}
	
	private void printPlaylists(TextChannel textChannel)
	{
		if (this.playlists.isEmpty())
		{
			messageSendHandler.sendAndDeleteMessageChars.accept(textChannel, "Sorry, no available playlists! :c");
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(Color.BLUE);
		embed.setTitle("Playlists");
		
		for (String key : playlists.keySet())
		{
			embed.appendDescription(key + System.lineSeparator());
		}
		
		messageSendHandler.sendAndDeleteMessage.accept(textChannel, MessageCreateData.fromEmbeds(embed.build()));
	}
	
	private String getPlaylistURL(String guildID, String name)
	{
		if (this.playlists.containsKey(name.toLowerCase()))
		{
			return playlists.get(name.toLowerCase());
		}
		else
		{
			String playlistURL = DBQueryHandler.getPlaylist(guildID, name);
			addPlaylist(guildID, name, playlistURL);
			return playlistURL;
		}
	}
	
	private String createPlaylistURL(ArrayList<AudioTrack> tracks)
	{
		tracks.removeIf(audioTrack -> !audioTrack.getSourceManager().getSourceName().equalsIgnoreCase("youtube"));
		if (tracks.isEmpty())
		{
			return null;
		}
		
		String playlistURL = "https://youtube.com/watch_videos?video_ids=";
		
		for (int i = 0, tracksSize = tracks.size(); i < tracksSize; i++)
		{
			AudioTrack track = tracks.get(i);
			
			playlistURL = playlistURL.concat(track.getInfo().identifier);
			if (i != tracksSize - 1)
			{
				playlistURL = playlistURL.concat(",");
			}
		}
		
		return playlistURL;
	}
	
	private void addPlaylist(String guildID, String name, String playlistURL)
	{
		this.playlists.putIfAbsent(name.toLowerCase(), playlistURL);
		DBQueryHandler.addPlaylistEntry(guildID, name, playlistURL);
	}
}
