package com.zischase.discordbot;

import com.github.ygimenez.method.Pages;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.CommandEventListener;
import com.zischase.discordbot.commands.audiocommands.Lyrics;
import com.zischase.discordbot.guildcontrol.GuildContext;
import me.duncte123.botcommons.BotCommons;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Bot {

	private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);

	private static final List<Guild> PREMIUM_GUILDS = new ArrayList<>();

	public static List<Guild> getPremiumGuilds() {
		return List.copyOf(PREMIUM_GUILDS);
	}

	static {
		DBConnectionHandler.getConnection();
	}

	public static void main(String[] args) {
		JDA jda = JDABuilder.createDefault(Config.get("TOKEN")).build();

		setShutdownHook(jda);
		jda.getPresence().setActivity(Activity.listening(" starting..."));

		try {
			jda.awaitReady();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		jda.getGuilds().forEach((guild) -> {
			DBQueryHandler.addGuild(guild);
			GuildContext ctx = new GuildContext(guild);
			if (ctx.isPremium()) PREMIUM_GUILDS.add(ctx.guild());
		});

		jda.addEventListener(new CommandEventListener());
		jda.getPresence().setActivity(Activity.listening(" commands."));
	}

	private static void setShutdownHook(JDA jda) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			LOGGER.info("Shutting down.");

			Lyrics.shutdown();
			Pages.deactivate();
			BotCommons.shutdown(jda);

			/* Iterate over each guild and save their audio state if premium subs */
			for (Guild guild : getPremiumGuilds()) {
				String       id = guild.getId();
				AudioManager audioManager = GuildContext.get(id).audioManager();

				List<String> queueURLs = audioManager.getScheduler()
						.getQueue()
						.stream()
						.map((track) -> track.getInfo().uri)
						.collect(Collectors.toList());

				AudioTrack playingTrack = audioManager.getPlayer().getPlayingTrack();

				if (playingTrack != null) {
					DBQueryHandler.set(id, "media_settings", "activesong", playingTrack.getInfo().uri);
					DBQueryHandler.set(id, "media_settings", "activesongduration", playingTrack.getPosition());
				}
				else {
					DBQueryHandler.set(id, "media_settings", "activesong", "");
					DBQueryHandler.set(id, "media_settings", "activesongduration", 0);

				}

				/* Check not required as empty queue adds nothing */
				DBQueryHandler.set(id, "media_settings", "currentqueue", String.join(",", queueURLs));
			}

			jda.shutdown();

			Runtime.getRuntime().halt(0);
		}));
	}

}
