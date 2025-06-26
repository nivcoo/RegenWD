package fr.nivcoo.regenwd;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.mvplugins.multiverse.core.MultiverseCore;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.UnloadWorldOptions;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class RegenWDCommands implements CommandExecutor {

    private void help(CommandSender sender) {
        if (sender.hasPermission("regenwd.commands")) {
            sender.sendMessage(ChatColor.GRAY + "§m------------------" + ChatColor.DARK_GRAY + "[" + ChatColor.GOLD
                    + "Menu d'aide" + ChatColor.DARK_GRAY + "]" + ChatColor.GRAY + "§m------------------");
            sender.sendMessage(ChatColor.GOLD + "/regenwd end " + ChatColor.YELLOW + "pour regen l'end");
            sender.sendMessage(ChatColor.GOLD + "/regenwd nether " + ChatColor.YELLOW + "pour regen le nether");
            sender.sendMessage(ChatColor.GOLD + "/regenwd resources " + ChatColor.YELLOW + "pour regen le monde ressource");
            sender.sendMessage(ChatColor.GOLD + "/regenwd resources_2 " + ChatColor.YELLOW + "pour regen le monde ressource amiral");
            sender.sendMessage(ChatColor.GRAY + "§m----------------------------------------------");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String msg, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("regenwd") || !sender.hasPermission("regenwd.commands")) {
            return false;
        }

        if (args.length == 0) {
            help(sender);
            return true;
        }

        String worldName;
        String worldNameBroadcast;
        String folderSave = "WorldSaves";
        List<String> commands = new ArrayList<>(List.of("hd reload"));

        switch (args[0].toLowerCase()) {
            case "end" -> {
                worldName = "world_the_end";
                worldNameBroadcast = "End";
            }
            case "nether" -> {
                worldName = "world_nether";
                worldNameBroadcast = "Nether";
                commands.add("netherportal reload");
            }
            case "resources" -> {
                worldName = "ressources";
                worldNameBroadcast = "Ressources";
            }
            case "resources_2" -> {
                worldName = "ressources_2";
                worldNameBroadcast = "Ressources_2";
            }
            default -> {
                help(sender);
                return true;
            }
        }

        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            sender.sendMessage(ChatColor.RED + "Monde introuvable : " + worldName);
            return true;
        }


        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getWorld().equals(bukkitWorld))
                .forEach(player -> {
                    player.sendMessage("§7[§c§lES§7] Le monde se régénère ! Vous avez été téléporté sur votre île !");
                    player.performCommand("is go");
                });


        RegenWD plugin = RegenWD.get();
        MultiverseCore mvCore = plugin.getMultiverseCore();
        if (mvCore == null) {
            sender.sendMessage(ChatColor.RED + "Multiverse-Core n'est pas chargé !");
            return true;
        }

        WorldManager worldManager = mvCore.getApi().getWorldManager();
        LoadedMultiverseWorld loadedWorld = worldManager.getLoadedWorld(worldName).getOrNull();

        if (loadedWorld == null) {
            sender.sendMessage(ChatColor.RED + "Le monde n'est pas chargé via Multiverse !");
            return true;
        }

        UnloadWorldOptions options = UnloadWorldOptions
                .world(loadedWorld)
                .saveBukkitWorld(true)
                .unloadBukkitWorld(true);

        String finalWorldName = worldName;
        worldManager.unloadWorld(options).onFailure(failure -> {
            sender.sendMessage(ChatColor.RED + "Échec du déchargement : " + failure);
        }).onSuccess(success -> {
            Bukkit.getLogger().info("Monde " + finalWorldName + " déchargé avec succès.");

            File worldFolder = new File(finalWorldName);
            try {
                FileUtils.deleteDirectory(worldFolder);
                worldFolder.mkdir();
                copyDirectory(folderSave + File.separator + finalWorldName, finalWorldName);
            } catch (IOException e) {
                sender.sendMessage(ChatColor.RED + "Erreur lors de la restauration du monde : " + e.getMessage());
                e.printStackTrace();
                return;
            }

            worldManager.loadWorld(finalWorldName).onFailure(failure -> {
                sender.sendMessage(ChatColor.RED + "Erreur lors du rechargement : " + failure);
            }).onSuccess(loaded -> {
                Bukkit.broadcastMessage("§7[§c§lES§7] Le monde §a" + worldNameBroadcast + " §7vient d'être régénéré !");
                ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
                for (String command : commands) {
                    Bukkit.dispatchCommand(console, command);
                }
            });
        });

        return true;
    }

    private void copyDirectory(String sourceDirectoryLocation, String destinationDirectoryLocation) throws IOException {
        Path sourcePath = Paths.get(sourceDirectoryLocation);
        Path destinationPath = Paths.get(destinationDirectoryLocation);

        if (!Files.exists(sourcePath)) {
            throw new IOException("Dossier source introuvable : " + sourcePath);
        }

        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.forEach(source -> {
                Path destination = destinationPath.resolve(sourcePath.relativize(source));
                try {
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

}
