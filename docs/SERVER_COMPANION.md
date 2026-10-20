# Server companion: design spec

Status: Fabric and NeoForge halves implemented and tested on real dedicated servers (branch `server-companion`). Networking facts below were tested on Fabric 1.21.1 (see "Tested facts"). Where the build differs from the first draft, this document has been updated to match the build.

## 1. Problem

Cobblemon syncs species, moves and (1.8+) TM/egg moves to clients, but never syncs tutor and legacy moves, spawn pools, habitat pools, structured evolutions (1.8.2+ sends display text only) or the species traits `catchRate`, `eggGroups`, `eggCycles`, `baseFriendship`, `evYield`, `baseExperienceYield` and `labels`. CobbleDex fills these from the client's own files, which are wrong or empty on a server that runs packs the client does not have.

## 2. Goals and non-goals

Goals
- Give a CobbleDex client the data above from the server, filtered and compressed.
- Keep the server footprint small: no dependency on Cobblemon, one request/response exchange, nothing pushed unasked.
- Never kick a player. Not a CobbleDex player, not a player without CobbleDex, not on a mismatch.
- Behaviour with no companion on the server is exactly what it is today.

Non-goals (v1)
- Marks, TMs, fossils, type-chart overrides, mega stones (still read locally).
- Live updates after a datapack reload mid-session (next join picks them up).
- Anything that changes gameplay or writes to the world.

## 3. Tested facts (Fabric 1.21.1, netprobe, 2026-10-20)

| Test | Result |
|---|---|
| Unknown S2C id | dropped, no kick |
| `canSend` for a channel the client lacks | false (correct) |
| 1.9 MB random payload to a client with the type | delivered |
| ~2.5 MB payload | server encoder throws "Packet too large", player kicked. Cap is the 3-byte length prefix, about 2 MiB after compression |
| 1.5 MB payload to a client that does not know the id | kick (`DecoderException`). 500 KB was dropped silently |
| Codec reads more than was sent | kick (`DecoderException`) |
| Extra trailing bytes | tolerated |
| Exception inside a client receiver | no kick, logged |

The last time this was tried, the kicks came from oversized packets.

## 4. Hard rules (each one maps to a test above)

1. Every send is gated on `canSend(player, type)`. No exceptions, no "it will just be ignored".
2. Every packet is at most 256 KB by default, and the sender refuses (throws before the wire) to emit anything above 900,000 bytes.
3. Codecs are trivial: fixed-size header fields, then "the rest of the buffer". They never read past what exists. All parsing happens in the handler, which may throw safely.
4. Channel ids carry a version (`..._v1`). A layout change means a new id, never a new layout under an old id.
5. Nothing is sent unless the client asked, and only to that client.
6. Client handlers catch everything and fall back to local data.

## 5. Components

- One jar per loader, the same jars we ship today. The server half lives in `common` beside the client half and shares the protocol classes, so the two sides cannot drift. No second artifact.
- Server half: a small handler that uses only Minecraft and loader APIs (no Cobblemon API calls). The jar still declares Cobblemon, Fabric Language Kotlin and Fabric API as required, which every Cobblemon server already has.
- Client half: request, reassembly, cache, and a server overlay consumed by the existing loaders.
- Hard constraint: on a dedicated server nothing may load a `net.minecraft.client` class. 23 of the 85 files in `common` import client classes today, so the server entrypoint must only reference server-safe classes, and a test must start a dedicated server and fail on any client class load.

## 5a. Packaging and platform metadata

Researched 2026-10-20.

Jar metadata
- Fabric: change `"environment": "client"` to `"*"`. Client entrypoints (`client`, `rei_client`, `jei_mod_plugin`, `emi`) already only run on the client. Add a `main` or `server` entrypoint for the server half. The `mixins` list is empty today, so there is no client-only mixin to guard (any future one needs `"environment": "client"`).
- NeoForge: `neoforge.mods.toml` already loads on both sides (`side="BOTH"`, `@Mod` without a `dist`), so no change to the toml. The server half registers its payloads as optional so a client without the mod can still connect. To test, along with whether the existing NeoForge class survives a dedicated-server start today.

Modrinth (changed 28 Aug 2025)
- The old `client_side` / `server_side` fields were replaced by one `environment` with nine values. The v2 fields are deprecated.
- Modrinth derives each version's environment from the uploaded jar. Our versions show this: Fabric (`environment: client`) is `client_only`, NeoForge (`side=BOTH`) is `client_and_server`. The 343 older versions are `client_only_server_optional`, carried over from the old settings.
- The right value for us is `client_only_server_optional` ("Client and server, optional on server"): the client is required, the server half is optional.
- Metadata cannot say "optional on server", so each upload needs the value set afterwards, either per version in the web UI, or with `PATCH /v3/version/{id}` (reported as best-effort by other projects), or through the `environment` input of mc-publish (see below).
- A project with versions in different environments reports `server_side: unsupported` through the legacy field (modrinth/code#7911, open). Keeping every version on the same value avoids it. Today our Fabric and NeoForge versions differ.
- v3 requires `environment` on create-version for some loaders (error "Missing mandatory loader fields: environment"). Our current mc-publish v3.3 uploads still succeeded on 2.31.2 (run 39438782393).

CurseForge
- There is no structured environment field in the API (feature request CF-I-8489, "future consideration").
- Files carry `Client` and `Server` tags that are passed as game version names on upload. There is no "optional" value, so a file meant for both gets both tags.
- Server-pack tools and hosts that auto-exclude client-only mods read those tags, so tagging the file `Server` is what makes a server pack include it.

mc-publish (v3.3 in our workflow)
- Infers the environment from the jar metadata and accepts an `environment` input (values `client`, `client?`, `client*`, `server`, `server?`, `server*`, `singleplayer`, `dedicated-server`, combinable with `|`).
- The docs do not say how those map to CurseForge tags or Modrinth's new field. Test with the first release rather than assume; plan a post-publish check of both sites and a `PATCH` fallback.

Consequence of the change
- Today the Fabric jar is skipped on dedicated servers. After the change, any server pack that includes it will load it, so the default server config must be safe (see section 8) and the server half must be silent and cheap when nobody asks.

## 6. Protocol v1

Namespace `cobbledex`. All integers big-endian.

| Id | Direction | Fields |
|---|---|---|
| `sync_request_v1` | C2S | `int protocol` (1), `string cobblemonVersion`, `long cachedHash` (0 = none), `string[] knownMods` as `id@version` (total at most 20 KB) |
| `sync_header_v1` | S2C | `byte status` (0 unchanged, 1 incoming, 2 refused), `long hash`, `int chunkCount`, `int compressedBytes`, `int rawBytes` |
| `sync_chunk_v1` | S2C | `int index`, then the remaining bytes of the buffer |

The request is small and the C2S limit (32 KB) is respected by the 20 KB cap on `knownMods`.

Reassembly: the client expects `chunkCount` chunks in order, a 30-second total timeout, a `compressedBytes` match and a hash match. Any gap, duplicate, size mismatch or hash mismatch discards the transfer.

## 7. Payload

Raw deflate of one UTF-8 JSON document, rejected above 32 MB inflated:

```
{ "v": 1, "cobblemon": "1.8.2",
  "categories": {
    "species":              { "<ns>:<path>": { filtered species JSON } },
    "species_additions":    { ... },
    "spawn_pool_world":     { "<ns>:<path>": { full spawn JSON } },
    "spawn_detail_presets": { ... },
    "habitat_pools":        { ... } } }
```

Keyed by resource path, so the client replaces the same key from its own files. Server data is authoritative for the keys it sends. Keys it does not send stay as they are locally.

No species key filtering in v1. Measured: `moves` is 56% of species bytes and is needed (tutor and legacy moves), and everything else compresses to very little, so filtering saved ~30% raw and almost nothing compressed while coupling the server to Cobblemon's file format. Files are sent whole (parsed and re-emitted, so a broken file is dropped without spoiling the rest). The exclude list in the server config is the privacy control.

The document also carries `"mods"` (mod ids whose files were left out because the client's copy is the same version) and `"origins"` (species file id to a display name for the "Added by" line; only the last path segment of a pack, never a server path).

Measured (Zaderos's packs plus Cobblemon 1.8.2, minified, one deflate stream): packs only 0.37 MB, everything 0.84 MB.

Skipping what the client already has:
- A file whose source pack is an installed mod (Fabric names it with the bare mod id, NeoForge expected as `mod/<id>`) is skipped when that mod's `id@version` is in the request's list.
- Datapacks (OpenLoader `openloader/<path>`, `file/<name>`) are always sent; a datapack folder named like a mod can never be mistaken for the mod.
- Measured on the test server (121 mods, 31 packs): 4,895 files / 1,079 KB without skipping, 2,107 files / 469 KB with it.
- Still to verify: the pack id strings on NeoForge.

## 8. Server behaviour

- Build the payload lazily on the first request and on each datapack reload, from `server.getResourceManager()` (resolved winners, so pack priority is applied). Build off the tick thread. Cache the compressed bytes and the hash. Invalidate on reload.
- On a request: reject if protocol differs, if the player is on cooldown (default 60 s), if `canSend(sync_chunk)` is false, or if the feature is disabled (header status 2). If `cachedHash` equals the current hash and the skip set matches, answer status 0 and send nothing.
- Stream chunks at most 2 per player and 6 in total per tick. Stop if the player disconnects.
- Two limits: requests closer than 2 s apart are ignored (a flood costs almost nothing); a request that would start a transfer must wait out the cooldown (answered `refused`), while a request whose cached hash is current is answered `unchanged` at any time. The transfer timestamp survives a disconnect, so reconnecting in a loop does not dodge the cooldown.
- Reading the server's files happens on a worker thread and is handed to the server thread on the next tick; until it is ready requests are answered `refused` without using up the cooldown.
- Config `config/cobbledex-server.json`: `enabled`, `chunkKB` (default 256, max 512), `cooldownSeconds`, `excludeSpecies[]`, `excludeFiles[]` (path globs), `maxPayloadMB`, `ops_only`.
- Logging: one line per transfer (player, bytes, chunks, ms). Nothing on rejected spam beyond a counter.

## 9. Client behaviour

- On join to a non-local world, if `canSend(sync_request)`, send the request at once. The saved copy (below) is loaded in parallel and offered by hash.
- Save each transfer on disk, one file per server address, in `cobbledex-server-cache/` in the game folder (same convention as the sprite atlas), written atomically, hash-checked on load, most recent 8 servers kept. `unchanged` is only accepted for a hash we offered; an unreadable saved copy is deleted and the server is asked again without a hash. A refusal, a failure or a timeout keeps whatever copy is already in use.
- A saved copy is not used when the server does not register the channel (the mod may have been removed).
- A request nobody answers ends after 15 s; a transfer that stalls ends after 30 s.
- Parse on a worker thread. Swap the overlay in atomically, then rebuild the affected indexes through the existing late-ready mechanism.
- While server data is active `JarDataCache` reconstructs the server's own view: this client's copies of the mods the server confirmed, plus every file the server sent, and nothing from the client's own `datapacks/` or `resourcepacks/` folders (those are not on the server). Categories that are not mirrored (fossils, TMs, marks, type charts, mega stones) are read locally as before. Leaving the server rebuilds from the client's own files.
- The server's files go through the same parsers local files do; the three copies of the spawn-file ingestion were unified into one routine on the way.
- New `DataSourceTier.SERVER_DATA`; it counts as verified, so the "local files only" caveat and the "tutor moves unknown on servers" note no longer appear for the data it supplies.
- `/cobbledex stats` reports: companion present or not, transfer size and time, categories applied, cache hit.
- Any failure: log once, keep local data, no user-visible error beyond the stats line.

## 10. Failure modes

| Situation | Behaviour |
|---|---|
| Server has no companion | `canSend` is false, no request, today's behaviour |
| Client has no CobbleDex | `canSend` false, server never sends. Request never arrives |
| Protocol version differs | different channel id, ignored on both sides |
| Chunk lost or corrupt | transfer dropped, local data used |
| Client disconnects mid-stream | server stops sending, frees the transfer |
| Request spam | cooldown, one counter line |
| Payload over `maxPayloadMB` | status 2, nothing sent |
| Server reload mid-transfer | transfer finishes with the old snapshot, next join gets the new |
| Handler throws | caught and logged, local data used |

## 11. Test plan (real server + mcdev client)

Everything below runs against `dev/test-server` and the mcdev-controlled clients.

Functional
- CobbleDex client joins a server with the companion: all categories applied, pages correct, stats line right.
- Reconnect: cache hit, zero chunks sent.
- Datapack reload on the server, rejoin: new hash, new data.
- Two clients joining at once.

Compatibility
- Client without CobbleDex joins a server with the companion (including a forced oversized send in a test build): no kick, nothing sent.
- CobbleDex client joins a server without the companion: no request, unchanged.
- Cobblemon 1.7.3 client and a 1.7.3 server pair, and mixed 1.7.3/1.8.2 pairs.
- NeoForge server with a NeoForge client and with a Fabric client.

Hostile / failure (done with a separate throwaway server mod that impersonates the server half and sends deliberately broken transfers to the real client, so the shipped jar contains no test hooks)
- Drop a chunk, reorder, duplicate, corrupt a byte, wrong hash, wrong length.
- Disconnect mid-transfer.
- Request flood.
- Synthetic payload above the cap and chunk size above 900 KB: the sender must refuse before the wire.

Regression
- Dump `SpawnDataIndex` with and without the companion and diff against 2.31.2 on 1.7.3 and 1.8.2. With no companion the dump must be identical.

## 12. Milestones

1. Protocol module plus server on Fabric, with a test client that only logs. Run the hostile tests.
2. Client request, reassembly, cache and overlay for species traits, moves and evolutions.
3. Spawn pools, presets and habitat pools.
4. Skip-lists (`mod/cobblemon`, `knownMods`), exclude list, config.
5. NeoForge server and client.
6. Hardening pass, regression diffs, changelog and docs.
7. Remove the netprobe and any test hooks, rebuild, confirm the shipped jars contain none.

## 12a. Results so far (Fabric)

- 250+ unit tests (codecs, assembler, writer, service, config, cache, data view, a guard that keeps client classes out of the server packages).
- 30+ hostile transfers on the real wire (gap, duplicate, reorder, corrupt byte, wrong hash, short or long final chunk, absurd counts and sizes, decompression bomb, garbage, bad JSON, wrong version, malformed or empty headers, flood, stall): all rejected with a reason, no player kicked, stall ends at 30 s.
- Real server: normal join, reconnect with saved copy (`unchanged`), `/reload`, an old 2.31.2 client joining, a client with the data sees a pack-only species with its tutor moves, spawns, evolution and catch rate.
- Regression: all 13 `JarDataCache` maps are byte-identical to released 2.31.2 on Cobblemon 1.8.2 and on 1.7.3, with no companion.
- Cobblemon 1.7.3: a 1.7.3 server with a 1.7.3 client (Cobblemon itself syncs no tutor, TM or egg moves there); an invented test species arrived with its tutor, egg and TM moves, spawn entry, catch rate, egg groups and source pack.
- NeoForge 21.1.264 (real dedicated server, client launched with PortableMC): server plus client (1,939 files, 394 KB, 2 chunks; 2,822 files skipped because the client has those mods), saved-copy reconnect (`unchanged`, no chunks), a client without CobbleDex joins and is never sent anything, a CobbleDex client on a server without it falls back to its own files. NeoForge pack ids are `mod/<id>` and `file/<name>.zip`.
- Fabric: a client with the new jar on a server running the released 2.31.2 sends nothing and its data is untouched; a released 2.31.2 client joins a new server without trouble.

## 12b. Viewer checks (all three, on the real server, pack-only species `dralily`)

REI, EMI and JEI each show Overview (Catch 45, 15 tutors, 4 spawns, 1 evolution, Source Lumoria 1.6.7), Spawn Locations (4 ways, Frozen Ocean) and Evolution (Drimid to Dralily, Lv. 50+). EMI and JEI were also checked when the data arrives after the viewer registered (fresh transfer): both reload and show it. REI had a pre-existing race (it could register 0 Pokémon entries on a server because it reloads before the index is built) which is fixed by asking REI to reload when its registered species count differs from the index.

## 13. Open questions

- Decided: one jar per loader, same project and files (no separate server project).
- Decided: no per-field allow-list. Files are sent whole; the exclude lists in the server config are the only filter.
- Whether `ops_only` and the exclude list should default to off.
- Which Modrinth environment value we publish with (`client_only_server_optional` recommended) and whether to automate the post-upload `PATCH`.
- Whether to tag the CurseForge files `Client` + `Server`.
