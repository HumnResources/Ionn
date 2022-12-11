package com.zischase.discordbot.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenuInteraction;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ResultMenuInteraction implements SelectMenuInteraction<String, SelectMenu> {
	@NotNull
	@Override
	public String getComponentId() {
		return null;
	}

	@NotNull
	@Override
	public SelectMenu getComponent() {
		return null;
	}

	@NotNull
	@Override
	public Message getMessage() {
		return null;
	}

	@Override
	public long getMessageIdLong() {
		return 0;
	}

	@NotNull
	@Override
	public Component.Type getComponentType() {
		return null;
	}

	@Override
	public int getTypeRaw() {
		return 0;
	}

	@NotNull
	@Override
	public String getToken() {
		return null;
	}

	@Nullable
	@Override
	public Guild getGuild() {
		return null;
	}

	@NotNull
	@Override
	public User getUser() {
		return null;
	}

	@Nullable
	@Override
	public Member getMember() {
		return null;
	}

	@Override
	public boolean isAcknowledged() {
		return false;
	}

	@NotNull
	@Override
	public MessageChannelUnion getChannel() {
		return null;
	}

	@NotNull
	@Override
	public DiscordLocale getUserLocale() {
		return null;
	}

	@NotNull
	@Override
	public JDA getJDA() {
		return null;
	}

	@NotNull
	@Override
	public List<String> getValues() {
		return null;
	}

	@NotNull
	@Override
	public MessageEditCallbackAction deferEdit() {
		return null;
	}

	@NotNull
	@Override
	public ModalCallbackAction replyModal(@NotNull Modal modal) {
		return null;
	}

	@NotNull
	@Override
	public ReplyCallbackAction deferReply() {
		return null;
	}

	@NotNull
	@Override
	public InteractionHook getHook() {
		return null;
	}

	@Override
	public long getIdLong() {
		return 0;
	}
}
