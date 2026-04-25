# CobbleDex Operating Model

CobbleDex is a viewer-native Cobblemon knowledge layer for REI, JEI, and EMI. The goal is that players can answer normal pack-installed Cobblemon questions in their recipe viewer instead of needing an external wiki.

## Scope

CobbleDex work is in scope when it improves Cobblemon data collection, interpretation, search, or display through REI, JEI, and EMI.

Good fits:

- Pokemon spawn, evolution, drops, stats, moves, forms, fossil, riding, obtainment, item, and mechanic reference pages.
- Reverse lookups from Pokemon, moves, TMs, fossils, drops, and relevant items.
- Diagnostics that explain what data CobbleDex believes and where it came from.
- Optional integrations that contribute viewer-native Cobblemon knowledge.

Poor fits:

- General gameplay QoL unrelated to the viewer reference mission.
- HUDs, automation, inventory tools, or unrelated client interaction helpers.
- Export-first features that duplicate the viewer experience.

## Approved Direction

- REI, JEI, and EMI parity is mandatory by default.
- Data should follow the source that best matches what the player experiences.
- Material forms should surface; texture-only variants should collapse.
- Obtainment is custom per-Pokemon spawn-like knowledge and can be gated by optional mods.
- Export remains a hidden diagnostic and planning tool.

## Work That Deserves Its Own Pass

These are approved, but should not be smuggled into small changes:

- Splitting `SpawnDataIndex` into load orchestration, snapshots, source merging, derived indexes, sync application, and query access.
- Creating canonical page projections used by viewer UI, search, export, and diagnostics.
- Replacing the giant display helper with dedicated page-family builders.
- Finishing a full measure-first layout engine across every page family.

## Feature Admission Checklist

Before adding a feature, answer yes to each question:

- Does this answer a real Cobblemon knowledge question inside REI, JEI, or EMI?
- Does it preserve parity across all three viewers?
- Does it follow player-truth data precedence?
- Does it respect the material-form policy?
- Can it extend an existing shared path instead of adding a parallel one?