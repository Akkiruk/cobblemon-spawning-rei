# Viewer Parity

CobbleDex supports REI, JEI, and EMI as equal first-class viewers.

## Rule

Every CobbleDex feature should work in REI, JEI, and EMI unless a viewer API makes exact parity impossible.

## Required Surfaces

For each new data domain or page family, check all of these:

- Category registration.
- Full browsing registration.
- Pokemon recipe lookup.
- Pokemon usage lookup when relevant.
- Item reverse lookup when relevant.
- Search aliases or hidden metadata.
- Reload behavior after sync or manual reload.
- Recipe-tree participation when relevant.

## Current Page Families

- Spawns
- Evolutions
- Special obtainment
- Drops
- Stats
- Moves and TM learners
- Pokedex info
- Pokedex descriptions
- Fossils
- Type chart
- Natures
- CobbleCrew jobs
- Material forms
- Riding

## Release Check

Before releasing viewer-facing changes:

- Build both Fabric and NeoForge.
- Confirm all three viewer integrations compile.
- Compare category counts in logs.
- Check reverse lookup behavior for any touched Pokemon, item, move, or TM path.
- Note any viewer API limitation explicitly in the changelog or PR.