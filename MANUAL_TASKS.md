# Manual Tasks: Update All Sites for REI/JEI/EMI Support

All code and documentation files have been automatically updated. Here's what you need to do manually:

## ✅ Completed Automatically

- [x] README.md updated with REI/JEI/EMI support
- [x] fabric.mod.json description updated
- [x] neoforge.mods.toml description updated
- [x] PLATFORM_DESCRIPTION.md created for reference
- [x] update-platform-descriptions.ps1 script created

## ⏳ Manual Tasks Required

### 1. Update CurseForge Project Page

**Why manual:** CurseForge API doesn't support project description updates via API.

**Steps:**
1. Go to: https://www.curseforge.com/minecraft/mc-mods/cobblemon-spawning-rei/files
2. Click "Edit" on the project
3. Update the **Summary** field to:
   ```
   View Cobblemon spawn locations & evolution chains in REI, JEI, or EMI. Works with any of the three recipe viewers!
   ```
4. Update the **Description** field with the content from `PLATFORM_DESCRIPTION.md` (Full Description section)
5. Save changes

### 2. Update Modrinth Project Page

**Option A: Using API (Recommended)**

Run this command in PowerShell with your Modrinth token:
```powershell
$env:MODRINTH_TOKEN = "your-token-here"
.\update-platform-descriptions.ps1
```

**Option B: Manual Update**

1. Go to: https://modrinth.com/mod/cobblemon-spawning-rei/settings
2. Update the **Summary** field to:
   ```
   View Cobblemon spawn locations & evolution chains in REI, JEI, or EMI. Works with any of the three recipe viewers!
   ```
3. Update the **Description** field with the content from `PLATFORM_DESCRIPTION.md` (Full Description section)
4. Save changes

### 3. Update GitHub Repository

**Repository Description:**
1. Go to: https://github.com/Akkiruk/cobblemon-spawning-rei
2. Click the gear icon ⚙️ next to "About"
3. Update **Description** to:
   ```
   View Cobblemon spawn & evolution info in REI, JEI, or EMI. Pure client-side Architectury mod for Minecraft 1.21.1 with Cobblemon.
   ```
4. Update **Topics** to include:
   - `minecraft`
   - `cobblemon`
   - `rei`
   - `jei`
   - `emi`
   - `recipe-viewer`
   - `fabric`
   - `neoforge`
   - `architectury`
   - `pokemon`
   - `spawning`
   - `evolution`
5. Save changes

### 4. Commit and Push Changes

```bash
cd "C:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\CobblemonSpawningREI"
git add .
git commit -m "Update all documentation to reflect REI/JEI/EMI support"
git push origin main
```

### 5. Create a New Release (Optional but Recommended)

Since this is a significant documentation update that clarifies multi-viewer support, consider creating a new patch release:

1. Update `gradle.properties` version (e.g., 1.10.3)
2. Update `CHANGELOG.md`:
   ```markdown
   ## [1.10.3] - 2026-02-11
   
   ### Changed
   - Updated all documentation to clearly show REI, JEI, and EMI support
   - Renamed mod display name to "Cobblemon Spawning for REI/JEI/EMI"
   ```
3. Build and release:
   ```bash
   git add .
   git commit -m "[1.10.3] Documentation update for multi-viewer support"
   git tag -a v1.10.3 -m "Release version 1.10.3"
   git push origin main --tags
   ```

## 📋 Quick Checklist

- [ ] CurseForge description updated
- [ ] Modrinth description updated  
- [ ] GitHub repository description updated
- [ ] GitHub repository topics/tags updated
- [ ] Changes committed to git
- [ ] Changes pushed to GitHub
- [ ] (Optional) New release created

## 🔗 Quick Links

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/cobblemon-spawning-rei
- Modrinth: https://modrinth.com/mod/cobblemon-spawning-rei
- GitHub: https://github.com/Akkiruk/cobblemon-spawning-rei

## 📝 Notes

- The mod has always supported REI/JEI/EMI — this is purely a documentation update
- No code changes are required beyond the metadata files
- You can verify the publish workflow already includes REI/JEI/EMI as optional dependencies in `.github/workflows/publish.yml`
