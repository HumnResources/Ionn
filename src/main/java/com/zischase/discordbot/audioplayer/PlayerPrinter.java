package com.zischase.discordbot.audioplayer;

import com.github.ygimenez.exception.InvalidHandlerException;
import com.github.ygimenez.model.Page;
import com.github.ygimenez.model.Paginator;
import com.github.ygimenez.model.PaginatorBuilder;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.player.event.TrackExceptionEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.zischase.discordbot.CPages;
import com.zischase.discordbot.DBQueryHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class PlayerPrinter {

	private static final int    PRINT_TIMEOUT_MS          = 2000;
	private static final int    NOW_PLAYING_TIMER_RATE_MS = 7500;
	private static final int    HISTORY_POLL_LIMIT        = 5;
	private static final int    PROGRESS_BAR_SIZE         = 16;
	private static final int    QUEUE_PAGE_SIZE           = 5;
	private static final int 	QUEUE_BUTTON_AMT		  = 4;
	private static final String PROGRESS_BAR_ICON_FILL    = "⬜";
	private static final String PROGRESS_BAR_ICON_EMPTY   = "⬛";
	private static final String NOW_PLAYING_MSG_NAME      = "**Now Playing**";
	private static final String QUEUE_MSG_NAME            = "**Queue**";
	private static final String NOTHING_PLAYING_MSG_NAME  = "**Nothing Playing**";

	private final ScheduledExecutorService SCHEDULED_EXEC      = Executors.newSingleThreadScheduledExecutor();
	private final AtomicReference<Message> nowPlayingMessage   = new AtomicReference<>(null);
	private final AtomicReference<Message> queueMessage        = new AtomicReference<>(null);
	private final Semaphore                nowPlayingSemaphore = new Semaphore(1);
	private final Semaphore                queueSemaphore      = new Semaphore(1);
	private final AudioManager             audioManager;

	private Runnable         trackTimer = null;
	private List<AudioTrack> copyQueue  = null;
	private Paginator        paginator;
	private int              playCount  = 0;

	public PlayerPrinter(AudioManager audioManager, Guild guild) {
		String id = guild.getId();
		this.audioManager = audioManager;
		initializeTrackListener(guild);

		try {
			paginator = PaginatorBuilder.createPaginator()
					.setHandler(guild.getJDA())
					.setDeleteOnCancel(true)
					.shouldEventLock(true)
					.shouldRemoveOnReact(true)
					.build();
		} catch (InvalidHandlerException e) {
			e.printStackTrace();
		}

		/* Add watcher for reaction response to now playing message */
		TrackWatcherEventListener trackWatcher = new TrackWatcherEventListener(this, audioManager, id);
		guild.getJDA().addEventListener(trackWatcher);
	}

	private void initializeTrackListener(Guild guild) {
		String id = guild.getId();
		/* Check for available channel to display Now PLaying prompt */
		/* Ensure we have somewhere to send the message, check for errors */
		/* Set up a timer to continually update the running time of the song */
		AudioEventListener audioEventListener = audioEvent -> {
			/* Check for available channel to display Now PLaying prompt */
			TextChannel  textChannel  = guild.getTextChannelById(DBQueryHandler.get(id, "media_settings", "textChannel"));
			VoiceChannel voiceChannel = guild.getVoiceChannelById(DBQueryHandler.get(id, "media_settings", "voicechannel"));
			this.playCount++;

			if (textChannel == null || voiceChannel == null) {
				return;
			}
			/* Release all holds from previous song */
			queueSemaphore.release();
			nowPlayingSemaphore.release();
			TrackScheduler scheduler = audioManager.getScheduler();

			/* Clear old now playing and queue messages on a fresh start of songs */
			if (playCount == 1) {
				deletePrevious(textChannel);
			}

			switch (audioEvent.getClass().getSimpleName()) {
				case "TrackStuckEvent" -> {
					textChannel.sendMessage("Audio track stuck! Ending track and continuing").queue();
					scheduler.nextTrack();
				}
				case "TrackExceptionEvent" -> {
					textChannel.sendMessage("Error loading the audio.").queue();
					((TrackExceptionEvent) audioEvent).exception.printStackTrace();
					scheduler.nextTrack();
				}
				case "TrackEndEvent" -> {
					if (scheduler.getQueue().isEmpty() && audioManager.getPlayer().getPlayingTrack() == null) {
						deletePrevious(textChannel);
						guild.getJDA().getDirectAudioController().disconnect(guild);
						CPages.deactivate();
						this.playCount = 0;
					}
				}
				case "TrackStartEvent" -> {
					boolean inChannel = guild.getSelfMember().getVoiceState() != null && Objects.requireNonNull(guild.getSelfMember().getVoiceState()).inVoiceChannel();

					/* Make sure someone can listen */
					if (!inChannel) {
						guild.getJDA().getDirectAudioController().connect(voiceChannel);
					}

					/* Set to empty at start, will print on first run if anything in queue */
					copyQueue = new ArrayList<>();

					if (trackTimer == null) {
						trackTimer = () -> {
							AudioTrack track = audioEvent.player.getPlayingTrack();

							if (track != null && track.getDuration() != Integer.MAX_VALUE && track.getPosition() < track.getDuration()) {
								printNowPlaying(textChannel);

								if (listChanged(audioManager.getScheduler().getQueue(), copyQueue)) {
									printQueue(textChannel);
									copyQueue = audioManager.getScheduler().getQueue();
								}
							}
						};
					}

					SCHEDULED_EXEC.scheduleAtFixedRate(trackTimer, PRINT_TIMEOUT_MS, NOW_PLAYING_TIMER_RATE_MS, TimeUnit.MILLISECONDS);
				}
			}
		};
		/* Add the audio event watcher to the current guild's audio manager */
		audioManager.getPlayer().addListener(audioEventListener);
	}


	public Message getNowPlayingMessage() {
		return nowPlayingMessage.get();
	}

	public void printNowPlaying(TextChannel textChannel) {
		printNowPlaying(textChannel, false);
	}

	public void printNowPlaying(TextChannel textChannel, boolean forcePrint) {
		if (!nowPlayingSemaphore.tryAcquire()) {
			return;
		}
		this.nowPlayingMessage.set(getNPMsg(textChannel.getHistory().retrievePast(HISTORY_POLL_LIMIT).complete()));
		Message builtMessage = buildNowPlaying();

		if (forcePrint) {
			deletePrevious(textChannel);
			if (audioManager.getScheduler().getQueue().size() > 0) {
				printQueue(textChannel);
			}
			textChannel.sendMessage(builtMessage).queue(message -> {
				addReactions(message);
				this.nowPlayingMessage.set(message);
			});
			if (audioManager.getScheduler().getQueue().size() > 0) {
				printQueue(textChannel);
			}
		} else {
			if (this.nowPlayingMessage.get() == null) {
				deletePrevious(textChannel);
				textChannel.sendMessage(builtMessage).queue(message -> {
					addReactions(message);
					this.nowPlayingMessage.set(message);
				});
			} else {
				textChannel.editMessageById(this.nowPlayingMessage.get().getId(), builtMessage).queue();
			}
		}

		try {
			Thread.sleep(PRINT_TIMEOUT_MS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		nowPlayingSemaphore.release();
	}

	public void printQueue(@NotNull TextChannel textChannel) {
		if (!queueSemaphore.tryAcquire()) {
			return;
		}

		if (!CPages.isActivated()) {
			try {
				CPages.activate(paginator);
			} catch (InvalidHandlerException e) {
				e.printStackTrace();
			}
		}

		List<Page> queuePages = buildQueue(audioManager.getScheduler().getQueue());
		if (queuePages == null) {
			return;
		}

		Message message = getQueueMsg(textChannel.getHistory().retrievePast(HISTORY_POLL_LIMIT).complete());
		if (message == null) {
			textChannel.sendMessage((Message) queuePages.get(0).getContent()).queue(
					success -> CPages.paginate(success, queuePages, true, true)
			);
		} else {
			textChannel.editMessageById(message.getId(), (Message) queuePages.get(0).getContent()).queue(
					success -> CPages.paginate(success, queuePages, success.getReactions().size() != QUEUE_BUTTON_AMT, true)
			);
		}

		try {
			Thread.sleep(PRINT_TIMEOUT_MS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		queueSemaphore.release();
	}

	public void deletePrevious(@NotNull TextChannel textChannel) {
		List<Message> msgList = textChannel.getHistory()
				.retrievePast(HISTORY_POLL_LIMIT)
				.complete()
				.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
				.filter(msg -> msg.getContentRaw().contains(NOW_PLAYING_MSG_NAME) || msg.getContentRaw().contains(NOTHING_PLAYING_MSG_NAME) || msg.getContentRaw().contains(QUEUE_MSG_NAME))
				.collect(Collectors.toList());

		if (msgList.size() > 1) {
			textChannel.deleteMessages(msgList).complete();
		} else if (msgList.size() == 1) {
			textChannel.deleteMessageById(msgList.get(0).getId()).complete();
		}
	}

	public void shutdown() {
		SCHEDULED_EXEC.shutdown();
	}

	@NotNull
	private Message buildNowPlaying() {
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
				embedBuilder.appendDescription("**%s - %s**\n".formatted(timeCurrent, timeTotal));
				embedBuilder.appendDescription(progressPercentage((int) position, (int) duration));
			}

			embedBuilder.setTitle("**%s**".formatted(title));

			if (paused.equals(MediaControls.PAUSE)) {
				embedBuilder.setColor(Color.RED);
			} else {
				embedBuilder.setColor(Color.GREEN);
			}
		}
		embedBuilder.addField("", "%s **%s**".formatted(MediaControls.VOLUME_HIGH, audioManager.getPlayer().getVolume()), true);
		embedBuilder.addField("", "%s %s %s".formatted(paused, repeat, repeatOne), true);

		return messageBuilder.setEmbeds(embedBuilder.build()).build();
	}

	@Nullable
	private Message getNPMsg(List<Message> messages) {
		return messages
				.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> msg.getAuthor().getId().equals(msg.getJDA().getSelfUser().getId()))
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
				.filter(msg -> msg.getContentDisplay().contains(NOW_PLAYING_MSG_NAME) || msg.getContentDisplay().contains(NOTHING_PLAYING_MSG_NAME))
				.findFirst()
				.flatMap(message -> {
					this.nowPlayingMessage.set(message);
					return Optional.of(message);
				})
				.orElse(null);
	}

	@NotNull
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

	@Nullable
	private Message getQueueMsg(List<Message> messages) {
		return messages.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> msg.getAuthor().getId().equals(msg.getJDA().getSelfUser().getId()))
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
				.filter(msg -> msg.getContentDisplay().contains(QUEUE_MSG_NAME))
				.findFirst()
				.flatMap(message -> {
					this.queueMessage.set(message);
					return Optional.of(message);
				})
				.orElse(null);
	}

	private List<Page> buildQueue(@NotNull List<AudioTrack> queue) {
		EmbedBuilder eb = new EmbedBuilder();

		if (queue.size() == 0) {
			return null;
		}

		List<Page> pages     = new ArrayList<>();
		Page       page;
		int        size      = queue.size();
		int        pageCount = 1;
		for (int i = 0; i < size; i++) {
			eb.setColor(Color.WHITE);
			eb.appendDescription("`%d.` %s\n".formatted(i + 1, queue.get(i).getInfo().title));

			/* Starts a new page or adds last one */
			if (i != 0 && i % QUEUE_PAGE_SIZE == 0 || i == size - 1) {
				eb.setFooter("Page: %d/%d - Songs: %d".formatted(
						pageCount, (int) Math.ceil((0.0d + size) / QUEUE_PAGE_SIZE), size)
				);
				pageCount++;
				page = new Page(new MessageBuilder().append(QUEUE_MSG_NAME).setEmbeds(eb.build()).build());
				pages.add(page);
				eb.clear();
			}
		}

		return pages;
	}

	private boolean listChanged(@NonNull List<AudioTrack> trackListOne, @NonNull List<AudioTrack> trackListTwo) {
		if (trackListOne.size() != trackListTwo.size()) {
			return true;
		}

		int size = trackListOne.size();
		for (int i = 0; i < size; i++) {
			if (trackListOne.get(i) != trackListTwo.get(i)) {
				return true;
			}
		}
		return false;
	}

	private void addReactions(Message nowPlayingMsg) {
		/* Small wait to allow printer to display song info if needed */

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
	}
}
