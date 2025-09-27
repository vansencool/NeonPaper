package net.vansen.neonpaper.regeneration;

import com.mojang.serialization.DataResult;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.vansen.neonpaper.NeonPaper;
import net.vansen.neonpaper.command.NeonCommand;
import net.vansen.neonpaper.config.ConfigVariables;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PendingChunks {

    private static final File REGEN_FILE = new File(new File(NeonCommand.SNAP_DIR, "lazy"), "regen_map.lmap");
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> future;

    public static void schedule() {
        if (future != null && !future.isCancelled()) {
            future.cancel(true);
        }
        future = EXECUTOR.scheduleAtFixedRate(
                PendingChunks::flush,
                ConfigVariables.FLUSH_INTERVAL,
                ConfigVariables.FLUSH_INTERVAL,
                TimeUnit.SECONDS
        );
    }

    public static synchronized void flush() {
        if (Level.pendingRegeneration.isEmpty()) return;

        File tmp = new File(REGEN_FILE.getParentFile(), "temp/" + REGEN_FILE.getName() + ".tmp");
        // noinspection ResultOfMethodCallIgnored
        tmp.getParentFile().mkdirs();
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(
                        new LZ4FrameOutputStream(Files.newOutputStream(tmp.toPath()))
                )
        )) {
            DataResult<Tag> encoded = NeonCommand.SnappedEntry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, Level.pendingRegeneration.entrySet().stream()
                    .map(e -> new NeonCommand.SnappedEntry(new ChunkPos(e.getKey()), e.getValue()))
                    .toList());
            if (encoded.result().isPresent()) {
                CompoundTag root = new CompoundTag();
                root.put("entries", encoded.result().get());
                NbtIo.write(root, dos);
                Files.move(tmp.toPath(), REGEN_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException ex) {
            NeonPaper.LOGGER.error("Could not flush pending chunks to disk!", ex);
        }
    }

    public static void load() {
        if (!REGEN_FILE.exists()) return;
        NeonPaper.LOGGER.info("Loading pending chunks from disk...");
        try (DataInputStream in = new DataInputStream(new LZ4FrameInputStream(new FileInputStream(REGEN_FILE)))) {
            List<NeonCommand.SnappedEntry> list = NeonCommand.SnappedEntry.CODEC.listOf()
                    .parse(NbtOps.INSTANCE, NbtIo.read(in, NbtAccounter.unlimitedHeap()).get("entries"))
                    .result().orElseThrow();
            NeonPaper.LOGGER.info("Loaded {} pending chunks from disk.", list.size());
            for (NeonCommand.SnappedEntry e : list) {
                Level.pendingRegeneration.put(e.pos().toLong(), e.chunk());
            }
        } catch (IOException ex) {
            NeonPaper.LOGGER.error("Could not load pending chunks from disk!", ex);
        }
    }

    @SuppressWarnings("LoggingSimilarMessage")
    public static void shutdown() {
        if (ConfigVariables.REGEN_MODE.equals("lazy") || (ConfigVariables.REGEN_MODE.equals("lazy_background") && ConfigVariables.SAVE_AT_STOP_LAZY_BACKGROUND)) {
            NeonPaper.LOGGER.warn("===============================================");
            NeonPaper.LOGGER.warn("!!! Flushing pending chunks to disk !!!");
            NeonPaper.LOGGER.warn("!!! DO NOT force stop or data WILL be corrupted !!!");
            NeonPaper.LOGGER.warn("===============================================");
            flush();
            EXECUTOR.shutdownNow();
        }
    }
}
