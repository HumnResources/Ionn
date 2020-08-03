package com.zischase.discordbot;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;

import javax.security.auth.login.LoginException;

public class Main {

    static {
        PostgreSQL.getConnection();
    }

    public static void main(String[] args) throws LoginException {


        JDABuilder.createDefault(Config.get("TOKEN"))
                .setActivity(Activity.watching("The server. . ."))
                .addEventListeners(new Listener())
                .build();
    }





}
