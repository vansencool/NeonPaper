package net.vansen.neonpaper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minecraft.commands.CommandSourceStack;
import net.vansen.fursconfig.FursConfig;
import net.vansen.neonpaper.command.NeonCommand;
import net.vansen.neonpaper.command.NeonTestingCommand;
import net.vansen.neonpaper.config.ConfigVariables;
import net.vansen.neonpaper.config.DefaultConfig;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

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

        if (config.hasPath("performance.add_ticket_to_chunks")) {
            ConfigVariables.ADD_TICKET_TO_CHUNKS = config.getBoolean("performance.add_ticket_to_chunks", false);
        }

        if (config.hasPath("performance.cache_chunk_data")) {
            ConfigVariables.CACHE_CHUNK_DATA = config.getBoolean("performance.cache_chunk_data", false);
        }

        if (config.hasPath("performance.chunk_setting_method")) {
            String method = config.getString("performance.chunk_setting_method", "direct").toLowerCase();
            if (method.equals("raw") || method.equals("direct") || method.equals("packed")) {
                ConfigVariables.CHUNK_SETTING_METHOD = method;
            } else {
                LOGGER.error("Invalid chunk_setting_method in config, defaulting to 'direct'. Valid options are: raw, direct, packed.");
            }
        }

        if (config.hasPath("general.filter_out_invalid_logs")) {
            ConfigVariables.FILTER_OUT_INVALID_LOGS = config.getBoolean("general.filter_out_invalid_logs");
        }
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
                Files.copy(new File("neonpaper/neonpaper-global.conf").toPath(), copiedFile.toPath());
            }
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
}
