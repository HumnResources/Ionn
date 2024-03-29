package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEvent;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.player.event.TrackExceptionEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.zischase.discordbot.DBQuery;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.MessageSendHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.zischase.discordbot.audioplayer.MediaControls.*;

public class NowPlayingMessageHandler extends ListenerAdapter
{
	
	private final static int                 RATE_LIMIT_SEC    = 5;
	private              TimerTask           trackTimerTask    = null;
	private              Message             nowPlayingMessage = null;
	private              Timer               timer             = new Timer();
	private List<AudioTrack>    copyQueue  = new ArrayList<>();
	private Instant             lastUpdate = Instant.now();
	private QueueMessageHandler queueMessageHandler;
	private final        AudioManager        audioManager;
	private final        String              guildID;
	private final AtomicReference<Integer> nRetries = new AtomicReference<>(1);
	
	public NowPlayingMessageHandler(AudioManager audioManager, Guild guild)
	{
		this.guildID      = guild.getId();
		this.audioManager = audioManager;
		initializeTrackListener(guild);
		this.queueMessageHandler = audioManager.getQueueMessageHandler();
	}
	
	private void initializeTrackListener(Guild guild)
	{
		String id = guild.getId();
		
		/* Check for available channel to display Now PLaying prompt */
		/* Ensure we have somewhere to send the message, check for errors */
		/* Set up a timer to continually update the running time of the song */
		AudioEventListener audioEventListener = audioEvent ->
		{
			/* Check for available channel to display Now PLaying prompt */
			TextChannel              textChannel              = guild.getTextChannelById(DBQueryHandler.get(id, DBQuery.MEDIA_SETTINGS, DBQuery.TEXTCHANNEL));
			VoiceChannel             voiceChannel             = guild.getVoiceChannelById(DBQueryHandler.get(id, DBQuery.MEDIA_SETTINGS, DBQuery.VOICECHANNEL));
			TrackScheduler           scheduler                = audioManager.getScheduler();
			NowPlayingMessageHandler nowPlayingMessageHandler = audioManager.getNowPlayingMessageHandler();
			queueMessageHandler = audioManager.getQueueMessageHandler();
			MessageSendHandler messageSendHandler = GuildContext.get(guildID).messageSendHandler();
			
			if (textChannel == null || voiceChannel == null)
			{
				return;
			}
			
			if (!guild.getJDA().getRegisteredListeners().contains(nowPlayingMessageHandler))
			{
				guild.getJDA().addEventListener(nowPlayingMessageHandler);
			}
			
			if (!guild.getJDA().getRegisteredListeners().contains(queueMessageHandler))
			{
				guild.getJDA().addEventListener(queueMessageHandler);
			}
			
			switch (audioEvent.getClass().getSimpleName())
			{
				case "TrackStuckEvent" ->
				{
					messageSendHandler.sendAndDeleteMessageChars.accept(textChannel, "Audio track stuck! Ending track and continuing");
					
					if (retrySong(textChannel, audioManager.getPlayer().getPlayingTrack().getInfo().title))
					{
						return;
					}
					
					if (!scheduler.getQueue().isEmpty())
					{
						scheduler.nextTrack();
					}
					if (scheduler.getQueue().isEmpty() && audioManager.getPlayer().getPlayingTrack() == null)
					{
						deletePrevious(textChannel);
						guild.getJDA().removeEventListener(queueMessageHandler, nowPlayingMessageHandler);
						guild.getJDA().getDirectAudioController().disconnect(guild);
					}
				}
				case "TrackExceptionEvent" ->
				{
					messageSendHandler.sendAndDeleteMessageChars.accept(textChannel, "Error loading the audio for track `" + audioEvent.player.getPlayingTrack().getInfo().title + "`.");
					
					if (retrySong(textChannel, audioManager.getPlayer().getPlayingTrack().getInfo().title))
					{
						return;
					}
					
					((TrackExceptionEvent) audioEvent).exception.printStackTrace();
					if (!scheduler.getQueue().isEmpty())
					{
						scheduler.nextTrack();
					}
					else if (scheduler.getQueue().isEmpty() && audioManager.getPlayer().getPlayingTrack() == null)
					{
						deletePrevious(textChannel);
						guild.getJDA().removeEventListener(queueMessageHandler, nowPlayingMessageHandler);
						guild.getJDA().getDirectAudioController().disconnect(guild);
					}
				}
				case "TrackEndEvent" ->
				{
					if (scheduler.getQueue().isEmpty() && audioManager.getPlayer().getPlayingTrack() == null)
					{
						deletePrevious(textChannel);
						guild.getJDA().removeEventListener(queueMessageHandler, nowPlayingMessageHandler);
						guild.getJDA().getDirectAudioController().disconnect(guild);
					}
				}
				case "TrackStartEvent" ->
				{
					boolean inChannel = voiceChannel.getMembers().contains(guild.getSelfMember());
					nRetries.set(1);
					
					/* Make sure someone can listen */
					if (!inChannel)
					{
						guild.getJDA().getDirectAudioController().connect(voiceChannel);
					}
					
					/* Clear any existing timers */
					if (trackTimerTask != null)
					{
						timer.cancel();
					}
					
					timer = new Timer();
					
					trackTimerTask = getTrackTimerTask(queueMessageHandler, textChannel, audioEvent);
					
					/* Timer to update progress bar of song */
					timer.scheduleAtFixedRate(trackTimerTask, 0, NOW_PLAYING_TIMER_RATE_MS);
				}
			}
		};
		
		/* Add the audio event watcher to the current guild's audio manager */
		audioManager.getPlayer().addListener(audioEventListener);
	}
	
	private boolean retrySong(TextChannel textChannel, String songName)
	{
		if (nRetries.get() != 1)
		{
			return false;
		}
		
		nRetries.set(0);
		audioManager.saveAudioState();
		long position = Long.parseLong(DBQueryHandler.get(guildID, DBQuery.ACTIVESONGDURATION));
		audioManager.getTrackLoader().loadNext(textChannel, songName);
		audioManager.getScheduler().nextTrack();
		audioManager.getPlayer().getPlayingTrack().setPosition(position);
		
		return true;
	}
	
	public void deletePrevious(@NotNull TextChannel textChannel)
	{
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
		
		if (msgList.size() > 1)
		{
			textChannel.deleteMessages(msgList).submit();
		}
		else if (msgList.size() == 1)
		{
			textChannel.deleteMessageById(msgList.get(0).getId()).submit();
		}
		
		/* Small sleep to allow messages to be deleted */
		try
		{
			Thread.sleep(100);
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}
	private final Semaphore semaphore = new Semaphore(1);
	private TimerTask getTrackTimerTask(QueueMessageHandler queueMessageHandler, TextChannel textChannel, AudioEvent audioEvent)
	{
		return new TimerTask()
		{
			@Override
			public void run()
			{
				try
				{
					{
						semaphore.acquire();
					}
				} catch (InterruptedException e)
				{
					throw new RuntimeException(e);
				}
				
				AudioTrack track = audioEvent.player.getPlayingTrack();
				if (track == null)
				{
					semaphore.release();
					return;
				}
				
				boolean incorrectState = audioManager.getPlayerState() != AudioPlayerState.PLAYING && audioManager.getPlayerState() != AudioPlayerState.PAUSED;
				boolean rateLimited    = Instant.now().isBefore(lastUpdate.plusSeconds(RATE_LIMIT_SEC));
				boolean isLive         = (audioManager.getPlayer().getPlayingTrack().getDuration() == Long.MAX_VALUE && getNowPlayingMessage() != null);
				boolean validMessage = false;
				
				if (getNowPlayingMessage() != null && getNowPlayingMessage().getEmbeds().get(0) != null)
				{
					MessageEmbed.AuthorInfo authorInfo = getNowPlayingMessage().getEmbeds().get(0).getAuthor();
					
					if (authorInfo != null && authorInfo.getName() != null)
					{
						validMessage = getNowPlayingMessage().getContentRaw().contains(NOW_PLAYING_MSG_NAME) && authorInfo.getName().matches(audioManager.getPlayer().getPlayingTrack().getInfo().title);
					}
				}
				
				if (track.getPosition() > track.getDuration() || incorrectState || rateLimited || (isLive && !validMessage))
				{
					semaphore.release();
					return;
				}
				
				lastUpdate = Instant.now();
				printNowPlaying(textChannel);
				audioManager.saveAudioState();
				
				if (listChanged(audioManager.getScheduler().getQueue(), copyQueue))
				{
					queueMessageHandler.printQueuePage(textChannel, queueMessageHandler.getCurrentPageNum());
					copyQueue = audioManager.getScheduler().getQueue();
				}
				
				Instant start = Instant.now();
				
				while (Instant.now().isBefore(start.plusMillis(NOW_PLAYING_TIMER_RATE_MS)))
				{
					/* We wait */
					if (Instant.now().isAfter(start.plusMillis(NOW_PLAYING_TIMER_RATE_MS)))
					{
						break;
					}
				}
				
				semaphore.release();
			}
		};
	}
	
	public void printNowPlaying(TextChannel textChannel)
	{
		printNowPlaying(textChannel, false);
	}
	
	private boolean listChanged(@NonNull List<AudioTrack> trackListOne, @NonNull List<AudioTrack> trackListTwo)
	{
		if (trackListOne.size() != trackListTwo.size())
		{
			return true;
		}
		
		int size = trackListOne.size();
		for (int i = 0; i < size; i++)
		{
			if (trackListOne.get(i) != trackListTwo.get(i))
			{
				return true;
			}
		}
		return false;
	}
	
	public synchronized void printNowPlaying(TextChannel textChannel, boolean forcePrint)
	{
		this.nowPlayingMessage = getNPMsg(textChannel.getHistory().retrievePast(HISTORY_POLL_LIMIT).complete());
		
		MessageCreateData msg = buildNowPlaying();
		
		if (this.nowPlayingMessage == null || forcePrint || this.nowPlayingMessage.getType() == MessageType.UNKNOWN)
		{
			deletePrevious(textChannel);
			
			QueueMessageHandler queueMessageHandler = audioManager.getQueueMessageHandler();
			queueMessageHandler.printQueuePage(textChannel, queueMessageHandler.getCurrentPageNum());
			
			Message message = GuildContext.get(guildID).messageSendHandler().sendAndRetrieveMessage.apply(textChannel, msg);
			
			addReactions(message);
			this.nowPlayingMessage = message;
		}
		else
		{
			GuildContext.get(guildID)
					.messageSendHandler()
					.editMessage
					.invoke(textChannel, this.nowPlayingMessage, MessageEditData.fromCreateData(msg));
		}
	}
	
	private Message getNPMsg(List<Message> messages)
	{
		return messages
				.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> msg.getAuthor().getId().equals(msg.getJDA().getSelfUser().getId()))
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
				.filter(msg -> msg.getContentDisplay().contains(NOW_PLAYING_MSG_NAME) || msg.getContentDisplay().contains(NOTHING_PLAYING_MSG_NAME))
				.findFirst()
				.flatMap(message ->
				{
					this.nowPlayingMessage = message;
					return Optional.of(message);
				})
				.orElse(null);
	}
	
	private MessageCreateData buildNowPlaying()
	{
		AudioPlayer          player         = audioManager.getPlayer();
		AudioTrack           track          = player.getPlayingTrack();
		MessageCreateBuilder messageBuilder = new MessageCreateBuilder();
		EmbedBuilder         embedBuilder   = new EmbedBuilder();
		
		String paused     = player.isPaused() ? MediaControls.PAUSE : MediaControls.PLAY;
		String repeatSong = audioManager.getScheduler().isRepeatSong() ? MediaControls.REPEAT_ONE : "";
		
		if (track == null)
		{
			messageBuilder.addContent(NOTHING_PLAYING_MSG_NAME);
			embedBuilder.setColor(Color.darkGray);
			embedBuilder.setTitle("...");
		}
		else
		{
			messageBuilder.addContent(NOW_PLAYING_MSG_NAME);
			
			AudioTrackInfo info              = track.getInfo();
			long           duration          = info.length / 1000;
			long           position          = track.getPosition() / 1000;
			String         timeTotal         = String.format("%d:%02d:%02d", duration / 3600, (duration % 3600) / 60, (duration % 60));
			String         timeCurrent       = String.format("%d:%02d:%02d", position / 3600, (position % 3600) / 60, (position % 60));
			String         title             = info.title;
			String         volume            = "%s %s".formatted(MediaControls.VOLUME_HIGH, audioManager.getPlayer().getVolume());
			String         playerStatusIcons = "%s %s".formatted(paused, repeatSong);
			String         description       = "";
			
			if (title == null || title.isEmpty())
			{
				String author = info.author;
				if (author != null && !author.isEmpty())
				{
					title = author;
				}
				else
				{
					title = "---";
				}
			}
			
			/* Checks to see if it's a live stream */
			if (track.getDuration() == Long.MAX_VALUE)
			{
				description = ("""
						%s - **Live**
						%s %s %s
						""").formatted(MediaControls.RED_RECORDING_DOT, RADIO, playerStatusIcons, volume);
			}
			else
			{
				description = ("""
						%s
						**%s / %s**
						%s %s""").formatted(progressPercentage((int) position, (int) duration), timeCurrent, timeTotal, playerStatusIcons, volume);
			}
			
			embedBuilder.appendDescription(description);
			embedBuilder.setTitle("**%s**".formatted(title));
			
			if (paused.equals(MediaControls.PAUSE))
			{
				embedBuilder.setColor(Color.RED);
			}
			else
			{
				embedBuilder.setColor(Color.GREEN);
			}
		}
		
		return messageBuilder.addEmbeds(embedBuilder.build()).build();
	}
	
	private void addReactions(Message nowPlayingMsg)
	{
		/* Small wait to allow printer to display song info if needed */
		
		if (nowPlayingMsg == null || nowPlayingMsg.getType() == MessageType.UNKNOWN)
		{
			return;
		}
		
		List<String> reactionsPresent = nowPlayingMsg.getReactions()
				.stream()
				.map(reaction -> reaction.getEmoji().getName())
				.collect(Collectors.toList());
		
		/* Only add a reaction if it's missing. Saves on queues submit to discord API */
		for (String reaction : MediaControls.getNowPlayingReactions())
		{
			if (reactionsPresent.contains(reaction))
			{
				continue;
			}
			
			nowPlayingMsg.addReaction(Emoji.fromFormatted(reaction)).submit();
		}
	}
	
	private String progressPercentage(int position, int duration)
	{
		if (position > duration)
		{
			throw new IllegalArgumentException();
		}
		
		int donePercent = (100 * position) / duration;
		int doneLength  = (PROGRESS_BAR_SIZE * donePercent) / 100;
		
		StringBuilder bar = new StringBuilder();
		for (int i = 0; i < PROGRESS_BAR_SIZE; i++)
		{
			if (i < doneLength)
			{
				bar.append(PROGRESS_BAR_ICON_FILL);
			}
			else
			{
				bar.append(PROGRESS_BAR_ICON_EMPTY);
			}
		}
		return bar.toString();
	}
	
	@Override
	public void onGenericMessageReaction(@NotNull GenericMessageReactionEvent event)
	{
		Member  eventMember = event.getMember();
		Message msg         = event.retrieveMessage().complete();
		
		if (msg == null)
		{
			return;
		}
		
		MessageReaction reactionE = msg.getReaction(event.getEmoji());
		if (eventMember == null || eventMember.getUser().isBot() || event.getReaction().isSelf() || !msg.getAuthor().isBot() || (reactionE != null && reactionE.getCount() <= 1))
		{
			return;
		}
		
		Message currentNPMessage = audioManager.getNowPlayingMessageHandler().getNowPlayingMessage();
		String  reaction         = event.getReaction().getEmoji().getName();
		
		if (currentNPMessage != null && msg.getId().equals(currentNPMessage.getId()))
		{
			nowPlayingInteraction(reaction, event.getUser());
		}
	}
	
	public Message getNowPlayingMessage()
	{
		return this.nowPlayingMessage;
	}
	
	private void nowPlayingInteraction(String reaction, User user)
	{
		Message currentNPMessage = audioManager.getNowPlayingMessageHandler().getNowPlayingMessage();
		switch (reaction)
		{
//			case SHUFFLE -> Shuffle.shuffle(guildID, audioManager);
			case REPEAT_QUEUE -> audioManager.getScheduler().setRepeatQueue(!audioManager.getScheduler().isRepeatQueue());
			case REPEAT_ONE -> audioManager.getScheduler().setRepeatSong(!audioManager.getScheduler().isRepeatSong());
			case PREV_TRACK ->
			{
				audioManager.getScheduler().prevTrack();
				queueMessageHandler.printQueue(currentNPMessage.getChannel().asTextChannel());
			}
			case PLAY_PAUSE -> audioManager.getScheduler().setPaused(!audioManager.getScheduler().isPaused());
			case NEXT_TRACK ->
			{
				audioManager.getScheduler().nextTrack();
				queueMessageHandler.printQueue(currentNPMessage.getChannel().asTextChannel());
			}
			case STOP ->
			{
				audioManager.saveAudioState();
				audioManager.getScheduler().clearQueue();
				audioManager.getPlayer().stopTrack();
			}
		}
		
		currentNPMessage.removeReaction(Emoji.fromFormatted(reaction), Objects.requireNonNull(user)).queue();
		printNowPlaying(currentNPMessage.getChannel().asTextChannel());
	}
}
