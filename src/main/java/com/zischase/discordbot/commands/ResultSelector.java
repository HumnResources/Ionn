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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ResultSelector {

	/* Constants for defining behavior of searches */
	private static final int    PAGE_SIZE                 = 20;
	private final CompletableFuture<ISearchable> futureResult = new CompletableFuture<>();
	private final JDA                            jda;
	private final List<ISearchable> searchResultList;
	private       int               offset = 0;

	public ResultSelector(SlashCommandInteractionEvent event, @NotNull List<ISearchable> searchResultList, TextChannel textChannel, JDA jda, Member initiator) throws InvalidHandlerException {
		this.searchResultList = searchResultList;
		this.jda              = jda;

		buildOptions(event.getHook());
	}

	private void buildOptions(InteractionHook hook) {
		SelectOption option;
		ISearchable searchResult;

		List<SelectOption> options = new ArrayList<>();

		options.add(SelectOption.of("Next Page", "forward"));
		options.add(SelectOption.of("Prev Page", "backward"));

		for (int i = offset; i < offset + PAGE_SIZE; i++) {
			if (i >= searchResultList.size()) break;

			searchResult = searchResultList.get(i);
			option = SelectOption.of(searchResult.getName().replaceAll("[!@#$%^&*()_+-=|:;'\"<>,./?\\\\{}]", ""), String.valueOf(i));
			options.add(option);
		}

		SelectMenu menu = StringSelectMenu.create("menu:results")
				.setPlaceholder("Select an option")
				.setRequiredRange(0, 1)
				.addOptions(options)
				.build();

		hook.editOriginal("")
				.setActionRow(menu)//, Button.primary("backward", "Prev. Page"), Button.primary("forward", "Next Page"))
				.queue();
	}

	@Nullable
	public ISearchable get() {
		ISearchable result = null;

		ListenerAdapter resultListener = new ListenerAdapter() {
			@Override
			public void onStringSelectInteraction(@org.jetbrains.annotations.NotNull StringSelectInteractionEvent event) {
				event.deferEdit().queue();
				if (event.getSelectedOptions().get(0).getValue().equals("forward")) {
					offset += PAGE_SIZE;
					buildOptions(event.getHook());
					return;
				} else if (event.getSelectedOptions().get(0).getValue().equals("backward")) {
					offset = Math.min(0, offset - PAGE_SIZE);
					buildOptions(event.getHook());
					return;
				}

				int index = Integer.parseInt(event.getSelectedOptions().get(0).getValue());

				if (index >= 0 && index < searchResultList.size())
					futureResult.complete(searchResultList.get(index));

				event.getMessage().delete().complete();
			}

			@Override
			public void onButtonInteraction(@org.jetbrains.annotations.NotNull ButtonInteractionEvent event) {
				if (event.getButton().getLabel().equals("forward")) {
					offset += PAGE_SIZE;
				} else if (event.getButton().getLabel().equals("backward")) {
					offset -= PAGE_SIZE;
				}
				buildOptions(event.getHook());
			}
		};

		jda.addEventListener(resultListener);

		try {
			result = futureResult.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

		jda.removeEventListener(resultListener);

		return result;
	}

}
