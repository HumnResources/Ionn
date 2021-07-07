package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.event.AudioEvent;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.player.event.TrackExceptionEvent;
import com.sedmelluq.discord.lavaplayer.player.event.TrackStuckEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;

public class TrackWatcherEventListener extends ListenerAdapter implements AudioEventListener {

	private static final int MAX_REACTIONS = 6;
	private final Timer nowPlayingTimer    = new Timer("nowPlayingTimer");
	private final String id;

	public TrackWatcherEventListener(AudioManager audioManager, String guildID) {
		this.id = guildID;
	}

	@Override
	public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event) {
		Message eventMessage = event.retrieveMessage().complete();
		Message currentNPMessage = GuildContext.get(id)
				.playerPrinter()
				.getCurrentNowPlayingMsg(event.getChannel());

		if (!eventMessage.getAuthor().isBot() || event.retrieveUser().complete().isBot() ||
				event.getReaction().isSelf() || !id.equalsIgnoreCase(event.getGuild().getId())) {
			return;
		}
		event.getReaction().getReactionEmote().getName();

		if (eventMessage.getIdLong() == currentNPMessage.getIdLong()) {
			AudioManager manager = GuildContext.get(event.getGuild().getId()).audioManager();

			switch (event.getReaction().getReactionEmote().getName()) {
				// Shuffle
				case "\uD83D\uDD00" -> {
					ArrayList<AudioTrack> currentQueue = manager.getScheduler().getQueue();
					Collections.shuffle(currentQueue);
					manager.getScheduler().clearQueue();
					manager.getScheduler().queueList(currentQueue);
				}
				// repeat
				case "\uD83D\uDD01" -> manager.getScheduler().setRepeat(!manager.getScheduler().isRepeat());
				// Prev. Track
				case "⏮" -> manager.getScheduler().prevTrack();
				// Play/Pause
				case "⏯" -> manager.getPlayer().setPaused(!manager.getPlayer().isPaused());
				// Next Track
				case "⏭" -> manager.getScheduler().nextTrack();
				// Stop All
				case "⏹" -> {
					manager.getScheduler().clearQueue();
					manager.getPlayer().stopTrack();
				}
			}
		}

		event.getReaction().clearReactions().complete();
		addReactions(GuildContext.get(event.getGuild().getId()).playerPrinter(), event.getChannel());
	}


	@Override
	public void onEvent(AudioEvent audioEvent) {
		/* Check for available channel to display Now PLaying prompt */
		String        dbQuery       = DBQueryHandler.get(id, "media_settings", "textChannel");
		TextChannel   activeChannel = GuildContext.get(id).guild().getTextChannelById(dbQuery);
		PlayerPrinter printer       = GuildContext.get(id).playerPrinter();
		AudioManager  audioManager  = GuildContext.get(id).audioManager();
		/* Clear the current timer, we got a new event to handle */
		nowPlayingTimer.purge();

		/* Ensure we have somewhere to send the message, check for errors */
		assert activeChannel != null;
		if (audioEvent instanceof TrackStuckEvent) {
			activeChannel.sendMessage("Audio track stuck! Ending track and continuing").queue();
			audioEvent.player.stopTrack();
		} else if (audioEvent instanceof TrackExceptionEvent) {
			activeChannel.sendMessage("Error loading the audio.").queue();
			((TrackExceptionEvent) audioEvent).exception.printStackTrace();
		} else if (audioEvent.player.getPlayingTrack() == null) {
			printer.deletePrevious(activeChannel);
			printer.printNowPlaying(audioManager, activeChannel);
			activeChannel.getJDA().getDirectAudioController().disconnect(activeChannel.getGuild());
		} else {
			/* Set up a timer to continually update the running time of the song */
			int nowPlayingTimerRateMs = 7500;
			nowPlayingTimer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					if (audioEvent.player.getPlayingTrack() != null) {
						if (audioEvent.player.getPlayingTrack().getPosition() < audioEvent.player.getPlayingTrack().getDuration()) {
							printer.printNowPlaying(audioManager, activeChannel);
							addReactions(printer, activeChannel);
						}
					}
				}
			}, Date.from(Instant.now().plusMillis(nowPlayingTimerRateMs)), nowPlayingTimerRateMs);
		}
	}


	private void addReactions(PlayerPrinter printer, TextChannel channel) {
		/* Small wait to allow printer to display song info if needed */
		Message currentMsg = printer.getCurrentNowPlayingMsg(channel);

		if (currentMsg == null || currentMsg.getType() == MessageType.UNKNOWN || currentMsg.getReactions().size() == MAX_REACTIONS) {
			return;
		}

		/* Add reactions to represent controls */
		currentMsg.addReaction("\uD83D\uDD00").queue(); // :twisted_rightwards_arrows:
		currentMsg.addReaction("\uD83D\uDD01").queue(); // :repeat:
		currentMsg.addReaction("⏮").queue();
		currentMsg.addReaction("⏯").queue();
		currentMsg.addReaction("⏭").queue();
		currentMsg.addReaction("⏹").queue();
	}
}
