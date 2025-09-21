package net.vansen.neonpaper.config;

public class DefaultConfig {

    public static String VERSION = "1.0.4";

    public static String CONFIG = """
        // NeonPaper Configuration
        
        // Do not change this
        version = "%s"
        
        commands {
            testing_command = false // Enable the testing command. This command is used for testing purposes only, or benchmarking.
            neonpaper_command = true // Main command, for saving and pasting regions
        }
        
        general {
            // Log filter to disable the block entity logs (caused by the changing chunk data)
            // They are really not relevant and can spam the console
            // THOUGH, IF THERE IS A ACTUAL BUG IN YOUR SERVER, YOU MAY NOT KNOW IT!
            // Recommended
            filter_out_invalid_logs = true
        }
        
        performance {
            // If true, regenerating an region will add a ticket to all chunks in the region
            // AND, if your region is very large, and players are not around the entire region to load it, the chunks can be slow to load
            // This will make sure the chunks are loaded as fast as possible
            // However, its only good if you regenerate regions often, otherwise its just not worth it
            // Recommended if you have large region that are regenerated often
            add_ticket_to_chunks = false
        
            // If true, caches the chunk data after loading it from disk, so it does not need to be loaded again, makes loading alot faster but uses more memory
            // Recommended if you have a decent amount of RAM to spare, and you regenerate regions often
            cache_chunk_data = false
        
            // The method used to copy chunk section data when regenerating or pasting chunks.
            // There are three possible modes for now:
            //
            //   raw
            //     This is the fastest method, directly calling PalettedContainer.copy().
            //     However, it has a serious downside: in many cases the copied section
            //     ends up incomplete or broken. The most common symptom is that after
            //     pasting, certain sections in specific chunks refuse to accept block changes (you can’t place blocks in them).
            //     Use this only if you dont need to place blocks in the pasted region.
            //
            //   direct
            //     This is the default. It manually rebuilds the palette and storage from
            //     the source section. The result is usually safe - block placement and
            //     editing works as expected. Performance is much faster than packed, and
            //     very good for real-world use. This is the recommended option unless
            //     you absolutely need guaranteed 100 correctness from vanilla code paths.
            //
            //   packed
            //     This is the safest but slowest. It works by using the built-in pack()
            //     and unpack() methods. It usually guarantees correct results in every
            //     case. The downside is that it is ALOT slower than raw/direct (10b/s -> 500m/s).
            //     however, its still really fast when taken in context of plugins.
            //     This is recommended if you experience issues with "direct" mode, and don't need the extreme performance.
            //
            // Recommended: "direct" - Good performance and usually safe
            // If you experience issues with it, try "packed"
            // If you dont need to place blocks in the pasted region, use "raw" for best performance
            chunk_setting_method = "direct"
        }
        
        // Auto Regeneration
        // Each arena can have its own regeneration settings. The options are:
        //
        // enabled
        //   Whether auto regeneration is active for this arena.
        //
        // snapshot
        //   The name of the saved NBT snapshot file to paste.
        //
        // time
        //   time_unit: S = seconds, M = minutes, H = hours, D = days
        //   duration: How long to wait between regenerations in the specified unit
        //
        // broadcast
        //   message: The message to display after regenerating. Supports MiniMessage and multiple lines using <newline>
        //   type: message = sends the message to players, actionbar, action_bar = shows in players' action bars, both = both
        //   to: arena = only to players inside arena bounds
        //       world = all players in the world
        //       global = all online players
        //
        // sounds
        //   sound: The Bukkit sound to play after regenerating, see https://jd.papermc.io/paper/1.21.1/org/bukkit/Sound.html for a list of sounds
        //   volume: Volume of the sound
        //   pitch: Pitch of the sound
        //   to: arena/world/global (same as broadcast)
        //
        // ticking
        //   enabled: Whether ticking messages are active
        //   value: Number of seconds before regeneration to start showing "tick" messages
        //          Example: value = 5 means 5 seconds countdown before regen triggers
        //   message: The message shown each second during ticking. Supports <count> placeholder
        //            which is replaced with remaining seconds.
        //   type: BROADCAST or ACTIONBAR (or BOTH)
        //   to: arena/world/global (same as broadcast)
        // Explanation:
        //   The ticking system is like a countdown before the arena regeneration occurs.
        //   Each second during the last 'value' seconds, a message is sent to players according
        //   to the specified 'to' and 'type'. For example, if value = 5, players will see messages
        //   for 5, 4, 3, 2, 1 seconds remaining before the arena regenerates.
        //   This is useful to warn players that blocks are about to reset.
        regeneration {
           arena {
             xyz {
               enabled = false
               snapshot = "xyz"
               time {
                 time_unit = "H"
                 duration = 1
               }
               broadcast {
                 message = [
                   "<gray>Regenerated arena XYZ..."
                 ]
                 type = "message"
                 to = "arena"
               }
               sounds {
                 sound = "ENTITY_PLAYER_LEVELUP"
                 volume = 1.0
                 pitch = 1.0
                 to = "arena"
               }
               ticking {
                 enabled = true
                 value = 5
                 message = [
                   "<gray>Regenerating in <count>..."
                 ]
                 type = "message"
                 to = "arena"
               }
             }
        
             xyz2 {
               enabled = false
               snapshot = "xyz_2"
               time {
                 time_unit = "M"
                 duration = 10
               }
               broadcast {
                 message = [
                   "<gray>Regenerating arena XYZ 2..."
                 ]
                 type = "message"
                 to = "arena"
               }
               sounds {
                 sound = "ENTITY_PLAYER_LEVELUP"
                 volume = 1.0
                 pitch = 1.0
                 to = "arena"
               }
               ticking {
                 enabled = true
                 value = 5
                 message = [
                   "<gray>Regenerating in <count>..."
                 ]
                 type = "message"
                 to = "arena"
               }
             }
           }
         }
        """.formatted(VERSION);
}
