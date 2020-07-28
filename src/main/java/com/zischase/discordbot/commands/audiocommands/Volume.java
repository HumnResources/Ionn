package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.PostgreSQL;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.entities.Guild;
import org.jdbi.v3.core.Jdbi;

import java.util.Arrays;
import java.util.List;

public class Volume extends Command {
    private final int MAX_VOL = Integer.parseInt(Config.get("MAX_VOLUME"));
    private int VOLUME = Integer.parseInt(Config.get("VOLUME"));

    public Volume() {
        super(false);
    }

    public void set(Guild guild) {

        VOLUME = Jdbi.create(PostgreSQL::getConnection).withHandle(handle -> {
            int r = handle.createQuery("SELECT volume FROM guild_settings WHERE guild_id = ?")
                    .bind(0, guild.getId())
                    .mapTo(Integer.class)
                    .findFirst()
                    .orElse(VOLUME);
            handle.close();
            return r;
        });

        GuildManager.getContext(guild)
                .getAudioManager()
                .getPlayer()
                .setVolume(VOLUME);
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("Vol", "V");
    }

    @Override
    public String getHelp() {
        return "Volume [amount] ~ Sets the volume. 0-"+MAX_VOL+" | Leave empty to display current volume.";
    }

    @Override
    public void execute(CommandContext ctx) {
        Guild guild =ctx.getGuild();
        List<String> args = ctx.getArgs();

        if (args.isEmpty()) {
            ctx.getEvent()
                    .getChannel()
                    .sendMessage("Volume is currently at: `" + VOLUME + "`")
                    .queue();
            return;
        }

        if (args.get(0).matches("\\d+")) {
            int num = Integer.parseInt(args.get(0));

            boolean validNum = (num >= 0 && num <= MAX_VOL);

            if (validNum) {
                Jdbi.create(PostgreSQL::getConnection)
                        .useHandle(h -> h.execute("UPDATE guild_settings SET volume = ? WHERE guild_id = ?", args.get(0), guild.getId()));

                this.set(guild);
                
                ctx.getEvent()
                        .getChannel()
                        .sendMessage("The volume has been set to `" + VOLUME + "`")
                        .queue();

                return;
            }
        }
        ctx.getEvent()
                .getChannel()
                .sendMessage("Please input a number between 0-"+MAX_VOL)
                .queue();
    }
}
