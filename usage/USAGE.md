# How to Use + Configuration

## NeonPaper Commands Usage

---

**You can use** `/neonpaper help` **to see a list of commands.**

### **/neonpaper reload**
- Reloads the plugin configuration.
- Restarts **AutoRegeneration** automatically.

---

### **/neonpaper inside <name>**
- Checks whether the player is currently **inside a specific region**.
- `<name>`: Name of the region.
- If outside, provides a **clickable teleport** to the center of the region.

---

### **/neonpaper insideregions**
- Lists all regions the player is currently inside.
- Each region name is **clickable** to teleport to its center.

---

### **/neonpaper map flushmap**
- Forces pending chunks to be **flushed to disk**.
- Useful for saving map changes manually.

### **/neonpaper map loadmap**
- Loads pending chunks from disk.
- Useful when restoring map data after a server restart.

---

### **/neonpaper teleportation <name> pos|center**
- Teleports the player to a region.
- `<name>`: Name of the region.
- `pos`: Teleports to `pos1` or `pos2` of the region.
- `center`: Teleports to the **center** of the region.

---

### **/neonpaper region pos <1|2> [x] [y] [z]**
- Sets **position 1 or 2** for defining a region.
- Optional coordinates `[x y z]`; if omitted, uses the player's **current location**.
- Positions are used for saving or regenerating regions later.

---

### **/neonpaper region save <name>**
- Saves the region defined by **positions 1 and 2**.
- `<name>`: Name of the region snapshot.
- Automatically stores snapshot + metadata for regeneration.

---

### **/neonpaper region delete <name>**
- Deletes a saved region snapshot.
- Removes both the **snapshot file** and **metadata**.
- `<name>`: Name of the region.

---

### **/neonpaper region paste <name>**
- Pastes (regenerates) the saved region at its original location.
- `<name>`: Name of the region snapshot.

### Notes
- Many commands support **clickable suggestions** in chat, or execution via clicking.
- Use common sense if you are struggling with commands :)

## Configuration

**commands**
- `testing_command` – Enables a testing/benchmarking command for development purposes only. Default: `false`
- `neonpaper_command` – Enables the main NeonPaper command for saving/pasting regions. Default: `true`

**general**
- `filter_out_invalid_logs` – When `true`, disables irrelevant block entity logs caused by chunk updates, reducing console spam. Default: `true`

**performance**
- `add_ticket_to_chunks` – When `true`, adds a “load ticket” to every chunk in a region during regeneration to force fast loading. Recommended only for decently sized regions. Default: `false`
- `cache_chunk_data` – When `true`, caches chunk data in memory after loading to speed up repeated access. Uses more RAM. Default: `false`
- `chunk_setting_method` – Determines how chunk section data is copied:
    - `"raw"` – Fastest, but may produce incomplete chunks (block placement might fail)
    - `"direct"` – Default; safe and performant, allows normal block placement
    - `"packed"` – Safest but slowest; guarantees correct results in all cases
- `regen_mode` – Determines how chunks are processed during regeneration:
    - `"immediate"` – Loads and regenerates chunks immediately; good for small/medium regions
    - `"lazy"` – Regenerates chunks on access; suitable for huge regions without load spikes
    - `"lazy_background"` – Like lazy, but periodically flushes chunk map to disk to reduce corruption risk
- `flush_interval` – Only used in `lazy_background` mode. Interval in seconds to flush the chunk map to disk. Default: `60`
- `save_at_stop_lazy_background` – Only used in `lazy_background` mode. When `true`, saves the chunk map on server stop to reduce corruption risk. Default: `true`

### Auto Regeneration

Each arena can have its own regeneration settings, defined under `regeneration.arena.<name>`.

#### Options per arena

**enabled**
- Whether auto regeneration is active for this arena.
- Type: `boolean`

**snapshot**
- The name of the saved NBT snapshot file to paste when regenerating.
- Type: `string`

**time**
- Controls how often the regeneration should occur.
    - `time_unit` – The unit of time. Supported values:
        - `S` = seconds
        - `M` = minutes
        - `H` = hours
        - `D` = days
    - `duration` – The length of the interval in the given unit.

**broadcast**
- Sends messages after regeneration. Supports [MiniMessage](https://docs.advntr.dev/minimessage/index.html).
    - `message` – One or more lines to display.
    - `type` – Where the message appears:
        - `message` = chat
        - `actionbar` / `action_bar` = action bar
        - `both` = both chat and action bar
    - `to` – Which players see it:
        - `arena` = only players inside arena bounds
        - `world` = all players in the same world
        - `global` = all online players

**sounds**
- Plays sounds after regeneration.
    - `sound` – A valid [Bukkit sound](https://jd.papermc.io/paper/1.21.4/org/bukkit/Sound.html)
    - `volume` – Volume of the sound (float)
    - `pitch` – Pitch of the sound (float)
    - `to` – Who hears it (`arena`, `world`, `global`)

**ticking**
- Displays countdown messages before regeneration.
    - `enabled` – Whether ticking messages are active.
    - `value` – Number of seconds before regeneration to start showing countdowns.
        - Example: `5` → shows messages for 5, 4, 3, 2, 1 seconds remaining.
    - `message` – Message shown each second. Supports `<count>` placeholder for remaining seconds.
    - `type` – Where the ticking message appears: `message`, `actionbar`, or `both`.
    - `to` – Who sees ticking messages (`arena`, `world`, `global`).