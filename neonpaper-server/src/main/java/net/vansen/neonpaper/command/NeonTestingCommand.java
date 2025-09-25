package net.vansen.neonpaper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.papermc.paper.util.MCUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.vansen.neonpaper.NeonPaper;
import net.vansen.neonpaper.chunk.SnappedChunk;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class NeonTestingCommand {

    public static final Map<ChunkPos, SnappedChunk> snapshots = new HashMap<>();

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        NeonPaper.register(dispatcher, Commands.literal("neontest")
            .requires(stack -> stack.getBukkitSender().hasPermission("neontest.use"))
            .then(Commands.literal("simple")
                .then(Commands.literal("snapshot")
                    .then(Commands.literal("take")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                            .executes(context -> {
                                CommandSender sender = context.getSource().getBukkitSender();
                                if (!(sender instanceof Player player)) {
                                    sender.sendRichMessage("<red>Only players can execute this command.");
                                    return 0;
                                }
                                snapshot(player, context.getArgument("radius", Integer.class));
                                return 1;
                            })))
                    .then(Commands.literal("place")
                        .executes(context -> {
                            CommandSender sender = context.getSource().getBukkitSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendRichMessage("<red>Only players can execute this command.");
                                return 0;
                            }
                            place(player);
                            return 1;
                        }))))
            .then(Commands.literal("benchmark")
                .then(Commands.literal("backandforth")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            CommandSender sender = context.getSource().getBukkitSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage("<red>Only players can execute this command.");
                                return 0;
                            }
                            runBenchmark(player, IntegerArgumentType.getInteger(context, "radius"), 5);
                            return 1;
                        })
                        .then(Commands.argument("runs", IntegerArgumentType.integer(1))
                            .then(Commands.argument("warmup", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getBukkitSender();
                                    if (!(sender instanceof Player player)) {
                                        sender.sendMessage("<red>Only players can execute this command.");
                                        return 0;
                                    }
                                    int radius = context.getArgument("radius", Integer.class);
                                    int chunks = (radius * 2 + 1) * (radius * 2 + 1);
                                    long blocks = (long) chunks * 16 * 16 * 381;

                                    player.sendMessage(Component.text(
                                        "Are you sure you want to run this benchmark? It will FULLY override one of your chunks with air (far away from your current location), " +
                                            "so make sure this is just a test world!\n" +
                                            "This will snapshot " + chunks + " chunks (" + format(blocks) + " blocks).",
                                        TextColor.fromHexString("#ff4f7e")
                                    ).clickEvent(ClickEvent.callback(ignored ->
                                        runBenchmark(player, radius, context.getArgument("runs", Integer.class))
                                    )));
                                    return 1;
                                })))))), "testing");
    }


    private static void snapshot(@NotNull Player player, int radius) {
        ServerLevel level = ((CraftWorld) player.getWorld()).getHandle();
        ChunkPos center = ((CraftChunk) player.getChunk()).getHandle(ChunkStatus.FULL).getPos();

        snapshots.clear();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                snapshots.put(new ChunkPos(chunkX, chunkZ), chunk.snap());
            }
        }

        player.sendRichMessage("<green>Processed " + snapshots.size() + " chunks");
    }

    private static void place(@NotNull Player player) {
        ServerLevel level = ((CraftWorld) player.getWorld()).getHandle();

        for (Map.Entry<ChunkPos, SnappedChunk> entry : snapshots.entrySet()) {
            ChunkPos pos = entry.getKey();
            level.setChunkAt(pos.x, pos.z, entry.getValue());
            player.getWorld().refreshChunk(pos.x, pos.z);
        }

        player.sendRichMessage("<green>Restored " + snapshots.size() + " chunks, and around " +
            format((long) snapshots.size() * 16 * 16 * 381) + " blocks");
    }

    private static void runBenchmark(@NotNull Player player, int radius, int runs) {
        CompletableFuture.runAsync(() -> {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            ServerLevel level = ((CraftWorld) player.getWorld()).getHandle();
            int originCX = sp.blockPosition().getX() >> 4;
            int originCZ = sp.blockPosition().getZ() >> 4;

            List<LevelChunk> chunksToUse = new ArrayList<>();
            Map<String, SnappedChunk> cleanSnapshots = new HashMap<>();
            DistanceManager dm = level.getChunkSource().chunkMap.distanceManager;

            int chunkCount = 0;
            int total = (radius * 2 + 1) * (radius * 2 + 1);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int cx = originCX + dx;
                    int cz = originCZ + dz;
                    dm.ticketStorage.addTicket(new Ticket<>(TicketType.REGEN, 0), new ChunkPos(cx, cz));
                    LevelChunk ca = level.getChunk(cx, cz);
                    chunksToUse.add(ca);
                    cleanSnapshots.put(cx + "," + cz, ca.snap());
                    chunkCount++;
                    if (chunkCount % 300 == 0) {
                        player.sendRichMessage(
                            "<gray>Loaded <green>" + chunkCount + "</green> chunks, " +
                                "<gray>left <red>" + (total - chunkCount) + "</red>, " +
                                "<gray><yellow>" + (int) (chunkCount * 100.0 / total) + "%</yellow> done"
                        );
                    }
                }
            }

            Chunk chunkAir = player.getWorld().getChunkAt(originCX + ThreadLocalRandom.current().nextInt(1581, 90000), originCZ + ThreadLocalRandom.current().nextInt(3142, 90000));
            CompletableFuture.runAsync(() -> {
                World world = player.getWorld();
                int chunkX = chunkAir.getX() * 16;
                int chunkZ = chunkAir.getZ() * 16;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                            Block block = world.getBlockAt(chunkX + x, y, chunkZ + z);
                            block.setType(Material.AIR, false);
                        }
                    }
                }
            }, MCUtil.MAIN_EXECUTOR).join();

            SnappedChunk airSnapshot = ((CraftChunk) chunkAir).getHandle(ChunkStatus.FULL).snap();

            player.sendRichMessage(
                "<gray>Running benchmark for <green>" + runs + "</green> runs with " +
                    "<gold>" + chunksToUse.size() + "</gold> chunks (" +
                    format((long) chunksToUse.size() * 16 * 16 * 381) +
                    " blocks)…"
            );

            AtomicLong totalRestore = new AtomicLong();
            try {
                for (int i = 0; i < runs; i++) {
                    for (LevelChunk chunk : chunksToUse) {
                        Level.setChunkAt(chunk, airSnapshot);
                        player.getWorld().refreshChunk(chunk.getPos().x, chunk.getPos().z);
                    }
                    Thread.sleep(2000);

                    long start = System.nanoTime();
                    for (LevelChunk chunk : chunksToUse)
                        Level.setChunkAt(chunk, cleanSnapshots.get(chunk.getPos().x + "," + chunk.getPos().z));
                    long end = System.nanoTime();

                    totalRestore.addAndGet(end - start);
                    System.out.println("Restored " + chunksToUse.size() + " chunks in " + (end - start) / 1_000_000.0 + "ms");

                    for (LevelChunk chunk : chunksToUse)
                        player.getWorld().refreshChunk(chunk.getPos().x, chunk.getPos().z);
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                NeonPaper.LOGGER.error("Benchmark failed", e);
            }
            double totalMs = totalRestore.get() / 1_000_000.0;
            player.sendRichMessage("<green>Benchmark complete!</green>");
            player.sendRichMessage("");
            player.sendRichMessage("<#c2d8ff>Restore avg: <yellow>" + String.format("%.2f", (double) totalRestore.get() / runs / 1_000_000.0) + "</yellow> ms");
            player.sendRichMessage("<gray>Per-chunk time: <white>" + String.format("%.3f", (double) totalRestore.get() / runs / chunksToUse.size() / 1_000_000.0) + "</white> ms");
            player.sendRichMessage("");
            player.sendRichMessage("<gray>Total time: <white>" + String.format("%.2f", totalMs) + "</white> ms");
        });
    }

    public static String format(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.2f %s", number / 1_000_000_000.0, "billion");
        } else if (number >= 1_000_000) {
            return String.format("%.2f %s", number / 1_000_000.0, "million");
        } else if (number >= 1_000) {
            return String.format("%.0f %s", number / 1_000.0, "thousand");
        } else {
            return String.valueOf(number);
        }
    }
}
