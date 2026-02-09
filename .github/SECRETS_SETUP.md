# CURSEFORGE SECRETS SETUP

## What You Need

To publish automatically to CurseForge, you need:

1. **CurseForge API Token** (from https://www.curseforge.com/settings/api-tokens)
2. **CurseForge Project ID** (from your project page)
3. **Modrinth API Token** (optional, from https://modrinth.com/settings/account)
4. **Modrinth Project ID** (optional, your project slug)

## Step-by-Step

### 1. Get CurseForge API Token

1. Go to https://www.curseforge.com/settings/api-tokens
2. Click **"Generate Token"**
3. Name: `GitHub Actions - CobblemonSpawningREI`
4. **COPY THE TOKEN** (you can't see it again!)

### 2. Get CurseForge Project ID

1. Go to your CurseForge project: https://www.curseforge.com/minecraft/mc-mods/cobblemon-spawning-rei
2. Click **"Manage Project"** → **"Settings"**
3. Find **"Project ID"** (it's a number like `123456`)

### 3. Add to GitHub

1. Go to: https://github.com/Akkiruk/cobblemon-spawning-rei/settings/secrets/actions
2. Click **"New repository secret"**
3. Add each secret:

   **Secret 1:**
   - Name: `CURSEFORGE_TOKEN`
   - Value: (paste your API token)

   **Secret 2:**
   - Name: `CURSEFORGE_PROJECT_ID`
   - Value: (paste your project ID)

   **Secret 3** (optional for Modrinth):
   - Name: `MODRINTH_TOKEN`
   - Value: (your Modrinth API token)

   **Secret 4** (optional for Modrinth):
   - Name: `MODRINTH_PROJECT_ID`
   - Value: (your Modrinth project slug)

## Done!

Once secrets are added, every time you push a git tag (like `v1.4.3`), the workflow will:
- Build the mod
- Create a GitHub release
- Upload to CurseForge
- Upload to Modrinth (if configured)

## Test It

To test the setup:

```bash
cd "C:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\CobblemonSpawningREI"

# Update version in gradle.properties to 1.4.3
# Update CHANGELOG.md with changes

git add gradle.properties CHANGELOG.md
git commit -m "[1.4.3] Initial automated release setup"
git tag -a v1.4.3 -m "Release version 1.4.3"
git push origin main --tags
```

Then go to https://github.com/Akkiruk/cobblemon-spawning-rei/actions to watch it run!
