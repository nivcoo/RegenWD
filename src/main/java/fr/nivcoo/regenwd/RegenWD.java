package fr.nivcoo.regenwd;

import org.bukkit.plugin.java.JavaPlugin;


public class RegenWD extends JavaPlugin {

    private static RegenWD INSTANCE;

    @Override
    public void onEnable() {
        INSTANCE = this;
        getCommand("regenwd").setExecutor(new RegenWDCommands());

    }

    @Override
    public void onDisable() {
    }

    public static RegenWD get() {
        return INSTANCE;
    }

}
