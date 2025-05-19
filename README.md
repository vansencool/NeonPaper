# NeonPaper

This is a custom Paper fork made for one thing: **regenerating massive regions extremely fast.**
It uses a new method of regeneration by directly replacing full chunks instead of setting blocks one by one.

---

## Benchmarks

NeonPaper was tested on a **Ryzen 7 3700X**, regenerating **hundreds of millions of blocks** with **almost no impact** on server performance.
These tests were repeated multiple times, including full regeneration cycles.

Actual **MSPT impact** may vary depending on the number of players online (due to chunk priority).

This test regenerated around **164 million blocks** in one go.
It completed in just **\~15ms**, with **no performance hit**.

![Benchmark 164 Million Blocks](images/1.png)

This one regenerated nearly **640 million blocks**.
Even with this huge volume, **MSPT remained stable**.

![Benchmark 640 Million Blocks](images/2.png)

The benchmark involved:

* Setting chunks to air
* Regenerating them
* Repeating this cycle **5 times**

Through all of this, **MSPT barely changed**. Any minor fluctuations came from mob spawning and suffocation after the chunks were set to air, not from the regeneration itself.

## Why not a plugin?

Plugins *can't necessarily* use this method because of how Minecraft is built.

However, plugins *can* change blocks one by one, but this is **extremely slow** (even when using low-level methods).  
When a plugin changes blocks, it triggers a long chain of code.
For example, regenerating 30 million blocks through a plugin (low level) might involve:

- **Around 60 million** array accesses
- **Hundreds of millions** of method calls (due to internal chaining)
- Constant creation and modification of integers, longs, and objects
- *Much more*

These numbers are rough estimates and may vary, but the key point is that plugins simply **can’t skip that chain or directly replace raw data. This fork does.**

## What makes it fast?

This fork introduces a new way to regenerate areas by changing the chunk's data directly.
That means almost *no overhead*, no chains of logic, and no per-block cost.
Because of this, it can regenerate up to **2 billion blocks in under 250 milliseconds.**

## World Regeneration Support

World regeneration support will come soon, but it is *not a priority*.

## How to Use

Usage is coming soon.

## Known Issues

- **Block entities that are ticking (like spawners,) do not work yet.**  
  They will be restored properly, but they won't be ticking.

## Disclaimer

The numbers listed above are only examples of what is possible.  
To regenerate a billion or more blocks, the server must be able to load that many chunks at once.  
For example, regenerating two billion blocks requires loading around **20,000 chunks** at the same time.