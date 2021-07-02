package com.zischase.discordbot.commands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import de.sfuhrm.radiobrowser4j.Station;

public class SearchInfo implements ISearchable {

	private final String url;
	private final String name;


	public SearchInfo(String name, String url) {
		this.url  = url;
		this.name = name;
	}

	public SearchInfo(Station station) {
		this.name = station.getName();
		this.url  = station.getUrl();
	}

	public SearchInfo(AudioTrack track) {
		this.name = track.getInfo().title;
		this.url  = track.getInfo().uri;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getUrl() {
		return url;
	}

}
