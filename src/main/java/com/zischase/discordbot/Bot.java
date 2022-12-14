package com.zischase.discordbot;

import com.github.ygimenez.method.Pages;
import com.zischase.discordbot.commands.CommandEventListener;
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
			LOGGER.warn("Shutting down.");

//			Lyrics.shutdown();
			Pages.deactivate();

			/* Iterate over each guild and save their audio state if premium subs */
			for (Guild guild : jda.getGuilds()) {
				GuildContext.get(guild.getId()).audioManager().onShutdown();
			}

			BotCommons.shutdown(jda);
			jda.shutdown();
		}));
	}

}
