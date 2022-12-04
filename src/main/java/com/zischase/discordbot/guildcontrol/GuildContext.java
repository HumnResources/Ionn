package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.CommandHandler;
import net.dv8tion.jda.api.entities.Guild;

import java.util.HashMap;
import java.util.Map;

public class GuildContext implements IGuildContext {

	private static final Map<Long, GuildContext> GUILDS = new HashMap<>();
	private final        Guild                   guild;
	private final        AudioManager            audioManager;
	private final        CommandHandler          commandHandler;

	public GuildContext(Guild guild) {
		this.guild          = guild;
		this.audioManager   = new AudioManager(guild);
		this.commandHandler = new CommandHandler();

		/* Update global GuildContext references */
		setGuild(this);
	}

	private static void setGuild(GuildContext guildContext) {
		Guild guild = guildContext.guild();
		GUILDS.putIfAbsent(guild.getIdLong(), guildContext);
		int v = Integer.parseInt(DBQueryHandler.get(guild.getId(), "volume"));
		guildContext.audioManager()
				.getPlayer()
				.setVolume(v);
	}

	@Override
	public Guild guild() {
		return guild;
	}

	@Override
	public AudioManager audioManager() {
		return this.audioManager;
	}

	@Override
	public CommandHandler commandHandler() {
		return this.commandHandler;
	}

	public static GuildContext get(String guildID) {
		return GUILDS.get(Long.parseLong(guildID));
	}

	public final boolean isPremium() {
		return DBQueryHandler.getPremiumStatus(guild.getId());
	}

}
