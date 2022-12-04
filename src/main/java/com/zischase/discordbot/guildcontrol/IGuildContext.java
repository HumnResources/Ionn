package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.CommandHandler;
import net.dv8tion.jda.api.entities.Guild;

public interface IGuildContext {

	Guild guild();

	AudioManager audioManager();

	CommandHandler commandHandler();

}
