package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sun.istack.Nullable;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.CallBack;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.apache.commons.collections4.map.LinkedMap;

public class TrackLoader implements AudioLoadResultHandler {

	private static final LinkedMap<String, AudioTrack> CACHE = new LinkedMap<>(50);
	private final        String                        guildID;

	public TrackLoader(String guildID) {
		this.guildID = guildID;
	}

	public void load(TextChannel channel, @Nullable VoiceChannel voiceChannel, String uri, CallBack callback) {
		load(channel, voiceChannel, uri);
		callback.methodCallBack();
	}

	public void load(TextChannel textChannel, @Nullable VoiceChannel voiceChannel, String uri) {
		if (textChannel == null || voiceChannel == null) {
			return;
		}
		String id  = textChannel.getGuild().getId();
		Member bot = textChannel.getGuild().getMember(textChannel.getJDA().getSelfUser());

		DBQueryHandler.set(id, "media_settings", "textChannel", textChannel.getId());
		DBQueryHandler.set(id, "media_settings", "voiceChannel", voiceChannel.getId());


		if (!voiceChannel.getMembers().contains(bot)) {
			textChannel.getJDA()
					.getDirectAudioController()
					.connect(voiceChannel);
		}
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

	@Override
	public void trackLoaded(AudioTrack audioTrack) {
		CACHE.putIfAbsent(audioTrack.getInfo().uri, audioTrack);

		if (CACHE.size() >= 300) {
			CACHE.remove(0);
		}

		GuildContext.get(guildID)
				.audioManager()
				.getScheduler()
				.queueAudio(audioTrack);
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

}
