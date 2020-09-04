package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.PlayerPrinter;
import net.dv8tion.jda.api.entities.Guild;

public class GuildContext implements IGuildContext
{
	private final        Guild                   guild;
	private final        boolean                 premium;
	private final        AudioManager            audioManager;
	private final        PlayerPrinter           playerPrinter;
	
	
	public GuildContext(Guild guild)
	{
		this.guild = guild;
		this.premium = false;
		this.audioManager = new AudioManager(guild);
		this.playerPrinter = new PlayerPrinter(audioManager);
		
		GuildManager.setGuild(this);
	}
	
	@Override
	public PlayerPrinter playerPrinter()
	{
		return playerPrinter;
	}
	
	@Override
	public Guild guild()
	{
		return guild;
	}
	
	@Override
	public AudioManager audioManager()
	{
		return this.audioManager;
	}
	
	@Override
	public boolean isPremium()
	{
		return premium;
	}
}
