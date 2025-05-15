# NeonPaper

This is a custom Paper fork made for one thing: **regenerating massive regions extremely fast.**  
It uses a new method of regeneration by directly replacing full chunks instead of setting blocks one by one.

## Why not a plugin?

Plugins *can't necessarily* use this method because of how Minecraft is built.

However, plugins *can* change blocks one by one, but this is **extremely slow** (even when using low-level methods).  
When a plugin changes blocks, it triggers a long chain of code in Minecraft.  
For example, regenerating 30 million blocks through a plugin means:

- **60 million** array accesses
- Around **400 million** method calls (due to internal chaining)
- Constant creation and modification of integers, longs, and objects
- *Much more*

Plugins simply **can’t skip that chain. This fork does.**

## What makes it fast?

This fork introduces a new way to regenerate areas by changing the chunk's data directly (replacing entire sections will be supported very soon).  
That means almost *no overhead*, no chains of logic, and no per-block cost.  
Because of this, it can regenerate up to **1 billion blocks in under 200 milliseconds.**

## World Regeneration Support

World regeneration support will come soon, but it is *not a priority*.

Because of how this system works, world regeneration can also run at extreme speeds.  
It can regenerate over **40,000 chunks in just 500 milliseconds**, which is around **2.6 BILLION blocks.**

## How to Use

Usage is coming soon.

## Disclaimer

The numbers listed above are only examples of what is possible.  
To regenerate a billion or more blocks, the server must be able to load that many chunks at once.  
For example, regenerating one billion blocks requires loading around **20,000 chunks** at the same time.