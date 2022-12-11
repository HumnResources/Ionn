package com.zischase.discordbot.commands;

import com.sun.istack.Nullable;
import com.zischase.discordbot.DBQueryHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.messages.MessageData;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.List;

public class CommandContext {

	private final TextChannel  textChannel;
	private final VoiceChannel voiceChannel;
	private final Guild        guild;
	private final Member       initiator;
	private final MessageData      message;
	private final List<String> args;
	private final SlashCommandInteractionEvent event;

	public CommandContext(Guild guild, Member initiator, List<String> args, MessageData commandMessage, TextChannel textChannel, @Nullable VoiceChannel voiceChannel, @Nullable SlashCommandInteractionEvent event) {
		this.initiator   = initiator;
		this.message     = commandMessage;
		this.args        = args;
		this.guild       = guild;
		this.textChannel = textChannel;
		this.event = event;

//		if (voiceChannel != null)
			this.voiceChannel = voiceChannel;
//		} else {
//			this.voiceChannel = initiator.getVoiceState() != null ? initiator.getVoiceState().getChannel().asVoiceChannel() : null;
//		}
	}

	public final boolean isPremiumGuild() {
		return DBQueryHandler.getPremiumStatus(getGuild().getId());
	}

	@NonNull
	public Guild getGuild() {
		return this.guild;
	}

	public List<String> getArgs() {
		return args;
	}

	@Nullable
	public VoiceChannel getVoiceChannel() {
		return this.voiceChannel;
	}

	public TextChannel getChannel() {
		return this.textChannel;
	}

	@Nullable
	public MessageData getMessage() {
		return this.message;
	}

	public Member getMember() {
		return this.initiator;
	}

	@NonNull
	public User getSelfUser() {
		return this.getJDA().getSelfUser();
	}

	@NonNull
	public JDA getJDA() {
		return this.initiator.getJDA();
	}

	@NonNull
	public Member getSelfMember() {
		return this.getGuild().getSelfMember();
	}

	@Nullable
	public ShardManager getShardManager() {
		return this.getJDA().getShardManager();
	}

	public SlashCommandInteractionEvent getEvent() {
		return this.event;
	}
}
