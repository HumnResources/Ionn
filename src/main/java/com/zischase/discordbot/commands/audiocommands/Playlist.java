package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.DBConnectionHandler;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Playlist extends Command {

	private final Map<String, String> playlists;

	private boolean playlistsInitialized = false;

	public Playlist() {
		super(true);
		playlists = new HashMap<>();
	}

	private static boolean checkTable(String guildID, String name) {
		return Jdbi.create(DBConnectionHandler.getConnection())
				.withHandle(handle -> {
					List<String> settings = handle.createQuery( /* Language=PostgreSQL */
							"SELECT playlist_name FROM youtube_playlists WHERE guild_id = :guildID")
							.bind("guildID", guildID)
							.mapTo(String.class)
							.list();

					handle.close();
					return settings;
				})
				.stream()
				.anyMatch(s -> s.equalsIgnoreCase(name.trim()));
	}

	@Override
	public CommandData getCommandData() {
		return CommandData.fromData(DataObject.fromJson("""
				{
					"name": "playlist",
					"description": "Provides custom playlist support for youtube songs",
					"options": [
						{
							"name": "play", 
							"description": "Load a playlist",
							"type": 1,
							"options": [
								{
									"name": "name",
									"description": "Load playlist with specified name",
									"type": 3,
									"required": true
								}
							]
						},
						{
							"name": "delete", 
							"description": "Delete a playlist",
							"type": 1,
							"options": [
								{
									"name": "name",
									"description": "Delete playlist with specified name",
									"type": 3,
									"required": true
								}
							]
						},
						{
							"name": "add", 
							"description": "Add new playlist",
							"type": 1,
							"options": [
								{
									"name": "name",
									"description": "Create playlist of current queue with specified name",
									"type": 3,
									"required": true
								}
							]
						},
						{
							"name": "current", 
							"description": "Create playlist using current queue. Generates simple name",
							"type": 1
						},
						{
							"name": "display",
							"description": "Shows a list of available playlists",
							"type": 1
						}
					]
				}
				"""));
	}

	@Override
	public @NotNull String shortDescription() {
		return "Play/Save a playlist of songs from youtube.";
	}

	@Override
	public List<String> getAliases() {
		return List.of("pl", "plist");
	}

	@Override
	public String helpText() {
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
	public void handle(CommandContext ctx) {
		VoiceChannel voiceChannel = ctx.getMember().getVoiceState() != null ?
				ctx.getMember().getVoiceState().getChannel() : null;

		if (!playlistsInitialized) {
			List<String> dbPlaylists = DBQueryHandler.getList(ctx.getGuild().getId(), "playlists", "name");

			for (String playlist : dbPlaylists) {
				this.playlists.put(playlist, DBQueryHandler.get(ctx.getGuild().getId(), playlist));
			}

			this.playlistsInitialized = true;
		}

		List<String> args = ctx.getArgs();
		String       playlistName;

		if (args.isEmpty()) {
			printPlaylists(ctx.getChannel());
			return;
		}

		String cmd = args.get(0).toLowerCase();

		playlistName = String.join(" ", args.subList(0, args.size()));

		if (cmd.startsWith("-")) {
			playlistName = playlistName.replaceAll(cmd, "").trim().toLowerCase();
		}

		if (playlistName.startsWith("-")) {
			ctx.getChannel()
					.sendMessage("Sorry, names cannot start with '-'.")
					.queue();
			return;
		}

		if (cmd.matches("(?i)-(play|p)") || (playlists.containsKey(playlistName) && !cmd.startsWith("-"))) {
			if (!playlistName.isEmpty() && !playlists.containsKey(playlistName.toLowerCase())) {
				ctx.getChannel()
						.sendMessage("Sorry, playlist not found.")
						.queue();
				return;
			} else {
				ctx.getChannel()
						.sendMessage("Loading playlist `" + playlistName + "`")
						.queue();

				GuildContext.get(ctx.getGuild().getId())
						.audioManager()
						.getTrackLoader()
						.load(ctx.getChannel(), voiceChannel, getPlaylistURL(ctx.getGuild().getId(), playlistName));
			}
			return;
		} else if (args.size() < 2) {
			ctx.getChannel()
					.sendMessage("Not enough arguments ! Type `[prefix]help playlist` for help.")
					.queue();
		} else if (cmd.matches("(?i)-(current|c|q|queue)")) {
			ArrayList<AudioTrack> queue = GuildContext.get(ctx.getGuild().getId())
					.audioManager()
					.getScheduler()
					.getQueue();

			queue.add(0, GuildContext.get(ctx.getGuild().getId()).audioManager().getPlayer().getPlayingTrack());

			String youtubePlaylistURL = createPlaylistURL(queue);

			if (youtubePlaylistURL != null) {
				addPlaylist(ctx.getGuild().getId(), "playlist-" + (playlists.size() + 1), youtubePlaylistURL);
			}
		} else if (cmd.matches("(?i)-(add|a)")) {
			ArrayList<AudioTrack> queue = GuildContext.get(ctx.getGuild().getId())
					.audioManager()
					.getScheduler()
					.getQueue();

			queue.add(0, GuildContext.get(ctx.getGuild().getId()).audioManager().getPlayer().getPlayingTrack());

			String youtubePlaylistURL = createPlaylistURL(queue);

			if (youtubePlaylistURL != null) {
				addPlaylist(ctx.getGuild().getId(), playlistName, youtubePlaylistURL);
			}

		} else if (cmd.matches("(?i)-(delete|d|remove|r)")) {
			this.playlists.remove(playlistName);
			DBQueryHandler.deletePlaylistFromDB(ctx.getGuild().getId(), playlistName);
		}

		printPlaylists(ctx.getChannel());
	}

	private void addPlaylist(String guildID, String name, String playlistURL) {
		this.playlists.putIfAbsent(name.toLowerCase(), playlistURL);
		DBQueryHandler.set(guildID, name, playlistURL);
	}

	private String getPlaylistURL(String guildID, String name) {
		if (this.playlists.containsKey(name.toLowerCase())) {
			return playlists.get(name.toLowerCase());
		} else {
			String playlistURL = DBQueryHandler.get(guildID, name);
			addPlaylist(guildID, name, playlistURL);
			return playlistURL;
		}
	}

	private void printPlaylists(TextChannel textChannel) {
		if (this.playlists.isEmpty()) {
			textChannel.sendMessage("Sorry, no available playlists! :c")
					.queue();
			return;
		}

		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(Color.BLUE);
		embed.setTitle("Playlists");

		for (String key : playlists.keySet()) {
			embed.appendDescription(key + System.lineSeparator());
		}

		textChannel.sendMessageEmbeds(embed.build())
				.queue();
	}

	private String createPlaylistURL(ArrayList<AudioTrack> tracks) {
		tracks.removeIf(audioTrack -> !audioTrack.getSourceManager().getSourceName().equalsIgnoreCase("youtube"));
		if (tracks.isEmpty()) {
			return null;
		}

		String playlistURL = "https://youtube.com/watch_videos?video_ids=";

		for (int i = 0, tracksSize = tracks.size(); i < tracksSize; i++) {
			AudioTrack track = tracks.get(i);

			playlistURL = playlistURL.concat(track.getInfo().identifier);
			if (i != tracksSize - 1) {
				playlistURL = playlistURL.concat(",");
			}
		}

		return playlistURL;
	}
}
