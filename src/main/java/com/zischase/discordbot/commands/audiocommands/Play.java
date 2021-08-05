package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.TrackLoader;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Play extends Command {

	private static final int SEARCH_TIMEOUT_SEC = 10;

	public Play() {
		super(false);
	}

	@Override
	public CommandData getCommandData() {
		OptionData name   = new OptionData(OptionType.STRING, "name", "Plays song by name", true);
		OptionData link   = new OptionData(OptionType.STRING, "link", "Plays audio by url", true);
		OptionData search = new OptionData(OptionType.STRING, "search", "Youtube search query", true);

		return super.getCommandData().addSubcommands(
				new SubcommandData("song", "search song name").addOptions(name),
				new SubcommandData("url", "Link to audio track").addOptions(link),
				new SubcommandData("ytplaylist", "Adds a playlist of songs from provided search").addOptions(search),
				new SubcommandData("next", "Play requested song next").addOptions(name.setDescription("Title of song to move.")),
				new SubcommandData("pause", "Play or pause current track")
		);
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
		VoiceChannel voiceChannel = ctx.getMember().getVoiceState() != null ?
				ctx.getMember().getVoiceState().getChannel() : null;

		List<String> args    = ctx.getArgs();
		String       guildID = ctx.getGuild().getId();
		TrackLoader trackLoader = GuildContext.get(guildID)
				.audioManager()
				.getTrackLoader();

		// Get arg or set default if no args input - Ignored if user inputs a search param
		String arg = !args.isEmpty() ? args.get(0) : "-pause";
		String search;
		switch (arg) {
			case "-pause" -> {
				AudioPlayer player = GuildContext.get(guildID)
						.audioManager()
						.getPlayer();
				player.setPaused(!player.isPaused());
			}
			case "-next" -> {
				String song = String.join(" ", args.subList(1, args.size()));
				playNext(song, ctx.getVoiceChannel(), ctx.getChannel(), trackLoader);
				ctx.getChannel().sendMessage("Playing `%s` next!".formatted(song)).queue(m -> m.delete().queueAfter(4, TimeUnit.SECONDS), null);
			}
			case "-url" -> {
				List<Message.Attachment> attachments = ctx.getMessage().getAttachments();

				if (!attachments.isEmpty()) {
					trackLoader.load(ctx.getChannel(), voiceChannel, attachments.get(0).getProxyUrl());
				} else {
					trackLoader.load(ctx.getChannel(), voiceChannel, args.get(1));
				}
			}
			case "-ytplaylist" -> {
				search = String.join(" ", args.subList(1, args.size()));
				GuildContext.get(guildID)
						.audioManager()
						.getTrackLoader()
						.loadYTSearchResults(ctx.getChannel(), voiceChannel, search);
				ctx.getChannel()
						.sendMessage("%s Added list of songs from search `%s`.".formatted(ctx.getMember().getUser().getAsTag(), search))
						.queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS));
			}
			case "-song" -> {
				search = String.join(" ", args).replaceFirst("-(song)", "");
				trackLoader.load(ctx.getChannel(), voiceChannel, search);
			}
			default -> {
				search = String.join(" ", args);
				trackLoader.load(ctx.getChannel(), voiceChannel, search);
			}
		}
	}

	private void playNext(String song, VoiceChannel voiceChannel, TextChannel textChannel, TrackLoader trackLoader) {

		String                guildID      = textChannel.getGuild().getId();
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
		} else {
			AtomicReference<AudioTrack> track = new AtomicReference<>(null);
			audioManager.getPlayerManager()
					.loadItem("ytsearch: " + song, new FunctionalResultHandler(
							track::set,
							playlist -> track.set(playlist.getTracks().get(0)),
							null,
							null)
					);

			OffsetDateTime start = OffsetDateTime.now();
			while (track.get() == null) {
				if (OffsetDateTime.now().isAfter(start.plusSeconds(SEARCH_TIMEOUT_SEC))) {
					return;
				}
			}

			currentQueue = audioManager.getScheduler().getQueue();
			currentQueue.add(0, track.get());
		}
		audioManager.getScheduler().clearQueue();
		audioManager.getScheduler().queueList(currentQueue);
	}
}
