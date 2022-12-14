package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sun.istack.Nullable;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.DBQuery;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.apache.commons.collections4.map.LinkedMap;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TrackLoader implements AudioLoadResultHandler {

	private static final LinkedMap<String, AudioTrack> CACHE = new LinkedMap<>(50);
	private final        String                        guildID;

	public TrackLoader(String guildID) {
		this.guildID = guildID;
	}

	public void loadYTSearchResults(TextChannel textChannel, @Nullable VoiceChannel voiceChannel, String uri) {
		if (joinChannels(voiceChannel, textChannel)) {
			uri = "ytsearch: " + uri;

			GuildContext.get(guildID)
					.audioManager()
					.getPlayerManager()
					.loadItem(uri, this);
		}
	}

	private boolean joinChannels(@Nullable VoiceChannel voiceChannel, TextChannel textChannel) {
		if (voiceChannel == null) {
			voiceChannel = textChannel.getGuild().getVoiceChannelById(DBQueryHandler.get(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.VOICECHANNEL));
			if (voiceChannel == null) {
				return false;
			}
		}

		Member bot = textChannel.getGuild().getMember(textChannel.getJDA().getSelfUser());
		DBQueryHandler.set(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.TEXTCHANNEL, textChannel.getId());
		DBQueryHandler.set(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.TEXTCHANNEL, voiceChannel.getId());

		if (!voiceChannel.getMembers().contains(bot)) {
			textChannel.getJDA()
					.getDirectAudioController()
					.connect(voiceChannel);
		}

		return true;
	}

	public void load(TextChannel textChannel, @Nullable VoiceChannel voiceChannel, String uri) {
		if (joinChannels(voiceChannel, textChannel)) {
			try {
				/* Checks to see if we have a valid URL */
				new URL(uri);
				if (CACHE.containsKey(uri)) {
					GuildContext.get(guildID)
							.audioManager()
							.getScheduler()
							.queueAudio(CACHE.get(uri).makeClone());
				} else {
					GuildContext.get(guildID)
							.audioManager()
							.getPlayerManager()
							.loadItem(uri, this);
				}
			}
			/* No Match - Search YouTube instead */ catch (MalformedURLException e) {
				GuildContext.get(guildID)
						.audioManager()
						.getPlayerManager()
						.loadItem("ytsearch: " + uri, new FunctionalResultHandler(this::trackLoaded, (playlist) -> trackLoaded(playlist.getTracks().get(0)), this::noMatches, this::loadFailed));
			}
		}
	}

	public void loadURIListSequentially(List<String> uriList) {
		CompletableFuture.runAsync(() -> {
			final String[] lastURI = new String[1];

			for (String uri : uriList) {
				GuildContext.get(guildID)
						.audioManager()
						.getPlayerManager()
						.loadItem(uri, new AudioLoadResultHandler() {
							@Override
							public void trackLoaded(AudioTrack audioTrack) {
								load(audioTrack);
								lastURI[0] = audioTrack.getInfo().uri;
							}

							@Override
							public void playlistLoaded(AudioPlaylist audioPlaylist) {
//								GuildContext.get(guildID)
//										.audioManager()
//										.getScheduler()
//										.queueList(audioPlaylist);
							}

							@Override
							public void noMatches() {

							}

							@Override
							public void loadFailed(FriendlyException e) {

							}
						});
//				try {
//					Thread.sleep(620);
//				} catch (InterruptedException e) {
//					throw new RuntimeException(e);
//				}

				OffsetDateTime start = OffsetDateTime.now();
				int timeout = 1;
				while ((lastURI[0] == null || Objects.equals(uri, lastURI[0])) && OffsetDateTime.now().isBefore(start.plusSeconds(timeout))) {

				}

//				GuildContext.get(guildID).audioManager().getPlayer()
			}
		});
	}

	@Override
	public void trackLoaded(AudioTrack audioTrack) {
		load(audioTrack);

		if (GuildContext.get(guildID).audioManager().getScheduler().getQueue().isEmpty()) {
			/* No point in displaying added message if it's the next song */
			return;
		}

		TextChannel channel = GuildContext.get(guildID)
				.guild()
				.getTextChannelById(DBQueryHandler.get(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.TEXTCHANNEL));

		songAddedConfirmation(channel, audioTrack);
	}

	private void load(AudioTrack audioTrack) {
		CACHE.putIfAbsent(audioTrack.getInfo().uri, audioTrack);
		if (CACHE.size() > 300) {
			CACHE.remove(0);
		}

		GuildContext.get(guildID)
				.audioManager()
				.getScheduler()
				.queueAudio(audioTrack);

	}

	private void songAddedConfirmation(TextChannel channel, AudioTrack audioTrack) {
		if (audioTrack.getInfo() != null) {
			String s;

			if (audioTrack.getInfo().title != null) {
				s = "Added: `%s` to queue.".formatted(audioTrack.getInfo().title);
			} else {
				s = "Added: `%s` to queue.".formatted(audioTrack.getIdentifier());
			}

			channel.sendMessage(s).queue(success -> success.delete().queueAfter(4, TimeUnit.SECONDS));
		}
	}

	@Override
	public void playlistLoaded(AudioPlaylist audioPlaylist) {
		GuildContext.get(guildID)
				.audioManager()
				.getScheduler()
				.queueList(audioPlaylist);
	}

	@Override
	public void noMatches() {
		TextChannel channel = GuildContext.get(guildID)
				.guild()
				.getTextChannelById(DBQueryHandler.get(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.TEXTCHANNEL));

		assert channel != null;
		channel.sendMessage("Darn, no matches found !").queue();
	}

	@Override
	public void loadFailed(FriendlyException e) {
		TextChannel channel = GuildContext.get(guildID)
				.guild()
				.getTextChannelById(DBQueryHandler.get(guildID, DBQuery.MEDIA_SETTINGS, DBQuery.TEXTCHANNEL));
		AudioTrack lastSong = GuildContext.get(guildID)
				.audioManager()
				.getScheduler()
				.getLastTrack();

		assert channel != null;
		channel.sendMessage("Loading failed for `" + lastSong.getInfo().title + "`, sorry.").queue();

		if (CACHE.containsValue(lastSong)) CACHE.remove(lastSong.getInfo().uri);
	}

	public void load(VoiceChannel voiceChannel, TextChannel textChannel, AudioTrack audioTrack) {
		if (joinChannels(voiceChannel, textChannel)) {
			this.trackLoaded(audioTrack);
		}
	}
}
