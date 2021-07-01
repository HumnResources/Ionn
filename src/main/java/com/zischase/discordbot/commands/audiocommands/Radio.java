package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.commands.*;
import com.zischase.discordbot.guildcontrol.GuildHandler;
import de.sfuhrm.radiobrowser4j.Paging;
import de.sfuhrm.radiobrowser4j.RadioBrowser;
import de.sfuhrm.radiobrowser4j.Station;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class Radio extends Command
{
	private static final Logger        LOGGER        = LoggerFactory.getLogger(Radio.class);
	private static final RadioBrowser  RADIO_BROWSER = new RadioBrowser(5000, "Beta");
	private static final List<Station> STATION_LIST;
	
	static
	{
		AtomicInteger i = new AtomicInteger();
		LOGGER.info("Loading Radio Stations");
		STATION_LIST = RADIO_BROWSER.listStations(Paging.at(0, 25000));
		STATION_LIST.removeIf(station -> {

			boolean nullState = station.getState() == null || station.getState().matches("null");
			boolean noBitRate = station.getBitrate() <= 0;
			boolean noURL = station.getUrl().isEmpty();
			boolean noCodec = station.getCodec().isEmpty();

			if (nullState || noBitRate || noURL || noCodec)
			{
				i.getAndIncrement();
//				LOGGER.info("Station Removed: " + station.getUrl());
				return true;
			}
			return false;
		});
		LOGGER.info("Stations Loaded - {} Stations Removed", i);
	}
	
	public Radio()
	{
		super(false);
	}
	
	@Override
	public @NotNull String shortDescription() {
		return "Plays a selected radio station.";
	}
	
	@Override
	public List<String> getAliases()
	{
		return List.of("R");
	}
	
	@Override
	public String helpText()
	{
		return String.format(""" 
				Radio [genre] 
				Radio [-search|-s] [search term] 
				Aliases: %s
				""", getAliases());
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		List<String> args = ctx.getArgs();
		
		if (! args.isEmpty())
		{
			String query = String.join(" ", args).toLowerCase();
			
			if (args.get(0).matches("(?i)-(search|s)"))
			{
				
				String searchQuery = query.replaceFirst("(?i)-(search|s)", "")
							 .trim();
				
				searchByString(ctx, searchQuery);
				return;
			}
			boolean tagFound = STATION_LIST.stream()
					.anyMatch(stn -> stn.getTags().matches("(?i)("+query+")"));

			if (tagFound)
			{
				searchByTag(ctx, query);
			}
		}
	}
	
	private void searchByTag(CommandContext ctx, String query)
	{
		List<Station> stations = STATION_LIST.stream()
											 .filter(stn -> stn.getTags().matches("(?i)("+query+")"))
											 .collect(Collectors.toList());
		if (stations.isEmpty())
		{
			return;
		}
		
		VoiceChannel voiceChannel = ctx.getMember() != null && ctx.getMember().getVoiceState() != null ?
									ctx.getMember().getVoiceState().getChannel() : null;
		
		Collections.shuffle(stations);
		
		GuildHandler.getContext(ctx.getGuild())
                    .audioManager()
                    .getTrackLoader()
                    .load(ctx.getChannel(), voiceChannel, stations.get(0).getUrl());
	}
	
	private void searchByString(CommandContext ctx, String query)
	{
		// RegEx for negative lookahead searching for only non word characters.
		String finalQuery = query.replaceAll("(?!\\w|\\s)(\\W)", "")
								 .toLowerCase();



		List<Station> stations = STATION_LIST.stream()
											 .filter(stn -> stn.getName()
													 .toLowerCase()
													 .replaceAll("(?!\\w|\\s)(\\W)", " ")
//													 .matches("(?i)("+finalQuery+")"))
													 .contains(finalQuery))
											 .limit(50)
											 .collect(Collectors.toList());

		if (stations.isEmpty())
		{

			return;
		}
		
		VoiceChannel voiceChannel = ctx.getMember() != null && ctx.getMember().getVoiceState() != null ?
									ctx.getMember().getVoiceState().getChannel() : null;
		
		List<ISearchable> results = new ArrayList<>();
		for (Station s : stations)
		{
			results.add(new SearchInfo(s));
		}

		try
		{
			Future<ISearchable> result = new ResultSelector(results).getChoice(ctx);


			long timeoutDelayMS = Long.parseLong(Config.get("SEARCH_RESULT_DELAY_MS"));

			GuildHandler.getContext(ctx.getGuild())
                        .audioManager()
                        .getTrackLoader()
                        .load(ctx.getChannel(), voiceChannel, result.get(timeoutDelayMS, TimeUnit.MILLISECONDS).getUrl());
		}
		catch (TimeoutException | InterruptedException | ExecutionException e)
		{
			LOGGER.warn("Radio result exception: \n" + e.getCause()
														.getLocalizedMessage());
		}
	}
	
}
