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
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.apache.commons.collections4.map.LinkedMap;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;

public class TrackLoader implements AudioLoadResultHandler {

	private static final LinkedMap<String, AudioTrack> CACHE = new LinkedMap<>(50);
	private final        String                        guildID;

	public TrackLoader(String guildID) {
		this.guildID = guildID;
	}

	public void loadYTSearchResults(TextChannel textChannel, @Nullable VoiceChannel voiceChannel, String uri) {
		if (!joinChannels(voiceChannel, textChannel)) {
			return;
		}

		uri = "ytsearch: " + uri;

		GuildContext.get(guildID)
				.audioManager()
				.getPlayerManager()
				.loadItem(uri, this);
	}

	public void load(TextChannel textChannel, @Nullable VoiceChannel voiceChannel, String uri) {
		if (!joinChannels(voiceChannel, textChannel)) {
			return;
		}
		/* Checks to see if we have a valid URL */
		try {
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
		/* No Match - Search YouTube instead */
		catch (MalformedURLException e) {
			LoggerFactory.getLogger(this.getClass()).info("No url provided, searching youtube instead. - {}", uri);

			GuildContext.get(guildID)
					.audioManager()
					.getPlayerManager()
					.loadItem("ytsearch: " + uri, new FunctionalResultHandler(this::trackLoaded, (playlist) -> trackLoaded(playlist.getTracks().get(0)), this::noMatches, this::loadFailed));
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

		assert channel != null;
		if (audioTrack.getInfo() != null) {
			if (audioTrack.getInfo().title != null) {
				channel.sendMessage("Added: " + audioTrack.getInfo().title).queue();
			}
			else {
				channel.sendMessage("Added: " + audioTrack.getIdentifier()).queue();
			}
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

		assert channel != null;
		channel.sendMessage("Loading failed, sorry.").queue();
	}

	private boolean joinChannels(@Nullable VoiceChannel voiceChannel, TextChannel textChannel) {
		if (voiceChannel == null) {
			voiceChannel = textChannel.getGuild().getVoiceChannelById(DBQueryHandler.get(guildID, "media_settings", "voicechannel"));
		}
		if (voiceChannel == null) {
			return false;
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
}
