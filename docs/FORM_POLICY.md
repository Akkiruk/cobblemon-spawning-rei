# Material Form Policy

CobbleDex should surface a form as a first-class entry only when the form changes player-relevant data.

## Material Differences

A form is material when it differs from the base species in at least one of these areas:

- Type or ability data.
- Base stats, EV yield, catch rate, or other mechanical species info.
- Level-up, egg, tutor, or TM move availability.
- Drops, fossils, evolution lines, riding data, or CobbleCrew job relevance.
- Spawn data or custom obtainment data specific to that form.
- Any other gameplay-facing rule the player would reasonably need to know.

## Cosmetic Differences

Texture-only forms should not become separate CobbleDex pages, sidebar entries, search entries, icon identities, sync identities, or export identities by default.

If a form only changes texture, model, color, or name, it should remain folded into the base species.

## Consistency Rule

The same material-form decision should be reused across:

- REI, JEI, and EMI entry registration.
- Forms pages.
- Search aliases.
- Reverse lookups.
- Diagnostics.
- Export identity.

If a future change needs an exception, document the reason in the PR.