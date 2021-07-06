package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.DBConnectionHandler;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.PlayerPrinter;
import com.zischase.discordbot.commands.CommandHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import org.jdbi.v3.core.Jdbi;

import java.util.HashMap;
import java.util.Map;

public class GuildContext implements IGuildContext {

	private static final Map<Long, GuildContext> GUILDS = new HashMap<>();
	private final        Guild                   guild;
	private final        AudioManager            audioManager;
	private final        PlayerPrinter           playerPrinter;
	private final        CommandHandler          commandHandler;

	public GuildContext(Guild guild) {
		this.guild        = guild;
		this.audioManager = new AudioManager(guild);

		String textChannelID = DBQueryHandler.get(guild.getId(), "media_settings", "textchannel");

		if (textChannelID != null && !textChannelID.isEmpty()) {
			this.playerPrinter = new PlayerPrinter(this.audioManager, guild.getTextChannelById(textChannelID));
		} else if (guild.getDefaultChannel() != null) {
			this.playerPrinter = new PlayerPrinter(this.audioManager, guild.getDefaultChannel());
		} else {
			TextChannel channel = guild.getTextChannels()
					.stream()
					.filter(TextChannel::canTalk)
					.findFirst()
					.orElseThrow();
			this.playerPrinter = new PlayerPrinter(this.audioManager, channel);
		}

		this.commandHandler = new CommandHandler();
		setGuild(this);
	}

	private static void setGuild(GuildContext guildContext) {
		Guild guild = guildContext.guild();
		GUILDS.putIfAbsent(guild.getIdLong(), guildContext);

		boolean initSettings = DBQueryHandler.get(guild.getId(), "prefix").isEmpty();

		if (initSettings) {
			Jdbi.create(DBConnectionHandler::getConnection)
					.useHandle(handle ->
							handle.createUpdate("""
									INSERT INTO guilds(id, name) VALUES (:guildID, :name);
									INSERT INTO media_settings(guild_id) VALUES (:guildID);
									INSERT INTO guild_settings(guild_id) VALUES (:guildID);
									""")
									.bind("name", guild.getName())
									.bind("guildID", guild.getId())
									.execute());
		}
		int v = Integer.parseInt(DBQueryHandler.get(guild.getId(), "volume"));
		guildContext.audioManager()
				.getPlayer()
				.setVolume(v);
	}

	public static GuildContext get(String guildID) {
		return GUILDS.get(Long.parseLong(guildID));
	}

	@Override
	public PlayerPrinter playerPrinter() {
		return playerPrinter;
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

	public final boolean isPremium() {
		return DBQueryHandler.getPremiumStatus(guild.getId());
	}

}
