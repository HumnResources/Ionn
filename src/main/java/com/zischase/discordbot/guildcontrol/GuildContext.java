package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.DatabaseHandler;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.PlayerPrinter;
import com.zischase.discordbot.commands.CommandHandler;
import net.dv8tion.jda.api.entities.Guild;

public class GuildContext implements IGuildContext
{
	private final Guild          guild;
	private final AudioManager   audioManager;
	private final PlayerPrinter  playerPrinter;
	private final CommandHandler commandHandler;
	
	public GuildContext(Guild guild)
	{
		this.guild          = guild;
		this.audioManager   = new AudioManager(guild);
		this.playerPrinter  = new PlayerPrinter();
		this.commandHandler = new CommandHandler();
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
	public CommandHandler commandManager() {
		return this.commandHandler;
	}

	@Override
	public boolean isPremium()
	{
		return Boolean.parseBoolean(DatabaseHandler.get(this.guild.getId(), "premium"));
	}
}
