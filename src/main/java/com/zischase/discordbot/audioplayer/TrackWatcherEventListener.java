package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.event.*;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.audiocommands.Shuffle;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.zischase.discordbot.audioplayer.MediaControls.*;

public class TrackWatcherEventListener extends ListenerAdapter implements AudioEventListener {

	private static final Logger LOGGER                    = LoggerFactory.getLogger(TrackWatcherEventListener.class);
	private static final int    NOW_PLAYING_TIMER_RATE_MS = 10000;

	private final String        id;
	private final PlayerPrinter printer;
	private final AudioManager  audioManager;
	private final Guild         guild;
	private       TextChannel   textChannel;
	private       VoiceChannel  voiceChannel;

	private final ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);

	public TrackWatcherEventListener(GuildContext ctx) {
		this.id           = ctx.getID();
		this.guild        = ctx.guild();
		this.printer      = ctx.playerPrinter();
		this.audioManager = ctx.audioManager();
		this.textChannel  = ctx.guild().getTextChannelById(DBQueryHandler.get(ctx.getID(), "media_settings", "textChannel"));
		this.voiceChannel  = ctx.guild().getVoiceChannelById(DBQueryHandler.get(ctx.getID(), "media_settings", "voicechannel"));
		this.exec.setMaximumPoolSize(1);
		ctx.guild().getJDA().addEventListener(this);
		ctx.audioManager().getPlayer().addListener(this);
	}

	@Override
	public void onGenericGuildMessageReaction(@NotNull GenericGuildMessageReactionEvent event) {
		event.retrieveMessage().queue(msg -> {
			if (!msg.getAuthor().isBot() || event.retrieveUser().complete().isBot() ||
					event.getReaction().isSelf() || !id.equalsIgnoreCase(event.getGuild().getId())) {
				return;
			}
			Message currentNPMessage = printer.getCurrentNPMessage(textChannel);

			if (currentNPMessage != null && msg.getId().equals(currentNPMessage.getId())) {
				String reaction = event.getReaction().getReactionEmote().getName();

				switch (reaction) {
					case SHUFFLE -> {
						((Shuffle) Objects.requireNonNull(GuildContext.get(id).commandHandler().getCommand("Shuffle"))).shuffle(id, audioManager);
						printer.printQueue(audioManager.getScheduler().getQueue(), msg.getTextChannel());
					}
					case REPEAT_QUEUE -> audioManager.getScheduler().setRepeatQueue(!audioManager.getScheduler().isRepeatQueue());
					case REPEAT_ONE -> audioManager.getScheduler().setRepeatSong(!audioManager.getScheduler().isRepeatSong());
					case PREV -> audioManager.getScheduler().prevTrack();
					case PLAY_PAUSE -> audioManager.getPlayer().setPaused(!audioManager.getPlayer().isPaused());
					case NEXT -> audioManager.getScheduler().nextTrack();
					case STOP -> {
						audioManager.getScheduler().clearQueue();
						audioManager.getPlayer().stopTrack();
					}
				}
			}
		}, (err) -> LOGGER.warn("Unknown message reference for reaction - {}", err.getCause().toString()));
	}

	@Override
	public void onEvent(AudioEvent audioEvent) {

		/* Check for available channel to display Now PLaying prompt */
		textChannel = guild.getTextChannelById(DBQueryHandler.get(id, "media_settings", "textChannel"));
		voiceChannel  = guild.getVoiceChannelById(DBQueryHandler.get(id, "media_settings", "voicechannel"));

		if (textChannel == null) {
			return;
		}

		/* Ensure we have somewhere to send the message, check for errors */
		if (audioEvent instanceof TrackStuckEvent) {
			textChannel.sendMessage("Audio track stuck! Ending track and continuing").queue();
			audioManager.getScheduler().nextTrack();
		} else if (audioEvent instanceof TrackExceptionEvent) {
			textChannel.sendMessage("Error loading the audio.").queue();
			((TrackExceptionEvent) audioEvent).exception.printStackTrace();
			audioManager.getScheduler().nextTrack();
		} else if (audioEvent instanceof TrackEndEvent && audioManager.getScheduler().getQueue().isEmpty()) {
			Message npMessage = printer.getCurrentNPMessage(textChannel);
			if (npMessage != null) {
				npMessage.clearReactions().complete();
			}

			printer.deletePrevious(textChannel);
			guild.getJDA().getDirectAudioController().disconnect(voiceChannel.getGuild());

		} else if (audioEvent instanceof TrackStartEvent) {
			boolean inChannel = guild.getSelfMember().getVoiceState() != null && Objects.requireNonNull(guild.getSelfMember().getVoiceState()).inVoiceChannel();

			if (!inChannel) {
				guild.getJDA().getDirectAudioController()
						.connect(voiceChannel);
			}
			if (!audioManager.getScheduler().getQueue().isEmpty()) {
				printer.printQueue(audioManager.getScheduler().getQueue(), textChannel);
			}
			/* Set up a timer to continually update the running time of the song */
			Runnable runnable = () -> {
				AudioTrack track = audioEvent.player.getPlayingTrack();
				if (track != null) {
					if (track.getDuration() != Integer.MAX_VALUE && track.getPosition() < track.getDuration()) {
						printer.printNowPlaying(audioManager, textChannel);
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
		audioManager.getPlayer().removeListener(this);
		event.getJDA().removeEventListener(this);
	}

}
