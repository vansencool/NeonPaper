package net.vansen.neonpaper.auto;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.vansen.fursconfig.FursConfig;
import net.vansen.fursconfig.lang.Node;
import net.vansen.neonpaper.NeonPaper;
import net.vansen.neonpaper.command.NeonCommand;
import net.vansen.neonpaper.region.Regions;
import net.vansen.neonpaper.util.TrioValue;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("BusyWait")
public class AutoRegeneration {

    private static final Map<String, FursConfig> active = new HashMap<>();
    private static final Map<String, Long> next = new HashMap<>();
    private static final Map<String, Set<Long>> sentTicks = new HashMap<>();
    private static boolean started = false;

    public static void start() {
        FursConfig config = FursConfig.createAndParseFile(NeonPaper.CONFIG_FILE);
        active.clear();
        next.clear();
        sentTicks.clear();

        for (Map.Entry<String, Object> entry : config.getNode("regeneration.arena").children.entrySet()) {
            if (active.containsKey("regen." + entry.getKey()) || next.containsKey("regen." + entry.getKey())) {
                NeonPaper.LOGGER.warn("Duplicate regeneration configuration for arena '{}', skipping...", entry.getKey());
                continue;
            }
            FursConfig arenaCfg;
            try {
                arenaCfg = FursConfig.from((Node) entry.getValue());
            } catch (Exception e) {
                NeonPaper.LOGGER.error("Could not parse regeneration configuration for arena '{}', skipping...", entry.getKey(), e);
                continue;
            }
            if (!arenaCfg.getBoolean("enabled")) continue;
            if (!Regions.exists(arenaCfg.getString("snapshot"))) {
                NeonPaper.LOGGER.warn("Auto regeneration for arena '{}' is enabled, but the snapshot '{}' does not exist.", entry.getKey(), arenaCfg.getString("snapshot"));
                continue;
            }

            active.put("regen." + entry.getKey(), arenaCfg);
            next.put("regen." + entry.getKey(), System.currentTimeMillis() + time(arenaCfg.getFursConfig("time")) * 50L);
        }
        if (!started) {
            new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    long now = System.currentTimeMillis();
                    for (String arenaId : new HashSet<>(active.keySet())) {
                        long runAt = next.getOrDefault(arenaId, 0L);
                        FursConfig cfg = active.get(arenaId);
                        if (cfg == null) continue;

                        FursConfig ticking = cfg.getFursConfig("ticking");
                        if (ticking.getBoolean("enabled", false)) {
                            long secondsLeft = (runAt - now) / 1000L;
                            int tickingValue = ticking.getInt("value");

                            if (secondsLeft > 0 && secondsLeft <= tickingValue) {
                                sentTicks.putIfAbsent(arenaId, new HashSet<>());
                                if (!sentTicks.get(arenaId).contains(secondsLeft)) {
                                    sentTicks.get(arenaId).add(secondsLeft);

                                    ticking(
                                        MiniMessage.miniMessage().deserialize(
                                            String.join("<newline>", ticking.getList("message", String.class)),
                                            Placeholder.parsed("count", String.valueOf(secondsLeft))
                                        ), ticking.getString("type"), ticking.getString("to"), cfg);
                                }
                            }
                        }

                        if (now >= runAt) {
                            regenerate(cfg);
                            long intervalMs = time(cfg.getFursConfig("time")) * 50L;
                            next.put(arenaId, now + intervalMs);
                            sentTicks.remove(arenaId);
                        }
                    }

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "arena-regen-scheduler").start();
            started = true;
        }
    }

    public static void remove(@NotNull String arenaId) {
        active.remove("regen." + arenaId);
        next.remove("regen." + arenaId);
        sentTicks.remove("regen." + arenaId);
    }

    private static void regenerate(@NotNull FursConfig arenaCfg) {
        CompletableFuture.runAsync(() -> {
            TrioValue<BlockPos, BlockPos, World> trioValue = NeonCommand.paste(Bukkit.getConsoleSender(), arenaCfg.getString("snapshot"));
            if (trioValue == null) {
                NeonPaper.LOGGER.error("Auto regeneration failed: Could not paste snapshot '{}'.", arenaCfg.getString("snapshot"));
                return;
            }

            MinecraftServer.getServer().executor.execute(() -> {
                broadcast(arenaCfg.getFursConfig("broadcast"), trioValue);
                sounds(arenaCfg.getFursConfig("sounds"), trioValue);
            });
        });
    }

    private static void broadcast(@NotNull FursConfig cfg, @NotNull TrioValue<BlockPos, BlockPos, World> trioValue) {
        String type = cfg.getString("type", "broadcast");
        Component msg = MiniMessage.miniMessage().deserialize(String.join("<newline>", cfg.getList("message", String.class)));

        switch (cfg.getString("to").toLowerCase()) {
            case "global" -> sendMessage(type, msg, Bukkit.getOnlinePlayers());
            case "world" -> sendMessage(type, msg, trioValue.third().getPlayers());
            case "arena" -> sendMessage(type, msg, trioValue.third().getPlayers().stream()
                .filter(p -> Regions.isIn(p.getLocation(), trioValue.first(), trioValue.second()))
                .toList());
        }
    }

    private static void sounds(@NotNull FursConfig cfg, @NotNull TrioValue<BlockPos, BlockPos, World> trioValue) {
        Sound sound;
        try {
            // noinspection deprecation
            sound = Sound.valueOf(cfg.getString("sound").toUpperCase());
        } catch (IllegalArgumentException e) {
            NeonPaper.LOGGER.warn("Invalid sound '{}', skipping...", cfg.getString("sound")); return;
        }

        float volume = (float) cfg.getDouble("volume", 1.0);
        float pitch = (float) cfg.getDouble("pitch", 1.0);

        switch (cfg.getString("to").toLowerCase()) {
            case "global" -> Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), sound, volume, pitch));
            case "world" ->
                trioValue.third().getPlayers().forEach(p -> p.playSound(p.getLocation(), sound, volume, pitch));
            case "arena" -> trioValue.third().getPlayers().stream()
                .filter(p -> Regions.isIn(p.getLocation(), trioValue.first(), trioValue.second()))
                .forEach(p -> p.playSound(p.getLocation(), sound, volume, pitch));
        }
    }

    private static void sendMessage(@NotNull String type, @NotNull Component msg, @NotNull Collection<? extends Player> players) {
        switch (type.toUpperCase()) {
            case "MESSAGE" -> players.forEach(p -> p.sendMessage(msg));
            case "ACTIONBAR", "ACTION_BAR" -> players.forEach(p -> p.sendActionBar(msg));
            case "BOTH" -> {
                players.forEach(p -> p.sendMessage(msg));
                players.forEach(p -> p.sendActionBar(msg));
            }
            default -> {
                NeonPaper.LOGGER.warn("Invalid broadcast type '{}', defaulting to MESSAGE.", type);
                players.forEach(p -> p.sendMessage(msg));
            }
        }
    }

    private static long time(@NotNull FursConfig timeCfg) {
        String unit = timeCfg.getString("time_unit").toUpperCase();
        long duration = timeCfg.getLong("duration");

        return switch (unit) {
            case "M" -> duration * 20L * 60L;
            case "H" -> duration * 20L * 60L * 60L;
            case "D" -> duration * 20L * 60L * 60L * 24L;
            default -> duration * 20L;
        };
    }

    private static void ticking(Component comp, @NotNull String type, String target, FursConfig cfg) {
        TrioValue<BlockPos, BlockPos, World> trio = Regions.metadata(cfg.getString("snapshot"));
        if (trio == null) return;

        switch (target.toLowerCase()) {
            case "global" -> sendMessage(type, comp, Bukkit.getOnlinePlayers());
            case "world" -> sendMessage(type, comp, trio.third().getPlayers());
            case "arena" -> sendMessage(type, comp, trio.third().getPlayers().stream()
                .filter(p -> Regions.isIn(p.getLocation(), trio.first(), trio.second()))
                .toList());
        }
    }
}
