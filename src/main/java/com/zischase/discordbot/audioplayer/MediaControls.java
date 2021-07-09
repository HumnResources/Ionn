package com.zischase.discordbot.audioplayer;

import java.util.List;

public class MediaControls {

	public static final String        SHUFFLE               = "🔀";
	public static final String        REPEAT_QUEUE          = "🔁";
	public static final String        REPEAT_ONE            = "🔃";
	public static final String        PREV                  = "⏮";
	public static final String        PLAY_PAUSE            = "⏯";
	public static final String        NEXT                  = "⏭";
	public static final String        STOP                  = "⏹";
	public static final String        PAUSE                 = "⏸";
	public static final String        PLAY                  = "▶";
	public static final String        NOTES_ONE             = "🎶";
	public static final String        RADIO                 = "📻";
	public static final String        MICROPHONE            = "🎙";
	public static final String        VOLUME_HIGH           = "🔊";
	private static final List<String> NOW_PLAYING_REACTIONS = List.of(SHUFFLE, REPEAT_QUEUE, REPEAT_ONE, PREV, PLAY_PAUSE, NEXT);

	MediaControls() {
	}

	public static List<String> getReactions() {
		return List.copyOf(NOW_PLAYING_REACTIONS);
	}
}
