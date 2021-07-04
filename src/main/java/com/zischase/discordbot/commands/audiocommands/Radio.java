package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.*;
import com.zischase.discordbot.guildcontrol.GuildContext;
import de.sfuhrm.radiobrowser4j.Paging;
import de.sfuhrm.radiobrowser4j.RadioBrowser;
import de.sfuhrm.radiobrowser4j.Station;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class Radio extends Command {

	private static final Logger        LOGGER        = LoggerFactory.getLogger(Radio.class);
	private static final RadioBrowser  RADIO_BROWSER = new RadioBrowser(5000, "Beta");
	private static final List<Station> STATION_LIST;

	static {
		AtomicInteger i = new AtomicInteger();
		LOGGER.info("Loading Radio Stations");
		STATION_LIST = RADIO_BROWSER.listStations(Paging.at(0, 25000));
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
	public @NotNull String shortDescription() {
		return "Plays a selected radio station.";
	}

	@Override
	public List<String> getAliases() {
		return List.of("R");
	}

	@Override
	public String helpText() {
		return String.format("""
				Radio [genre]
				Radio [-search|-s] [search term]
				Aliases: %s
				""", getAliases());
	}

	@Override
	public void handle(CommandContext ctx) {
		List<String> args = ctx.getArgs();

		String guildID = ctx.getGuild().getId();
		VoiceChannel voiceChannel = ctx.getEventInitiator().getVoiceState() != null ?
				ctx.getEventInitiator().getVoiceState().getChannel() : null;
		TextChannel textChannel = ctx.getChannel();

		if (voiceChannel != null) {
			DBQueryHandler.set(guildID, "media_settings", "voiceChannel", voiceChannel.getId());
		} else if (DBQueryHandler.get(guildID, "media_settings", "voiceChannel") == null) {
			return;
		}

		DBQueryHandler.set(guildID, "media_settings", "textChannel", textChannel.getId());

		if (!args.isEmpty()) {
			String query = String.join(" ", args).toLowerCase();

			if (args.get(0).matches("(?i)-(search|s)")) {

				query = query.replaceFirst("(?i)-(search|s)", "").trim();

				searchByString(guildID, textChannel, voiceChannel, query, ctx.getEventInitiator());
				return;
			}
			String finalQuery = query;
			boolean tagFound = STATION_LIST.stream().anyMatch(stn ->
					stn.getTags().matches("(?i)(" + finalQuery + ")"));

			if (tagFound) {
				searchByTag(guildID, textChannel, voiceChannel, query);
			}
		}
	}

	private void searchByString(String guildID, TextChannel textChannel, VoiceChannel voiceChannel, String query, Member initiator) {
		// RegEx for negative lookahead searching for only non word characters.
		String finalQuery = query.replaceAll("(?!\\w|\\s)(\\W)", "")
				.toLowerCase();

		List<Station> stations = STATION_LIST.stream()
				.filter(stn -> stn.getName()
						.toLowerCase()
						.replaceAll("(?!\\w|\\s)(\\W)", " ")
						.contains(finalQuery))
				.limit(50)
				.collect(Collectors.toList());

		if (stations.isEmpty()) {
			return;
		}

		List<ISearchable> results = new ArrayList<>();
		for (Station s : stations) {
			results.add(new SearchInfo(s));
		}

		ISearchable result = new ResultSelector(results, textChannel, textChannel.getJDA(), initiator).getChoice();
		GuildContext.get(guildID)
				.audioManager()
				.getTrackLoader()
				.load(textChannel, voiceChannel, result.getUrl());
	}

	private void searchByTag(String guildID, TextChannel textChannel, VoiceChannel voiceChannel, String query) {
		List<Station> stations = STATION_LIST.stream()
				.filter(stn -> stn.getTags().matches("(?i)(" + query + ")"))
				.collect(Collectors.toList());
		if (stations.isEmpty()) {
			return;
		}
		Collections.shuffle(stations);
		GuildContext.get(guildID)
				.audioManager()
				.getTrackLoader()
				.load(textChannel, voiceChannel, stations.get(0).getUrl());
	}

}
