package com.zischase.discordbot.commands;

import com.sun.istack.Nullable;
import com.zischase.discordbot.DBQueryHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.dv8tion.jda.api.utils.messages.MessageData;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

		if (voiceChannel != null) {
			this.voiceChannel = voiceChannel;
		} else {
			this.voiceChannel = guild.getChannelById(VoiceChannel.class, DBQueryHandler.get(guild.getId(), "voicechannel"));
		}
	}

	public CommandContext(Guild guild, Member initiator, List<String> args) {
		this.voiceChannel = guild.getChannelById(VoiceChannel.class, DBQueryHandler.get(guild.getId(), "voicechannel"));
		this.textChannel = guild.getChannelById(TextChannel.class, DBQueryHandler.get(guild.getId(), "textchannel"));
		this.event = null;
		this.args = args;
		this.initiator = initiator;
		this.guild = guild;

		this.message = new MessageData() {
			@NotNull
			@Override
			public String getContent() {
				String s = DBQueryHandler.get(guild.getId(), "prefix");
				s += String.join(" ", args);
				return s;
			}

			@NotNull
			@Override
			public List<MessageEmbed> getEmbeds() {
				return List.of();
			}

			@NotNull
			@Override
			public List<LayoutComponent> getComponents() {
				return List.of();
			}

			@NotNull
			@Override
			public List<? extends AttachedFile> getAttachments() {
				return List.of();
			}

			@Override
			public boolean isSuppressEmbeds() {
				return false;
			}

			@NotNull
			@Override
			public Set<String> getMentionedUsers() {
				return Set.of();
			}

			@NotNull
			@Override
			public Set<String> getMentionedRoles() {
				return Set.of();
			}

			@NotNull
			@Override
			public EnumSet<Message.MentionType> getAllowedMentions() {
				return EnumSet.of(Message.MentionType.SLASH_COMMAND);
			}

			@Override
			public boolean isMentionRepliedUser() {
				return false;
			}
		};
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
