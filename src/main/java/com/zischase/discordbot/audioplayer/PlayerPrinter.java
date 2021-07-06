package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sun.istack.Nullable;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PlayerPrinter {

	private final int   historyPollLimit = 7;

	public PlayerPrinter(AudioManager audioManager, TextChannel defaultChannel) {
		/* Add the event watcher to the current guild's audio manager */
		TrackWatcherEventListener watcher = new TrackWatcherEventListener(audioManager, defaultChannel.getGuild().getId());
		audioManager.getPlayer().addListener(watcher);
		defaultChannel.getJDA().addEventListener(watcher);
	}

	public void printNowPlaying(AudioManager audioManager, TextChannel channel) {
		AudioPlayer  player = audioManager.getPlayer();
		EmbedBuilder embed  = new EmbedBuilder();

		if (player.getPlayingTrack() == null) {
			embed.setTitle("Nothing Playing");
			embed.setColor(Color.darkGray);
			embed.setFooter(". . .");
		} else {
			AudioTrackInfo info      = player.getPlayingTrack().getInfo();
			long           duration  = info.length / 1000;
			long           position  = player.getPlayingTrack().getPosition() / 1000;
			String         paused    = player.isPaused() ? "⏸" : "▶";
			String         repeat    = audioManager.getScheduler().isRepeat() ? " \uD83D\uDD01" : "";
			String         timeTotal = String.format("%d:%02d:%02d", duration / 3600, (duration % 3600) / 60, (duration % 60));
			String timeCurrent = String.format("%d:%02d:%02d",
					position / 3600,
					(position % 3600) / 60,
					(position % 60)
			);


			if (info.title != null && !info.title.isEmpty()) {
				embed.appendDescription(info.title + "\n\n");
			} else if (info.author != null && !info.author.isEmpty()) {
				embed.appendDescription(info.author + "\n\n");
			} else {
				embed.appendDescription("-----\n\n");
			}


			if (player.getPlayingTrack().getDuration() == Long.MAX_VALUE) {
				embed.appendDescription("\uD83C\uDF99");
			} else {
				embed.appendDescription(timeCurrent + " - " + timeTotal + "");
				String progressBar = progressPercentage((int) position, (int) duration);
				embed.appendDescription(System.lineSeparator() + progressBar);
			}

			embed.setColor(Color.CYAN);
			embed.setTitle("\uD83D\uDCFB \uD83C\uDFB6 Now Playing \uD83C\uDFB6 \uD83D\uDCFB\n");
			embed.appendDescription("\n" + paused + " " + repeat);
			embed.setFooter(info.uri);
		}

		Message message    = new MessageBuilder().setEmbeds(embed.build()).build();
		Message currentMsg = getCurrentNowPlayingMsg(channel);


		if (currentMsg != null) {
			channel.editMessageById(currentMsg.getId(), message).queue();
		} else {
			channel.sendMessage(message).queue();
		}
	}

	@Nullable
	Message getCurrentNowPlayingMsg(TextChannel textChannel) {
		return textChannel
				.getHistory()
				.retrievePast(historyPollLimit)
				.complete()
				.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> !msg.getEmbeds().isEmpty())
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now().plusDays(14)))
				.filter(msg -> msg.getEmbeds().get(0).getTitle() != null && Objects.requireNonNull(msg.getEmbeds().get(0).getTitle()).contains("Now Playing"))
				.findFirst()
				.orElse(null);
	}

	void deletePrevious(TextChannel textChannel) {

		List<Message> msgList = textChannel.getHistory()
				.retrievePast(historyPollLimit)
				.complete()
				.stream()
				.filter(msg ->
				{
					boolean isBot     = msg.getAuthor().isBot();
					boolean hasEmbeds = !msg.getEmbeds().isEmpty();
					boolean notPinned = !msg.isPinned();
					boolean createdBeforeNow = msg.getTimeCreated()
							.isBefore(OffsetDateTime.now());
					boolean lessThan2Weeks = msg.getTimeCreated()
							.isAfter(OffsetDateTime.now()
									.minusDays(13));

					if (isBot && hasEmbeds && notPinned && createdBeforeNow && lessThan2Weeks) {
						MessageEmbed embed = msg.getEmbeds().get(0);
						if (embed.getTitle() != null) {
							return embed.getTitle().contains("Now Playing") || embed.getTitle().contains("Queue") || embed.getTitle().contains("Nothing Playing");
						}
					}
					return false;
				})
				.collect(Collectors.toList());

		/* Using blocking method to preserve execution order in class - Print queue attempts to rewrite a message that gets deleted here before success */
		if (msgList.size() > 1) {
			textChannel.deleteMessages(msgList).complete();
		} else if (msgList.size() == 1) {
			textChannel.deleteMessageById(msgList.get(0).getId()).complete();
		}
	}

	private String progressPercentage(int done, int total) {
		int    size              = 30;
		String iconLeftBoundary  = "|";
		String iconDone          = "=";
		String iconRemain        = "\u00A0. \u200b"; // '&nbsp. <zero-width-sp>'
		String iconRightBoundary = "|";

		if (done > total) {
			throw new IllegalArgumentException();
		}
		int donePercent = (100 * done) / total;
		int doneLength  = (size * donePercent) / 100;

		StringBuilder bar = new StringBuilder(iconLeftBoundary);
		for (int i = 0; i < size; i++) {
			if (i < doneLength) {
				bar.append(iconDone);
			} else {
				bar.append(iconRemain);
			}
		}
		bar.append(iconRightBoundary);

		return bar.toString();
	}

	public void printQueue(AudioManager audioManager, TextChannel channel) {

		ArrayList<AudioTrack> queue = audioManager.getScheduler()
				.getQueue();

		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(Color.BLUE);
		embed.setTitle("Queue");

		deletePrevious(channel);

		if (queue.isEmpty()) {
			embed.appendDescription("Nothing in the queue.");
			channel.sendMessageEmbeds(embed.build()).queue();
			return;
		}


		if (queue.size() > 1) {
			Collections.reverse(queue);

			int index = queue.size();
			embed.appendDescription("```\n");
			// Subtract 1 to remove next(last in list) song in queue to display separately.
			for (AudioTrack track : queue) {
				if (queue.get(queue.size() - 1) == track) {
					continue;
				}

				embed.appendDescription((index) + ". ");
				index--;

				embed.appendDescription(track.getInfo().title + "\n");

				// Limit is 2048 characters per embed description. This allows some buffer. Had issues at 2000 characters.
				if (embed.getDescriptionBuilder().toString().length() >= 1800) {
					embed.appendDescription("```");
					channel.sendMessageEmbeds(embed.build())
							.queue();

					embed = new EmbedBuilder();
					embed.setColor(Color.BLUE);
					embed.setTitle("Queue");
					embed.appendDescription("```\n");
				}
			}
			embed.appendDescription("```");
		}

		embed.appendDescription(" ```fix\nUp Next: " + queue.get(queue.size() - 1).getInfo().title + "```");

		channel.sendMessageEmbeds(embed.build()).queue();
	}

}
