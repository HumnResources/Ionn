package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sun.istack.Nullable;
import com.zischase.discordbot.guildcontrol.GuildHandler;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.apache.commons.collections4.map.LinkedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrackLoader implements AudioLoadResultHandler
{
	private static final Logger                        LOGGER = LoggerFactory.getLogger(TrackLoader.class);
	private static final LinkedMap<String, AudioTrack> CACHE  = new LinkedMap<>(50);
	private              TextChannel                   textChannel;
	
	public TrackLoader()
	{
		this.textChannel = null;
	}
	
	public void load(TextChannel channel, @Nullable VoiceChannel voiceChannel, String uri) {
		
		if (channel == null || voiceChannel == null) {
			return;
		}
		
		this.textChannel = channel;
		
		channel.getJDA()
				.getDirectAudioController()
				.connect(voiceChannel);
		
		if (CACHE.containsKey(uri))
		{
			GuildHandler.getContext(textChannel.getGuild())
						.audioManager()
						.getScheduler()
						.queueAudio(CACHE.get(uri)
										 .makeClone(), textChannel);
		}
		else
		{
			GuildHandler.getContext(textChannel.getGuild())
						.audioManager()
						.getPlayerManager()
						.loadItem(uri, this);
			
		}
	}
	
	@Override
	public void trackLoaded(AudioTrack audioTrack)
	{
		textChannel.sendMessage("Added: " + audioTrack.getInfo().title).queue();
		CACHE.putIfAbsent(audioTrack.getInfo().uri, audioTrack);
		
		if (CACHE.size() >= 100)
		{
			CACHE.remove(0);
		}
		
		GuildHandler.getContext(textChannel.getGuild())
                    .audioManager()
                    .getScheduler()
                    .queueAudio(audioTrack, textChannel);
	}
	
	@Override
	public void playlistLoaded(AudioPlaylist audioPlaylist)
	{
		GuildHandler.getContext(textChannel.getGuild())
                    .audioManager()
                    .getScheduler()
                    .queueList(audioPlaylist, textChannel);
	}
	
	@Override
	public void noMatches()
	{
		textChannel.sendMessage("Darn, no matches found !")
				   .queue();
	}
	
	@Override
	public void loadFailed(FriendlyException e)
	{
		textChannel.sendMessage("Loading failed, sorry.").queue();

	}
}
