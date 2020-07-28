package com.zischase.discordbot.commands;

public abstract class Command implements ICommand {

    public boolean premiumCommand;

    public Command(boolean premiumCommand) {
        this.premiumCommand = premiumCommand;
    }

    public abstract void execute(CommandContext ctx);

}
