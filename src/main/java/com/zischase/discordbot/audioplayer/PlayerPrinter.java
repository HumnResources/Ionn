package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sun.istack.Nullable;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class PlayerPrinter {

	private final int   historyPollLimit = 7;

	private final AtomicReference<Message> nowPlayingMessage = new AtomicReference<>(null);

	public PlayerPrinter() {

	}

	public synchronized void printNowPlaying(AudioManager audioManager, TextChannel channel) {
		printNowPlaying(audioManager, channel, false);
	}

	public synchronized void printNowPlaying(AudioManager audioManager, TextChannel channel, boolean forcePrint) {
		Message currentMsg;
		Message newMessage;

		if (forcePrint) {
			currentMsg = getCurrentNowPlayingMsg(channel);
			channel.sendMessage(currentMsg).queue();
			return;
		}

		newMessage = buildNewMessage(audioManager);
		currentMsg = getCurrentNowPlayingMsg(channel);

		if (currentMsg != null) {
			channel.editMessageById(currentMsg.getId(), newMessage).queue(null, err -> LoggerFactory.getLogger(this.getClass()).info("Error editing message, probably deleted."));
		} else {
			channel.sendMessage(newMessage).queue();
		}
	}

	private Message buildNewMessage(AudioManager audioManager) {
		AudioPlayer  player = audioManager.getPlayer();
		EmbedBuilder embed  = new EmbedBuilder();
		AudioTrack track = player.getPlayingTrack();


		if (track == null) {
			embed.setTitle("Nothing Playing");
			embed.setColor(Color.darkGray);
			embed.setFooter(". . .");
		} else {
			AudioTrackInfo info      = track.getInfo();
			long           duration  = info.length / 1000;
			long           position  = track.getPosition() / 1000;
			String         paused    = player.isPaused() ? "⏸" : "▶";
			String         repeat    = audioManager.getScheduler().isRepeat() ? " \uD83D\uDD01" : ""; // Repeat sign
			String         timeTotal = String.format("%d:%02d:%02d", duration / 3600, (duration % 3600) / 60, (duration % 60));
			String timeCurrent = String.format("%d:%02d:%02d",
					position / 3600,
					(position % 3600) / 60,
					(position % 60)
			);

			String title = info.title;
			String author = info.author;
			if (title != null && !title.isEmpty()) {
				embed.appendDescription(title + "\n\n");
			} else if (author != null && !author.isEmpty()) {
				embed.appendDescription(author + "\n\n");
			} else {
				embed.appendDescription("-----\n\n");
			}

			/* Checks to see if it's a live stream */
			if (track.getDuration() == Long.MAX_VALUE) {
				embed.appendDescription("\uD83C\uDF99"); // Microphone
			} else {
				embed.appendDescription(timeCurrent + " - " + timeTotal + "");
				String progressBar = progressPercentage((int) position, (int) duration);
				embed.appendDescription(System.lineSeparator() + progressBar);
			}
			embed.setColor(Color.CYAN);
			embed.setTitle("\uD83D\uDCFB \uD83C\uDFB6 Now Playing \uD83C\uDFB6 \uD83D\uDCFB "); // Music Notes
			embed.appendDescription("\n\n" + paused + " " + repeat);
			embed.addField("", "\n\uD83D\uDD0A " + audioManager.getPlayer().getVolume(), true); // Volume
		}

		return new MessageBuilder().setEmbeds(embed.build()).build();
	}

	@Nullable
	Message getCurrentNowPlayingMsg(TextChannel textChannel) {
		List<Message> messages = textChannel
				.getHistory()
				.retrievePast(historyPollLimit)
				.complete();
//
//		if (nowPlayingMessage.get() != null && messages.contains(nowPlayingMessage.get())) {
//			return nowPlayingMessage.get();
//		}

		Message message = messages.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> !msg.getEmbeds().isEmpty())
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now().plusDays(14)))
				.filter(msg -> msg.getEmbeds().get(0).getTitle() != null && Objects.requireNonNull(msg.getEmbeds().get(0).getTitle()).contains("Now Playing"))
				.findFirst()
				.orElse(null);

		this.nowPlayingMessage.set(message);
		return message;
	}

	void deletePrevious(TextChannel textChannel) {

		List<Message> msgList = textChannel.getHistory()
				.retrievePast(historyPollLimit)
				.complete()
				.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now().plusDays(14)))
				.filter(msg -> !msg.getEmbeds().isEmpty() && msg.getEmbeds().get(0).getTitle() != null)
				.filter(msg -> Objects.requireNonNull(msg.getEmbeds().get(0).getTitle()).contains("Now Playing"))
				.filter(msg -> Objects.requireNonNull(msg.getEmbeds().get(0).getTitle()).contains("Queue"))
				.filter(msg -> Objects.requireNonNull(msg.getEmbeds().get(0).getTitle()).contains("Nothing Playing"))
				.collect(Collectors.toList());

		if (msgList.size() > 1) {
			textChannel.deleteMessages(msgList).queue();
		} else if (msgList.size() == 1) {
			textChannel.deleteMessageById(msgList.get(0).getId()).queue();
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

		embed.appendDescription(" ```\n1. " + queue.get(queue.size() - 1).getInfo().title + "```");

		channel.sendMessageEmbeds(embed.build()).queue();
	}

}
