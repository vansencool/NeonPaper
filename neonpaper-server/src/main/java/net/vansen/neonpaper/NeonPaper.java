package net.vansen.neonpaper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.jpountz.lz4.LZ4FrameOutputStream;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.vansen.fursconfig.FursConfig;
import net.vansen.neonpaper.command.NeonCommand;
import net.vansen.neonpaper.command.NeonTestingCommand;
import net.vansen.neonpaper.config.ConfigVariables;
import net.vansen.neonpaper.config.DefaultConfig;
import net.vansen.neonpaper.regeneration.PendingChunks;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

@SuppressWarnings({"ResultOfMethodCallIgnored", "LoggingSimilarMessage"})
public class NeonPaper {

    public static final ComponentLogger LOGGER = ComponentLogger.logger("NeonPaper");
    public static final File PARENT_CONFIG_FILE = new File("neonpaper");
    public static final File CONFIG_FILE = new File(PARENT_CONFIG_FILE, "neonpaper-global.conf");
    private static FursConfig config;

    /**
     * Registers a command if the configuration key is enabled.
     *
     * @param dispatcher The command dispatcher.
     * @param command    The command to register.
     * @param key        The command's configuration key.
     */
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher, @NotNull LiteralArgumentBuilder<CommandSourceStack> command, @NotNull String key) {
        if (config == null) {
            loadConfig();
        }

        String fullKey = "commands." + key + "_command";
        if (config.hasPath(fullKey) && config.getBoolean(fullKey)) {
            dispatcher.register(command);
        }
    }

    public static void registerDefaults(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        NeonTestingCommand.register(dispatcher);
        NeonCommand.register(dispatcher);
    }

    /**
     * Loads the configuration from file or creates a new one using defaults.
     */
    private static void loadConfig() {
        if (!CONFIG_FILE.exists()) {
            save();
        }

        try {
            config = FursConfig.createAndParseFile(CONFIG_FILE);
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration", e);
            config = FursConfig.createAndParse(DefaultConfig.CONFIG);
        }
    }

    public static void load() {
        loadConfig();

        if (!config.hasPath("version")) {
            if (copyAndCreate(DefaultConfig.VERSION))
                loadConfig();
        } else if (!config.getString("version").equals(DefaultConfig.VERSION)) {
            if (copyAndCreate(DefaultConfig.VERSION))
                loadConfig();
        }

        ConfigVariables.ADD_TICKET_TO_CHUNKS = bool("performance.add_ticket_to_chunks", false);
        ConfigVariables.CACHE_CHUNK_DATA = bool("performance.cache_chunk_data", false);

        ConfigVariables.CHUNK_SETTING_METHOD = string("performance.chunk_setting_method", "direct", "raw", "direct", "packed");
        ConfigVariables.FILTER_OUT_INVALID_LOGS = bool("general.filter_out_invalid_logs", false);

        if (config.hasPath("general.clear_entities_after_regen")) {
            String option = config.getString("general.clear_entities_after_regen", "none").toLowerCase();
            if (option.contains("mobs") || option.contains("none") || option.contains("items") || option.contains("players") || option.contains("armor_stands") || option.contains("crystals")) {
                ConfigVariables.CLEAR_ENTITIES_AFTER_REGEN = option;
            } else {
                LOGGER.error("Invalid clear_entities_after_regen in config, defaulting to 'none'. Valid options are: none, mobs, items, players, armor_stands, crystals.");
            }
        } else LOGGER.error("Missing clear_entities_after_regen in config, defaulting to 'none'");
        ConfigVariables.IGNORE_Y_AXIS_IN_REGION_CHECK = bool("general.ignore_y_axis_in_region_check", true);

        ConfigVariables.REGEN_MODE = string("performance.regen_mode", "immediate", "immediate", "lazy", "lazy_background");
        if (ConfigVariables.REGEN_MODE.equals("lazy_background")) PendingChunks.schedule();

        ConfigVariables.FLUSH_INTERVAL = integer("performance.flush_interval", 60);
        ConfigVariables.SAVE_AT_STOP_LAZY_BACKGROUND = bool("performance.save_at_stop_lazy_background", true);

        try {
            convertNBT();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String string(@NotNull String path, @NotNull String def, @NotNull String... valid) {
        if (!config.hasPath(path)) {
            LOGGER.error("Missing {} in config, defaulting to '{}'", path, def);
            return def;
        }
        String value = config.getString(path, def).toLowerCase();
        if (valid.length > 0 && Arrays.stream(valid).noneMatch(value::equals)) {
            LOGGER.error("Invalid {} in config ('{}'), defaulting to '{}'. Valid options are: {}", path, value, def, String.join(", ", valid));
            return def;
        }
        return value;
    }

    @SuppressWarnings("SameParameterValue")
    private static int integer(@NotNull String path, int def) {
        if (!config.hasPath(path)) {
            LOGGER.error("Missing {} in config, defaulting to '{}'", path, def);
            return def;
        }
        return config.getInt(path, def);
    }

    private static boolean bool(@NotNull String path, boolean def) {
        if (!config.hasPath(path)) {
            LOGGER.error("Missing {} in config, defaulting to '{}'", path, def);
            return def;
        }
        return config.getBoolean(path, def);
    }

    private static boolean copyAndCreate(@NotNull String currentVersion) {
        LOGGER.error("The config version is not {}, that means the config file is not compatible with this version of NeonPaper, please delete the file and create a new one, or update it manually:", currentVersion);
        LOGGER.error("For now, we are creating a copy of it in neonpaper/configcopied, and creating the default config file.");
        try {
            int copyIndex = 1;
            File copiedFile = new File("neonpaper/configcopied/neonpaper-global COPIED-" + copyIndex + ".conf");
            while (copiedFile.exists()) {
                copyIndex++;
                copiedFile = new File("neonpaper/configcopied/neonpaper-global COPIED-" + copyIndex + ".conf");
            }

            File configCopiedDir = new File("neonpaper/configcopied");
            if (!configCopiedDir.exists()) {
                configCopiedDir.mkdirs();
            }

            Files.copy(new File("neonpaper/neonpaper-global.conf").toPath(), copiedFile.toPath());
            LOGGER.info("Created a copy of the default config file in the same folder as the config file, now deleting the old one and creating a new one.");
            if (CONFIG_FILE.delete()) {
                save();
                return true;
            } else {
                LOGGER.error("Failed to delete the old config file! shouldn't happen, please delete it manually! throwing a exception...");
                throw new RuntimeException("Failed to delete the old config file!, shouldn't had happened");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create a copy of the config file.", e);
            return false;
        }
    }

    /**
     * Saves the default configuration to the config file.
     */
    private static void save() {
        if (!PARENT_CONFIG_FILE.exists() && !PARENT_CONFIG_FILE.mkdirs()) {
            LOGGER.error("Failed to create parent config directory.");
            return;
        }
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            writer.write(DefaultConfig.CONFIG);
        } catch (IOException e) {
            LOGGER.error("Failed to save default configuration", e);
        }
    }

    public static void convertNBT() throws IOException {
        if (!NeonCommand.BACKUP_DIR.exists()) NeonCommand.BACKUP_DIR.mkdirs();

        File[] files = NeonCommand.SNAP_DIR.listFiles((dir, name) -> name.endsWith(".nbt"));
        if (files == null) return;

        for (File nbtFile : files) {
            try (DataOutputStream dos = new DataOutputStream(
                    new BufferedOutputStream(
                            new LZ4FrameOutputStream(Files.newOutputStream(new File(NeonCommand.SNAP_DIR, nbtFile.getName().replace(".nbt", ".lnbt")).toPath()))
                    )
            )) {
                CompoundTag root = NbtIo.readCompressed(nbtFile.toPath(), NbtAccounter.unlimitedHeap());
                NbtIo.write(root, dos);
                LOGGER.info("Converted snapshot {} to the new format.", nbtFile.getName());
            }

            Files.move(nbtFile.toPath(), new File(NeonCommand.BACKUP_DIR, nbtFile.getName()).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
