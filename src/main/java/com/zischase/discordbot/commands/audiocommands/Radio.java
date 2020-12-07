package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.commands.*;
import com.zischase.discordbot.guildcontrol.GuildManager;
import de.sfuhrm.radiobrowser4j.Paging;
import de.sfuhrm.radiobrowser4j.RadioBrowser;
import de.sfuhrm.radiobrowser4j.Station;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
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
	public List<String> getAliases()
	{
		return List.of("R");
	}
	
	@Override
	public String getHelp()
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
		GuildMessageReceivedEvent event = ctx.getEvent();
		List<String> args = ctx.getArgs();
		
		if (! args.isEmpty())
		{
			String query = String.join(" ", args).toLowerCase();
			
			if (args.get(0).matches("(?i)-(search|s)"))
			{
				
				String searchQuery = query.replaceFirst("(?i)-(search|s)", "")
							 .trim();
				
				searchByString(event, searchQuery);
				return;
			}
			boolean tagFound = STATION_LIST.stream()
					.anyMatch(stn -> stn.getTags().matches("(?i)("+query+")"));

			if (tagFound)
			{
				searchByTag(event, query);
			}
		}
	}
	
	private void searchByTag(GuildMessageReceivedEvent event, String query)
	{
		List<Station> stations = STATION_LIST.stream()
											 .filter(stn -> stn.getTags().matches("(?i)("+query+")"))
											 .collect(Collectors.toList());
		if (stations.isEmpty())
		{
			return;
		}

		Collections.shuffle(stations);
		
		GuildManager.getContext(event.getGuild())
					.audioManager()
					.getTrackLoader()
					.load(event.getChannel(), event.getMember(), stations.get(0)
																		 .getUrl());
	}
	
	private void searchByString(GuildMessageReceivedEvent event, String query)
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
		
		List<ISearchable> results = new ArrayList<>();
		for (Station s : stations)
		{
			results.add(new SearchInfo(s));
		}

		try
		{
			Future<ISearchable> result = new ResultSelector(results).getChoice(event);


			long timeoutDelayMS = Long.parseLong(Config.get("SEARCH_RESULT_DELAY_MS"));

			GuildManager.getContext(event.getGuild())
						.audioManager()
						.getTrackLoader()
						.load(event.getChannel(), event.getMember(), result.get(timeoutDelayMS, TimeUnit.MILLISECONDS).getUrl());
		}
		catch (TimeoutException | InterruptedException | ExecutionException e)
		{
			LOGGER.warn("Radio result exception: \n" + e.getCause()
														.getLocalizedMessage());
		}
	}
	
}
