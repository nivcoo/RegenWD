package fr.nivcoo.regenwd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.mvplugins.multiverse.core.MultiverseCore;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.UnloadWorldOptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class RegenWDCommands implements CommandExecutor {

    private static final String SAVE_FOLDER = "DimensionSaves";
    private static final String OVERWORLD_NAME = "world";

    private void help(CommandSender sender) {
        if (sender.hasPermission("regenwd.commands")) {
            sender.sendMessage(Component.text("------------------", NamedTextColor.GRAY)
                    .append(Component.text("[", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Menu d'aide", NamedTextColor.GOLD))
                    .append(Component.text("]", NamedTextColor.DARK_GRAY))
                    .append(Component.text("------------------", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/regenwd end ", NamedTextColor.GOLD)
                    .append(Component.text("pour regen l'end", NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("/regenwd nether ", NamedTextColor.GOLD)
                    .append(Component.text("pour regen le nether", NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("----------------------------------------------", NamedTextColor.GRAY));
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

        WorldTarget target;
        List<String> commands = new ArrayList<>(List.of("dh reload"));

        switch (args[0].toLowerCase()) {
            case "end" -> target = new WorldTarget(
                    World.Environment.THE_END,
                    "the_end",
                    "End"
            );
            case "nether" -> {
                target = new WorldTarget(
                        World.Environment.NETHER,
                        "the_nether",
                        "Nether"
                );
                commands.add("netherportal reload");
            }
            default -> {
                help(sender);
                return true;
            }
        }

        World bukkitWorld = findWorld(target);
        if (bukkitWorld == null) {
            sender.sendMessage(error("Dimension introuvable : " + target.dimensionFolder() + " (" + target.environment() + ")"));
            return true;
        }

        FileRestoreTarget restoreTarget = findRestoreTarget(target);
        if (restoreTarget == null) {
            sender.sendMessage(error("Dossier source introuvable : " + SAVE_FOLDER + "/" + target.dimensionFolder()));
            return true;
        }

        List<Player> playersToTeleport = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getWorld().equals(bukkitWorld))
                .map(player -> (Player) player)
                .toList();

        for (Player player : playersToTeleport) {
            Bukkit.getScheduler().runTask(RegenWD.get(), () -> {
                player.sendMessage(prefix().append(Component.text(" Le monde se régénère ! Vous avez été téléporté au spawn.", NamedTextColor.GRAY)));
                player.performCommand("spawn");
            });
        }

        RegenWD plugin = RegenWD.get();
        MultiverseCore mvCore = plugin.getMultiverseCore();
        Bukkit.getScheduler().runTaskLater(plugin, () -> unloadRestoreAndLoad(sender, mvCore, bukkitWorld, restoreTarget, target, commands), 20L);

        return true;
    }

    private World findWorld(WorldTarget target) {
        return Bukkit.getWorlds().stream()
                .filter(loaded -> loaded.getEnvironment() == target.environment())
                .findFirst()
                .orElse(null);
    }

    private FileRestoreTarget findRestoreTarget(WorldTarget target) {
        Path source = findSourcePath(target);
        if (source == null) return null;

        Path destination = findDestinationPath(target);
        if (destination == null) return null;

        return new FileRestoreTarget(source, destination);
    }

    private Path findSourcePath(WorldTarget target) {
        Path directDimensionSource = Paths.get(SAVE_FOLDER, target.dimensionFolder());

        if (Files.isDirectory(directDimensionSource)) return directDimensionSource;

        return null;
    }

    private Path findDestinationPath(WorldTarget target) {
        return Paths.get(OVERWORLD_NAME, "dimensions", "minecraft", target.dimensionFolder());
    }

    private void unloadRestoreAndLoad(CommandSender sender, MultiverseCore mvCore, World bukkitWorld, FileRestoreTarget restoreTarget, WorldTarget target, List<String> commands) {
        String worldName = bukkitWorld.getName();
        WorldManager worldManager = mvCore == null ? null : mvCore.getApi().getWorldManager();
        LoadedMultiverseWorld loadedWorld = worldManager == null ? null : worldManager.getLoadedWorld(worldName).getOrNull();

        if (worldManager != null && loadedWorld != null) {
            UnloadWorldOptions options = UnloadWorldOptions
                    .world(loadedWorld)
                    .saveBukkitWorld(true)
                    .unloadBukkitWorld(true);

            worldManager.unloadWorld(options).onFailure(failure -> {
                sender.sendMessage(error("Échec du déchargement : " + failure));
            }).onSuccess(success -> {
                if (!restoreWorld(sender, restoreTarget)) return;

                worldManager.loadWorld(worldName).onFailure(failure -> {
                    sender.sendMessage(error("Erreur lors du rechargement : " + failure));
                }).onSuccess(loaded -> finishRegen(target.broadcastName(), commands));
            });
            return;
        }

        if (!Bukkit.unloadWorld(bukkitWorld, true)) {
            sender.sendMessage(error("Impossible de décharger le monde : " + worldName));
            return;
        }

        if (!restoreWorld(sender, restoreTarget)) return;

        if (Bukkit.createWorld(new WorldCreator(worldName).environment(bukkitWorld.getEnvironment())) == null) {
            sender.sendMessage(error("Erreur lors du rechargement : " + worldName));
            return;
        }

        finishRegen(target.broadcastName(), commands);
    }

    private boolean restoreWorld(CommandSender sender, FileRestoreTarget restoreTarget) {
        try {
            Bukkit.getLogger().info("RegenWD restore: " + restoreTarget.sourcePath() + " -> " + restoreTarget.destinationPath());
            copyDirectory(restoreTarget.sourcePath(), restoreTarget.destinationPath());
            return true;
        } catch (IOException | UncheckedIOException e) {
            sender.sendMessage(error("Erreur lors de la restauration du monde : " + e.getMessage()));
            e.printStackTrace();
            return false;
        }
    }

    private void finishRegen(String worldNameBroadcast, List<String> commands) {
        Bukkit.broadcast(prefix()
                .append(Component.text(" Le monde ", NamedTextColor.GRAY))
                .append(Component.text(worldNameBroadcast, NamedTextColor.GREEN))
                .append(Component.text(" vient d'être régénéré !", NamedTextColor.GRAY)));
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        for (String command : commands) {
            Bukkit.dispatchCommand(console, command);
        }
    }

    private void copyDirectory(Path sourcePath, Path destinationPath) throws IOException {
        if (!Files.exists(sourcePath)) {
            throw new IOException("Dossier source introuvable : " + sourcePath);
        }

        FileUtils.forceMkdir(destinationPath.toFile());

        try (Stream<Path> files = Files.walk(destinationPath)) {
            files.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(destinationPath))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException("Impossible de supprimer : " + path, e);
                        }
                    });
        }

        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.forEach(source -> {
                Path destination = destinationPath.resolve(sourcePath.relativize(source));
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Erreur lors de la copie de " + source, e);
                }
            });
        }
    }

    private Component prefix() {
        return Component.text("[", NamedTextColor.GRAY)
                .append(Component.text("ES", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("]", NamedTextColor.GRAY));
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private record WorldTarget(World.Environment environment, String dimensionFolder, String broadcastName) {
    }

    private record FileRestoreTarget(Path sourcePath, Path destinationPath) {
    }
}
