# Data Precedence

CobbleDex should prefer the data source that best matches what the player will experience in the current pack or server.

## Source Order

1. Authoritative server sync.
2. Live runtime data from the installed game.
3. Jar, mod, or datapack fallback data.
4. Bundled defaults.

## Practical Rules

- Server sync wins because it reflects the server the player is actually connected to.
- Runtime data wins over static fallback because loaded mods and datapacks may transform behavior.
- Jar and datapack fallback fills gaps when runtime APIs are unavailable or incomplete.
- Bundled defaults should only fill known optional-integration gaps, and should never pretend to be authoritative pack truth.

## Diagnostics

Diagnostics should make these questions easy to answer:

- Which source won for this domain?
- Which domains are partial or missing?
- Which species have only fallback or bundled information?
- Where do optional integrations contribute data?

When sources disagree, prefer the source closest to actual player experience and make the disagreement inspectable.