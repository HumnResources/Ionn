package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sun.istack.Nullable;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.apache.commons.collections4.map.LinkedMap;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
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
			voiceChannel = textChannel.getGuild().getVoiceChannelById(DBQueryHandler.get(guildID, "media_settings", "voicechannel"));
			if (voiceChannel == null) {
				return false;
			}
		}

		Member bot = textChannel.getGuild().getMember(textChannel.getJDA().getSelfUser());
		DBQueryHandler.set(guildID, "media_settings", "textChannel", textChannel.getId());
		DBQueryHandler.set(guildID, "media_settings", "voiceChannel", voiceChannel.getId());

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

	public void loadURIList(List<String> uriList) {
		for (String uri : uriList) {
			GuildContext.get(guildID)
					.audioManager()
					.getPlayerManager()
					.loadItem(uri, this);
		}
	}

	@Override
	public void trackLoaded(AudioTrack audioTrack) {
		CACHE.putIfAbsent(audioTrack.getInfo().uri, audioTrack);
		if (CACHE.size() > 300) {
			CACHE.remove(0);
		}

		GuildContext.get(guildID)
				.audioManager()
				.getScheduler()
				.queueAudio(audioTrack);
		TextChannel channel = GuildContext.get(guildID)
				.guild()
				.getTextChannelById(DBQueryHandler.get(guildID, "media_settings", "textchannel"));

		if (GuildContext.get(guildID).audioManager().getScheduler().getQueue().isEmpty()) {
			/* No point in displaying added message if it's the next song */
			return;
		}

		String s;
		assert channel != null;
		if (audioTrack.getInfo() != null) {
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
				.getTextChannelById(DBQueryHandler.get(guildID, "media_settings", "textchannel"));

		assert channel != null;
		channel.sendMessage("Darn, no matches found !").queue();
	}

	@Override
	public void loadFailed(FriendlyException e) {
		TextChannel channel = GuildContext.get(guildID)
				.guild()
				.getTextChannelById(DBQueryHandler.get(guildID, "media_settings", "textchannel"));
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
