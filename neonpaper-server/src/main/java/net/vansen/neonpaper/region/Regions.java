package net.vansen.neonpaper.region;

import net.minecraft.core.BlockPos;
import net.vansen.neonpaper.NeonPaper;
import net.vansen.neonpaper.util.TrioValue;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static net.vansen.neonpaper.command.NeonCommand.SNAP_DIR;

public class Regions {

    public static List<String> snapshots() {
        List<String> list = new ArrayList<>();
        if (!SNAP_DIR.exists() || !SNAP_DIR.isDirectory()) return list;
        File[] files = SNAP_DIR.listFiles((dir, name) -> name.endsWith(".lnbt"));
        if (files == null) return list;
        for (File f : files) {
            if (f.isFile()) {
                String n = f.getName();
                list.add(n.substring(0, n.length() - 5));
            }
        }
        return list;
    }

    public static boolean isIn(@NotNull Location location, @NotNull BlockPos pos1, @NotNull BlockPos pos2) {
        return location.getX() >= Math.min(pos1.getX(), pos2.getX()) && location.getX() <= Math.max(pos1.getX(), pos2.getX()) &&
            location.getZ() >= Math.min(pos1.getZ(), pos2.getZ()) && location.getZ() <= Math.max(pos1.getZ(), pos2.getZ());
    }

    public static @Nullable TrioValue<BlockPos, BlockPos, World> metadata(@NotNull String name) {
        File datFile = new File(SNAP_DIR, name + ".dat");
        if (!datFile.exists()) return null;

        try (DataInputStream in = new DataInputStream(new FileInputStream(datFile))) {
            String worldName = in.readUTF();
            BlockPos from = new BlockPos(in.readInt(), in.readInt(), in.readInt());
            BlockPos to = new BlockPos(in.readInt(), in.readInt(), in.readInt());

            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;

            return TrioValue.of(from, to, world);
        } catch (IOException e) {
            NeonPaper.LOGGER.error("Could not read region metadata for snapshot '{}'", name, e);
            return null;
        }
    }

    public static boolean exists(@NotNull String name) {
        return new File(SNAP_DIR, name + ".lnbt").isFile();
    }
}
