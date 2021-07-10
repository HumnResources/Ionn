package com.zischase.discordbot.commands;

import com.sun.istack.Nullable;
import com.zischase.discordbot.DBQueryHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.List;

public class CommandContext {

	private final TextChannel textChannel;
	private final VoiceChannel voiceChannel;
	private final Guild guild;
	private final Member initiator;
	private final Message message;
	private final List<String> args;

	CommandContext(Guild guild, Member initiator, List<String> args, Message commandMessage, TextChannel textChannel, @Nullable VoiceChannel voiceChannel) {
		this.initiator = initiator;
		this.message = commandMessage;
		this.args = args;
		this.guild = guild;
		this.textChannel = textChannel;

		if (voiceChannel != null) {
			this.voiceChannel = voiceChannel;
		}
		else {
			this.voiceChannel = initiator.getVoiceState() != null ? initiator.getVoiceState().getChannel() : null;
		}
	}

	public final boolean isPremiumGuild() {
		return DBQueryHandler.getPremiumStatus(getGuild().getId());
	}

	public List<String> getArgs() {
		return args;
	}

	@NonNull
	public Guild getGuild() {
		return this.guild;
	}

	@Nullable
	public VoiceChannel getVoiceChannel() {
		return this.voiceChannel;
	}

	@NonNull
	public TextChannel getChannel() {
		return this.textChannel;
	}

	@Nullable
	public Message getMessage() {
		return this.message;
	}

	public Member getMember() {
		return this.initiator;
	}

	@NonNull
	public JDA getJDA() {
		return this.initiator.getJDA();
	}

	@NonNull
	public User getSelfUser() {
		return this.getJDA().getSelfUser();
	}

	@NonNull
	public Member getSelfMember() {
		return this.getGuild().getSelfMember();
	}

	@Nullable
	public ShardManager getShardManager() {
		return this.getJDA().getShardManager();
	}
}
