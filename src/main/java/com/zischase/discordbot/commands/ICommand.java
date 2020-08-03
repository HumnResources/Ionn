package com.zischase.discordbot.commands;

import java.util.List;

public interface ICommand {
    default String getName() {
        return this.getClass().getSimpleName();
    }

    default List<String> getAliases() {
        return List.of(); // return immutable list of 0 elements
    }

    default String getHelp() {
        return "No description provided.";
    };

}
