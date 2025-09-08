# NeonPaper

**NeonPaper** is a custom Paper fork built for one purpose:
**regenerating massive regions at extreme speed.**

---

## Benchmarks

All benchmarks were run on a **Ryzen 7 3700X**.

### NeonTest | 164 Million Blocks

* Completed in **\~15ms**
* **No visible MSPT impact**

![Benchmark 164 Million Blocks](images/1.png)

### NeonTest | 640 Million Blocks

* Regenerated nearly **640 million blocks** at once
* **MSPT remained stable** throughout

![Benchmark 640 Million Blocks](images/2.png)

### NeonTest | Benchmark Method

The test involved repeatedly:

1. Setting chunks to air
2. Regenerating them
3. Repeating this five times

Through all of this, server performance stayed stable. The only small spikes came from mobs suffocating when their chunks were cleared, not from the regeneration itself.

### Benchmark | Realistic Benchmark
The benchmarks above did not include loading chunks from the files, and it was setting to air and regenerating it, which is well, not really a real world use case.
This benchmark will show, the time it takes to load the chunks from the world files, and then regenerate them.

* Regenerated **100 million blocks** in **16ms**
  ![Benchmark 100 Million Blocks](images/3.png)

* Regenerated **500 million blocks** in **79ms**
  ![Benchmark 500m Million Blocks](images/4.png)

---

## Why Not a Plugin?

Plugins can regenerate blocks, but only one at a time. Every single block change runs through a long chain of logic inside Minecraft, causing:

* Tens of millions of array lookups
* Hundreds of millions of method calls
* Constant object and integer creation

Even using the lowest-level APIs, regenerating tens of millions of blocks is painfully slow.

NeonPaper avoids all of that by **working directly with raw chunk data**. No chains, no per-block cost.

---

## What Makes It Fast?

By directly replacing chunk data, NeonPaper can regenerate **billions of blocks in under a quarter of a second**.

This method removes nearly all overhead, allowing regeneration on a scale that plugins simply cannot achieve.

---

## World Regeneration

Support for full world regeneration is planned, but not a current priority.

---

## How to Use

Usage instructions will be added soon.

---

## Known Issues

* Block entities that tick (e.g., spawners) regenerate correctly but do not tick yet. This will not be fixed anytime soon.

---

## Disclaimer

Performance depends on how many chunks your server can load at once.
For example, regenerating **two billion blocks** requires loading around **20,000 chunks simultaneously**.

The benchmarks above show what’s possible under the right conditions.