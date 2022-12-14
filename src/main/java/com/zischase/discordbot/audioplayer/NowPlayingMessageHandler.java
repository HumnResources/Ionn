package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.player.event.TrackExceptionEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.audiocommands.Shuffle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.zischase.discordbot.audioplayer.MediaControls.*;

public class NowPlayingMessageHandler extends ListenerAdapter {

	private final Timer        timer = new Timer();
	private final AudioManager audioManager;
	private final String       guildID;

	private List<AudioTrack> copyQueue         = new ArrayList<>();
	private TimerTask        trackTimerTask    = null;
	private Message          nowPlayingMessage = null;
	TrackScheduler scheduler;
	NowPlayingMessageHandler nowPlayingMessageHandler;
	QueueMessageHandler      queueMessageHandler;

	public NowPlayingMessageHandler(AudioManager audioManager, Guild guild) {
		this.guildID      = guild.getId();
		this.audioManager = audioManager;
		scheduler    = audioManager.getScheduler();
		nowPlayingMessageHandler = audioManager.getNowPlayingMessageHandler();
		queueMessageHandler      = audioManager.getQueueMessageHandler();

		initializeTrackListener(guild);
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

			if (textChannel == null || voiceChannel == null) {
				return;
			}

			if (!guild.getJDA().getRegisteredListeners().contains(nowPlayingMessageHandler)) {
				guild.getJDA().addEventListener(nowPlayingMessageHandler);
			}

			if (!guild.getJDA().getRegisteredListeners().contains(queueMessageHandler)) {
				guild.getJDA().addEventListener(queueMessageHandler);
			}

			switch (audioEvent.getClass().getSimpleName()) {
				case "TrackStuckEvent" -> {
					textChannel.sendMessage("Audio track stuck! Ending track and continuing").queue();
					if (!scheduler.getQueue().isEmpty()) {
						scheduler.nextTrack();
					}
					if (scheduler.getQueue().isEmpty() && audioManager.getPlayer().getPlayingTrack() == null) {
						deletePrevious(textChannel);
						guild.getJDA().removeEventListener(queueMessageHandler, nowPlayingMessageHandler);
						guild.getJDA().getDirectAudioController().disconnect(guild);
					}
				}
				case "TrackExceptionEvent" -> {
					textChannel.sendMessage("Error loading the audio for track `"+audioEvent.player.getPlayingTrack().getInfo().title+"`.").queue();
					((TrackExceptionEvent) audioEvent).exception.printStackTrace();
					if (!scheduler.getQueue().isEmpty()) {
						scheduler.nextTrack();
					}
					if (scheduler.getQueue().isEmpty() && audioManager.getPlayer().getPlayingTrack() == null) {
						deletePrevious(textChannel);
						guild.getJDA().removeEventListener(queueMessageHandler, nowPlayingMessageHandler);
						guild.getJDA().getDirectAudioController().disconnect(guild);
					}
				}
				case "TrackEndEvent" -> {
					if (scheduler.getQueue().isEmpty() && audioManager.getPlayer().getPlayingTrack() == null) {
						deletePrevious(textChannel);
						guild.getJDA().removeEventListener(queueMessageHandler, nowPlayingMessageHandler);
						guild.getJDA().getDirectAudioController().disconnect(guild);
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
									queueMessageHandler.printQueuePage(textChannel, queueMessageHandler.getCurrentPageNum());
									copyQueue = audioManager.getScheduler().getQueue();
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

		/* Small sleep to allow messages to be deleted */
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
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

	public void printNowPlaying(TextChannel textChannel) {
		printNowPlaying(textChannel, false);
	}

	public synchronized void printNowPlaying(TextChannel textChannel, boolean forcePrint) {
		this.nowPlayingMessage = getNPMsg(textChannel.getHistory().retrievePast(HISTORY_POLL_LIMIT).complete());

		MessageCreateData msg = buildNowPlaying();

		if (this.nowPlayingMessage == null || forcePrint || this.nowPlayingMessage.getType() == MessageType.UNKNOWN) {
			deletePrevious(textChannel);

			QueueMessageHandler queueMessageHandler = audioManager.getQueueMessageHandler();
			queueMessageHandler.printQueuePage(textChannel, queueMessageHandler.getCurrentPageNum());

			textChannel.sendMessage(msg).queue(message -> {
				addReactions(message);
				this.nowPlayingMessage = message;
			});
		} else {
			textChannel.editMessageById(this.nowPlayingMessage.getId(), MessageEditData.fromCreateData(msg)).queue();
		}

		try {
			Thread.sleep(PRINT_TIMEOUT_MS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

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

	private MessageCreateData buildNowPlaying() {
		AudioPlayer          player         = audioManager.getPlayer();
		AudioTrack           track          = player.getPlayingTrack();
		MessageCreateBuilder messageBuilder = new MessageCreateBuilder();
		EmbedBuilder   embedBuilder   = new EmbedBuilder();

		String paused     = player.isPaused() ? MediaControls.PAUSE : MediaControls.PLAY;
		String repeatSong = audioManager.getScheduler().isRepeatSong() ? MediaControls.REPEAT_ONE : "";

		if (track == null) {
			messageBuilder.addContent(NOTHING_PLAYING_MSG_NAME);
			embedBuilder.setColor(Color.darkGray);
			embedBuilder.setTitle("...");
		} else {
			messageBuilder.addContent(NOW_PLAYING_MSG_NAME);

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

		return messageBuilder.addEmbeds(embedBuilder.build()).build();
	}

	private void addReactions(Message nowPlayingMsg) {
		/* Small wait to allow printer to display song info if needed */

		if (nowPlayingMsg == null || nowPlayingMsg.getType() == MessageType.UNKNOWN) {
			return;
		}

		List<String> reactionsPresent = nowPlayingMsg.getReactions()
				.stream()
				.map(reaction -> reaction.getEmoji().getName())
				.collect(Collectors.toList());

		/* Only add a reaction if it's missing. Saves on queues submit to discord API */
		for (String reaction : MediaControls.getNowPlayingReactions()) {
			if (!reactionsPresent.contains(reaction)) {
				nowPlayingMsg.addReaction(Emoji.fromFormatted(reaction)).submit();
			}
		}
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

	@Override
	public void onGenericMessageReaction(@NotNull GenericMessageReactionEvent event) {
		Member eventMember = event.getMember();
		if (eventMember == null || eventMember.getUser().isBot() || event.getReaction().isSelf()) {
			return;
		}
		Message msg = event.retrieveMessage().complete();
		if (!msg.getAuthor().isBot()) {
			return;
		}
		Message currentNPMessage = audioManager.getNowPlayingMessageHandler().getNowPlayingMessage();
		String  reaction         = event.getReaction().getEmoji().getName();

		CompletableFuture.runAsync(() -> {
			if (currentNPMessage != null && msg.getId().equals(currentNPMessage.getId())) {
				nowPlayingInteraction(reaction);
			}
		});
	}

	public Message getNowPlayingMessage() {
		return this.nowPlayingMessage;
	}

	private void nowPlayingInteraction(String reaction) {
		Message currentNPMessage = audioManager.getNowPlayingMessageHandler().getNowPlayingMessage();
		switch (reaction) {
			case SHUFFLE -> Shuffle.shuffle(guildID, audioManager);
			case REPEAT_QUEUE -> audioManager.getScheduler().setRepeatQueue(!audioManager.getScheduler().isRepeatQueue());
			case REPEAT_ONE -> audioManager.getScheduler().setRepeatSong(!audioManager.getScheduler().isRepeatSong());
			case PREV_TRACK -> audioManager.getScheduler().prevTrack();
			case PLAY_PAUSE -> audioManager.getPlayer().setPaused(!audioManager.getPlayer().isPaused());
			case NEXT_TRACK -> audioManager.getScheduler().nextTrack();
			case STOP -> {
				audioManager.getScheduler().clearQueue();
				audioManager.getPlayer().stopTrack();
			}
		}
		printNowPlaying(currentNPMessage.getChannel().asTextChannel());
	}
}
