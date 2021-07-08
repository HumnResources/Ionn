package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.TrackLoader;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Play extends Command {

	public Play() {
		super(false);
	}

	@Override
	public CommandData getCommandData() {
		OptionData name = new OptionData(OptionType.STRING, "name", "Plays song by name", true);
		OptionData link = new OptionData(OptionType.STRING, "link", "Plays audio by url", true);
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
		VoiceChannel voiceChannel = ctx.getEventInitiator().getVoiceState() != null ?
				ctx.getEventInitiator().getVoiceState().getChannel() : null;

		List<String> args    = ctx.getArgs();
		String       guildID = ctx.getGuild().getId();
		TrackLoader trackLoader = GuildContext.get(guildID)
				.audioManager()
				.getTrackLoader();

		if (args.isEmpty() || args.get(0).matches("(?i)-(pause)")) {
			AudioPlayer player = GuildContext.get(guildID)
					.audioManager()
					.getPlayer();
			player.setPaused(!player.isPaused());
		} else if (args.get(0).matches("(?i)-(next|n)")) {
			String song = String.join(" ", args.subList(1, args.size()));
			playNext(song, ctx.getEvent(), trackLoader);
			ctx.getChannel().sendMessage("Playing `%s` next!".formatted(song)).queue(m -> m.delete().queueAfter(3000, TimeUnit.MILLISECONDS), null);
		}
		/* Checks to see if we have a potential link in the message */
		else if (args.get(0).matches("(?i)-(url)")) {
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
		else if (args.get(0).matches("(?i)-(ytplaylist)")) {
			String search = String.join(" ", args.subList(1, args.size()));
			GuildContext.get(guildID)
					.audioManager()
					.getTrackLoader()
					.loadYTSearchResults(ctx.getChannel(), voiceChannel, search);
		}

		/* Otherwise we check to see if they input a string, process using YT as default */
		else {
			String search;
			/* Removes the -song flag added by slash command */
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

		} else {
			trackLoader.load(event.getChannel(), voiceChannel, song);
		}
	}
}
