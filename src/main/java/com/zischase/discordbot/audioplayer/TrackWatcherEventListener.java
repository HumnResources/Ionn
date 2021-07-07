package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.event.AudioEvent;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.player.event.TrackExceptionEvent;
import com.sedmelluq.discord.lavaplayer.player.event.TrackStuckEvent;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

public class TrackWatcherEventListener extends ListenerAdapter implements AudioEventListener {

	private final Timer  nowPlayingTimer = new Timer("nowPlayingTimer");
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
			switch (event.getReaction().getReactionEmote().getName()) {
				case "\uD83D\uDD00":
					LoggerFactory.getLogger(this.getClass()).info("Shuffle call - {}", Thread.currentThread());
					break;
			}
		}

		event.getReaction().clearReactions().complete();
		addReactions(event.getChannel());
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
			int nowPlayingTimerRateMs = 1200;
			nowPlayingTimer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					if (audioEvent.player.getPlayingTrack() != null) {
						if (audioEvent.player.getPlayingTrack().getPosition() < audioEvent.player.getPlayingTrack().getDuration()) {
							printer.printNowPlaying(audioManager, activeChannel);
						}
					}
				}
			}, Date.from(Instant.now().plusSeconds(5)), nowPlayingTimerRateMs);

			printer.printNowPlaying(audioManager, activeChannel);
			addReactions(activeChannel);
		}
	}

	@Override
	public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
		Message eventMessage     = event.getMessage();
		Message currentNPMessage = GuildContext.get(id).playerPrinter().getCurrentNowPlayingMsg(event.getChannel());

		/* If we have a new Now Playing message, add reactions */
		if (currentNPMessage != null && eventMessage.getIdLong() == currentNPMessage.getIdLong()) {
			addReactions(event.getChannel());
		}
	}

	@Override
	public void onGuildMessageUpdate(@NotNull GuildMessageUpdateEvent event) {
			Message eventMessage     = event.getMessage();
			Message currentNPMessage = GuildContext.get(id).playerPrinter().getCurrentNowPlayingMsg(event.getChannel());

			/* If we have a new Now Playing message, add reactions */
			if (currentNPMessage != null && eventMessage.getIdLong() == currentNPMessage.getIdLong()) {
				addReactions(event.getChannel());
			}
	}

	private void addReactions(TextChannel activeChannel) {
		/* Small wait to allow printer to display song info if needed */
		OffsetDateTime start   = OffsetDateTime.now();
		PlayerPrinter  printer = GuildContext.get(id).playerPrinter();

		CompletableFuture.runAsync(() -> {
			/* Check if we have a Now PLaying dialogue box */
			Message currentMsg = printer.getCurrentNowPlayingMsg(activeChannel);

			while (currentMsg == null) {
				currentMsg = printer.getCurrentNowPlayingMsg(activeChannel);
				/* Set a timeout to avoid infinite loop */
				if (start.isAfter(OffsetDateTime.now().plusSeconds(30))) {
					return;
				}
			}
			/* Add reactions to represent controls */
			currentMsg.addReaction("\uD83D\uDD00").queue(); // :twisted_rightwards_arrows:
			currentMsg.addReaction("\uD83D\uDD01").queue(); // :repeat:
			currentMsg.addReaction("⏮").queue();
			currentMsg.addReaction("⏯").queue();
			currentMsg.addReaction("⏭").queue();
		});
	}
}
