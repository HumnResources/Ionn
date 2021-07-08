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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zischase.discordbot.audioplayer.MediaControls.*;

public class TrackWatcherEventListener extends ListenerAdapter implements AudioEventListener {

	private static final int          NOW_PLAYING_TIMER_RATE_MS = 5000;
	private final        GuildContext ctx;

	private final Semaphore                   semaphore = new Semaphore(1);
	private final ScheduledThreadPoolExecutor exec      = new ScheduledThreadPoolExecutor(1);

	public TrackWatcherEventListener(GuildContext ctx) {
		this.ctx = ctx;
		this.ctx.guild().getJDA().addEventListener(this);
		this.ctx.audioManager().getPlayer().addListener(this);
		this.exec.setMaximumPoolSize(1);
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

				String reaction = event.getReaction().getReactionEmote().getName();

				switch (reaction) {
					case SHUFFLE -> {
						((Shuffle) Objects.requireNonNull(ctx.commandHandler().getCommand("Shuffle"))).shuffle(ctx.getID(), manager);
						ctx.playerPrinter().printQueue(manager, eventMessage.getTextChannel());
					}
					case REPEAT_QUEUE -> manager.getScheduler().setRepeatQueue(!manager.getScheduler().isRepeatQueue());
					case REPEAT_ONE -> manager.getScheduler().setRepeatSong(!manager.getScheduler().isRepeatSong());
					case PREV -> manager.getScheduler().prevTrack();
					case PLAY_PAUSE -> manager.getPlayer().setPaused(!manager.getPlayer().isPaused());
					case NEXT -> manager.getScheduler().nextTrack();
					case STOP -> {
						manager.getScheduler().clearQueue();
						manager.getPlayer().stopTrack();
					}
				}
			}
	}

	@Override
	public void onEvent(AudioEvent audioEvent) {
		try {
			semaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

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

		} else if (audioEvent instanceof TrackExceptionEvent) {

			printer.getCurrentNowPlayingMsg(activeChannel).clearReactions().complete();
			activeChannel.sendMessage("Error loading the audio.").queue();
			((TrackExceptionEvent) audioEvent).exception.printStackTrace();

		} else if (audioEvent instanceof TrackEndEvent && audioManager.getScheduler().getQueue().isEmpty()) {
			Message npMessage = printer.getCurrentNowPlayingMsg(activeChannel);
			if (npMessage != null ) {
				npMessage.clearReactions().complete();
			}
			printer.deletePrevious(activeChannel);
			printer.printNowPlaying(audioManager, activeChannel);
			activeChannel.getJDA().getDirectAudioController().disconnect(activeChannel.getGuild());

		} else if (audioEvent instanceof TrackStartEvent) {
			boolean inChannel = ctx.guild().getSelfMember().getVoiceState() != null && Objects.requireNonNull(ctx.guild().getSelfMember().getVoiceState()).inVoiceChannel();

			if (!inChannel) {
				ctx.guild()
						.getJDA()
						.getDirectAudioController()
						.connect(Objects.requireNonNull(ctx.guild().getVoiceChannelById(DBQueryHandler.get(ctx.getID(), "voicechannel"))));
			}


			if (!audioManager.getScheduler().getQueue().isEmpty()) {
				printer.printQueue(audioManager, activeChannel);
				printer.printNowPlaying(audioManager, activeChannel, true);
			} else {
				printer.deletePrevious(activeChannel);
			}

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

		semaphore.release();
	}

	@Override
	public void onShutdown(@NotNull ShutdownEvent event) {
		this.exec.purge();
		this.exec.shutdown();
		ctx.audioManager().getPlayer().removeListener(this);
		event.getJDA().removeEventListener(this);
	}

	private void addReactions(PlayerPrinter printer, TextChannel channel) {
			/* Small wait to allow printer to display song info if needed */
			Message nowPlayingMsg = printer.getCurrentNowPlayingMsg(channel);

			if (nowPlayingMsg == null || nowPlayingMsg.getType() == MessageType.UNKNOWN || nowPlayingMsg.getReactions().size() == MediaControls.getReactions().size()) {
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
