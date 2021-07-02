package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.TrackLoader;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Play extends Command {

	public Play() {
		super(false);
	}

	@Override
	public CommandData getCommandData() {
		/* Parse JSON using Discord slash command API docs as reference */
		return CommandData.fromData(DataObject.fromJson("""
				{
					"name": "play",
					"description": "Plays a song from youtube or link",
					"options": [
						{
							"name": "song",
							"description": "search song name",
							"type": 1,
							"options": [
								{
									"name": "name",
									"description": "Plays song by name",
									"type": 3,
									"required": true
								}
							]
						},
						{
							"name": "url",
							"description": "Link to audio track",
							"type": 1,
							"options": [
								{
									"name": "link",
									"description": "Plays audio by url",
									"type": 3,
									"required": true
								}
							]
						},
						{
							"name": "next",
							"description": "Play requested song next",
							"type": 1,
							"options": [
								{
									"name": "name",
									"description": "Title of song to move.",
									"type": 3,
									"required": true
								}
							]
						},
						{
							"name": "pause",
							"description": "Play or pause current track",
							"type": 1
						}
					]
				}
				"""));
	}

	@Override
	public @NotNull String shortDescription() {
		return "Plays a requested song.";
	}

	@Override
	public List<String> getAliases() {
		return List.of("P");
	}

	@Override
	public String helpText() {
		return "`Play/Pause : Play or pause the player.`\n" + "`Play song <name of song>: Searches YT and adds first song.`\n" + "`Play url <link>: Adds the specified song/playlist to queue.`\n" + "`Play next <url | name> : Adds the specified song/playlist to next in queue`" + "`Aliases : " + String
				.join(" ", getAliases()) + "`";
	}

	@Override
	public void handle(CommandContext ctx) {
		VoiceChannel voiceChannel = ctx.getEventInitiator().getVoiceState() != null ?
				ctx.getEventInitiator().getVoiceState().getChannel() : null;

		List<String> args    = ctx.getArgs();
		String       guildID = ctx.getGuild().getId();
		TrackLoader trackLoader = GuildContext.get(guildID)
				.audioManager()
				.getTrackLoader();

		if (voiceChannel != null) {
			DBQueryHandler.set(guildID, "media_settings", "voiceChannel", voiceChannel.getId());
		}
		DBQueryHandler.set(guildID, "media_settings", "textChannel", ctx.getChannel().getId());


		if (args.isEmpty() || args.get(0).matches("(?i)-(pause)")) {
			AudioPlayer player = GuildContext.get(guildID)
					.audioManager()
					.getPlayer();

			player.setPaused(!player.isPaused());
		} else if (args.get(0).matches("(?i)-(next|n)")) {
			String song = String.join(" ", args.subList(1, args.size()));
			playNext(song, ctx.getEvent(), trackLoader);
		}
		/* Checks to see if we have a potential link in the message */
		if (args.get(0).matches("(?i)-(url)")) {
			List<Message.Attachment> attachments = ctx.getEvent()
					.getMessage()
					.getAttachments();
			if (!attachments.isEmpty()) {
				trackLoader.load(ctx.getChannel(), voiceChannel, attachments.get(0).getProxyUrl());
			}
			else {
				trackLoader.load(ctx.getChannel(), voiceChannel, args.get(1));
			}
		}
		/* Otherwise we check to see if they input a string, process using YT as default */
		else {
			String search;
			if (args.get(0).matches("(?i)-(song)")) {
				search = String.join(" ", args).replaceFirst("(?i)-(song)", "");
			}
			else {
				search = String.join(" ", args);
			}
			trackLoader.load(ctx.getChannel(), voiceChannel, search);
		}
	}

	private void playNext(String song, GuildMessageReceivedEvent event, TrackLoader trackLoader) {
		VoiceChannel voiceChannel = event.getMember() != null && event.getMember().getVoiceState() != null ?
				event.getMember().getVoiceState().getChannel() : null;

		String                guildID      = event.getGuild().getId();
		AudioManager          audioManager = GuildContext.get(guildID).audioManager();
		ArrayList<AudioTrack> currentQueue = audioManager.getScheduler().getQueue();
		AudioTrack            nextTrack    = null;
		String                trackName;

		for (AudioTrack track : currentQueue) {
			trackName = track.getInfo().title.toLowerCase();
			if (song.matches("\\d+") && currentQueue.indexOf(track) == Integer.parseInt(song) - 1) // Subtract '1' for '0' based counting.
			{
				nextTrack = track;
				break;
			} else if (trackName.contains(song.trim().toLowerCase())) {
				nextTrack = track;
				break;
			}
		}

		boolean songFound = nextTrack != null && currentQueue.contains(nextTrack);
		if (songFound) {
			currentQueue.remove(nextTrack);
			currentQueue.add(0, nextTrack);
			audioManager.getScheduler().clearQueue();
			audioManager.getScheduler().queueList(currentQueue);

			GuildContext.get(guildID)
					.playerPrinter()
					.printQueue(GuildContext.get(guildID).audioManager(), event.getChannel());

			GuildContext.get(guildID)
					.playerPrinter()
					.printNowPlaying(GuildContext.get(guildID).audioManager(), event.getChannel());
		} else {
			trackLoader.load(event.getChannel(), voiceChannel, song, () -> {
				GuildContext.get(guildID)
						.playerPrinter()
						.printQueue(GuildContext.get(guildID).audioManager(), event.getChannel());

				GuildContext.get(guildID)
						.playerPrinter()
						.printNowPlaying(GuildContext.get(guildID).audioManager(), event.getChannel());
			});
		}
	}
}
