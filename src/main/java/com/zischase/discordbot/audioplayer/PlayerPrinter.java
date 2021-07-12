package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.*;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.zischase.discordbot.DBQueryHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class PlayerPrinter {

	//	private static final int    WAITER_TIMEOUT_MS         = 30;
	private static final int                      MAX_WAITER_THREADS        = 1;
	private static final int                      NOW_PLAYING_TIMER_RATE_MS = 7500;
	private static final int                      HISTORY_POLL_LIMIT        = 5;
	private static final int                      PROGRESS_BAR_SIZE         = 16;
	private static final String                   PROGRESS_BAR_ICON_FILL    = "⬜";
	private static final String                   PROGRESS_BAR_ICON_EMPTY   = "⬛";
	private static final String                   NOW_PLAYING_MSG_NAME      = "**Now Playing**";
	private static final String                   QUEUE_MSG_NAME            = "**Queue**";
	private static final String                   NOTHING_PLAYING_MSG_NAME  = "**Nothing Playing**";
	private static final ScheduledExecutorService SCHEDULED_EXEC            = new ScheduledThreadPoolExecutor(MAX_WAITER_THREADS);

	private final AtomicReference<Message> nowPlayingMessage = new AtomicReference<>(null);
	private final AtomicReference<Message> queueMessage      = new AtomicReference<>(null);
	private final Semaphore                semaphore         = new Semaphore(1);

	public PlayerPrinter(AudioManager audioManager, Guild guild) {
		String id = guild.getId();

		/* Check for available channel to display Now PLaying prompt */
		/* Ensure we have somewhere to send the message, check for errors */
		/* Set up a timer to continually update the running time of the song */
		AudioEventListener audioEventListener = audioEvent -> {
			/* Check for available channel to display Now PLaying prompt */
			TextChannel  textChannel  = guild.getTextChannelById(DBQueryHandler.get(id, "media_settings", "textChannel"));
			VoiceChannel voiceChannel = guild.getVoiceChannelById(DBQueryHandler.get(id, "media_settings", "voicechannel"));

			if (textChannel == null || voiceChannel == null) {
				return;
			}

			TrackScheduler scheduler = audioManager.getScheduler();

			/* Ensure we have somewhere to send the message, check for errors */
			if (audioEvent instanceof TrackStuckEvent) {
				textChannel.sendMessage("Audio track stuck! Ending track and continuing").queue();
				scheduler.nextTrack();

			} else if (audioEvent instanceof TrackExceptionEvent) {
				textChannel.sendMessage("Error loading the audio.").queue();
				((TrackExceptionEvent) audioEvent).exception.printStackTrace();
				scheduler.nextTrack();

			} else if (audioEvent instanceof TrackEndEvent && scheduler.getQueue().isEmpty() && audioManager.getPlayer().getPlayingTrack() == null) {
				deletePrevious(textChannel);
				guild.getJDA().getDirectAudioController().disconnect(guild);

			} else if (audioEvent instanceof TrackStartEvent) {
				boolean inChannel = guild.getSelfMember().getVoiceState() != null && Objects.requireNonNull(guild.getSelfMember().getVoiceState()).inVoiceChannel();

				if (!inChannel) {
					guild.getJDA().getDirectAudioController().connect(voiceChannel);
				}

				if (audioManager.getScheduler().getQueue().size() > 0) {
					printQueue(audioManager.getScheduler().getQueue(), textChannel);
				}

				Runnable runnable = () -> {
					AudioTrack track = audioEvent.player.getPlayingTrack();
					if (track != null) {
						if (track.getDuration() != Integer.MAX_VALUE && track.getPosition() < track.getDuration()) {
							printNowPlaying(audioManager, textChannel);
						}
					}
				};
				SCHEDULED_EXEC.scheduleAtFixedRate(runnable, NOW_PLAYING_TIMER_RATE_MS, NOW_PLAYING_TIMER_RATE_MS, TimeUnit.MILLISECONDS);
			}
		};

		/* Add the audio event watcher to the current guild's audio manager */
		audioManager.getPlayer().addListener(audioEventListener);

		/* Add watcher for reaction response to now playing message */
		TrackWatcherEventListener trackWatcher = new TrackWatcherEventListener(this, audioManager, id);
		guild.getJDA().addEventListener(trackWatcher);
	}

	public Message getNowPlayingMessage() {
		return nowPlayingMessage.get();
	}

	public void printNowPlaying(AudioManager audioManager, TextChannel channel) {
		printNowPlaying(audioManager, channel, false);
	}

	public void printNowPlaying(AudioManager audioManager, TextChannel channel, boolean forcePrint) {
		if (!semaphore.tryAcquire()) {
			return;
		}
		this.nowPlayingMessage.set(getNPMsg(channel.getHistory().retrievePast(HISTORY_POLL_LIMIT).complete()));
		Message builtMessage = buildNowPlaying(audioManager);

		if (forcePrint) {
			deletePrevious(channel);
			if (audioManager.getScheduler().getQueue().size() > 0) {
				printQueue(audioManager.getScheduler().getQueue(), channel);
			}
			channel.sendMessage(builtMessage).queue(message -> {
				addReactions(message);
				this.nowPlayingMessage.set(message);
			});
			if (audioManager.getScheduler().getQueue().size() > 0) {
				printQueue(audioManager.getScheduler().getQueue(), channel);
			}
		} else {
			if (this.nowPlayingMessage.get() == null) {
				channel.sendMessage(builtMessage).queue(message -> {
					addReactions(message);
					this.nowPlayingMessage.set(message);
				});
			} else {
				channel.editMessageById(this.nowPlayingMessage.get().getId(), builtMessage).queue();
			}
		}
		semaphore.release();
	}

	public void printQueue(List<AudioTrack> queue, @NotNull TextChannel channel) {
		buildQueue(queue, channel);
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

	@NotNull
	private Message buildNowPlaying(AudioManager audioManager) {
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
			embedBuilder.setColor(Color.CYAN);
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

	@Nullable
	public Message getQueueMsg(List<Message> messages) {
		return messages.stream()
				.filter(msg -> msg.getAuthor().isBot())
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

	private void buildQueue(@NotNull List<AudioTrack> queue, @NotNull TextChannel textChannel) {

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
