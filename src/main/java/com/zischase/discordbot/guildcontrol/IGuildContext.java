package com.zischase.discordbot.guildcontrol;

import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.audioplayer.PlayerPrinter;
import net.dv8tion.jda.api.entities.Guild;

public interface IGuildContext
{
	PlayerPrinter playerPrinter();
	
	Guild guild();
	
	AudioManager audioManager();
	
	boolean isPremium();
}
