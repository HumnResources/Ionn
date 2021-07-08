package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.event.*;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.audiocommands.Shuffle;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zischase.discordbot.audioplayer.MediaControls.*;

public class TrackWatcherEventListener extends ListenerAdapter implements AudioEventListener {

	private static final int          NOW_PLAYING_TIMER_RATE_MS = 4800;
	private static final int          MAX_REACTIONS             = 6;
	private final        GuildContext ctx;

	private final Semaphore                   semaphore = new Semaphore(1);
	private final ScheduledThreadPoolExecutor exec      = new ScheduledThreadPoolExecutor(1);

	public TrackWatcherEventListener(GuildContext ctx) {
		this.ctx = ctx;
		this.ctx.guild().getJDA().addEventListener(this);
		this.ctx.audioManager().getPlayer().addListener(this);
	}

	@Override
	public void onGenericGuildMessageReaction(@NotNull GenericGuildMessageReactionEvent event) {
		Message eventMessage = event.retrieveMessage().complete();

		if (!eventMessage.getAuthor().isBot() || event.retrieveUser().complete().isBot() ||
				event.getReaction().isSelf() || !ctx.getID().equalsIgnoreCase(event.getGuild().getId())) {
			return;
		}
		Message currentNPMessage = ctx.playerPrinter().getCurrentNowPlayingMsg(event.getChannel());
		if (currentNPMessage != null && eventMessage.getId().equals(currentNPMessage.getId())) {
			AudioManager manager = ctx.audioManager();

			switch (event.getReaction().getReactionEmote().getName()) {
				/* Shuffle */
				case SHUFFLE -> {
					((Shuffle) Objects.requireNonNull(ctx.commandHandler().getCommand("Shuffle"))).shuffle(ctx.getID(), manager);
					ctx.playerPrinter().printQueue(manager, eventMessage.getTextChannel());
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
	}

	@Override
	public void onEvent(AudioEvent audioEvent) {
		/* Check for available channel to display Now PLaying prompt */
		String        dbQuery       = DBQueryHandler.get(ctx.getID(), "media_settings", "textChannel");
		TextChannel   activeChannel = ctx.guild().getTextChannelById(dbQuery);
		PlayerPrinter printer       = ctx.playerPrinter();
		AudioManager  audioManager  = ctx.audioManager();

		if (activeChannel == null || audioEvent.player != ctx.audioManager().getPlayer()) {
			return;
		}

		/* Ensure we have somewhere to send the message, check for errors */
		if (audioEvent instanceof TrackStuckEvent) {

			activeChannel.sendMessage("Audio track stuck! Ending track and continuing").queue();
			audioManager.getScheduler().nextTrack();

			/* Clear the current daemon, we got a new event to handle */
			exec.purge();
		} else if (audioEvent instanceof TrackExceptionEvent) {

			printer.getCurrentNowPlayingMsg(activeChannel).clearReactions().complete();
			activeChannel.sendMessage("Error loading the audio.").queue();
			((TrackExceptionEvent) audioEvent).exception.printStackTrace();

			exec.purge();
		} else if (audioEvent.player.getPlayingTrack() == null) {
			Message npMessage = printer.getCurrentNowPlayingMsg(activeChannel);
			if (npMessage != null ) {
				npMessage.clearReactions().complete();
			}
			printer.deletePrevious(activeChannel);
			printer.printNowPlaying(audioManager, activeChannel);
			activeChannel.getJDA().getDirectAudioController().disconnect(activeChannel.getGuild());

			exec.purge();
		} else if (audioEvent instanceof TrackStartEvent) {
			exec.purge();

			boolean inChannel = ctx.guild().getSelfMember().getVoiceState() != null && Objects.requireNonNull(ctx.guild().getSelfMember().getVoiceState()).inVoiceChannel();

			if (!inChannel) {
				ctx.guild()
						.getJDA()
						.getDirectAudioController()
						.connect(Objects.requireNonNull(ctx.guild().getVoiceChannelById(DBQueryHandler.get(ctx.getID(), "voicechannel"))));
			}

			try {
				semaphore.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			if (!audioManager.getScheduler().getQueue().isEmpty()) {
				printer.printQueue(audioManager, activeChannel);
				printer.printNowPlaying(audioManager, activeChannel, true);
			} else {
				printer.deletePrevious(activeChannel);
			}
			semaphore.release();

			/* Set up a timer to continually update the running time of the song */
			Runnable runnable = () -> {
				AudioTrack track = audioEvent.player.getPlayingTrack();
				if (track != null) {
					if (track.getPosition() < track.getDuration()) {
						try {
							semaphore.acquire();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						printer.printNowPlaying(audioManager, activeChannel);
						addReactions(printer, activeChannel);
						semaphore.release();
					}
				}
			};
			exec.scheduleAtFixedRate(runnable, NOW_PLAYING_TIMER_RATE_MS, NOW_PLAYING_TIMER_RATE_MS, TimeUnit.MILLISECONDS);
		}
	}

	@Override
	public void onShutdown(@NotNull ShutdownEvent event) {
		this.exec.purge();
		this.exec.shutdown();
		ctx.audioManager().getPlayer().removeListener(this);
		event.getJDA().removeEventListener(this);
	}

	private void addReactions(PlayerPrinter printer, TextChannel channel) {
		CompletableFuture.runAsync(() -> {
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
		});
	}

}
