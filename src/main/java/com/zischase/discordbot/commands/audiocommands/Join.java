package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Join extends Command
{
	
	public Join()
	{
		super(false);
	}
	
	@Override
	public @NotNull String shortDescription()
	{
		return "Summons the bot to the channel";
	}
	
	@Override
	public String helpText()
	{
		return """
				Joins the users current voice channel.
				""";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		List<VoiceChannel> voiceChannels = ctx.getGuild()
				.getVoiceChannels();
		VoiceChannel voiceChannel = null;
		
		if (!voiceChannels.isEmpty())
		{
			for (VoiceChannel c : voiceChannels)
			{
				if (c.getMembers().contains(ctx.getMember()))
				{
					c.getGuild()
							.getJDA()
							.getDirectAudioController()
							.connect(c);
					voiceChannel = c;
				}
			}
		}
		if (voiceChannel == null && ctx.getMember().getUser() != ctx.getJDA().getSelfUser())
		{
			ctx.getChannel()
					.sendMessage("You must be in a voice channel for that !")
					.queue();
		}
	}
}
