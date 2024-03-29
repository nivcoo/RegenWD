package fr.nivcoo.regenwd;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RegenWDCommands implements CommandExecutor {

    public void help(CommandSender p) {

        if (p.hasPermission("regenwd.commands")) {
            p.sendMessage(ChatColor.GRAY + "§m------------------" + ChatColor.DARK_GRAY + "[" + ChatColor.GOLD
                    + "Menu d'aide" + ChatColor.DARK_GRAY + "]" + ChatColor.GRAY + "§m------------------");
            p.sendMessage(ChatColor.GOLD + "/regenwd end " + ChatColor.YELLOW + "pour regen l'end");
            p.sendMessage(ChatColor.GOLD + "/regenwd nether " + ChatColor.YELLOW + "pour regen le nether");
            p.sendMessage(ChatColor.GOLD + "/regenwd resources " + ChatColor.YELLOW + "pour regen le monde ressource");
            p.sendMessage(ChatColor.GOLD + "/regenwd resources_2 " + ChatColor.YELLOW + "pour regen le monde ressource amiral");
            p.sendMessage(ChatColor.GRAY + "§m----------------------------------------------");

        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String msg, String[] args) {
        if (cmd.getName().equalsIgnoreCase("regenwd")) {
            if (sender.hasPermission("regenwd.commands")) {
                if (args.length == 0) {
                    help(sender);
                    return true;
                } else {
                    String worldNameBroadcast = null;
                    String extraMessageBroadcast = "";
                    String worldName = null;
                    String folderSave = "WorldSaves";
                    List<String> commands = new ArrayList<>();
                    commands.add("hd reload");

                    if (args[0].equalsIgnoreCase("end")) {
                        worldNameBroadcast = "End";
                        extraMessageBroadcast = "";
                        worldName = "world_the_end";

                    } else if (args[0].equalsIgnoreCase("nether")) {
                        worldNameBroadcast = "Nether";
                        worldName = "world_nether";

                        commands.add("netherportal reload");
                    }

                    if (worldName != null) {
                        World world = Bukkit.getWorld(worldName);
                        if (world == null)
                            return false;
                        List<Player> playerList = Bukkit.getOnlinePlayers().stream()
                                .filter(onlinePlayer -> onlinePlayer.getWorld() == world).collect(Collectors.toList());
                        for (Player player : playerList) {
                            player.sendMessage(
                                    "§7[§c§lES§7] Le monde se régénére ! Vous avez été téléporté sur votre île !");
                            player.performCommand("is go");
                        }

                        RegenWD regenWD = RegenWD.get();

                        MVWorldManager worldManager = regenWD.getMultiverseCore().getMVWorldManager();

                        if (worldManager.unloadWorld(worldName, true)) {

                            Bukkit.getLogger().info("Successfully unloaded world " + worldName);

                            File file = new File(worldName);

                            try {
                                FileUtils.deleteDirectory(file);
                                file.mkdir();
                                copyDirectory(folderSave + File.separator + worldName, worldName);
                            } catch (IOException ignored) {

                            }

                            boolean loadWorld = worldManager.loadWorld(worldName);

                            ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
                            for (String command : commands)
                                Bukkit.dispatchCommand(console, command);

                            if (loadWorld)
                                Bukkit.broadcastMessage(
                                        "§7[§c§lES§7] Le monde §a" + worldNameBroadcast + " §7vient d'être régénéré ! " + extraMessageBroadcast);

                        }


                    }
                }
            }
        }
        return false;
    }

    public void copyDirectory(String sourceDirectoryLocation, String destinationDirectoryLocation) throws IOException {
        Files.walk(Paths.get(sourceDirectoryLocation)).forEach(source -> {
            Path destination = Paths.get(destinationDirectoryLocation,
                    source.toString().substring(sourceDirectoryLocation.length()));
            try {
                Files.copy(source, destination);
            } catch (IOException ignored) {
            }
        });
    }

}
