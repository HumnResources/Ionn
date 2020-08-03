package com.zischase.discordbot.commands;

public abstract class Command implements ICommand {
    /*

    Creates a thread pool where each guild gets a single thread. Providing a 200 thread buffer for max capacity.

    Usage: Query results and asynchronous results. To prevent blocking of commands when being used.


    Thread count is created using number of commands and guilds combined, to ensure seamless command usage.
     */
    public boolean premiumCommand;

    public Command(boolean premiumCommand) {
        this.premiumCommand = premiumCommand;
    }


    public abstract void handle(CommandContext ctx);


}
