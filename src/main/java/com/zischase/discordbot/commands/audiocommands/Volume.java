package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class Volume extends Command {

    private final int maxVolume = Integer.parseInt(Config.get("MAX_VOLUME"));

    public Volume() {
        super(false);
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("Vol", "V");
    }

    @Override
    public String helpText() {
        return "Volume [amount] ~ Sets the volume. 0-" + maxVolume + " | Leave empty to display current volume.";
    }

    @Override
    public @NotNull String shortDescription() {
        return "Sets or displays the volume level.";
    }

    @Override
    public void handle(CommandContext ctx) {
        String guildID = ctx.getGuild().getId();
        List<String> args = ctx.getArgs();

        if (args.isEmpty()) {
            ctx.getEvent()
                    .getChannel()
                    .sendMessage("Volume is currently at: `" + getVolume(guildID) + "`")
                    .queue();
            return;
        }

        if (args.get(0).matches("\\d+")) {
            int num = Integer.parseInt(args.get(0));
            int max = maxVolume;

            if (GuildContext.get(guildID).isPremium()) {
                max = 100;
            }

            boolean validNum = (num >= 0 && num <= max);

            if (validNum) {
                setVolume(guildID, num);

                ctx.getEvent()
                        .getChannel()
                        .sendMessage("The volume has been set to `" + getVolume(guildID) + "`")
                        .queue();

                return;
            }
        }
        ctx.getEvent()
                .getChannel()
                .sendMessage("Please input a number between 0-" + maxVolume)
                .queue();
    }

    private String getVolume(String guildID) {
        return String.valueOf(GuildContext.get(guildID)
                .audioManager()
                .getPlayer()
                .getVolume());
    }

    private void setVolume(String guildID, int value) {
        DBQueryHandler.set(guildID, "volume", value);
        GuildContext.get(guildID)
                .audioManager()
                .getPlayer()
                .setVolume(value);
    }

}
