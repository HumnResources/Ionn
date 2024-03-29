package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.DBQuery;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.CommandHandler;
import com.zischase.discordbot.MessageSendHandler;
import net.dv8tion.jda.api.entities.Guild;

import java.util.HashMap;
import java.util.Map;

public class GuildContext implements IGuildContext
{
	
	private static final Map<Long, GuildContext> GUILDS = new HashMap<>();
	private final        Guild                   guild;
	private final        AudioManager            audioManager;
	private final        CommandHandler          commandHandler;
	
	public MessageSendHandler messageSendHandler()
	{
		return messageSendHandler;
	}
	
	private final MessageSendHandler messageSendHandler;
	
	public GuildContext(Guild guild)
	{
		this.guild          = guild;
		this.messageSendHandler = new MessageSendHandler();
		this.audioManager   = new AudioManager(guild);
		this.commandHandler = new CommandHandler();
		
		/* Update global GuildContext references */
		setGuild(this);
		
		/* Reconnects and starts audio if restart or crash occurred */
		audioManager.loadAudioState(true);
	}
	
	private static void setGuild(GuildContext guildContext)
	{
		Guild guild = guildContext.guild();
		GUILDS.putIfAbsent(guild.getIdLong(), guildContext);
		int v = Integer.parseInt(DBQueryHandler.get(guild.getId(), DBQuery.VOLUME));
		guildContext.audioManager()
				.getScheduler()
				.setVolume(v);
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
	public CommandHandler commandHandler()
	{
		return this.commandHandler;
	}
	
	public static GuildContext get(String guildID)
	{
		return GUILDS.get(Long.parseLong(guildID));
	}
	
	public final boolean isPremium()
	{
		return DBQueryHandler.getPremiumStatus(guild.getId());
	}
	
}
