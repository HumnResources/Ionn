package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.SearchInfo;
import com.zischase.discordbot.audioplayer.TrackLoader;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.ISearchable;
import com.zischase.discordbot.commands.ResultSelector;
import de.sfuhrm.radiobrowser4j.Paging;
import de.sfuhrm.radiobrowser4j.RadioBrowser;
import de.sfuhrm.radiobrowser4j.Station;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public class Radio extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(Radio.class);
    private static final RadioBrowser RADIO_BROWSER = new RadioBrowser(5000, "Beta");
    private static List<Station> STATION_LIST;

    static {
        new CompletableFuture<>().completeAsync(() -> {
            STATION_LIST = RADIO_BROWSER.listStations(Paging.at(0, 25000));
            LOGGER.info("Stations Loaded!");
            return STATION_LIST;
        });
    }

    public Radio() {
        super(false);
    }

    @Override
    public List<String> getAliases() {
        return List.of("R");
    }

    @Override
    public String getHelp() {
        return String.format(" Radio [genre] \n" +
                "Radio s [search term] \n" +
                "Aliases: %s", getAliases());
    }

    @Override
    public void handle(CommandContext ctx) {
        GuildMessageReceivedEvent event = ctx.getEvent();
        List<String> args = ctx.getArgs();

        if (!args.isEmpty()) {
            String query = String.join(" ", args).toLowerCase();

            if (args.get(0).matches("(i?)(search|s)")) {

                query = query.replaceFirst("(i?)(search|s)", "").trim();

                searchByString(event, query);
            }
            else if (RADIO_BROWSER.listTags().containsKey(query))
                searchByTag(event, query);
        }
    }

    private void searchByTag(GuildMessageReceivedEvent event, String query) {
        List<Station> stations = STATION_LIST.stream()
                .filter(stn -> stn.getTags().equalsIgnoreCase(query))
                .collect(Collectors.toList());

        Collections.shuffle(stations);

        if (stations.isEmpty())
            return;

        new TrackLoader().load(event.getChannel(), event.getMember(), stations.get(0).getUrl());
    }

    private void searchByString(GuildMessageReceivedEvent event, String query) {
        // RegEx for negative lookahead searching for only non word characters.
        String finalQuery = query.replaceAll("(?!\\w|\\s)(\\W)", "").toLowerCase();

        List<Station> stations = STATION_LIST.stream()
                .filter(stn -> stn.getName().toLowerCase().contains(finalQuery))
                .limit(20)
                .collect(Collectors.toList());

        List<ISearchable> results = new ArrayList<>();
        for (Station s : stations) {
            results.add(new SearchInfo(s));
        }

        new ResultSelector(results)
                .getChoice(event)
                .thenApplyAsync(result -> {
                    new TrackLoader().load(event.getChannel(), event.getMember(), result.getUrl());
                    return null;
                });
    }

}
