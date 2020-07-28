package com.zischase.discordbot.guildcontrol;

import java.util.HashMap;
import java.util.Map;

public class GuildSettingsManager{

    /* Where string = setting name, Object = value */
    private final Map<String, Object> guildSettings;

    public GuildSettingsManager() {
        this.guildSettings = new HashMap<>();
    }



}
