package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.AudioPlayerState;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.CommandHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class GuildContext implements IGuildContext {

	private static final Map<Long, GuildContext> GUILDS = new HashMap<>();
	private final        Guild                   guild;
	private final        AudioManager            audioManager;
	private final        CommandHandler          commandHandler;

	public GuildContext(Guild guild) {
		this.guild          = guild;
		this.audioManager   = new AudioManager(guild);
		this.commandHandler = new CommandHandler();

		/* Update global GuildContext references */
		setGuild(this);

		String activeSongURL = DBQueryHandler.get(guild.getId(), "activesong");
		boolean hasActiveMedia = !activeSongURL.isEmpty();

		if (hasActiveMedia) {

			CompletableFuture.runAsync(() -> {
				CommandContext ctx = new CommandContext(guild, guild.getSelfMember(), List.of("join"));
				GuildContext.get(guild.getId()).commandHandler().invoke(ctx);

				VoiceChannel voiceChannel = guild.getChannelById(VoiceChannel.class, DBQueryHandler.get(guild.getId(), "voicechannel"));
				TextChannel textChannel = guild.getChannelById(TextChannel.class, DBQueryHandler.get(guild.getId(), "textchannel"));
				List<String> queueURLs = List.of(DBQueryHandler.get(guild.getId(), "currentqueue").split(","));
				long currentSongPosition = Long.parseLong(DBQueryHandler.get(guild.getId(), "activesongduration"));

				audioManager.getTrackLoader().load(textChannel, voiceChannel, activeSongURL);

				OffsetDateTime now     = OffsetDateTime.now();
				int            timeout = 3;
				while (true) {
					if (audioManager.getPlayerState() == AudioPlayerState.LOADING_TRACK) {
						continue;
					}

					if (currentSongPosition >=0 && audioManager.getPlayer().getPlayingTrack().isSeekable()) {
						audioManager.getPlayer().getPlayingTrack().setPosition(currentSongPosition);
						break;
					}
					else if (now.isAfter(now.plusSeconds(timeout))) {
						break;
					}
				}

				/* Checks that the list both has items in it and the first item if only one listed is not empty */
				boolean validQURI = !queueURLs.isEmpty() && !(queueURLs.size() == 1 && queueURLs.get(0).equals(""));
				if (validQURI){
					audioManager.getTrackLoader().loadURIList(queueURLs);
				}

			});
		}
	}

	private static void setGuild(GuildContext guildContext) {
		Guild guild = guildContext.guild();
		GUILDS.putIfAbsent(guild.getIdLong(), guildContext);
		int v = Integer.parseInt(DBQueryHandler.get(guild.getId(), "volume"));
		guildContext.audioManager()
				.getPlayer()
				.setVolume(v);
	}

	@Override
	public Guild guild() {
		return guild;
	}

	@Override
	public AudioManager audioManager() {
		return this.audioManager;
	}

	@Override
	public CommandHandler commandHandler() {
		return this.commandHandler;
	}

	public static GuildContext get(String guildID) {
		return GUILDS.get(Long.parseLong(guildID));
	}

	public final boolean isPremium() {
		return DBQueryHandler.getPremiumStatus(guild.getId());
	}

}
