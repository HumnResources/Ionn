package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.player.event.TrackExceptionEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.zischase.discordbot.DBQueryHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static com.zischase.discordbot.audioplayer.MediaControls.*;

public class PlayerPrinter {
	private final Semaphore    nowPlayingSemaphore = new Semaphore(1);
	private final Timer        timer               = new Timer();
	private final AudioManager audioManager;

	private List<AudioTrack> copyQueue         = new ArrayList<>();
	private TimerTask        trackTimerTask    = null;
	private Message          nowPlayingMessage = null;
	private int              playCount         = 0;

	private final QueuePrinter queuePrinter;

	public PlayerPrinter(AudioManager audioManager, Guild guild) {
		String id = guild.getId();
		this.audioManager = audioManager;
		this.queuePrinter = new QueuePrinter(audioManager);
		initializeTrackListener(guild);


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
			TextChannel  textChannel  = guild.getTextChannelById(DBQueryHandler.get(id, "media_settings", "textchannel"));
			VoiceChannel voiceChannel = guild.getVoiceChannelById(DBQueryHandler.get(id, "media_settings", "voicechannel"));
			this.playCount++;

			if (textChannel == null || voiceChannel == null) {
				return;
			}
			TrackScheduler scheduler = audioManager.getScheduler();

			/* Clear old now playing and queue messages on a fresh start of songs */
			if (playCount == 1) {
				deletePrevious(textChannel);
			}

			switch (audioEvent.getClass().getSimpleName()) {
				case "TrackStuckEvent" -> {
					textChannel.sendMessage("Audio track stuck! Ending track and continuing").queue();
					if (!scheduler.getQueue().isEmpty()) {
						scheduler.nextTrack();
					}
					if (scheduler.getQueue().isEmpty() && audioManager.getPlayer().getPlayingTrack() == null) {
						deletePrevious(textChannel);
						guild.getJDA().removeEventListener(queuePrinter);
						guild.getJDA().getDirectAudioController().disconnect(guild);
						this.playCount = 0;
					}
				}
				case "TrackExceptionEvent" -> {
					textChannel.sendMessage("Error loading the audio.").queue();
					((TrackExceptionEvent) audioEvent).exception.printStackTrace();
					if (!scheduler.getQueue().isEmpty()) {
						scheduler.nextTrack();
					}
					if (scheduler.getQueue().isEmpty() && audioManager.getPlayer().getPlayingTrack() == null) {
						deletePrevious(textChannel);
						guild.getJDA().removeEventListener(queuePrinter);
						guild.getJDA().getDirectAudioController().disconnect(guild);
						this.playCount = 0;
					}
				}
				case "TrackEndEvent" -> {
					if (scheduler.getQueue().isEmpty() && audioManager.getPlayer().getPlayingTrack() == null) {
						deletePrevious(textChannel);
						guild.getJDA().removeEventListener(queuePrinter);
						guild.getJDA().getDirectAudioController().disconnect(guild);
						this.playCount = 0;
					}
				}
				case "TrackStartEvent" -> {
					boolean inChannel = voiceChannel.getMembers().contains(guild.getSelfMember());

					/* Make sure someone can listen */
					if (!inChannel) {
						guild.getJDA().getDirectAudioController().connect(voiceChannel);
					}

					/* Clear any existing timers */
					if (trackTimerTask != null) {
						trackTimerTask.cancel();
					}

					/* Timer to update progress bar of song */
					trackTimerTask = new TimerTask() {
						@Override
						public void run() {
							AudioTrack track = audioEvent.player.getPlayingTrack();

							if (track != null && track.getPosition() < track.getDuration()) {
								printNowPlaying(textChannel);

								if (listChanged(audioManager.getScheduler().getQueue(), copyQueue)) {
									queuePrinter.printQueuePage(textChannel, queuePrinter.getCurrentPageNum());
									copyQueue = audioManager.getScheduler().getQueue();

									/* Queue reactions require event listener */
									if (!guild.getJDA().getRegisteredListeners().contains(queuePrinter)) {
										guild.getJDA().addEventListener(queuePrinter);
									}
								}

							}
						}
					};
					timer.scheduleAtFixedRate(trackTimerTask, 0, NOW_PLAYING_TIMER_RATE_MS);
				}
			}
		};
		/* Add the audio event watcher to the current guild's audio manager */
		audioManager.getPlayer().addListener(audioEventListener);
	}

	public QueuePrinter getQueuePrinter() {
		return this.queuePrinter;
	}

	public Message getNowPlayingMessage() {
		return this.nowPlayingMessage;
	}

	public void printNowPlaying(TextChannel textChannel) {
		printNowPlaying(textChannel, false);
	}

	public void printNowPlaying(TextChannel textChannel, boolean forcePrint) {
		if (!nowPlayingSemaphore.tryAcquire()) {
			return;
		}
		this.nowPlayingMessage = getNPMsg(textChannel.getHistory().retrievePast(HISTORY_POLL_LIMIT).complete());
		Message builtMessage = buildNowPlaying();

		if (forcePrint) {
			deletePrevious(textChannel);
			textChannel.sendMessage(builtMessage).queue(message -> {
				addReactions(message);
				this.nowPlayingMessage = message;
			});
			if (audioManager.getScheduler().getQueue().size() > 0) {
				queuePrinter.printQueuePage(textChannel, queuePrinter.getCurrentPageNum());
			}
			try {
				Thread.sleep(PRINT_TIMEOUT_MS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			nowPlayingSemaphore.release();
			return;
		}

		if (this.nowPlayingMessage == null || this.nowPlayingMessage.getType() == MessageType.UNKNOWN) {
			deletePrevious(textChannel);
			textChannel.sendMessage(builtMessage).queue(message -> {
				addReactions(message);
				this.nowPlayingMessage = message;
			});
		} else {
			textChannel.editMessageById(this.nowPlayingMessage.getId(), builtMessage).queue();
		}

		try {
			Thread.sleep(PRINT_TIMEOUT_MS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		nowPlayingSemaphore.release();
	}


	public void deletePrevious(@NotNull TextChannel textChannel) {
		List<Message> msgList = textChannel.getHistory()
				.retrievePast(HISTORY_POLL_LIMIT * 10) // Use a larger number to ensure we delete every media message
				.complete()
				.stream()
				.filter(msg -> msg.getAuthor().isBot() && msg.getAuthor() == msg.getJDA().getSelfUser())
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
				.filter(msg -> msg.getContentRaw().contains(NOW_PLAYING_MSG_NAME) || msg.getContentRaw().contains(NOTHING_PLAYING_MSG_NAME) || msg.getContentRaw().contains(QUEUE_MSG_NAME))
				.collect(Collectors.toList());

		if (msgList.size() > 1) {
			textChannel.deleteMessages(msgList).submit();
		} else if (msgList.size() == 1) {
			textChannel.deleteMessageById(msgList.get(0).getId()).submit();
		}
	}

	@NotNull
	private Message buildNowPlaying() {
		AudioPlayer    player         = audioManager.getPlayer();
		AudioTrack     track          = player.getPlayingTrack();
		MessageBuilder messageBuilder = new MessageBuilder();
		EmbedBuilder   embedBuilder   = new EmbedBuilder();

		String paused    = player.isPaused() ? MediaControls.PAUSE : MediaControls.PLAY;
		String repeatSong = audioManager.getScheduler().isRepeatSong() ? MediaControls.REPEAT_ONE : "";

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
		embedBuilder.addField("", "%s %s".formatted(paused, repeatSong), true);

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
					this.nowPlayingMessage = message;
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
		for (String reaction : MediaControls.getNowPlayingReactions()) {
			if (!reactionsPresent.contains(reaction)) {
				nowPlayingMsg.addReaction(reaction).submit();
			}
		}
	}
}
