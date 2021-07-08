package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sun.istack.Nullable;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

public class PlayerPrinter {

	private final int       historyPollLimit = 7;
	private final Semaphore semaphore        = new Semaphore(1);

	public PlayerPrinter() {

	}

	public void printNowPlaying(AudioManager audioManager, TextChannel channel) {
		printNowPlaying(audioManager, channel, false);
	}

	public void printNowPlaying(AudioManager audioManager, TextChannel channel, boolean forcePrint) {

		try {
			semaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Message newMessage = buildNewMessage(audioManager);

		if (forcePrint) {
			channel.sendMessage(newMessage).queue();
			semaphore.release();
			return;
		}

		Message currentMsg = getCurrentNowPlayingMsg(channel);

		if (currentMsg != null) {
			channel.editMessageById(currentMsg.getId(), newMessage).complete();
		} else {
			channel.sendMessage(newMessage).complete();
		}
		semaphore.release();
	}

	private Message buildNewMessage(AudioManager audioManager) {
		AudioPlayer  player = audioManager.getPlayer();
		EmbedBuilder embed  = new EmbedBuilder();
		AudioTrack   track  = player.getPlayingTrack();


		if (track == null) {
			embed.setTitle("Nothing Playing");
			embed.setColor(Color.darkGray);
			embed.setFooter(". . .");
		} else {
			AudioTrackInfo info      = track.getInfo();
			long           duration  = info.length / 1000;
			long           position  = track.getPosition() / 1000;
			String         paused    = player.isPaused() ? MediaControls.PAUSE : MediaControls.PLAY;
			String         repeat    = audioManager.getScheduler().isRepeat() ? MediaControls.REPEAT : "";
			String         timeTotal = String.format("%d:%02d:%02d", duration / 3600, (duration % 3600) / 60, (duration % 60));
			String timeCurrent = String.format("%d:%02d:%02d",
					position / 3600,
					(position % 3600) / 60,
					(position % 60)
			);

			String title  = info.title;
			String author = info.author;
			if (title != null && !title.isEmpty()) {
				embed.appendDescription("\n**%s**\n\n".formatted(title));
			} else if (author != null && !author.isEmpty()) {
				embed.appendDescription("**%s**\n\n".formatted(author));
			} else {
				embed.appendDescription("-----\n\n");
			}

			/* Checks to see if it's a live stream */
			if (track.getDuration() == Long.MAX_VALUE) {
				embed.appendDescription(MediaControls.MICROPHONE); // Microphone
			} else {
				embed.appendDescription(timeCurrent + " - " + timeTotal);
				String progressBar = progressPercentage((int) position, (int) duration);
				embed.appendDescription(System.lineSeparator() + progressBar);
			}
			embed.setColor(Color.CYAN);
			embed.setTitle("%s%s  Now Playing  %s%s".formatted(MediaControls.RADIO, MediaControls.NOTES_ONE, MediaControls.NOTES_ONE, MediaControls.RADIO)); // Music Notes
			embed.appendDescription("\n\n%s  %s".formatted(paused, repeat));
			embed.addField("", "\n%s    **%s**".formatted(MediaControls.VOLUME_HIGH, audioManager.getPlayer().getVolume()), true); // Volume
		}

		return new MessageBuilder().setEmbeds(embed.build()).build();
	}

	@Nullable
	public Message getCurrentNowPlayingMsg(TextChannel textChannel) {
		return textChannel
				.getHistory()
				.retrievePast(historyPollLimit)
				.complete()
				.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> !msg.getEmbeds().isEmpty())
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
				.filter(msg -> msg.getEmbeds().get(0).getTitle() != null && Objects.requireNonNull(msg.getEmbeds().get(0).getTitle()).contains("Now Playing"))
				.findFirst()
				.orElse(null);
	}

	void deletePrevious(TextChannel textChannel) {

		List<Message> msgList = textChannel.getHistory()
				.retrievePast(historyPollLimit)
				.complete()
				.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
				.filter(msg -> !msg.getEmbeds().isEmpty())
				.filter(msg -> msg.getEmbeds().get(0).getTitle() != null && Objects.requireNonNull(msg.getEmbeds().get(0).getTitle()).contains("Now Playing") ||
						msg.getEmbeds().get(0).getTitle() != null && Objects.requireNonNull(msg.getEmbeds().get(0).getTitle()).contains("Nothing Playing") ||
						msg.getEmbeds().get(0).getTitle() != null && Objects.requireNonNull(msg.getEmbeds().get(0).getTitle()).contains("Queue")
				)
				.collect(Collectors.toList());

		if (msgList.size() > 1) {
			textChannel.deleteMessages(msgList).complete();
		} else if (msgList.size() == 1) {
			textChannel.deleteMessageById(msgList.get(0).getId()).complete();
		}
	}

	private String progressPercentage(int done, int total) {
		int    size       = 19;
		String iconDone   = "⬜";
		String iconRemain = "⬛";

		if (done > total) {
			throw new IllegalArgumentException();
		}
		int donePercent = (100 * done) / total;
		int doneLength  = (size * donePercent) / 100;

		StringBuilder bar = new StringBuilder();
		for (int i = 0; i < size; i++) {
			if (i < doneLength) {
				bar.append(iconDone);
			} else {
				bar.append(iconRemain);
			}
		}
		return bar.toString();
	}

	public void printQueue(AudioManager audioManager, TextChannel channel) {
		try {
			semaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
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

		channel.sendMessageEmbeds(embed.build()).complete();
		semaphore.release();
	}

}
