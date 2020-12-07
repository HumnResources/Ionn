package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.DataBaseManager;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.PlayerPrinter;
import com.zischase.discordbot.commands.CommandManager;
import net.dv8tion.jda.api.entities.Guild;

public class GuildContext implements IGuildContext
{
	private final Guild         guild;
	private final boolean       premium;
	private final AudioManager  audioManager;
	private final PlayerPrinter playerPrinter;
	private final CommandManager commandManager;
	
	public GuildContext(Guild guild)
	{
		this.guild = guild;
		this.premium = Boolean.parseBoolean(DataBaseManager.get(this.guild.getId(), "ispremium"));
		this.audioManager = new AudioManager(guild);
		this.playerPrinter = new PlayerPrinter();
		this.commandManager = new CommandManager();
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
	public CommandManager commandManager() {
		return this.commandManager;
	}

	@Override
	public boolean isPremium()
	{
		return this.premium;
	}
}
