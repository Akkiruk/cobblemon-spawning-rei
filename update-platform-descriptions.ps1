# Update CurseForge and Modrinth Project Descriptions
# This script updates the project descriptions on both platforms to reflect REI/JEI/EMI support

param(
    [string]$CurseForgeToken = $env:CURSEFORGE_TOKEN,
    [string]$ModrinthToken = $env:MODRINTH_TOKEN,
    [string]$CurseForgeProjectId = "cobblemon-spawning-rei",  # Update if different
    [string]$ModrinthProjectId = "cobblemon-spawning-rei"     # Update if different
)

# Short description for both platforms
$shortDescription = "View Cobblemon spawn locations & evolution chains in REI, JEI, or EMI. Works with any of the three recipe viewers!"

# Full description (markdown format)
$fullDescription = @"
Cobblemon Spawning for REI/JEI/EMI integrates Cobblemon spawn data and evolution chains into your favorite recipe viewer mod. **Works with REI, JEI, AND EMI** — install any one of them and the mod will automatically integrate!

## ✨ Multi-Viewer Support

This mod natively supports:
- **REI** (Roughly Enough Items)
- **JEI** (Just Enough Items)
- **EMI** (Enough Mod Items)

You only need ONE of these installed. The mod detects which viewer you have and integrates seamlessly!

## 🎯 Features

**Spawn Locations**
- Browse spawn conditions for every Pokémon in your modpack
- Biomes, time of day, weather, light level, Y-level, structures, and more
- Rarity tiers with color coding (Common / Uncommon / Rare / Ultra Rare)
- Weight multipliers and anti-conditions displayed
- Supports spawns from Cobblemon, datapacks, and other mods

**Evolution Chains**
- Full evolution requirements pulled from Cobblemon's runtime API
- Level-up, item use, trade, friendship, time of day, biome, held item, and dozens more
- Form-specific evolutions (regional variants, gender-based, etc.)
- Branching evolutions shown with branch indicators

**Special Obtainment**
- Shows how to obtain legendary/mythical Pokémon via altars, shrines, and special methods
- Supports LumyMon summoning altars and resurrection machines
- Custom datapack support for modpacks with unique obtainment methods

**Universal Integration**
- Pokémon rendered as 3D models using Cobblemon's own rendering
- Searchable by species name in your recipe viewer
- Click any Pokémon to view its spawn or evolution displays
- Native plugins for each viewer (no compatibility layers needed)

## 📦 Requirements

**Fabric:**
- Minecraft 1.21.1
- Fabric Loader 0.15.0+
- Fabric API 0.116.7+
- Fabric Language Kotlin 1.13.4+
- Cobblemon 1.7.1+
- **ONE of:** REI, JEI, or EMI

**NeoForge:**
- Minecraft 1.21.1
- NeoForge 21.1.77+
- Kotlin for Forge 5.11.0+
- Cobblemon 1.7.1+
- **ONE of:** REI, JEI, or EMI

## 🚀 Installation

1. Download the appropriate version for your mod loader (Fabric or NeoForge)
2. Install ONE recipe viewer mod: REI, JEI, or EMI
3. Place both mods in your ``mods/`` folder
4. Launch Minecraft

This is a **pure client-side mod** — no server installation needed!

## 🔗 Links

- [GitHub Repository](https://github.com/Akkiruk/cobblemon-spawning-rei)
- [Issue Tracker](https://github.com/Akkiruk/cobblemon-spawning-rei/issues)

## 📝 License

MIT License

## 🙏 Credits

- [Cobblemon Team](https://cobblemon.com/) for the Pokémon mod
- [shedaniel](https://github.com/shedaniel) for REI
- [mezz](https://github.com/mezz) for JEI
- [emilyploszaj](https://github.com/emilyploszaj) for EMI
- [Architectury](https://github.com/architectury/architectury-api) for multiloader support
"@

Write-Host "===========================================" -ForegroundColor Cyan
Write-Host "CurseForge & Modrinth Description Updater" -ForegroundColor Cyan
Write-Host "===========================================" -ForegroundColor Cyan
Write-Host ""

# Check if tokens are provided
if (-not $CurseForgeToken) {
    Write-Host "⚠️  CurseForge token not found. Set CURSEFORGE_TOKEN environment variable or pass -CurseForgeToken parameter." -ForegroundColor Yellow
    Write-Host "   CurseForge update will be skipped." -ForegroundColor Yellow
    Write-Host ""
}

if (-not $ModrinthToken) {
    Write-Host "⚠️  Modrinth token not found. Set MODRINTH_TOKEN environment variable or pass -ModrinthToken parameter." -ForegroundColor Yellow
    Write-Host "   Modrinth update will be skipped." -ForegroundColor Yellow
    Write-Host ""
}

# Update Modrinth
if ($ModrinthToken) {
    Write-Host "🔹 Updating Modrinth..." -ForegroundColor Cyan
    
    # Prepare JSON payload
    $modrinthPayload = @{
        description = $fullDescription
        body = $fullDescription  # Some API versions use 'body'
    } | ConvertTo-Json -Depth 10
    
    try {
        $response = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$ModrinthProjectId" `
            -Method Patch `
            -Headers @{
                "Authorization" = $ModrinthToken
                "Content-Type" = "application/json"
            } `
            -Body $modrinthPayload
        
        Write-Host "✅ Modrinth updated successfully!" -ForegroundColor Green
    } catch {
        Write-Host "❌ Modrinth update failed: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "   Response: $($_.Exception.Response)" -ForegroundColor Red
    }
    Write-Host ""
}

# Update CurseForge
if ($CurseForgeToken) {
    Write-Host "🔹 Updating CurseForge..." -ForegroundColor Cyan
    Write-Host "⚠️  Note: CurseForge API doesn't currently support description updates via API." -ForegroundColor Yellow
    Write-Host "   You'll need to update the description manually on the CurseForge project page." -ForegroundColor Yellow
    Write-Host "   The description text is saved in PLATFORM_DESCRIPTION.md" -ForegroundColor Yellow
    Write-Host ""
}

Write-Host "===========================================" -ForegroundColor Cyan
Write-Host "Summary" -ForegroundColor Cyan
Write-Host "===========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Updated files:" -ForegroundColor White
Write-Host "  ✅ README.md" -ForegroundColor Green
Write-Host "  ✅ fabric.mod.json" -ForegroundColor Green
Write-Host "  ✅ neoforge.mods.toml" -ForegroundColor Green
Write-Host "  📄 PLATFORM_DESCRIPTION.md (created)" -ForegroundColor Cyan
Write-Host ""
Write-Host "Manual tasks remaining (see below for details):" -ForegroundColor White
Write-Host "  ⏳ Update CurseForge project description" -ForegroundColor Yellow
Write-Host "  ⏳ Update GitHub repository description" -ForegroundColor Yellow
Write-Host "  ⏳ Update GitHub repository topics/tags" -ForegroundColor Yellow
Write-Host "  ⏳ Commit and push changes" -ForegroundColor Yellow
Write-Host ""
