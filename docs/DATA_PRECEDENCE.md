# Data Precedence

CobbleDex sends no packets and runs no code on the server. Everything it shows comes from data
Cobblemon and Minecraft already sync to the client, or from this client's own files. It should
prefer whichever available source best matches what the player will actually experience.

## Source Order

1. Cobblemon's own client-side registries (`PokemonSpecies`, `Moves`, `Abilities`, `Fossils`, …).
   On a server these are exactly what Cobblemon's `species_sync` delivered, so they already
   reflect that server's datapacks; in singleplayer they're the loaded datapacks directly.
2. This client's own files — mod JARs, `datapacks/`, enabled resource packs — consulted only for
   the specific fields Cobblemon's sync does not carry (evolutions, egg groups/cycles, catch
   rate, base friendship, EV yield, base experience yield, spawn pools, fossil item predicates).
3. Tables compiled into CobbleDex itself (type chart, natures), which need no source at all.

A gap in one source is filled from the next **per field**, not by falling back wholesale — see
`SpawnDataIndex.doLoad` and `SpeciesTraitMerger`.

## Practical Rules

- Cobblemon's synced data wins because it's guaranteed to match the server the player is on.
- This client's files are the only source for what Cobblemon never syncs, and are only as
  trustworthy as whether they match the server — see the caveat rule below.
- Bundled defaults should only fill known optional-integration gaps, and should never pretend to
  be authoritative pack truth.

## Caveats

A category whose data could only come from this client's files, while connected to a server that
may not share them, should say so rather than silently risk showing the wrong thing — see
`DataAvailability` and the hover caveat on the spawn and evolution pages. This is a deliberate,
accepted limitation of being fully client-side: without any packet of our own, CobbleDex cannot
confirm the client's files actually match that server's. Singleplayer and LAN worlds are exempt
(the client's files *are* the world's data there).

## Diagnostics

`/cobbledex stats` and `DiagnosticService.appendSourceDiagnostics` answer:

- Which source won for this domain?
- Which domains are partial or missing?
- Which species have only fallback or bundled information?
- Where do optional integrations (CobbleRegions, CobbleCrew) contribute data?

When sources disagree, prefer the source closest to actual player experience and make the
disagreement inspectable.
