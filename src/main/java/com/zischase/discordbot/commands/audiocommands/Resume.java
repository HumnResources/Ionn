package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.DBQuery;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.jetbrains.annotations.NotNull;

public class Resume extends Command
{
	public Resume()
	{
		super(false);
	}
	
	@NotNull
	@Override
	public String shortDescription()
	{
		return "Resume the current queue.";
	}
	
	@Override
	public String helpText()
	{
		return "Starts playing your audio tracks if there is still an active queue / song.";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		AudioManager audioManager = GuildContext.get(ctx.getGuild().getId())
				.audioManager();
		
		VoiceChannel voiceChannel = ctx.getGuild().getVoiceChannelById(DBQueryHandler.get(ctx.getGuild().getId(), DBQuery.VOICECHANNEL));
		
		if (voiceChannel == null)
		{
			return;
		}
		
		if (!voiceChannel.getMembers().contains(ctx.getSelfMember()))
		{
			voiceChannel.getJDA().getDirectAudioController().connect(voiceChannel);
		}
		
		audioManager.loadAudioState(true);
		
		if (audioManager.getPlayer().isPaused())
		{
			audioManager.getPlayer().setPaused(false);
		}
	}
}
