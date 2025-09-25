package net.vansen.neonpaper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.vansen.neonpaper.NeonPaper;
import net.vansen.neonpaper.auto.AutoRegeneration;
import net.vansen.neonpaper.chunk.SnappedChunk;
import net.vansen.neonpaper.config.ConfigVariables;
import net.vansen.neonpaper.regeneration.PendingChunks;
import net.vansen.neonpaper.region.Regions;
import net.vansen.neonpaper.util.TrioValue;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NeonCommand {
    public static final File SNAP_DIR = new File("neon_snapshots");
    public static final File BACKUP_DIR = new File(SNAP_DIR, "nbtbackups");
    private static final HashMap<String, CachedSnapshot> SNAP_CACHE = new HashMap<>();

    public static final Codec<ChunkPos> CHUNKPOS_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("x").forGetter(p -> p.x),
        Codec.INT.fieldOf("z").forGetter(p -> p.z)
    ).apply(i, ChunkPos::new));

    public record SnappedEntry(ChunkPos pos, SnappedChunk chunk) {
        public static final Codec<SnappedEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            CHUNKPOS_CODEC.fieldOf("pos").forGetter(SnappedEntry::pos),
            SnappedChunk.CODEC.fieldOf("chunk").forGetter(SnappedEntry::chunk)
        ).apply(i, SnappedEntry::new));
    }

    public record CachedSnapshot(List<SnappedEntry> entries, BlockPos from, BlockPos to, String world) {
    }

    private static final Map<UUID, BlockPos> pos1Map = new HashMap<>();
    private static final Map<UUID, BlockPos> pos2Map = new HashMap<>();

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        NeonPaper.register(dispatcher, command("neonpaper"), "neonpaper");
        NeonPaper.register(dispatcher, command("np"), "neonpaper");
        NeonPaper.register(dispatcher, command("neon"), "neonpaper");
        NeonPaper.register(dispatcher, command("neonp"), "neonpaper");

    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String cname) {
        return Commands.literal(cname)
                .requires(stack -> stack.getBukkitSender().hasPermission("neonpaper.use"))

                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            NeonPaper.load();
                            AutoRegeneration.start();
                            ctx.getSource().getBukkitSender().sendRichMessage("<#80d0ff>NeonPaper configuration reloaded.");
                            return 1;
                        }))

                .then(Commands.literal("inside")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Regions.snapshots()
                                            .stream()
                                            .filter(s -> s.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getBukkitSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendRichMessage("<#ff388b>[neonpaper inside] Only players.");
                                        return 0;
                                    }

                                    String name = StringArgumentType.getString(ctx, "name");
                                    TrioValue<BlockPos, BlockPos, World> meta = Regions.metadata(name);
                                    if (meta == null) {
                                        s.sendRichMessage("<#ff388b>No metadata for that region.");
                                        return 0;
                                    }

                                    if (!p.getWorld().getName().equals(meta.third().getName())) {
                                        s.sendRichMessage("<#ff388b>You are not in the correct world.");
                                        return 0;
                                    }

                                    BlockPos min = meta.first();
                                    BlockPos max = meta.second();

                                    if (Regions.isIn(p.getLocation(), min, max)) {
                                        s.sendRichMessage("<#80d0ff>You are inside of the region '" + name + "'");
                                    } else {
                                        s.sendRichMessage("<#ff388b>You are outside of the region '" + name + "'");
                                        p.sendMessage(Component.text("Click here to teleport to the center of " + name + "!", TextColor.fromHexString("#80D0FF"))
                                                .clickEvent(ClickEvent.runCommand("/neonpaper tpcenter " + name))
                                                .hoverEvent(HoverEvent.showText(
                                                        Component.text("Teleport to the center of " + name, TextColor.fromHexString("#80D0FF"))
                                                )));
                                    }

                                    return 1;
                                })))

                .then(Commands.literal("flushmap").executes(ctx -> {
                    CompletableFuture.runAsync(() -> {
                        long start = System.nanoTime();
                        PendingChunks.flush();
                        long timeMs = (System.nanoTime() - start) / 1_000_000;
                        ctx.getSource().getBukkitSender().sendRichMessage("<#a1ceff>Flushed pending chunks in " + timeMs + "ms");
                    });
                    return 1;
                }))

                .then(Commands.literal("loadmap").executes(ctx -> {
                    CompletableFuture.runAsync(() -> {
                        long start = System.nanoTime();
                        PendingChunks.load();
                        long timeMs = (System.nanoTime() - start) / 1_000_000;
                        ctx.getSource().getBukkitSender().sendRichMessage("<#a1ceff>Loaded pending chunks in " + timeMs + "ms");
                    });
                    return 1;
                }))

                .then(Commands.literal("tpcenter")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Regions.snapshots()
                                            .stream()
                                            .filter(s -> s.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getBukkitSender();
                                    if (!(s instanceof Player p)) {
                                        s.sendRichMessage("<#ff388b>[neonpaper tpcenter] Only players can use this command.");
                                        return 0;
                                    }

                                    String name = StringArgumentType.getString(ctx, "name");
                                    TrioValue<BlockPos, BlockPos, World> meta = Regions.metadata(name);
                                    if (meta == null) return 0;

                                    BlockPos min = meta.first();
                                    BlockPos max = meta.second();

                                    p.teleport(new Location(meta.third(),
                                            (min.getX() + max.getX()) / 2.0 + 0.5,
                                            (min.getY() + max.getY()) / 2.0,
                                            (min.getZ() + max.getZ()) / 2.0 + 0.5
                                    ));
                                    p.sendRichMessage("<#80d0ff>Teleported to the center of region " + name);
                                    return 1;
                                })))

                .then(Commands.literal("insideregions")
                        .executes(ctx -> {
                            CommandSender s = ctx.getSource().getBukkitSender();
                            if (!(s instanceof Player p)) {
                                s.sendRichMessage("<#ff388b>[neonpaper insideregions] Only players can use this command.");
                                return 0;
                            }

                            List<String> insideRegions = new ArrayList<>();
                            for (String name : Regions.snapshots()) {
                                TrioValue<BlockPos, BlockPos, World> meta = Regions.metadata(name);
                                if (meta == null) continue;
                                if (!p.getWorld().getName().equals(meta.third().getName())) continue;

                                if (Regions.isIn(p.getLocation(), meta.first(), meta.second())) {
                                    insideRegions.add(name);
                                }
                            }

                            if (insideRegions.isEmpty()) {
                                p.sendRichMessage("<#ff388b>You are not inside any region.");
                                return 1;
                            }

                            p.sendRichMessage("<#80d0ff>You are inside the following regions:");
                            for (String name : insideRegions) {
                                TrioValue<BlockPos, BlockPos, World> meta = Regions.metadata(name);
                                if (meta == null) continue;

                                p.sendMessage(Component.text("- " + name, TextColor.fromHexString("#80D0FF"))
                                        .clickEvent(ClickEvent.runCommand("/neonpaper tpcenter " + name))
                                        .hoverEvent(HoverEvent.showText(
                                                Component.text("Teleport to the center of " + name, TextColor.fromHexString("#80D0FF"))
                                        )));
                            }

                            return 1;
                        }))

                .then(Commands.literal("region")
                        .then(Commands.literal("pos")
                                .then(Commands.literal("1")
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getBukkitSender();
                                            if (!(s instanceof Player p)) {
                                                s.sendRichMessage("<#ff388b>[neonpaper region pos 1] Only players.");
                                                return 0;
                                            }
                                            return pos(s, 1, p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
                                        })
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .executes(ctx -> pos(
                                                                        ctx.getSource().getBukkitSender(),
                                                                        1,
                                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                                        IntegerArgumentType.getInteger(ctx, "z")
                                                                ))))))
                                .then(Commands.literal("2")
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getBukkitSender();
                                            if (!(s instanceof Player p)) {
                                                s.sendRichMessage("<#ff388b>[neonpaper region pos 2] Only players.");
                                                return 0;
                                            }
                                            return pos(s, 2, p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
                                        })
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .executes(ctx -> pos(
                                                                        ctx.getSource().getBukkitSender(),
                                                                        2,
                                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                                        IntegerArgumentType.getInteger(ctx, "z")
                                                                )))))))

                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getBukkitSender();
                                            if (!(s instanceof Player p)) {
                                                s.sendRichMessage("<#ff388b>[neonpaper region save] Only players.");
                                                return 0;
                                            }

                                            if (!pos1Map.containsKey(p.getUniqueId()) || !pos2Map.containsKey(p.getUniqueId())) {
                                                s.sendRichMessage("<#ff388b>Both positions must be set first using /neonpaper pos 1 and /neonpaper pos 2.");
                                                return 0;
                                            }

                                            CompletableFuture.runAsync(() -> save(p, pos1Map.get(p.getUniqueId()), pos2Map.get(p.getUniqueId()), StringArgumentType.getString(ctx, "name")));
                                            return 1;
                                        })))

                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            Regions.snapshots()
                                                    .stream()
                                                    .filter(s -> s.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            CommandSender s = ctx.getSource().getBukkitSender();
                                            String name = StringArgumentType.getString(ctx, "name");

                                            File nbtFile = new File(SNAP_DIR, name + ".nbt");
                                            File datFile = new File(SNAP_DIR, name + ".dat");

                                            SNAP_CACHE.remove(name);
                                            AutoRegeneration.remove(name);

                                            boolean existed = false;
                                            if (nbtFile.exists() && nbtFile.isFile()) {
                                                if (nbtFile.delete()) {
                                                    existed = true;
                                                } else {
                                                    s.sendRichMessage("<#ff388b>Could not delete snapshot file.");
                                                    return 0;
                                                }
                                            }

                                            if (datFile.exists() && datFile.isFile()) {
                                                if (datFile.delete()) {
                                                    existed = true;
                                                } else {
                                                    s.sendRichMessage("<#ff388b>Could not delete metadata file.");
                                                    return 0;
                                                }
                                            }

                                            if (existed) {
                                                s.sendRichMessage("<#80d0ff>Deleted snapshot " + name);
                                            } else {
                                                s.sendRichMessage("<#ff388b>No snapshot with that name.");
                                            }
                                            return 1;
                                        })))

                        .then(Commands.literal("paste")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            Regions.snapshots()
                                                    .stream()
                                                    .filter(s -> s.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            CompletableFuture.runAsync(() -> paste(ctx.getSource().getBukkitSender(), StringArgumentType.getString(ctx, "name")));
                                            return 1;
                                        }))));
    }

    private static int pos(@NotNull CommandSender sender, int slot, int x, int y, int z) {
        if (!(sender instanceof Player p)) {
            sender.sendRichMessage("<#ff388b>[neonpaper region pos " + slot + "] Only players.");
            return 0;
        }

        BlockPos newPos = new BlockPos(x, y, z);
        if (slot == 1) pos1Map.put(p.getUniqueId(), newPos);
        else pos2Map.put(p.getUniqueId(), newPos);

        if (pos1Map.containsKey(p.getUniqueId()) || pos2Map.containsKey(p.getUniqueId())) {
            BlockPos from = pos1Map.get(p.getUniqueId());
            BlockPos to = pos2Map.get(p.getUniqueId());
            if (from == null || to == null) {
                sender.sendActionBar(Component.text("Position " + slot + " set to " + newPos, TextColor.fromHexString("#80d0ff")));
                return 1;
            }
            int chunks = (Math.abs((from.getX() >> 4) - (to.getX() >> 4)) + 1) * (Math.abs((from.getZ() >> 4) - (to.getZ() >> 4)) + 1);
            sender.sendRichMessage("<#80d0ff>Position " + slot + " set to " + newPos + ". " + chunks + " chunks, and " + format((long) chunks * 16 * 16 * 381) + " blocks selected.");
        } else {
            sender.sendActionBar(Component.text("Position " + slot + " set to " + newPos, TextColor.fromHexString("#80d0ff")));
        }
        return 1;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void save(@NotNull Player player, @NotNull BlockPos from, @NotNull BlockPos to, @NotNull String name) {
        List<SnappedEntry> list = new ArrayList<>();

        long startLoad = System.currentTimeMillis();
        for (int cx = Math.min(from.getX() >> 4, to.getX() >> 4); cx <= Math.max(from.getX() >> 4, to.getX() >> 4); cx++) {
            for (int cz = Math.min(from.getZ() >> 4, to.getZ() >> 4); cz <= Math.max(from.getZ() >> 4, to.getZ() >> 4); cz++) {
                LevelChunk chunk = ((CraftWorld) player.getWorld()).getHandle().getChunk(cx, cz);
                list.add(new SnappedEntry(new ChunkPos(cx, cz), chunk.snap()));
            }
        }
        player.sendRichMessage("<#a1ceff>Loaded " + list.size() + " chunks in " + (System.currentTimeMillis() - startLoad) + "ms, now saving...");

        SNAP_DIR.mkdirs();

        long start = System.currentTimeMillis();
        File nbtFile = new File(SNAP_DIR, name + ".lnbt");
        try (DataOutputStream dos = new DataOutputStream(new LZ4FrameOutputStream(new FileOutputStream(nbtFile)))) {
            DataResult<Tag> encoded = SnappedEntry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, list);
            if (encoded.result().isEmpty()) {
                player.sendRichMessage("<#ff388b>Chunk encode failed: " + encoded.error().orElseThrow().message());
                return;
            }
            CompoundTag root = new CompoundTag();
            root.put("entries", encoded.result().get());
            NbtIo.write(root, dos);
        } catch (IOException e) {
            player.sendRichMessage("<#ff388b>Save failed: " + e.getMessage());
            NeonPaper.LOGGER.error("Could not save snapshot '{}'", name, e);
            return;
        }

        File datFile = new File(SNAP_DIR, name + ".dat");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(datFile))) {
            out.writeUTF(player.getWorld().getName());
            out.writeInt(from.getX());
            out.writeInt(from.getY());
            out.writeInt(from.getZ());
            out.writeInt(to.getX());
            out.writeInt(to.getY());
            out.writeInt(to.getZ());
        } catch (IOException e) {
            player.sendRichMessage("<#ff388b>Metadata save failed: " + e.getMessage());
            return;
        }

        player.sendRichMessage("<#a1ceff>Saved " + list.size() + " chunks to " + nbtFile.getName() + " in " + (System.currentTimeMillis() - start) + "ms");
    }

    public static @Nullable TrioValue<BlockPos, BlockPos, World> paste(@NotNull CommandSender sender, @NotNull String name) {
        File nbtFile = new File(SNAP_DIR, name + ".lnbt");
        File datFile = new File(SNAP_DIR, name + ".dat");

        if (!nbtFile.exists() || !datFile.exists()) {
            sender.sendRichMessage("<red>No snapshot or metadata for " + name);
            return null;
        }

        CachedSnapshot snapshot;
        long startRead = System.currentTimeMillis();

        if (SNAP_CACHE.containsKey(name)) {
            snapshot = SNAP_CACHE.get(name);
            sender.sendMessage("");
            sender.sendRichMessage("<#a1ceff>Using cached snapshot " + name + " with " + snapshot.entries().size() + " chunks.");
        } else {
            List<SnappedEntry> entries;
            try (DataInputStream in = new DataInputStream(new LZ4FrameInputStream(new FileInputStream(nbtFile)))) {
                CompoundTag root = NbtIo.read(in, NbtAccounter.unlimitedHeap());
                Tag tag = root.get("entries");
                if (!(tag instanceof ListTag)) {
                    sender.sendRichMessage("<#ff388b>Invalid NBT format: 'entries' is not a list");
                    return null;
                }
                DataResult<List<SnappedEntry>> decoded = SnappedEntry.CODEC.listOf().parse(NbtOps.INSTANCE, tag);
                if (decoded.result().isEmpty()) {
                    sender.sendRichMessage("<#ff388b>Chunk decode failed: " + decoded.error().orElseThrow().message());
                    return null;
                }
                entries = decoded.result().get();
            } catch (IOException e) {
                sender.sendRichMessage("<#ff388b>Failed to read chunks: " + e.getMessage());
                NeonPaper.LOGGER.error("Could not load snapshot '{}'", name, e);
                return null;
            }

            BlockPos from, to;
            String worldName;
            try (DataInputStream in = new DataInputStream(new FileInputStream(datFile))) {
                worldName = in.readUTF();
                from = new BlockPos(in.readInt(), in.readInt(), in.readInt());
                to = new BlockPos(in.readInt(), in.readInt(), in.readInt());
            } catch (IOException e) {
                sender.sendRichMessage("<#ff388b>Failed to read metadata: " + e.getMessage());
                return null;
            }

            snapshot = new CachedSnapshot(entries, from, to, worldName);

            if (ConfigVariables.CACHE_CHUNK_DATA) SNAP_CACHE.put(name, snapshot);

            sender.sendMessage("");
            sender.sendRichMessage("<#a1ceff>Loaded " + snapshot.entries().size() + " chunks from " +
                nbtFile.getName() + " in " + (System.currentTimeMillis() - startRead) + "ms");
        }

        World world = Bukkit.getWorld(snapshot.world());
        if (world == null) {
            sender.sendRichMessage("<#ff388b>World not found: " + snapshot.world());
            return null;
        }

        ServerLevel lvl = ((CraftWorld) world).getHandle();
        DistanceManager dm = lvl.getChunkSource().chunkMap.distanceManager;

        long start = System.nanoTime();
        if (ConfigVariables.REGEN_MODE.equals("immediate")) {
            for (SnappedEntry e : snapshot.entries()) {
                ChunkPos pos = e.pos();
                if (ConfigVariables.ADD_TICKET_TO_CHUNKS)
                    dm.addRegionTicket(TicketType.REGEN, pos, 33, Unit.INSTANCE);
                lvl.setChunkAt(pos.x, pos.z, e.chunk());
            }
        }
        else {
            for (SnappedEntry e : snapshot.entries()) {
                ChunkPos pos = e.pos();
                ServerLevel.pendingRegeneration.put(pos.longKey, e.chunk);
            }
        }

        long blocks = (long) snapshot.entries().size() * 16L * 16 * 381;
        double timeMs = (System.nanoTime() - start) / 1_000_000.0;

        sender.sendRichMessage("<#bb45ff>Pasted " +
                format(blocks) + " blocks in " +
                String.format("%.2f", timeMs) + "ms (" +
                format((long) (blocks * 1000.0 / timeMs)) + "/s)");

        for (SnappedEntry e : snapshot.entries()) {
            ChunkPos pos = e.pos();
            world.refreshChunk(pos.x, pos.z);
        }

        return TrioValue.of(snapshot.from(), snapshot.to(), world);
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
