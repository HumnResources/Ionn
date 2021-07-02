package com.zischase.discordbot.commands;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public abstract class Command {

    private final boolean premiumCommand;
    private final AtomicReference<CommandData> commandData = new AtomicReference<>(null);

    public Command(boolean premiumCommand) {
        this.premiumCommand = premiumCommand;
    }

    public void setCommandData(CommandData commandData) {
        this.commandData.set(commandData);
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
