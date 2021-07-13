package com.zischase.discordbot;

import com.github.ygimenez.method.Pages;
import com.zischase.discordbot.commands.CommandEventListener;
import com.zischase.discordbot.guildcontrol.GuildContext;
import me.duncte123.botcommons.BotCommons;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;

public class Bot {

	private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);

	static {
		DBConnectionHandler.getConnection();
	}

	public static void main(String[] args) {
		JDA jda = null;
		try {
			jda = JDABuilder.createDefault(Config.get("TOKEN")).build();
		} catch (LoginException e) {
			e.printStackTrace();
		}
		if (jda == null) {
			throw new RuntimeException();
		}

		setShutdownHook(jda);
		jda.getPresence().setActivity(Activity.listening(" starting..."));

		try {
			jda.awaitReady();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		jda.getPresence().setActivity(Activity.listening(" commands."));
		jda.getGuilds().forEach(DBQueryHandler::addGuild);
		jda.getGuilds().forEach(GuildContext::new);
		jda.addEventListener(new CommandEventListener());
	}

	private static void setShutdownHook(JDA jda) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			LOGGER.info("Shutting down.");

			jda.getGuilds().forEach(g -> GuildContext.get(g.getId()).playerPrinter().shutdown());
			Pages.deactivate();
			BotCommons.shutdown(jda);
			jda.shutdown();

			Runtime.getRuntime().halt(0);
		}));
	}

}
