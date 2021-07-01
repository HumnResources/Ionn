package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Join extends Command
{
	public Join()
	{
		super(false);
	}
	
	@Override
	public String helpText() {
		return """
				Joins the users current voice channel.
				""";
	}
	
	@Override
	public @NotNull String shortDescription() {
		return "Summons the bot to the channel";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		connectToVoice(ctx.getChannel(), ctx.getMember());
	}
	
	private void connectToVoice(TextChannel channel, Member member)
	{
		List<VoiceChannel> voiceChannels = member.getGuild()
												 .getVoiceChannels();
		VoiceChannel voiceChannel = null;
		
		if (! voiceChannels.isEmpty())
		{
			for (VoiceChannel c : voiceChannels)
			{
				if (c.getMembers()
					 .contains(member))
				{
					c.getGuild()
					 .getJDA()
					 .getDirectAudioController()
					 .connect(c);
					voiceChannel = c;
				}
			}
		}
		if (voiceChannel == null)
		{
			channel.sendMessage("You must be in a voice channel for that !")
				   .queue();
		}
	}
	
}
