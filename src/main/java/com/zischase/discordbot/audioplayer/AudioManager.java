package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import net.dv8tion.jda.api.entities.Guild;


public class AudioManager
{
	private final static AudioPlayerManager PLAYER_MANAGER = new DefaultAudioPlayerManager();
	private final        AudioPlayer        player;
	private final        TrackScheduler     scheduler;
	
	static
	{
		PLAYER_MANAGER.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
		PLAYER_MANAGER.getConfiguration().setOpusEncodingQuality(128);
		PLAYER_MANAGER.registerSourceManager(new YoutubeAudioSourceManager());
		PLAYER_MANAGER.registerSourceManager(new HttpAudioSourceManager());
		PLAYER_MANAGER.registerSourceManager(new LocalAudioSourceManager());
		AudioSourceManagers.registerRemoteSources(PLAYER_MANAGER);
		AudioSourceManagers.registerLocalSource(PLAYER_MANAGER);
		
		
	}
	
	public AudioManager(Guild guild)
	{
		this.player = PLAYER_MANAGER.createPlayer();
		this.scheduler = new TrackScheduler(this);
		this.player.addListener(scheduler);
		
		guild.getAudioManager().setSendingHandler(this.getSendHandler());
	}
	
	public AudioPlayerManager getPlayerManager()
	{
		return PLAYER_MANAGER;
	}
	
	public AudioPlayer getPlayer()
	{
		return player;
	}
	
	public TrackScheduler getScheduler()
	{
		return scheduler;
	}
	
	public AudioPlayerSendHandler getSendHandler()
	{
		return new AudioPlayerSendHandler(player);
	}
}