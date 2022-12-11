package com.zischase.discordbot.commands.audiocommands;

import com.github.ygimenez.exception.InvalidHandlerException;
import com.zischase.discordbot.commands.*;
import com.zischase.discordbot.guildcontrol.GuildContext;
import de.sfuhrm.radiobrowser4j.Paging;
import de.sfuhrm.radiobrowser4j.RadioBrowser;
import de.sfuhrm.radiobrowser4j.Station;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class Radio extends Command {

	private static final Logger        LOGGER        = LoggerFactory.getLogger(Radio.class);
	private static final RadioBrowser  RADIO_BROWSER = new RadioBrowser(5000, "Beta");
	private static final List<Station> STATION_LIST;
	private static final int MAX_RADIO_STATIONS = 100000;

	static {
		AtomicInteger i = new AtomicInteger();
		LOGGER.info("Loading Radio Stations");
		STATION_LIST = RADIO_BROWSER.listStations(Paging.at(0, MAX_RADIO_STATIONS));
		STATION_LIST.removeIf(station -> {
			boolean nullState = station.getState() == null || station.getState().matches("null");
			boolean noBitRate = station.getBitrate() <= 0;
			boolean noURL     = station.getUrl().isEmpty();
			boolean noCodec   = station.getCodec().isEmpty();
			if (nullState || noBitRate || noURL || noCodec) {
				i.getAndIncrement();
				return true;
			}
			return false;
		});
		LOGGER.info("Stations Loaded - {} Stations Removed", i);
	}

	public Radio() {
		super(false);
	}

	@Override
	public SlashCommandData getCommandData() {
		return super.getCommandData().addOption(OptionType.STRING, "query", "Displays a list from search result", true);
	}

	@Override
	public @NotNull String shortDescription() {
		return "Plays a selected radio station. Input number to choose";
	}

	@Override
	public List<String> getAliases() {
		return List.of("R");
	}

	@Override
	public String helpText() {
		return String.format("""
				Radio
				Radio [search term]
				Aliases: %s
				""", getAliases());
	}

	@Override
	public void handle(CommandContext ctx) {
		List<String> args         = ctx.getArgs();
		String       guildID      = ctx.getGuild().getId();
		TextChannel  textChannel  = ctx.getChannel();
		VoiceChannel voiceChannel = ctx.getVoiceChannel();

		String query = String.join(" ", args).toLowerCase();
		searchByString(ctx.getEvent(), guildID, textChannel, voiceChannel, query, ctx.getMember());
	}


	private void searchByString(SlashCommandInteractionEvent event, String guildID, TextChannel textChannel, VoiceChannel voiceChannel, String query, Member initiator) {
		// RegEx for negative lookahead searching for only non word characters.
		String finalQuery = query.replaceAll("(?!\\w|\\s)(\\W)", "")
				.toLowerCase();

		List<Station> stations = STATION_LIST.stream()
				.filter(stn -> stn.getName()
						.toLowerCase()
						.replaceAll("(?!\\w|\\s)(\\W)", " ")
						.contains(finalQuery))
				.limit(100)
				.collect(Collectors.toList());

		if (stations.isEmpty()) {
			return;
		}

		List<ISearchable> results = new ArrayList<>();
		for (Station s : stations) {
			results.add(new SearchInfo(s));
		}

		ISearchable result;
		try {
			result = new ResultSelector(event, results, textChannel, textChannel.getJDA(), initiator).get();
			GuildContext.get(guildID)
					.audioManager()
					.getTrackLoader()
					.load(textChannel, voiceChannel, result.getUrl());
		} catch (InvalidHandlerException e) {
			e.printStackTrace();
		}
	}
}
