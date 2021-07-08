package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.event.AudioEvent;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.player.event.TrackExceptionEvent;
import com.sedmelluq.discord.lavaplayer.player.event.TrackStuckEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.audiocommands.Shuffle;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.zischase.discordbot.audioplayer.MediaControls.*;

public class TrackWatcherEventListener extends ListenerAdapter implements AudioEventListener {

	private static final int          NOW_PLAYING_TIMER_RATE_MS = 5000;
	private static final int          MAX_REACTIONS             = 6;
	private final        Timer        nowPlayingTimer           = new Timer("nowPlayingTimer");
	private final        GuildContext ctx;

	public TrackWatcherEventListener(GuildContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void onGenericGuildMessageReaction(@NotNull GenericGuildMessageReactionEvent event) {
		Message eventMessage = event.retrieveMessage().complete();

		if (!eventMessage.getAuthor().isBot() || event.retrieveUser().complete().isBot() ||
				event.getReaction().isSelf() || !ctx.getID().equalsIgnoreCase(event.getGuild().getId())) {
			return;
		}
			CompletableFuture.runAsync(() -> {
			Message currentNPMessage = ctx.playerPrinter().getCurrentNowPlayingMsg(event.getChannel());

			if (eventMessage.getIdLong() == currentNPMessage.getIdLong()) {
				AudioManager manager = ctx.audioManager();

				switch (event.getReaction().getReactionEmote().getName()) {
					/* Shuffle */
					case SHUFFLE -> {
						((Shuffle) Objects.requireNonNull(ctx.commandHandler().getCommand("Shuffle"))).shuffle(ctx.getID(), manager);
						ctx.playerPrinter().printQueue(manager, currentNPMessage.getTextChannel());
					}
					/* repeat */
					case REPEAT -> manager.getScheduler().setRepeat(!manager.getScheduler().isRepeat());
					/* Prev. Track */
					case PREV -> manager.getScheduler().prevTrack();
					/* Play/Pause */
					case PLAY_PAUSE -> manager.getPlayer().setPaused(!manager.getPlayer().isPaused());
					/* Next Track */
					case NEXT -> manager.getScheduler().nextTrack();
					/* Stop */
					case STOP -> {
						manager.getScheduler().clearQueue();
						manager.getPlayer().stopTrack();
					}
				}
			}
		});
	}

	@Override
	public void onEvent(AudioEvent audioEvent) {
		/* Check for available channel to display Now PLaying prompt */
		String        dbQuery       = DBQueryHandler.get(ctx.getID(), "media_settings", "textChannel");
		TextChannel   activeChannel = ctx.guild().getTextChannelById(dbQuery);
		PlayerPrinter printer       = ctx.playerPrinter();
		AudioManager  audioManager  = ctx.audioManager();

		/* Clear the current timer, we got a new event to handle */
		nowPlayingTimer.purge();

		if (activeChannel == null) {
			return;
		}

		Message npMessage = printer.getCurrentNowPlayingMsg(activeChannel);
		if (npMessage != null) {
			npMessage.clearReactions().complete();
		}

		/* Ensure we have somewhere to send the message, check for errors */
		if (audioEvent instanceof TrackStuckEvent) {
			activeChannel.sendMessage("Audio track stuck! Ending track and continuing").queue();
			audioEvent.player.stopTrack();
		} else if (audioEvent instanceof TrackExceptionEvent) {
			printer.getCurrentNowPlayingMsg(activeChannel).clearReactions().complete();
			activeChannel.sendMessage("Error loading the audio.").queue();
			((TrackExceptionEvent) audioEvent).exception.printStackTrace();
		} else if (audioEvent.player.getPlayingTrack() == null) {
			printer.deletePrevious(activeChannel);
			printer.printNowPlaying(audioManager, activeChannel);
			activeChannel.getJDA().getDirectAudioController().disconnect(activeChannel.getGuild());
		} else {
			if (!audioManager.getScheduler().getQueue().isEmpty()) {
				printer.printQueue(audioManager, activeChannel);
			}
			/* Set up a timer to continually update the running time of the song */
			nowPlayingTimer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					AudioTrack track = audioEvent.player.getPlayingTrack();
					if (track != null) {
						if (track.getPosition() < track.getDuration()) {
							printer.printNowPlaying(audioManager, activeChannel);
							addReactions(printer, activeChannel);
						}
					}
				}
				/* Delay first execution to prevent reprinting of Now Playing - Sets timer interval */
			}, Date.from(Instant.now().plusMillis(NOW_PLAYING_TIMER_RATE_MS)), NOW_PLAYING_TIMER_RATE_MS);
		}
	}

	private void addReactions(PlayerPrinter printer, TextChannel channel) {
		/* Small wait to allow printer to display song info if needed */
		Message nowPlayingMsg = printer.getCurrentNowPlayingMsg(channel);

		if (nowPlayingMsg == null || nowPlayingMsg.getType() == MessageType.UNKNOWN || nowPlayingMsg.getReactions().size() == MAX_REACTIONS) {
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
