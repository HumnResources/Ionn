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
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class TrackLoader implements AudioLoadResultHandler
{
	private       Member              member;
	private       TextChannel         textChannel;
	
	private final LinkedMap<String, AudioTrack> cache = new LinkedMap<>(250);
	
	public TrackLoader()
	{
		this.member = null;
		this.textChannel = null;
	}
	
	public void load(TextChannel channel, Member member, String url)
	{
		this.textChannel = channel;
		
		if (member != null)
		{
			this.member = member;
		}
		
		if (connectVoice())
		{
			if (cache.containsKey(url))
			{
				GuildManager.getContext(textChannel.getGuild())
							.audioManager()
							.getScheduler()
							.queueAudio(cache.get(url).makeClone(), textChannel);
			}
			else
			{
				GuildManager.getContext(textChannel.getGuild())
							.audioManager()
							.getPlayerManager()
							.loadItem(url, this);
			}
			textChannel.sendMessage("You must be in a voice channel to listen to music silly.")
					   .queue();
		}
		
	}
	
//	public void load(TextChannel channel, Member member, AudioTrack track)
//	{
//		this.textChannel = channel;
//		if (member != null)
//		{
//			this.member = member;
//		}
//
//		if (connectVoice())
//		{
//			if (cache.containsValue(track))
//			{
//				GuildManager.getContext(textChannel.getGuild())
//							.audioManager()
//							.getScheduler()
//							.queueAudio(track.makeClone(), textChannel);
//			}
//
//			GuildManager.getContext(channel.getGuild())
//						.audioManager()
//						.getScheduler()
//						.queueAudio(track, channel);
//		}
//		else
//		{
//			textChannel.sendMessage("You must be in a voice channel to listen to music silly.")
//					   .queue();
//		}
//	}
	
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
		if (!audioTrack.getInfo().title.isEmpty())
		{
			cache.putIfAbsent(audioTrack.getInfo().title, audioTrack);
		}
		else
		{
			cache.putIfAbsent(audioTrack.getIdentifier(), audioTrack);
		}
		if (cache.size() >= 250)
		{
			cache.remove(0);
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
					.queueList((ArrayList<AudioTrack>) audioPlaylist.getTracks(), textChannel);
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
		
		LoggerFactory.getLogger(TrackLoader.class)
					 .error(e.getCause()
							 .getLocalizedMessage());

	}
}
