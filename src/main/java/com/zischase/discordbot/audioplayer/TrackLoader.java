package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class TrackLoader implements AudioLoadResultHandler
{
	private Member      member;
	private TextChannel textChannel;
	private String      lastURL = "";
	
	public TrackLoader()
	{
		this.member = null;
		this.textChannel = null;
	}
	
	public void load(TextChannel channel, Member member, String url)
	{
		this.textChannel = channel;
		this.lastURL = url;
		if (member != null)
		{
			this.member = member;
		}
		
		if (connectVoice())
		{
			GuildManager.getContext(textChannel.getGuild())
						.getAudioManager()
						.getPlayerManager()
						.loadItem(url, this);
		}
		else
		{
			textChannel.sendMessage("You must be in a voice channel to listen to music silly.")
					   .queue();
		}
	}
	
	public void load(TextChannel channel, Member member, AudioTrack track)
	{
		this.textChannel = channel;
		this.lastURL = track.getIdentifier();
		if (member != null)
		{
			this.member = member;
		}
		
		if (connectVoice())
		{
			GuildManager.getContext(channel.getGuild())
						.getAudioManager()
						.getScheduler()
						.queueAudio(track, channel);
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
		GuildManager.getContext(textChannel.getGuild())
					.getAudioManager()
					.getScheduler()
					.queueAudio(audioTrack, textChannel);
	}
	
	@Override
	public void playlistLoaded(AudioPlaylist audioPlaylist)
	{
		GuildManager.getContext(textChannel.getGuild())
					.getAudioManager()
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
		//        LoggerFactory.getLogger(TrackLoader.class).warn("Error loading '" + lastURL + "' Retrying...");
		//
		//        if (connectVoice()) {
		//            GuildManager.getContext(textChannel.getGuild())
		//                    .getAudioManager()
		//                    .getPlayerManager()
		//                    .loadItem(lastURL, this);
		//        }
		
	}
}
