package fr.nivcoo.regenwd;


import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.mvplugins.multiverse.core.MultiverseCore;


public class RegenWD extends JavaPlugin {

    private static RegenWD INSTANCE;

    private MultiverseCore multiverseCore;

    @Override
    public void onEnable() {
        INSTANCE = this;
        getCommand("regenwd").setExecutor(new RegenWDCommands());

        multiverseCore = (MultiverseCore) Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");

    }

    @Override
    public void onDisable() {
    }

    public static RegenWD get() {
        return INSTANCE;
    }

    public MultiverseCore getMultiverseCore() {
        return multiverseCore;
    }

}
