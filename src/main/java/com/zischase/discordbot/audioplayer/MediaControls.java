package com.zischase.discordbot.audioplayer;

import java.util.List;

public class MediaControls
{
	
	public static final String SHUFFLE                   = "🔀";
	public static final String REPEAT_QUEUE              = "🔁";
	public static final String REPEAT_ONE                = "🔃";
	public static final String PREV_TRACK                = "⏮";
	public static final String FAST_REVERSE              = "⏪";
	public static final String REVERSE                   = "◀";
	public static final String PLAY_PAUSE                = "⏯";
	public static final String PLAY                      = "▶";
	public static final String PAUSE                     = "⏸";
	public static final String FAST_FORWARD              = "⏩";
	public static final String NEXT_TRACK                = "⏭";
	public static final String STOP                      = "⏹";
	public static final String NOTES_ONE                 = "🎶";
	public static final String RADIO                     = "📻";
	public static final String MICROPHONE                = "🎙";
	public static final String VOLUME_HIGH               = "🔊";
	public static final String RED_RECORDING_DOT         = "🔴";
	public static final String PROGRESS_BAR_ICON_FILL    = "⬜";
	public static final String PROGRESS_BAR_ICON_EMPTY   = "⬛";
	public static final String NOW_PLAYING_MSG_NAME      = "**Now Playing**";
	public static final String QUEUE_MSG_NAME            = "**Queue**";
	public static final String NOTHING_PLAYING_MSG_NAME  = "**Nothing Playing**";
	public static final int    HISTORY_POLL_LIMIT        = 10;
	public static final int    PRINT_TIMEOUT_MS          = 2000;
	public static final int    REACTION_TIMEOUT_MS       = 1000;
	public static final int    NOW_PLAYING_TIMER_RATE_MS = 11300;
	public static final int    PROGRESS_BAR_SIZE         = 10;
	public static final int    QUEUE_PAGE_SIZE           = 10;
	
	public static final List<String> NOW_PLAYING_REACTIONS = List.of(REPEAT_ONE, PREV_TRACK, PLAY_PAUSE, NEXT_TRACK);
	public static final List<String> QUEUE_REACTIONS       = List.of(SHUFFLE, REPEAT_QUEUE, PREV_TRACK, REVERSE, PLAY, NEXT_TRACK);
	
	MediaControls()
	{
	}
	
	public static List<String> getNowPlayingReactions()
	{
		return List.copyOf(NOW_PLAYING_REACTIONS);
	}
	
	public static List<String> getQueueReactions()
	{
		return List.copyOf(QUEUE_REACTIONS);
	}
}
