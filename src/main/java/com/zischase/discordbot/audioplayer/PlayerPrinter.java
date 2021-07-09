package com.zischase.discordbot.audioplayer;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jdautilities.menu.Paginator;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.TextChannel;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PlayerPrinter {

	private static final int    DEFAULT_ACTION_TIMEOUT_SEC = 30;
	private static final int    HISTORY_POLL_LIMIT         = 7;
	private static final int    PROGRESS_BAR_SIZE          = 16;
	private static final String PROGRESS_BAR_ICON_FILL     = "⬜";
	private static final String PROGRESS_BAR_ICON_EMPTY    = "⬛";
	private static final String NOW_PLAYING_MSG_NAME       = "**Now Playing**";
	private static final String QUEUE_MSG_NAME             = "**Queue**";
	private static final String NOTHING_PLAYING_MSG_NAME   = "**Nothing Playing**";
	private static final Logger LOGGER                     = LoggerFactory.getLogger(PlayerPrinter.class);

	private final EventWaiter              waiter              = new EventWaiter();
	private final AtomicReference<Message> currentNPMessage    = new AtomicReference<>(null);
	private final AtomicReference<Message> currentQueueMessage = new AtomicReference<>(null);
	private final Semaphore                nowPlayingSemaphore = new Semaphore(1);

	public PlayerPrinter() {

	}

	public void printNowPlaying(AudioManager audioManager, TextChannel channel) {
		printNowPlaying(audioManager, channel, false);
	}

	public void printNowPlaying(AudioManager audioManager, TextChannel channel, boolean forcePrint) {
		Message newMessage = buildNPMessage(audioManager);

		if (forcePrint) {
			channel.sendMessage(newMessage).queue();
			return;
		}

		channel.getHistory().retrievePast(HISTORY_POLL_LIMIT).queue(messages -> {
			Message currentMsg = getNPMsg().apply(messages);
			if (currentMsg != null) {
				channel.editMessageById(currentMsg.getId(), newMessage).queue();
			} else {
				channel.sendMessage(newMessage).queue();
			}
		});

		addReactions().accept(channel);
	}

	public void deletePrevious(TextChannel textChannel) {
		List<Message> msgList = textChannel.getHistory()
				.retrievePast(HISTORY_POLL_LIMIT)
				.complete()
				.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
				.filter(msg -> msg.getContentDisplay().contains(NOW_PLAYING_MSG_NAME) || msg.getContentDisplay().contains(NOTHING_PLAYING_MSG_NAME) || msg.getContentDisplay().contains(QUEUE_MSG_NAME))
				.collect(Collectors.toList());

		if (msgList.size() > 1) {
			textChannel.deleteMessages(msgList).complete();
		} else if (msgList.size() == 1) {
			textChannel.deleteMessageById(msgList.get(0).getId()).complete();
		}
	}

	public void printQueue(List<AudioTrack> queue, TextChannel channel) {
		channel.getHistory().retrievePast(HISTORY_POLL_LIMIT).queue(messages -> {
			Message qMessage = getQueueMsg().apply(messages);
			if (qMessage == null) {
				buildQueueMsg(queue).andThen((Message m) -> printMessage()).apply(channel);
			} else {
				qMessage.editMessage(buildQueueMsg(queue).apply(channel)).queue();
			}
		});
	}

	private String progressPercentage(int position, int duration) {
		if (position > duration) {
			throw new IllegalArgumentException();
		}

		int donePercent = (100 * position) / duration;
		int doneLength  = (PROGRESS_BAR_SIZE * donePercent) / 100;

		StringBuilder bar = new StringBuilder();
		for (int i = 0; i < PROGRESS_BAR_SIZE; i++) {
			if (i < doneLength) {
				bar.append(PROGRESS_BAR_ICON_FILL);
			} else {
				bar.append(PROGRESS_BAR_ICON_EMPTY);
			}
		}
		return bar.toString();
	}

	private Consumer<TextChannel> addReactions() {
		return textChannel -> textChannel.getHistory().retrievePast(HISTORY_POLL_LIMIT).queue(messages -> {
			/* Small wait to allow printer to display song info if needed */
			Message nowPlayingMsg = getNPMsg().apply(messages);

			if (nowPlayingMsg == null || nowPlayingMsg.getType() == MessageType.UNKNOWN) {
				return;
			}

			List<String> reactionsPresent = nowPlayingMsg.getReactions()
					.stream()
					.map(reaction -> reaction.getReactionEmote().getName())
					.collect(Collectors.toList());

			/* Only add a reaction if it's missing. Saves on queues submit to discord API */
			for (String reaction : MediaControls.getReactions()) {
				if (!reactionsPresent.contains(reaction)) {
					nowPlayingMsg.addReaction(reaction).queue();
				}
			}
		});
	}

	private Message buildNPMessage(AudioManager audioManager) {
		AudioPlayer    player         = audioManager.getPlayer();
		AudioTrack     track          = player.getPlayingTrack();
		MessageBuilder messageBuilder = new MessageBuilder();
		EmbedBuilder   embedBuilder   = new EmbedBuilder();

		String paused    = player.isPaused() ? MediaControls.PAUSE : MediaControls.PLAY;
		String repeatOne = audioManager.getScheduler().isRepeatSong() ? MediaControls.REPEAT_ONE : "";
		String repeat    = audioManager.getScheduler().isRepeatQueue() ? MediaControls.REPEAT_QUEUE : "";

		if (track == null) {
			messageBuilder.append(NOTHING_PLAYING_MSG_NAME);
			embedBuilder.setColor(Color.darkGray);
			embedBuilder.setFooter(". . .");
		} else {
			messageBuilder.append(NOW_PLAYING_MSG_NAME);

			AudioTrackInfo info        = track.getInfo();
			long           duration    = info.length / 1000;
			long           position    = track.getPosition() / 1000;
			String         timeTotal   = String.format("%d:%02d:%02d", duration / 3600, (duration % 3600) / 60, (duration % 60));
			String         timeCurrent = String.format("%d:%02d:%02d", position / 3600, (position % 3600) / 60, (position % 60));
			String         title       = info.title;

			if (title == null || title.isEmpty()) {
				String author = info.author;
				if (author != null && !author.isEmpty()) {
					title = author;
				} else {
					title = "---";
				}
			}

			/* Checks to see if it's a live stream */
			if (track.getDuration() == Long.MAX_VALUE) {
				embedBuilder.appendDescription(MediaControls.RED_RECORDING_DOT);
			} else {
				embedBuilder.appendDescription("**%s \u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0 %s**\n".formatted(timeCurrent, timeTotal));
				embedBuilder.appendDescription(progressPercentage((int) position, (int) duration));
			}

			embedBuilder.setTitle("**%s**".formatted(title));
			embedBuilder.setColor(Color.CYAN);
		}
		embedBuilder.addField("", "%s **%s**".formatted(MediaControls.VOLUME_HIGH, audioManager.getPlayer().getVolume()), true);
		embedBuilder.addField("", "%s %s %s".formatted(paused, repeat, repeatOne), true);

		return messageBuilder.setEmbeds(embedBuilder.build()).build();
	}

	public Message getCurrentNPMessage(TextChannel textChannel) {
		try {
			this.nowPlayingSemaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (currentNPMessage.get() == null) {

			/* Start fetch for new message */
			textChannel.getHistory().retrievePast(HISTORY_POLL_LIMIT).queue(messages -> getNPMsg().apply(messages));

			/* Wait until we receive a result */
			OffsetDateTime start = OffsetDateTime.now();
			while (this.nowPlayingSemaphore.availablePermits() == 0) {
				if (OffsetDateTime.now().isAfter(start.plusSeconds(DEFAULT_ACTION_TIMEOUT_SEC))) {
					LOGGER.warn("Timeout on player printer, too long waiting for semaphore!");
					this.nowPlayingSemaphore.drainPermits();
				}
			}
		}

		if (this.nowPlayingSemaphore.availablePermits() == 0) {
			this.nowPlayingSemaphore.release();
		}

		return currentNPMessage.get();
	}

	@NonNull
	private Function<List<Message>, Message> getNPMsg() {
		return messages -> messages
				.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
				.filter(msg -> msg.getContentDisplay().contains(NOW_PLAYING_MSG_NAME) || msg.getContentDisplay().contains(NOTHING_PLAYING_MSG_NAME))
				.findFirst()
				.flatMap(message -> {
					this.currentNPMessage.set(message);
					this.nowPlayingSemaphore.release();
					return Optional.of(message);
				})
				.orElse(null);
	}

	public Function<List<Message>, Message> getQueueMsg() {
		return messages ->
				messages.stream()
						.filter(msg -> msg.getAuthor().isBot())
						.filter(msg -> !msg.isPinned())
						.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
						.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
						.filter(msg -> msg.getContentDisplay().contains(QUEUE_MSG_NAME))
						.findFirst()
						.orElse(null);
	}

	private Function<TextChannel, Message> buildQueueMsg(List<AudioTrack> queue) {
		return (textChannel) -> {
			textChannel.getJDA().removeEventListener(waiter);

			Message qMessage = new MessageBuilder().append(QUEUE_MSG_NAME).build();
			if (queue.size() > 0) {
				Paginator.Builder builder = new Paginator.Builder()
						.setText(QUEUE_MSG_NAME)
						.setColor(Color.darkGray)
						.useNumberedItems(true)
						.showPageNumbers(true)
						.setColumns(1)
						.waitOnSinglePage(true)
						.setItemsPerPage(10)
						.setEventWaiter(waiter)
						.allowTextInput(true)
						.setFinalAction((msg) -> textChannel.getJDA().removeEventListener(waiter));

				queue.forEach(track -> builder.addItems(track.getInfo().title));

				textChannel.sendMessage(qMessage).queue(qMsg -> builder.build().display(qMsg));
				textChannel.getJDA().addEventListener(waiter);
			}

			return qMessage;
		};
	}

	private BiConsumer<TextChannel, Message> printMessage() {
		return (textChannel, message) -> textChannel.sendMessage(message).queue();
	}

}
