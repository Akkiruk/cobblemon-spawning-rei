# Rebrand Audit: "Cobblemon Spawning REI" → "Cobbledex"

## Summary

This audit covers **every file and reference** that needs to change to rebrand the mod from "Cobblemon Spawning for REI/JEI/EMI" to "Cobbledex".

**Total files affected: ~120+**
- 78 Kotlin source files (all contain `package com.cobblemonrei` at minimum)
- 28 language JSON files (all translation keys prefixed `cobblemon-spawning-rei.`)
- 1 fabric.mod.json
- 1 neoforge.mods.toml
- 3 build.gradle.kts files (root + fabric + neoforge)
- 1 settings.gradle.kts
- 1 gradle.properties
- 3 GitHub workflow files
- 7+ documentation/config files
- 1 PowerShell script
- Asset directory rename

---

## Category 1: Mod Identity (CRITICAL — breaks the mod if missed)

### 1A. Mod IDs

| Location | Old Value | New Value |
|----------|-----------|-----------|
| `gradle.properties` → `mod_id` | `cobblemon-spawning-rei` | `cobbledex` |
| `CobblemonSpawningMod.kt` → `MOD_ID` | `"cobblemon-spawning-rei"` | `"cobbledex"` |
| `CobblemonSpawningMod.kt` → `NEOFORGE_MOD_ID` | `"cobblemon_spawning_rei"` | `"cobbledex"` |
| `fabric.mod.json` → `"id"` | `"cobblemon-spawning-rei"` | `"cobbledex"` |
| `neoforge.mods.toml` → `modId` | `"cobblemon_spawning_rei"` | `"cobbledex"` |
| `neoforge.mods.toml` → `[[dependencies.*]]` (×5) | `cobblemon_spawning_rei` | `cobbledex` |

### 1B. Package Names (com.cobblemonrei → com.cobbledex)

**ALL 78 .kt files** have `package com.cobblemonrei` or subpackages. Every single Kotlin source file needs:
1. `package` declaration updated
2. All `import com.cobblemonrei.*` statements updated
3. Physical directory structure renamed: `com/cobblemonrei/` → `com/cobbledex/`

**Directory renames required:**
- `common/src/main/kotlin/com/cobblemonrei/` → `common/src/main/kotlin/com/cobbledex/`
- `fabric/src/main/kotlin/com/cobblemonrei/` → `fabric/src/main/kotlin/com/cobbledex/`
- `neoforge/src/main/kotlin/com/cobblemonrei/` → `neoforge/src/main/kotlin/com/cobbledex/`

**Affected packages:**
- `com.cobblemonrei` (root — 21 files)
- `com.cobblemonrei.config` (1 file)
- `com.cobblemonrei.emi` (6 files)
- `com.cobblemonrei.jei` (8+ files)
- `com.cobblemonrei.jei.drops` (2 files)
- `com.cobblemonrei.jei.evolution` (2 files)
- `com.cobblemonrei.jei.obtainment` (2 files)
- `com.cobblemonrei.jei.pokedex` (2 files)
- `com.cobblemonrei.jei.spawn` (2 files)
- `com.cobblemonrei.jei.stats` (2 files)
- `com.cobblemonrei.network` (4 files)
- `com.cobblemonrei.platform` (1 file)
- `com.cobblemonrei.platform.fabric` (1 file)
- `com.cobblemonrei.platform.neoforge` (1 file)
- `com.cobblemonrei.rei` (1 file)
- `com.cobblemonrei.rei.drops` (2 files)
- `com.cobblemonrei.rei.entry` (4 files)
- `com.cobblemonrei.rei.evolution` (2 files)
- `com.cobblemonrei.rei.obtainment` (2 files)
- `com.cobblemonrei.rei.pokedex` (2 files)
- `com.cobblemonrei.rei.spawn` (2 files)
- `com.cobblemonrei.rei.stats` (2 files)
- `com.cobblemonrei.server` (1 file)
- `com.cobblemonrei.fabric` (2 files)
- `com.cobblemonrei.neoforge` (5 files)

### 1C. Maven Group

| Location | Old Value | New Value |
|----------|-----------|-----------|
| `gradle.properties` → `maven_group` | `com.cobblemonrei` | `com.cobbledex` |

### 1D. Assets Directory

**Physical directory rename:**
`common/src/main/resources/assets/cobblemon-spawning-rei/` → `common/src/main/resources/assets/cobbledex/`

Contains:
- `icon.png`
- `lang/` (28 language files)
- `textures/pokemon/` (sprite assets)

**References to this path:**
- `fabric.mod.json` → `"icon": "assets/cobblemon-spawning-rei/icon.png"` → `"assets/cobbledex/icon.png"`
- `neoforge.mods.toml` → `logoFile="assets/cobblemon-spawning-rei/icon.png"` → `"assets/cobbledex/icon.png"`
- `Translations.kt` → `"/assets/cobblemon-spawning-rei/lang/en_us.json"` → `"/assets/cobbledex/lang/en_us.json"`

---

## Category 2: Translation Keys (ALL 28 language files + source code)

### 2A. Language Files

**28 JSON files** in `common/src/main/resources/assets/cobblemon-spawning-rei/lang/`:

```
cs_cz.json, de_de.json, el_cy.json, en_gb.json, en_pt.json, en_us.json,
eo_eo.json, es_es.json, es_mx.json, fr_ca.json, fr_fr.json, hu_hu.json,
it_it.json, ja_jp.json, ko_kr.json, nl_nl.json, pl_pl.json, pt_br.json,
pt_pt.json, ru_ru.json, sv_se.json, th_th.json, tr_tr.json, uk_ua.json,
vi_vn.json, zh_cn.json, zh_hk.json, zh_tw.json
```

**Every key** in every file is prefixed with `cobblemon-spawning-rei.` or `category.cobblemon-spawning-rei.`

Key prefix categories to replace:
- `category.cobblemon-spawning-rei.spawn` → `category.cobbledex.spawn`
- `category.cobblemon-spawning-rei.evolution` → `category.cobbledex.evolution`
- `category.cobblemon-spawning-rei.obtainment` → `category.cobbledex.obtainment`
- `category.cobblemon-spawning-rei.drops` → `category.cobbledex.drops`
- `category.cobblemon-spawning-rei.stats` → `category.cobbledex.stats`
- `category.cobblemon-spawning-rei.pokedex_info` → `category.cobbledex.pokedex_info`
- `cobblemon-spawning-rei.context.*` → `cobbledex.context.*`
- `cobblemon-spawning-rei.weather.*` → `cobbledex.weather.*`
- `cobblemon-spawning-rei.biome.*` → `cobbledex.biome.*`
- `cobblemon-spawning-rei.obtainment.*` → `cobbledex.obtainment.*`
- `cobblemon-spawning-rei.evo.*` → `cobbledex.evo.*`
- `cobblemon-spawning-rei.bucket.*` → `cobbledex.bucket.*`
- `cobblemon-spawning-rei.preset.*` → `cobbledex.preset.*`
- `cobblemon-spawning-rei.spawn.*` → `cobbledex.spawn.*`
- `cobblemon-spawning-rei.fluid.*` → `cobbledex.fluid.*`
- `cobblemon-spawning-rei.dimension.*` → `cobbledex.dimension.*`
- `cobblemon-spawning-rei.tooltip.*` → `cobbledex.tooltip.*`
- `cobblemon-spawning-rei.source.*` → `cobbledex.source.*`
- `cobblemon-spawning-rei.cmd.*` → `cobbledex.cmd.*`
- `cobblemon-spawning-rei.drops.*` → `cobbledex.drops.*`
- `cobblemon-spawning-rei.info.*` → `cobbledex.info.*`

### 2B. Source Code Using Translation Keys

Files with hardcoded translation key strings (using `tr("cobblemon-spawning-rei....")` or `Component.translatable("category.cobblemon-spawning-rei....")`):

| File | Approx. Count |
|------|---------------|
| `EvolutionInfo.kt` | ~70+ occurrences |
| `SpawnDisplayHelper.kt` | ~50+ occurrences |
| `DiagnosticService.kt` | ~15 occurrences |
| `DisplayLayout.kt` | ~3 occurrences |
| `Translations.kt` | ~4 occurrences |
| `rei/drops/DropCategory.kt` | 1 |
| `rei/stats/StatsCategory.kt` | 1 |
| `rei/pokedex/PokedexInfoCategory.kt` | 1 |
| `rei/spawn/SpawnCategory.kt` | 1 |
| `rei/evolution/EvolutionCategory.kt` | 1 |
| `rei/obtainment/ObtainmentCategory.kt` | 1 |
| `jei/drops/JeiDropCategory.kt` | 1 |
| `jei/stats/JeiStatsCategory.kt` | 1 |
| `jei/pokedex/JeiPokedexInfoCategory.kt` | 1 |
| `jei/spawn/JeiSpawnCategory.kt` | 1 |
| `jei/evolution/JeiEvolutionCategory.kt` | 1 |
| `jei/obtainment/JeiObtainmentCategory.kt` | 1 |

---

## Category 3: Class Names

### 3A. Classes to Rename

| Current Class | New Class | File |
|---------------|-----------|------|
| `CobblemonSpawningMod` | `CobbledexMod` | `CobblemonSpawningMod.kt` |
| `CobblemonSpawningConfig` | `CobbledexConfig` | `config/CobblemonSpawningConfig.kt` |
| `CobblemonSpawningFabric` | `CobbledexFabric` | `fabric/.../CobblemonSpawningFabric.kt` |
| `CobblemonSpawningFabricClient` | `CobbledexFabricClient` | `fabric/.../CobblemonSpawningFabricClient.kt` |
| `CobblemonSpawningNeoForge` | `CobbledexNeoForge` | `neoforge/.../CobblemonSpawningNeoForge.kt` |
| `CobblemonSpawningNeoForgeClient` | `CobbledexNeoForgeClient` | `neoforge/.../CobblemonSpawningNeoForgeClient.kt` |
| `CobblemonSpawningNeoForgeREI` | `CobbledexNeoForgeREI` | `neoforge/.../CobblemonSpawningNeoForgeREI.kt` |
| `CobblemonSpawningNeoForgeJEI` | `CobbledexNeoForgeJEI` | `neoforge/.../CobblemonSpawningNeoForgeJEI.kt` |
| `CobblemonSpawningNeoForgeEMI` | `CobbledexNeoForgeEMI` | `neoforge/.../CobblemonSpawningNeoForgeEMI.kt` |
| `CobblemonREIClientPlugin` | `CobbledexREIClientPlugin` | `rei/CobblemonREIClientPlugin.kt` |
| `CobblemonJEIPlugin` | `CobbledexJEIPlugin` | `jei/CobblemonJEIPlugin.kt` |
| `CobblemonEMIPlugin` | `CobbledexEMIPlugin` | `emi/CobblemonEMIPlugin.kt` |

### 3B. Class Name References in Metadata

| File | Reference |
|------|-----------|
| `fabric.mod.json` → entrypoints.main | `com.cobblemonrei.fabric.CobblemonSpawningFabric` |
| `fabric.mod.json` → entrypoints.client | `com.cobblemonrei.fabric.CobblemonSpawningFabricClient` |
| `fabric.mod.json` → entrypoints.rei_client | `com.cobblemonrei.rei.CobblemonREIClientPlugin` |
| `fabric.mod.json` → entrypoints.jei_mod_plugin | `com.cobblemonrei.jei.CobblemonJEIPlugin` |
| `fabric.mod.json` → entrypoints.emi | `com.cobblemonrei.emi.CobblemonEMIPlugin` |

### 3C. Files to Physically Rename

| Current Filename | New Filename |
|------------------|--------------|
| `CobblemonSpawningMod.kt` | `CobbledexMod.kt` |
| `CobblemonSpawningConfig.kt` | `CobbledexConfig.kt` |
| `CobblemonSpawningFabric.kt` | `CobbledexFabric.kt` |
| `CobblemonSpawningFabricClient.kt` | `CobbledexFabricClient.kt` |
| `CobblemonSpawningNeoForge.kt` | `CobbledexNeoForge.kt` |
| `CobblemonSpawningNeoForgeClient.kt` | `CobbledexNeoForgeClient.kt` |
| `CobblemonSpawningNeoForgeREI.kt` | `CobbledexNeoForgeREI.kt` |
| `CobblemonSpawningNeoForgeJEI.kt` | `CobbledexNeoForgeJEI.kt` |
| `CobblemonSpawningNeoForgeEMI.kt` | `CobbledexNeoForgeEMI.kt` |
| `CobblemonREIClientPlugin.kt` | `CobbledexREIClientPlugin.kt` |
| `CobblemonJEIPlugin.kt` | `CobbledexJEIPlugin.kt` |
| `CobblemonEMIPlugin.kt` | `CobbledexEMIPlugin.kt` |

---

## Category 4: Build Configuration

### 4A. Gradle Files

| File | Property | Old Value | New Value |
|------|----------|-----------|-----------|
| `gradle.properties` | `mod_id` | `cobblemon-spawning-rei` | `cobbledex` |
| `gradle.properties` | `maven_group` | `com.cobblemonrei` | `com.cobbledex` |
| `gradle.properties` | `archives_base_name` | `cobblemon-spawning-rei` | `cobbledex` |
| `settings.gradle.kts` | `rootProject.name` | `"cobblemon-spawning-rei"` | `"cobbledex"` |
| `fabric/build.gradle.kts` | `archivesName` | `"cobblemon-spawning-rei-fabric"` | `"cobbledex-fabric"` |
| `neoforge/build.gradle.kts` | `archivesName` | `"cobblemon-spawning-rei-neoforge"` | `"cobbledex-neoforge"` |

### 4B. JAR Output Names (downstream of gradle changes)

- `cobblemon-spawning-rei-fabric-X.Y.Z.jar` → `cobbledex-fabric-X.Y.Z.jar`
- `cobblemon-spawning-rei-neoforge-X.Y.Z.jar` → `cobbledex-neoforge-X.Y.Z.jar`

---

## Category 5: Logger & Debug Output

| File | Old Value | New Value |
|------|-----------|-----------|
| `DebugLog.kt` (×9 occurrences) | `"[CobblemonSpawningREI] ..."` | `"[Cobbledex] ..."` |
| `CobblemonSpawningFabricClient.kt` | `"[CobblemonSpawningREI] Fabric client initialized"` | `"[Cobbledex] Fabric client initialized"` |
| `SpawnDataIndex.kt` → thread name | `"CobblemonSpawningREI-DataLoad"` | `"Cobbledex-DataLoad"` |
| `DiagnosticService.kt` → debug directory | `"spawningrei-debug"` | `"cobbledex-debug"` (or similar) |

---

## Category 6: Commands

| File | Old Command | New Command |
|------|-------------|-------------|
| `CobblemonSpawningFabricClient.kt` | `/spawningrei` | `/cobbledex` |
| `CobblemonSpawningNeoForgeClient.kt` | `/spawningrei` | `/cobbledex` |

---

## Category 7: Config File

| Location | Old Value | New Value |
|----------|-----------|-----------|
| `CobblemonSpawningConfig.kt` (×2) | `"cobblemon-spawning-rei.json"` | `"cobbledex.json"` |
| Player's `config/` folder | `cobblemon-spawning-rei.json` | `cobbledex.json` |

**Note:** Consider adding migration logic to read the old config file if the new one doesn't exist, to preserve user settings on upgrade.

---

## Category 8: GitHub & CI/CD

### 8A. Workflows

**`.github/workflows/publish.yml`:**
- Line 66: `cobblemon-spawning-rei-fabric-` → `cobbledex-fabric-`
- Line 67: `cobblemon-spawning-rei-neoforge-` → `cobbledex-neoforge-`
- Line 236: artifact name `cobblemon-spawning-rei-fabric-` → `cobbledex-fabric-`
- Line 238: glob `cobblemon-spawning-rei-fabric-*.jar` → `cobbledex-fabric-*.jar`
- Line 245: artifact name `cobblemon-spawning-rei-neoforge-` → `cobbledex-neoforge-`
- Line 247: glob `cobblemon-spawning-rei-neoforge-*.jar` → `cobbledex-neoforge-*.jar`

**`.github/workflows/build.yml`:**
- Check for any JAR name references

### 8B. Issue Templates

**`.github/ISSUE_TEMPLATE/bug_report.yml`:**
- "Cobblemon Spawning for REI/JEI/EMI" → "Cobbledex"

**`.github/ISSUE_TEMPLATE/feature_request.yml`:**
- "Cobblemon Spawning for REI/JEI/EMI" → "Cobbledex"

**`.github/ISSUE_TEMPLATE/config.yml`:**
- `https://github.com/Akkiruk/cobblemon-spawning-rei/discussions` → new repo URL
- `https://www.curseforge.com/minecraft/mc-mods/cobblemon-spawning-rei` → new CurseForge URL
- `https://github.com/Akkiruk/cobblemon-spawning-rei/blob/main/README.md` → new repo URL

### 8C. Other GitHub Files

**`.github/PUBLISHING_SETUP.md`:**
- Multiple references to "Cobblemon Spawning REI/JEI/EMI"
- GitHub repository URL references

**`.github/PULL_REQUEST_TEMPLATE.md`:**
- Recipe viewer related references

**`.github/FUNDING.yml`:** No changes needed (just `github: [Akkiruk]`)
**`.github/labeler.yml`:** No changes needed (path-based only)
**`.github/dependabot.yml`:** No changes needed

---

## Category 9: Documentation

### 9A. Files Requiring Full Rewrite/Search-Replace

| File | Key References |
|------|----------------|
| `README.md` | Title, badges, URLs, JAR names, git clone URL, download links, CurseForge/Modrinth links |
| `CHANGELOG.md` (525 lines) | "Cobblemon Spawning REI" in header and throughout entries |
| `PLATFORM_DESCRIPTION.md` | Multiple old name and GitHub URL references |
| `AUDIT_SUMMARY.md` (220 lines) | Multiple "CobblemonSpawningREI" references |
| `MANUAL_TASKS.md` | Multiple references and URLs |
| `LICENSE` | No name changes needed (MIT license) |

### 9B. Audit Files

| File | References |
|------|------------|
| `audits/COMPATIBILITY_AUDIT.md` | "CobblemonSpawningREI" references |
| `audits/POKEDEX_API_AUDIT.md` | "CobblemonSpawningREI" references |

---

## Category 10: Platform Publication

### 10A. CurseForge & Modrinth

**`update-platform-descriptions.ps1`:**
- `$curseForgeSlug = "cobblemon-spawning-rei"` → `"cobbledex"` (or new slug)
- `$modrinthId = "cobblemon-spawning-rei"` → new Modrinth ID
- GitHub URLs throughout

**Note:** New CurseForge and Modrinth project pages will likely need to be created since slugs/IDs change. The old pages can redirect or be updated.

### 10B. Metadata Display Names

| File | Field | Old Value | New Value |
|------|-------|-----------|-----------|
| `fabric.mod.json` | `"name"` | `"Cobblemon Spawning for REI/JEI/EMI"` | `"Cobbledex"` |
| `neoforge.mods.toml` | `displayName` | `"Cobblemon Spawning for REI/JEI/EMI"` | `"Cobbledex"` |

### 10C. URLs

| File | Old URL | New URL |
|------|---------|---------|
| `fabric.mod.json` | `https://github.com/Akkiruk/cobblemon-spawning-rei` | New repo URL |
| `fabric.mod.json` | `https://github.com/Akkiruk/cobblemon-spawning-rei/issues` | New repo URL |
| `neoforge.mods.toml` | `https://github.com/Akkiruk/cobblemon-spawning-rei` | New repo URL |
| `neoforge.mods.toml` | `https://github.com/Akkiruk/cobblemon-spawning-rei/issues` | New repo URL |

---

## Category 11: External/Instance References

These are **outside the project** but in the workspace:

| File/Location | Reference |
|---------------|-----------|
| `mods/cobblemon-spawning-rei-fabric-*.jar` | Deploy path in mods folder |
| `config/cobblemon-spawning-rei.json` | Config file in instance config folder |
| `spawningrei-debug/` | Debug output folder in instance root |
| `copilot-instructions.md` | All references to CobblemonSpawningREI patterns |
| `COBBLEVERSE_INFO_INDEX.md` | May contain references |

---

## Execution Plan (Recommended Order)

### Phase 1: Core Identity
1. Update `gradle.properties` (mod_id, maven_group, archives_base_name)
2. Update `settings.gradle.kts` (rootProject.name)
3. Rename asset directory `assets/cobblemon-spawning-rei/` → `assets/cobbledex/`

### Phase 2: Package Rename
4. Rename all `com/cobblemonrei/` directories → `com/cobbledex/`
5. Update all `package` declarations in 78 .kt files
6. Update all `import com.cobblemonrei.*` statements

### Phase 3: Class Renames
7. Rename 12 class files (CobblemonSpawning* → Cobbledex*)
8. Update all class name references throughout codebase

### Phase 4: Translation Keys
9. Find-replace `cobblemon-spawning-rei.` → `cobbledex.` in all 28 lang files
10. Find-replace translation key strings in ~15 source files

### Phase 5: Metadata & Config
11. Update `fabric.mod.json`
12. Update `neoforge.mods.toml`
13. Update `fabric/build.gradle.kts` and `neoforge/build.gradle.kts`
14. Update config filename in `CobbledexConfig.kt`

### Phase 6: Logger/Debug/Commands
15. Replace `[CobblemonSpawningREI]` logger prefix → `[Cobbledex]`
16. Update `/spawningrei` command → `/cobbledex`
17. Update debug directory name
18. Update thread name

### Phase 7: CI/CD & Documentation
19. Update all GitHub workflow files
20. Update issue templates
21. Update README.md, CHANGELOG.md, PLATFORM_DESCRIPTION.md
22. Update `update-platform-descriptions.ps1`
23. Update PUBLISHING_SETUP.md, MANUAL_TASKS.md

### Phase 8: Build & Verify
24. Run `gradlew clean :fabric:build :neoforge:build`
25. Verify both JARs produced with new names
26. Test in-game

---

## Risk Assessment

**HIGH RISK:**
- Missing a `package` or `import` statement → compile failure
- Missing an asset path reference → icon/translations not loading
- Missing a mod ID reference → mod fails to register
- `neoforge.mods.toml` dependency sections using old mod ID → crash on NeoForge

**MEDIUM RISK:**
- Translation key mismatch between source code and lang files → untranslated strings
- Config file name change without migration → users lose settings

**LOW RISK:**
- Documentation references → cosmetic only
- GitHub URLs → links break but mod works
- Debug directory name → non-functional

---

*Generated: Comprehensive rebrand reference for CobblemonSpawningREI → Cobbledex*
