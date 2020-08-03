package com.zischase.discordbot.commands;

public interface ISearchable {
    default String getName() { return ""; }
    default String getUrl() { return ""; }
}
