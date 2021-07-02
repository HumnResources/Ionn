package com.zischase.discordbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class Command {

    private final boolean premiumCommand;

    public Command(boolean premiumCommand) {
        this.premiumCommand = premiumCommand;
    }

    public CommandData getCommandData() {
        return CommandData.fromData(DataObject.fromJson("""
                {
                	"name": "%s",
                	"description": "%s"
                }
                """.formatted(getName().toLowerCase(), shortDescription())));
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    public final boolean isPremium() {
        return premiumCommand;
    }

    public List<String> getAliases() {
        return List.of();
    }

    public abstract String helpText();

    @NotNull
    public abstract String shortDescription();

    public abstract void handle(CommandContext ctx);

}
