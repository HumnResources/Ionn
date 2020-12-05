package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.apache.commons.collections4.map.LinkedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrackLoader implements AudioLoadResultHandler
{
	private static final Logger                        LOGGER = LoggerFactory.getLogger(TrackLoader.class);
	private static final LinkedMap<String, AudioTrack> CACHE  = new LinkedMap<>(250);
	private              Member                        member;
	private              TextChannel                   textChannel;
	
	public TrackLoader()
	{
		this.member = null;
		this.textChannel = null;
	}
	
	public void load(TextChannel channel, Member member, String uri)
	{
		this.textChannel = channel;
		
		if (member != null)
		{
			this.member = member;
		}
		
		if (connectVoice())
		{
			if (CACHE.containsKey(uri))
			{
				GuildManager.getContext(textChannel.getGuild())
							.audioManager()
							.getScheduler()
							.queueAudio(CACHE.get(uri)
									.makeClone(), textChannel);
			}
			else
			{
				GuildManager.getContext(textChannel.getGuild())
							.audioManager()
							.getPlayerManager()
							.loadItem(uri, this);
			}
		}
		else
		{
			textChannel.sendMessage("You must be in a voice channel to listen to music silly.")
					   .queue();
		}
	}
	
	private boolean connectVoice()
	{
		for (VoiceChannel channel : this.member.getGuild()
											   .getVoiceChannels())
		{
			if (channel.getMembers()
					   .contains(this.member))
			{
				this.member.getJDA()
						   .getDirectAudioController()
						   .connect(channel);
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void trackLoaded(AudioTrack audioTrack)
	{
		CACHE.putIfAbsent(audioTrack.getInfo().uri, audioTrack);
		
		if (CACHE.size() >= 100)
		{
			CACHE.remove(0);
		}
		
		GuildManager.getContext(textChannel.getGuild())
					.audioManager()
					.getScheduler()
					.queueAudio(audioTrack, textChannel);
	}
	
	@Override
	public void playlistLoaded(AudioPlaylist audioPlaylist)
	{
		GuildManager.getContext(textChannel.getGuild())
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
		e.getSuppressed();
	}
}
