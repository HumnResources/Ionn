package com.zischase.discordbot.audioplayer;

import com.zischase.discordbot.commands.audiocommands.Shuffle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.zischase.discordbot.audioplayer.MediaControls.*;

public class TrackWatcherEventListener extends ListenerAdapter {

	private static final long    REACTION_TIMEOUT_SEC = 3;

	private final String         id;
	private final PlayerPrinter  printer;
	private final AudioManager   audioManager;
	private       OffsetDateTime lastReactionTime     = OffsetDateTime.now();

	public TrackWatcherEventListener(PlayerPrinter printer, AudioManager audioManager, String guildID) {
		this.id           = guildID;
		this.printer      = printer;
		this.audioManager = audioManager;
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
		String nowPlayingMessageID = printer.getNowPlayingMessageID();

		if (nowPlayingMessageID != null && msg.getId().equals(nowPlayingMessageID)) {
			String reaction = event.getReaction().getReactionEmote().getName();

			if (OffsetDateTime.now().isBefore(lastReactionTime.plusSeconds(REACTION_TIMEOUT_SEC))) {
				event.getChannel().sendMessage("Please wait a few seconds!").queue(message -> message.delete().queueAfter(REACTION_TIMEOUT_SEC, TimeUnit.SECONDS));
				return;
			}

			CompletableFuture.runAsync(() -> {
				switch (reaction) {
					case SHUFFLE -> {
						Shuffle.shuffle(id, audioManager);
						if (audioManager.getScheduler().getQueue().size() > 0) {
							printer.printQueue(event.getChannel());
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
			}).thenAccept((v) -> {
				printer.printNowPlaying(event.getChannel());
				lastReactionTime = OffsetDateTime.now();
			});
		}
	}

	@Override
	public void onShutdown(@NotNull ShutdownEvent event) {
		event.getJDA().removeEventListener(this);
		super.onShutdown(event);
	}

}
