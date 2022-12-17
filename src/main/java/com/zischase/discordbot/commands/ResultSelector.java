package com.zischase.discordbot.commands;

import com.github.ygimenez.exception.InvalidHandlerException;
import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ResultSelector {

	/* Constants for defining behavior of searches */
	private static final int                       PAGE_SIZE    = 20;
	private final CompletableFuture<ISearchResult> futureResult = new CompletableFuture<>();
	private final JDA                              jda;
	private final List<ISearchResult> searchResultList;
	private       int                 offset       = 0;
	private final Member initiator;
	private       String          messageID;
	private final ListenerAdapter resultListener;
	private final String guildID;

	public ResultSelector(SlashCommandInteractionEvent event, @NotNull List<ISearchResult> searchResultList, JDA jda, Member initiator) throws InvalidHandlerException {
		this.searchResultList = searchResultList;
		this.jda              = jda;
		this.initiator = initiator;
		this.guildID = Objects.requireNonNull(event.getGuild()).getId();
		this.resultListener = addEventListeners();

		if (event.getHook().retrieveOriginal().complete() != null) {
			buildOptions(event.getHook());
		}

		/*
		 * Sets timer to remove listener after one 1m30s, you took too long :o
		 */
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				if (((TextChannel) event.getHook().getInteraction().getGuildChannel()).getHistory().getMessageById(messageID) == null)
					jda.removeEventListener(resultListener);
			}
		}, Date.from(Instant.now().plusMillis(60000)));
	}

	private void buildOptions(InteractionHook hook) {
		SelectOption  option;
		ISearchResult searchResult;

		List<SelectOption> options = new ArrayList<>();

		options.add(SelectOption.of("Next Page", "forward"));
		options.add(SelectOption.of("Prev Page", "backward"));
		options.add(SelectOption.of("Cancel", "cancel"));

		for (int i = offset; i < offset + PAGE_SIZE; i++) {
			if (i >= searchResultList.size()) break;

			searchResult = searchResultList.get(i);
			option = SelectOption.of(searchResult.getName().replaceAll("[^a-zA-Z0-9\\s\\-]", ""), String.valueOf(i));
			options.add(option);
		}

		SelectMenu menu = StringSelectMenu.create("menu:results")
				.setPlaceholder("Select an option")
				.setRequiredRange(0, 1)
				.addOptions(options)
				.build();

		hook.editOriginal("")
				.setActionRow(menu)
				.queue();

		
		this.messageID = hook.retrieveOriginal().complete().getId();
	}

	@Nullable
	public ISearchResult get() {
		ISearchResult result = null;


		jda.addEventListener(resultListener);

		try {
			result = futureResult.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

		jda.removeEventListener(resultListener);

		return result;
	}


	private ListenerAdapter addEventListeners() {
		return new ListenerAdapter() {

			@Override
			public void onStringSelectInteraction(@org.jetbrains.annotations.NotNull StringSelectInteractionEvent event) {

				boolean t = !event.getMember().getId().equals(initiator.getId());
				boolean o = event.getHook().isExpired();
				boolean p = !event.getMessage().getId().equals(messageID);

				if (!event.getMember().getId().equals(initiator.getId()) || event.getHook().isExpired() || !event.getMessage().getId().equals(messageID)) return;

				if (!event.isAcknowledged()) event.deferEdit().complete();

				String selection = event.getSelectedOptions().get(0).getValue();
				switch (selection) {
					case "forward" -> {
						offset += PAGE_SIZE;
						buildOptions(event.getHook());
					}
					case "backward" -> {
						offset = Math.min(0, offset - PAGE_SIZE);
						buildOptions(event.getHook());
					}
					case "cancel" -> futureResult.complete(null);
					default -> {
						int index = Integer.parseInt(event.getSelectedOptions().get(0).getValue());
						if (index >= 0 && index < searchResultList.size())
							futureResult.complete(searchResultList.get(index));
					}
				}
			}
			@Override
			public void onButtonInteraction(@org.jetbrains.annotations.NotNull ButtonInteractionEvent event) {
				if (event.getMember() != initiator && Objects.requireNonNull(event.getGuild()).getId().equals(guildID)) return;

				if (event.getButton().getLabel().equals("forward")) {
					offset += PAGE_SIZE;
				} else if (event.getButton().getLabel().equals("backward")) {
					offset -= PAGE_SIZE;
				}
				buildOptions(event.getHook());
			}
		};
	}
}
