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
import java.util.concurrent.*;
import java.util.stream.Collectors;


public class Radio extends Command
{
	private static final Logger        LOGGER        = LoggerFactory.getLogger(Radio.class);
	private static final RadioBrowser  RADIO_BROWSER = new RadioBrowser(5000, "Beta");
	private static       List<Station> STATION_LIST;
	
	static
	{
		LOGGER.info("Loading Radio Stations");
		STATION_LIST = RADIO_BROWSER.listStations(Paging.at(0, 25000));
		LOGGER.info("Stations Loaded");
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
		return String.format(" Radio [genre] \n" + "Radio -[search|s] [search term] \n" + "Aliases: %s", getAliases());
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		GuildMessageReceivedEvent event = ctx.getEvent();
		List<String> args = ctx.getArgs();
		
		if (! args.isEmpty())
		{
			String query = String.join(" ", args)
								 .toLowerCase();
			
			if (args.get(0).matches("(?i)(-search|-s)"))
			{
				
				query = query.replaceFirst("(?i)(-search|-s)", "")
							 .trim();
				
				searchByString(event, query);
			}
			else if (RADIO_BROWSER.listTags()
								  .containsKey(query))
			{
				searchByTag(event, query);
			}
		}
	}
	
	private void searchByTag(GuildMessageReceivedEvent event, String query)
	{
		List<Station> stations = STATION_LIST.stream()
											 .filter(stn -> stn.getTags()
															   .equalsIgnoreCase(query))
											 .collect(Collectors.toList());
		
		Collections.shuffle(stations);
		
		if (stations.isEmpty())
		{
			return;
		}
		
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
													 .replaceAll("\\W", "")
													 .contains(finalQuery))
											 .limit(50)
											 .collect(Collectors.toList());

		if (stations.isEmpty())
			return;
		
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
