package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.event.*;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.audiocommands.Shuffle;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.zischase.discordbot.audioplayer.MediaControls.*;

public class TrackWatcherEventListener extends ListenerAdapter {

//	private static final Logger LOGGER                    = LoggerFactory.getLogger(TrackWatcherEventListener.class);

	private final String        id;
	private final PlayerPrinter printer;
	private final AudioManager  audioManager;


	public TrackWatcherEventListener(JDA jda, PlayerPrinter printer, AudioManager audioManager, String guildID) {
		this.id           = guildID;
		this.printer      = printer;
		this.audioManager = audioManager;
		jda.addEventListener(this);
	}

	@Override
	public void onGenericGuildMessageReaction(@NotNull GenericGuildMessageReactionEvent event) {
		Member eventMember = event.getMember();
		if (eventMember == null || eventMember.getUser().isBot() || event.getReaction().isSelf()) {
			return;
		}
		Message msg = event.retrieveMessage().complete();
		if (!msg.getAuthor().isBot()) {
			return;
		}
		Message currentNPMessage = printer.getNowPlayingMessage();

		if (currentNPMessage != null && msg.getId().equals(currentNPMessage.getId())) {
			String reaction = event.getReaction().getReactionEmote().getName();

			CompletableFuture.runAsync(() -> {
				switch (reaction) {
					case SHUFFLE -> {
						Shuffle.shuffle(id, audioManager);
						if (audioManager.getScheduler().getQueue().size() > 0) {
							printer.printQueue(audioManager.getScheduler().getQueue(), currentNPMessage.getTextChannel());
						}
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
			}).thenAccept((v) -> printer.printNowPlaying(audioManager, event.getChannel()));
		}
	}

	@Override
	public void onShutdown(@NotNull ShutdownEvent event) {
		event.getJDA().removeEventListener(this);
		super.onShutdown(event);
	}

}
