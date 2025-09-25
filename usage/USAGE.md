## How to Use + Configuration

### Usage

**/neonpaper reload**
- Reloads configuration and restarts auto-regeneration

**/neonpaper inside <name>**
- Checks if player is inside a region
- `name` = region name

**/neonpaper flushmap**
- Flushes pending chunks to disk

**/neonpaper loadmap**
- Loads pending chunks from disk

**/neonpaper tpcenter <name>**
- Teleports player to region center
- `name` = region name

**/neonpaper insideregions**
- Lists all regions player is currently inside (clickable entries)

**/neonpaper region pos 1|2 [x] [y] [z]**
- Sets position 1 or 2 for defining a region
- Optional X-Y-Z coordinates, otherwise uses player location

**/neonpaper region save <name>**
- Saves a region using positions 1 and 2
- `name` = region name

**/neonpaper region delete <name>**
- Deletes a saved region (snapshot + metadata)
- `name` = region name

**/neonpaper region paste <name>**
- Pastes the specified region (aka regeneration)
- `name` = region name

---

### Configuration

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